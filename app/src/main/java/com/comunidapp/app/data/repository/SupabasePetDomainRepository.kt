package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.m08.ArchivePetParams
import com.comunidapp.app.data.remote.supabase.m08.DetectPetDuplicateParams
import com.comunidapp.app.data.remote.supabase.m08.MarkPetDeceasedParams
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toAggregate
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toDomain
import com.comunidapp.app.data.remote.supabase.m08.PetM08RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m08.RestorePetParams
import com.comunidapp.app.data.remote.supabase.m08.SupabasePetM08RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m08.CreatePetWithPrincipalParams
import com.comunidapp.app.data.remote.supabase.m08.UpdatePetProfileParams
import com.comunidapp.app.domain.pets.PetAggregate
import com.comunidapp.app.domain.pets.PetDomainRepository
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetLifecycleStatus
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.app.domain.pets.PetResponsibility
import com.comunidapp.app.domain.pets.PetStatusHistoryEntry
import com.comunidapp.app.domain.pets.petFailure
import java.time.Instant

/**
 * LeoVer M08 — domain pet repository over RPC / SELECT RLS.
 */
class SupabasePetDomainRepository(
    private val remote: PetM08RemoteDataSource = SupabasePetM08RemoteDataSource()
) : PetDomainRepository {

    override suspend fun getById(petId: PetId): PetAggregate? =
        remote.getPetById(petId.value)?.toAggregate()

    override suspend fun listAccessibleForActor(
        actorUserId: String,
        nowEpochMs: Long
    ): List<PetAggregate> =
        remote.listAccessiblePets(status = null).map { it.toAggregate() }

    override suspend fun createAggregate(
        pet: PetAggregate,
        principalResponsibility: PetResponsibility
    ): Result<PetId> {
        return try {
            val orgId = when (val p = pet.principal) {
                is PetPrincipalHolder.Organization -> p.organizationId.value
                is PetPrincipalHolder.Person -> null
            }
            val created = remote.createPetWithPrincipal(
                CreatePetWithPrincipalParams(
                    name = pet.displayName,
                    species = "OTHER",
                    sex = "UNKNOWN",
                    size = "MEDIUM",
                    description = "",
                    organizationId = orgId,
                    microchipId = null
                )
            )
            Result.success(PetId(created.id))
        } catch (e: Exception) {
            petFailure(e.message ?: "PET_CREATE_FAILED")
        }
    }

    override suspend fun updateBasicProfile(pet: PetAggregate): Result<Unit> {
        return try {
            remote.updatePetProfile(
                UpdatePetProfileParams(
                    petId = pet.id.value,
                    name = pet.displayName,
                    species = "OTHER",
                    breed = null,
                    sex = "UNKNOWN",
                    size = "MEDIUM",
                    description = "",
                    ageYears = 0,
                    ageMonths = 0,
                    color = null,
                    microchipId = pet.microchipNormalized
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            petFailure(e.message ?: "PET_UPDATE_FAILED")
        }
    }

    override suspend fun changeLifecycleStatus(
        petId: PetId,
        to: PetLifecycleStatus,
        actorUserId: String,
        atEpochMs: Long,
        reasonCode: String?
    ): Result<Unit> {
        return try {
            when (to) {
                PetLifecycleStatus.ARCHIVED ->
                    remote.archivePet(ArchivePetParams(petId.value, reasonCode))
                PetLifecycleStatus.DECEASED ->
                    remote.markPetDeceased(MarkPetDeceasedParams(petId.value, reasonCode))
                PetLifecycleStatus.ACTIVE ->
                    remote.restorePet(RestorePetParams(petId.value))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            petFailure(e.message ?: "PET_LIFECYCLE_FAILED")
        }
    }

    override suspend fun listStatusHistory(petId: PetId): List<PetStatusHistoryEntry> =
        remote.listStatusHistory(petId.value).map { it.toDomain() }

    override suspend fun findDuplicateCandidates(
        microchipNormalized: String?,
        excludePetId: PetId?
    ): List<PetAggregate> {
        val candidates = remote.detectDuplicates(
            DetectPetDuplicateParams(microchip = microchipNormalized, name = null)
        )
        return candidates
            .filter { excludePetId == null || it.petId != excludePetId.value }
            .mapNotNull { remote.getPetById(it.petId)?.toAggregate() }
    }

    override suspend fun existsActiveMicrochip(
        microchipNormalized: String,
        excludePetId: PetId?
    ): Boolean {
        val candidates = findDuplicateCandidates(microchipNormalized, excludePetId)
        return candidates.any {
            it.status == PetLifecycleStatus.ACTIVE &&
                it.microchipNormalized.equals(microchipNormalized, ignoreCase = true)
        }
    }
}
