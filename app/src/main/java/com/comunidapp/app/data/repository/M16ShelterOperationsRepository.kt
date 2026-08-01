package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M16ShelterOperationsFilter
import com.comunidapp.app.data.model.M16ShelterOperationsSummary
import com.comunidapp.app.data.model.filterOperationalPets
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m16.M16ShelterErrorMapper
import com.comunidapp.app.domain.m16.M16ShelterOperationsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

interface M16ShelterOperationsRepository {
    fun observeOperationsSummary(shelterId: String): Flow<Result<M16ShelterOperationsSummary>>
    fun observeOperationsSummaryByOrganization(organizationId: String): Flow<Result<M16ShelterOperationsSummary>>
    suspend fun refreshOperations(shelterId: String): Result<M16ShelterOperationsSummary>
    suspend fun getOperationalPets(
        shelterId: String,
        filter: M16ShelterOperationsFilter = M16ShelterOperationsFilter.ALL
    ): Result<M16ShelterOperationsSummary>
}

class M16ShelterOperationsRepositoryImpl(
    private val shelterRepository: M16ShelterRepository,
    private val petRepository: PetRepository = DataProvider.petRepository,
    private val adoptionRepository: AdoptionRepository = DataProvider.adoptionRepository,
    private val fosterPlacementRepository: M15FosterPlacementRepository = DataProvider.m15FosterPlacementRepository,
    private val shelterProfileRepository: ShelterProfileRepository = DataProvider.shelterProfileRepository,
    private val shelterPetRepository: ShelterPetRepository = DataProvider.shelterPetRepository,
    private val m11Store: M11ShelterMemoryStore = DataProvider.m11ShelterStore,
    private val service: M16ShelterOperationsService = M16ShelterOperationsService(),
    private val markFosterOrgQueryLimited: Boolean = false,
    private val canView: suspend (String) -> Boolean = { orgId ->
        DataProvider.m16ShelterRepository.canManageOrganization(orgId) ||
            m11Store.orgViewers.value[orgId]?.isNotEmpty() == true ||
            m11Store.orgManagers.value[orgId]?.isNotEmpty() == true
    }
) : M16ShelterOperationsRepository {

    override fun observeOperationsSummary(shelterId: String): Flow<Result<M16ShelterOperationsSummary>> =
        flow { emit(refreshOperations(shelterId)) }

    override fun observeOperationsSummaryByOrganization(
        organizationId: String
    ): Flow<Result<M16ShelterOperationsSummary>> = flow {
        val profile = shelterRepository.observeProfileByOrganization(organizationId).first()
            ?: return@flow emit(M16ShelterErrorMapper.fail("M16_SHELTER_NOT_FOUND"))
        emit(refreshOperations(profile.id))
    }

    override suspend fun refreshOperations(shelterId: String): Result<M16ShelterOperationsSummary> {
        val profile = shelterRepository.getProfileById(shelterId).getOrNull()
            ?: return M16ShelterErrorMapper.fail("M16_SHELTER_NOT_FOUND")
        return buildSummary(profile)
    }

    override suspend fun getOperationalPets(
        shelterId: String,
        filter: M16ShelterOperationsFilter
    ): Result<M16ShelterOperationsSummary> = refreshOperations(shelterId).map { summary ->
        summary.copy(pets = filterOperationalPets(summary.pets, filter))
    }

    private suspend fun buildSummary(profile: com.comunidapp.app.data.model.M16ShelterProfile): Result<M16ShelterOperationsSummary> {
        if (!canView(profile.organizationId)) {
            return M16ShelterErrorMapper.fail("M16_PERMISSION_DENIED")
        }
        var partial = com.comunidapp.app.data.model.M16ShelterOperationsPartialFlags()
        val m11Profile = m11Store.profiles.value.firstOrNull {
            it.organizationId == profile.organizationId
        }
        val shelterPlacements = if (m11Profile != null) {
            runCatching {
                shelterPetRepository.observeShelterPets(m11Profile.id).first()
            }.getOrElse {
                partial = partial.copy(shelterOpsSourceUnavailable = true)
                emptyList()
            }
        } else {
            emptyList()
        }
        val adoptions = runCatching {
            adoptionRepository.getAdoptionsByOrganization(profile.organizationId)
        }.getOrElse {
            partial = partial.copy(adoptionsSourceUnavailable = true)
            emptyList()
        }
        val fosterPlacements = runCatching {
            fosterPlacementRepository.observePlacementsForOrganization(profile.organizationId).first()
        }.getOrElse {
            partial = partial.copy(fosterSourceUnavailable = true)
            emptyList()
        }
        if (markFosterOrgQueryLimited) {
            partial = partial.copy(fosterOrgQueryLimited = true)
        }
        val petIds = linkedSetOf<String>()
        shelterPlacements.mapTo(petIds) { it.petId }
        adoptions.mapNotNullTo(petIds) { it.petId }
        fosterPlacements.mapTo(petIds) { it.petId }
        val petsById = mutableMapOf<String, com.comunidapp.app.data.model.Pet>()
        for (petId in petIds) {
            val pet = runCatching { petRepository.getPetById(petId) }.getOrNull()
            if (pet != null) {
                petsById[petId] = pet
            } else {
                partial = partial.copy(petsSourceUnavailable = true)
            }
        }
        return Result.success(
            service.buildSummary(
                M16ShelterOperationsService.SourceSnapshot(
                    profile = profile,
                    shelterPlacements = shelterPlacements,
                    adoptions = adoptions,
                    fosterPlacements = fosterPlacements,
                    petsById = petsById,
                    partialFlags = partial
                )
            )
        )
    }
}

class SupabaseM16ShelterOperationsRepository(
    private val inner: M16ShelterOperationsRepositoryImpl = M16ShelterOperationsRepositoryImpl(
        shelterRepository = DataProvider.m16ShelterRepository,
        markFosterOrgQueryLimited = true
    )
) : M16ShelterOperationsRepository by inner
