package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.remote.supabase.m08.ArchivePetParams
import com.comunidapp.app.data.remote.supabase.m08.DetectPetDuplicateParams
import com.comunidapp.app.data.remote.supabase.m08.M08PetErrorMapper
import com.comunidapp.app.data.remote.supabase.m08.MarkPetDeceasedParams
import com.comunidapp.app.data.remote.supabase.m08.PetAccessContext
import com.comunidapp.app.data.remote.supabase.m08.PetDuplicateCandidateRow
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toPet
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toUpdateHealthParams
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toUpdateProfileParams
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toCreateParams
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toDomain
import com.comunidapp.app.data.remote.supabase.m08.PetM08RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m08.PetStatusHistoryM08Row
import com.comunidapp.app.data.remote.supabase.m08.RestorePetParams
import com.comunidapp.app.data.remote.supabase.m08.SetPetAvatarAssetParams
import com.comunidapp.app.data.remote.supabase.m08.SupabasePetM08RemoteDataSource
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.pets.PetHealthMerge
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
                    _pets.value = enrichList(remote.listAccessiblePets(status = "ACTIVE"))
                } catch (_: Exception) {
                    // transient poll errors ignored
                }
                delay(4_000)
            }
        }
    }

    override fun observePets(): StateFlow<List<Pet>> = _pets.asStateFlow()

    override fun observePetsForOwner(ownerId: String): Flow<List<Pet>> = flow {
        val authUid = authUidProvider()
        if (authUid == null || authUid != ownerId) {
            emit(emptyList())
            return@flow
        }
        emit(_pets.value)
        while (coroutineContext.isActive) {
            emit(loadAccessibleForOwner(ownerId))
            delay(4_000)
        }
    }

    override fun observePet(petId: String): Flow<Pet?> = flow {
        while (coroutineContext.isActive) {
            try {
                val cached = _pets.value.find { it.id == petId }
                val fetched = fetchPetById(petId)
                emit(
                    when {
                        fetched != null -> PetHealthMerge.preferRicherHealth(cached, fetched)
                        else -> cached
                    }
                )
            } catch (_: Exception) {
                // Transient network/decode errors must not cancel PetDetail collectors.
                emit(_pets.value.find { it.id == petId })
            }
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
            com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging("PET-STAGE=CREATE-BEGIN")
            val created = remote.createPetWithPrincipal(pet.toCreateParams())
            val petId = created.id
            runCatching {
                remote.updatePetProfile(pet.copy(id = petId).toUpdateProfileParams())
            }
            runCatching {
                remote.updatePetHealth(pet.copy(id = petId).toUpdateHealthParams())
            }
            com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging(
                "PET-STAGE=CREATE-RESULT CREATED petId=$petId"
            )
            refreshCache()
            val mapped = created.toPet()
            _pets.value = com.comunidapp.app.domain.pets.PetListMerge.withCreated(_pets.value, mapped)
            com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging(
                "PET-STAGE=LIST-REFRESH count=${_pets.value.size} hasCreated=${_pets.value.any { it.id == petId }}"
            )
            Result.success(petId)
        } catch (e: Exception) {
            com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging(
                "PET-STAGE=CREATE-RESULT FAIL type=${e::class.java.simpleName}"
            )
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

    override suspend fun markPetDeceased(petId: String, reason: String?): Result<Pet> {
        return try {
            val row = remote.markPetDeceased(MarkPetDeceasedParams(petId = petId, reason = reason))
            refreshCache()
            Result.success(row.toPet())
        } catch (e: Exception) {
            M08PetErrorMapper.failure(e)
        }
    }

    override suspend fun restorePet(petId: String): Result<Pet> {
        return try {
            val row = remote.restorePet(RestorePetParams(petId = petId))
            refreshCache()
            Result.success(row.toPet())
        } catch (e: Exception) {
            M08PetErrorMapper.failure(e)
        }
    }

    override suspend fun listStatusHistory(petId: String): Result<List<PetStatusHistoryM08Row>> {
        return try {
            Result.success(remote.listStatusHistory(petId))
        } catch (e: Exception) {
            M08PetErrorMapper.failure(e)
        }
    }

    override suspend fun detectDuplicateCandidates(
        microchip: String?,
        name: String?
    ): Result<List<PetDuplicateCandidateRow>> {
        return try {
            Result.success(
                remote.detectDuplicates(
                    DetectPetDuplicateParams(microchip = microchip, name = name)
                )
            )
        } catch (e: Exception) {
            M08PetErrorMapper.failure(e)
        }
    }

    private suspend fun enrichList(rows: List<com.comunidapp.app.data.remote.supabase.m08.AccessiblePetM08Row>): List<Pet> {
        val previous = _pets.value.associateBy { it.id }
        return rows.map { row ->
            val base = row.toPet()
            val enriched = runCatching { remote.getPetById(row.id)?.toPet() }.getOrNull() ?: base
            PetHealthMerge.preferRicherHealth(previous[row.id], enriched)
        }
    }

    private suspend fun loadAccessibleForOwner(ownerId: String): List<Pet> {
        val authUid = authUidProvider()
        if (authUid == null || authUid != ownerId) return emptyList()
        return try {
            val listed = enrichList(remote.listAccessiblePets(status = "ACTIVE"))
            _pets.value = listed
            listed
        } catch (_: Exception) {
            _pets.value
        }
    }

    private suspend fun refreshCache() {
        try {
            com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging("PET-STAGE=LIST-REFRESH")
            _pets.value = enrichList(remote.listAccessiblePets(status = "ACTIVE"))
            com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging(
                "PET-STAGE=LIST-RESULT count=${_pets.value.size}"
            )
        } catch (error: Exception) {
            com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging(
                "PET-STAGE=LIST-RESULT FAIL type=${error::class.java.simpleName}"
            )
        }
    }
}
