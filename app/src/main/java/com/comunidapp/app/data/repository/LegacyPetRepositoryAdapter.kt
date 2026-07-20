package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.remote.supabase.m08.ArchivePetParams
import com.comunidapp.app.data.remote.supabase.m08.M08PetErrorMapper
import com.comunidapp.app.data.remote.supabase.m08.PetAccessContext
import com.comunidapp.app.data.remote.supabase.m08.PetCreatePartialException
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toPet
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toUpdateHealthParams
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toUpdateProfileParams
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toCreateParams
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toDomain
import com.comunidapp.app.data.remote.supabase.m08.PetM08RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m08.SetPetAvatarAssetParams
import com.comunidapp.app.data.remote.supabase.m08.SupabasePetM08RemoteDataSource
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * LeoVer M08 Etapa 4B — legacy [PetRepository] adapter over M08 RPCs + SELECT RLS.
 * Does not call the legacy pets data-source create/update/delete path.
 */
class LegacyPetRepositoryAdapter(
    private val remote: PetM08RemoteDataSource = SupabasePetM08RemoteDataSource(),
    private val authUidProvider: () -> String? = {
        supabase.auth.currentUserOrNull()?.id
    },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : PetRepository {

    private val _pets = MutableStateFlow<List<Pet>>(emptyList())

    init {
        scope.launch {
            while (isActive) {
                try {
                    _pets.value = remote.listAccessiblePets(status = "ACTIVE").map { it.toPet() }
                } catch (_: Exception) {
                    // transient poll errors ignored
                }
                delay(4_000)
            }
        }
    }

    override fun observePets(): StateFlow<List<Pet>> = _pets.asStateFlow()

    override fun observePetsForOwner(ownerId: String): Flow<List<Pet>> = flow {
        while (coroutineContext.isActive) {
            emit(loadAccessibleForOwner(ownerId))
            delay(4_000)
        }
    }

    override fun observePet(petId: String): Flow<Pet?> = flow {
        while (coroutineContext.isActive) {
            emit(fetchPetById(petId) ?: _pets.value.find { it.id == petId })
            delay(4_000)
        }
    }

    override fun getPetsByOwner(ownerId: String): List<Pet> {
        val authUid = authUidProvider()
        return if (authUid != null && authUid == ownerId) {
            _pets.value
        } else {
            emptyList()
        }
    }

    override fun getPetById(petId: String): Pet? = _pets.value.find { it.id == petId }

    override suspend fun fetchPetById(petId: String): Pet? {
        return try {
            remote.getPetById(petId)?.toPet()
        } catch (_: Exception) {
            getPetById(petId)
        }
    }

    override suspend fun createPet(pet: Pet): Result<String> {
        return try {
            val created = remote.createPetWithPrincipal(pet.toCreateParams())
            val petId = created.id
            val withId = pet.copy(id = petId, ownerId = created.ownerId)
            try {
                remote.updatePetProfile(withId.toUpdateProfileParams())
            } catch (e: Exception) {
                return Result.failure(PetCreatePartialException(petId, "profile", e))
            }
            try {
                remote.updatePetHealth(withId.toUpdateHealthParams())
            } catch (e: Exception) {
                return Result.failure(PetCreatePartialException(petId, "health", e))
            }
            refreshCache()
            Result.success(petId)
        } catch (e: Exception) {
            M08PetErrorMapper.failure(e)
        }
    }

    override suspend fun updatePet(pet: Pet): Result<Unit> {
        return try {
            remote.updatePetProfile(pet.toUpdateProfileParams())
            remote.updatePetHealth(pet.toUpdateHealthParams())
            refreshCache()
            Result.success(Unit)
        } catch (e: Exception) {
            M08PetErrorMapper.failure(e)
        }
    }

    override suspend fun deletePet(petId: String): Result<Unit> {
        return try {
            remote.archivePet(ArchivePetParams(petId = petId))
            refreshCache()
            Result.success(Unit)
        } catch (e: Exception) {
            M08PetErrorMapper.failure(e)
        }
    }

    override suspend fun getPetAccessContext(petId: String): Result<PetAccessContext> {
        return try {
            Result.success(remote.getPetAccessContext(petId).toDomain())
        } catch (e: Exception) {
            M08PetErrorMapper.failure(e)
        }
    }

    override suspend fun setPetAvatarAsset(petId: String, assetId: String?): Result<Pet> {
        return try {
            val row = remote.setPetAvatarAsset(
                SetPetAvatarAssetParams(petId = petId, assetId = assetId)
            )
            refreshCache()
            Result.success(row.toPet())
        } catch (e: Exception) {
            M08PetErrorMapper.failure(e)
        }
    }

    private suspend fun loadAccessibleForOwner(ownerId: String): List<Pet> {
        val authUid = authUidProvider()
        if (authUid == null || authUid != ownerId) return emptyList()
        return try {
            remote.listAccessiblePets(status = "ACTIVE").map { it.toPet() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun refreshCache() {
        try {
            _pets.value = remote.listAccessiblePets(status = "ACTIVE").map { it.toPet() }
        } catch (_: Exception) {
            // keep previous cache
        }
    }
}
