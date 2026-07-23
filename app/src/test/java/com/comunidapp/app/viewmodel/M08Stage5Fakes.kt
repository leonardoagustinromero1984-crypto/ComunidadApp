package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.remote.supabase.m08.PetAccessContext
import com.comunidapp.app.data.remote.supabase.m08.PetDuplicateCandidateRow
import com.comunidapp.app.data.remote.supabase.m08.PetStatusHistoryM08Row
import com.comunidapp.app.data.repository.PetRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.pets.PetAuthorization
import com.comunidapp.app.domain.pets.PetAuthorizationId
import com.comunidapp.app.domain.pets.PetAuthorizationRepository
import com.comunidapp.app.domain.pets.PetCapability
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetLinkStatus
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.app.domain.pets.PetResponsibility
import com.comunidapp.app.domain.pets.PetResponsibilityId
import com.comunidapp.app.domain.pets.PetResponsibilityRepository
import com.comunidapp.app.domain.pets.PetResponsibilityRole
import com.comunidapp.app.domain.pets.PetTransfer
import com.comunidapp.app.domain.pets.PetTransferId
import com.comunidapp.app.domain.pets.PetTransferRepository
import com.comunidapp.app.domain.pets.PetTransferStatus
import com.comunidapp.app.domain.user.PublicUserProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * LeoVer M08 Etapa 5 — fakes en memoria para tests de ViewModels
 * (sin red, sin Supabase).
 */

fun stage5AccessContext(
    petId: String = "pet-1",
    canRead: Boolean = true,
    canManageResponsibilities: Boolean = false,
    canManageAuthorizations: Boolean = false,
    canInitiateTransfer: Boolean = false,
    canAcceptTransfer: Boolean = false,
    canCancelTransfer: Boolean = false,
    principalPersonId: String? = "user_1",
    principalOrganizationId: String? = null
): PetAccessContext = PetAccessContext(
    petId = petId,
    relationCode = "PRINCIPAL",
    principalPersonId = principalPersonId,
    principalOrganizationId = principalOrganizationId,
    capabilities = emptyList(),
    canRead = canRead,
    canUpdate = false,
    canManageHealth = false,
    canManageMedia = false,
    canManageResponsibilities = canManageResponsibilities,
    canManageAuthorizations = canManageAuthorizations,
    canInitiateTransfer = canInitiateTransfer,
    canAcceptTransfer = canAcceptTransfer,
    canCancelTransfer = canCancelTransfer,
    canArchive = false,
    canRestore = false,
    canMarkDeceased = false,
    canViewHistory = false
)

fun stage5Pet(id: String = "pet-1", status: String = "ACTIVE"): Pet = Pet(
    id = id,
    ownerId = "user_1",
    name = "Luna",
    species = PetSpecies.DOG,
    sex = PetSex.FEMALE,
    ageYears = 2,
    size = PetSize.MEDIUM,
    description = "Test pet",
    status = status
)

fun stage5Responsibility(
    id: String = "resp-1",
    petId: String = "pet-1",
    role: PetResponsibilityRole = PetResponsibilityRole.CO_RESPONSIBLE,
    status: PetLinkStatus = PetLinkStatus.ACTIVE,
    holder: PetPrincipalHolder = PetPrincipalHolder.Person("user_2")
): PetResponsibility = PetResponsibility(
    id = PetResponsibilityId(id),
    petId = PetId(petId),
    role = role,
    status = status,
    holder = holder,
    validFromEpochMs = 1_000L,
    grantedByUserId = "user_1",
    createdAtEpochMs = 1_000L
)

fun stage5Authorization(
    id: String = "auth-1",
    petId: String = "pet-1",
    granteeUserId: String = "user_2",
    capabilities: Set<PetCapability> = setOf(PetCapability.READ, PetCapability.MANAGE_HEALTH),
    status: PetLinkStatus = PetLinkStatus.ACTIVE,
    validToEpochMs: Long? = null
): PetAuthorization = PetAuthorization(
    id = PetAuthorizationId(id),
    petId = PetId(petId),
    granteeUserId = granteeUserId,
    capabilities = capabilities,
    status = status,
    validFromEpochMs = 1_000L,
    validToEpochMs = validToEpochMs,
    grantedByUserId = "user_1",
    createdAtEpochMs = 1_000L
)

fun stage5Transfer(
    id: String = "xfer-1",
    petId: String = "pet-1",
    status: PetTransferStatus = PetTransferStatus.PENDING,
    from: PetPrincipalHolder = PetPrincipalHolder.Person("user_1"),
    to: PetPrincipalHolder = PetPrincipalHolder.Person("user_2"),
    expiresAtEpochMs: Long = System.currentTimeMillis() + 86_400_000L
): PetTransfer = PetTransfer(
    id = PetTransferId(id),
    petId = PetId(petId),
    fromPrincipal = from,
    toPrincipal = to,
    status = status,
    requestedAtEpochMs = 1_000L,
    expiresAtEpochMs = expiresAtEpochMs,
    requestedByUserId = "user_1"
)

class FakeStage5PetRepository(
    var accessResult: Result<PetAccessContext> = Result.success(stage5AccessContext()),
    pet: Pet? = stage5Pet()
) : PetRepository {

    private val petFlow = MutableStateFlow(pet)
    var pet: Pet?
        get() = petFlow.value
        set(value) {
            petFlow.value = value
        }

    var accessCalls = 0
    var fetchError: Throwable? = null
    var observeError: Throwable? = null

    override fun observePets(): StateFlow<List<Pet>> = MutableStateFlow(emptyList())
    override fun observePetsForOwner(ownerId: String): Flow<List<Pet>> = flowOf(emptyList())
    override fun observePet(petId: String): Flow<Pet?> = flow {
        observeError?.let { throw it }
        petFlow.collect { emit(it) }
    }
    override fun getPetsByOwner(ownerId: String): List<Pet> = emptyList()
    override fun getPetById(petId: String): Pet? = pet
    override suspend fun fetchPetById(petId: String): Pet? {
        fetchError?.let { throw it }
        return pet
    }
    override suspend fun createPet(pet: Pet): Result<String> = Result.success(pet.id)
    override suspend fun updatePet(pet: Pet): Result<Unit> = Result.success(Unit)
    override suspend fun deletePet(petId: String): Result<Unit> = Result.success(Unit)

    override suspend fun getPetAccessContext(petId: String): Result<PetAccessContext> {
        accessCalls++
        return accessResult
    }

    override suspend fun setPetAvatarAsset(petId: String, assetId: String?): Result<Pet> =
        pet?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("PET_NOT_FOUND"))

    var markDeceasedResult: Result<Pet> = pet?.let { Result.success(it.copy(status = "DECEASED")) }
        ?: Result.failure(IllegalStateException("PET_NOT_FOUND"))
    var restoreResult: Result<Pet> = pet?.let { Result.success(it.copy(status = "ACTIVE")) }
        ?: Result.failure(IllegalStateException("PET_NOT_FOUND"))
    var statusHistoryResult: Result<List<PetStatusHistoryM08Row>> = Result.success(emptyList())
    var duplicateCandidatesResult: Result<List<PetDuplicateCandidateRow>> =
        Result.success(emptyList())
    var markDeceasedCalls = 0
    var restoreCalls = 0

    override suspend fun markPetDeceased(petId: String, reason: String?): Result<Pet> {
        markDeceasedCalls++
        return markDeceasedResult.onSuccess { updated -> this.pet = updated }
    }

    override suspend fun restorePet(petId: String): Result<Pet> {
        restoreCalls++
        return restoreResult.onSuccess { updated -> this.pet = updated }
    }

    override suspend fun listStatusHistory(petId: String): Result<List<PetStatusHistoryM08Row>> =
        statusHistoryResult

    override suspend fun detectDuplicateCandidates(
        microchip: String?,
        name: String?
    ): Result<List<PetDuplicateCandidateRow>> = duplicateCandidatesResult
}

class FakeStage5UserRepository(
    var profiles: List<PublicUserProfile> = emptyList()
) : UserRepository {

    var searchCalls = 0

    override suspend fun getUser(userId: String): User? = null
    override suspend fun createUser(user: User): Result<Unit> = Result.success(Unit)
    override suspend fun updateUser(user: User): Result<Unit> = Result.success(Unit)
    override suspend fun searchUsers(query: String, excludeUserId: String): List<User> = emptyList()
    override fun observeUser(userId: String): Flow<User?> = flowOf(null)
    override fun observeUsers(): Flow<List<User>> = flowOf(emptyList())

    override suspend fun searchPublicProfiles(
        viewerId: String,
        query: String,
        limit: Int
    ): Result<List<PublicUserProfile>> {
        searchCalls++
        return Result.success(profiles)
    }
}

class FakeStage5ResponsibilityRepository : PetResponsibilityRepository {

    val items = mutableListOf<PetResponsibility>()
    var listFailure: Throwable? = null
    var mutationFailure: Throwable? = null
    var assignCalls = 0
    var revokeCalls = 0
    var lastAssigned: PetResponsibility? = null
    var lastAssignedRole: PetResponsibilityRole? = null

    /** Si se setea, assign espera hasta que el test lo complete (double-submit). */
    var assignGate: CompletableDeferred<Unit>? = null

    override suspend fun listForPet(petId: PetId): List<PetResponsibility> {
        listFailure?.let { throw it }
        return items.filter { it.petId == petId }
    }

    override suspend fun listActiveForPet(petId: PetId, nowEpochMs: Long): List<PetResponsibility> =
        listForPet(petId).filter { it.status == PetLinkStatus.ACTIVE }

    override suspend fun assignCoResponsible(
        responsibility: PetResponsibility
    ): Result<PetResponsibilityId> = assign(responsibility, PetResponsibilityRole.CO_RESPONSIBLE)

    override suspend fun assignTemporaryCustody(
        responsibility: PetResponsibility
    ): Result<PetResponsibilityId> = assign(responsibility, PetResponsibilityRole.TEMPORARY_CUSTODIAN)

    private suspend fun assign(
        responsibility: PetResponsibility,
        role: PetResponsibilityRole
    ): Result<PetResponsibilityId> {
        assignCalls++
        assignGate?.await()
        mutationFailure?.let { return Result.failure(it) }
        lastAssigned = responsibility
        lastAssignedRole = role
        val id = PetResponsibilityId("resp-${items.size + 1}")
        items += responsibility.copy(id = id, role = role)
        return Result.success(id)
    }

    override suspend fun revoke(
        responsibilityId: PetResponsibilityId,
        atEpochMs: Long,
        reason: String?
    ): Result<Unit> {
        revokeCalls++
        mutationFailure?.let { return Result.failure(it) }
        val idx = items.indexOfFirst { it.id == responsibilityId }
        if (idx < 0) return Result.failure(IllegalArgumentException("PET_RESPONSIBILITY_NOT_FOUND"))
        items[idx] = items[idx].copy(status = PetLinkStatus.REVOKED, revokedAtEpochMs = atEpochMs)
        return Result.success(Unit)
    }

    override suspend fun endCustody(
        responsibilityId: PetResponsibilityId,
        atEpochMs: Long
    ): Result<Unit> = revoke(responsibilityId, atEpochMs, "END_CUSTODY")

    override suspend fun getActivePrincipal(petId: PetId, nowEpochMs: Long): PetResponsibility? =
        listActiveForPet(petId, nowEpochMs)
            .firstOrNull { it.role == PetResponsibilityRole.PRINCIPAL }
}

class FakeStage5AuthorizationRepository : PetAuthorizationRepository {

    val items = mutableListOf<PetAuthorization>()
    var listFailure: Throwable? = null
    var mutationFailure: Throwable? = null
    var createCalls = 0
    var revokeCalls = 0
    var lastCreated: PetAuthorization? = null
    var createGate: CompletableDeferred<Unit>? = null

    override suspend fun listForPet(petId: PetId): List<PetAuthorization> {
        listFailure?.let { throw it }
        return items.filter { it.petId == petId }
    }

    override suspend fun create(authorization: PetAuthorization): Result<PetAuthorizationId> {
        createCalls++
        createGate?.await()
        mutationFailure?.let { return Result.failure(it) }
        lastCreated = authorization
        val id = PetAuthorizationId("auth-${items.size + 1}")
        items += authorization.copy(id = id)
        return Result.success(id)
    }

    override suspend fun revoke(
        authorizationId: PetAuthorizationId,
        atEpochMs: Long
    ): Result<Unit> {
        revokeCalls++
        mutationFailure?.let { return Result.failure(it) }
        val idx = items.indexOfFirst { it.id == authorizationId }
        if (idx < 0) return Result.failure(IllegalArgumentException("PET_AUTHORIZATION_NOT_FOUND"))
        items[idx] = items[idx].copy(status = PetLinkStatus.REVOKED, revokedAtEpochMs = atEpochMs)
        return Result.success(Unit)
    }

    override suspend fun listEffectiveCapabilities(
        petId: PetId,
        granteeUserId: String,
        nowEpochMs: Long
    ): Set<PetCapability> = listForPet(petId)
        .filter {
            it.granteeUserId == granteeUserId &&
                it.status == PetLinkStatus.ACTIVE &&
                (it.validToEpochMs == null || it.validToEpochMs > nowEpochMs)
        }
        .flatMap { it.capabilities }
        .toSet()
}

class FakeStage5TransferRepository : PetTransferRepository {

    val items = mutableListOf<PetTransfer>()
    var listFailure: Throwable? = null
    var mutationFailure: Throwable? = null
    var createCalls = 0
    var acceptCalls = 0
    var rejectCalls = 0
    var cancelCalls = 0
    var lastCreated: PetTransfer? = null
    var lastCancelReason: String? = null
    var createGate: CompletableDeferred<Unit>? = null

    override suspend fun create(transfer: PetTransfer): Result<PetTransferId> {
        createCalls++
        createGate?.await()
        mutationFailure?.let { return Result.failure(it) }
        lastCreated = transfer
        val id = PetTransferId("xfer-${items.size + 1}")
        items += transfer.copy(id = id)
        return Result.success(id)
    }

    override suspend fun getPending(petId: PetId): PetTransfer? =
        items.firstOrNull { it.petId == petId && it.status == PetTransferStatus.PENDING }

    override suspend fun accept(transferId: PetTransferId, atEpochMs: Long): Result<Unit> {
        acceptCalls++
        return resolve(transferId, PetTransferStatus.ACCEPTED, atEpochMs)
    }

    override suspend fun reject(transferId: PetTransferId, atEpochMs: Long): Result<Unit> {
        rejectCalls++
        return resolve(transferId, PetTransferStatus.REJECTED, atEpochMs)
    }

    override suspend fun cancel(
        transferId: PetTransferId,
        atEpochMs: Long,
        reason: String?
    ): Result<Unit> {
        cancelCalls++
        lastCancelReason = reason
        return resolve(transferId, PetTransferStatus.CANCELLED, atEpochMs)
    }

    override suspend fun expire(transferId: PetTransferId, atEpochMs: Long): Result<Unit> =
        Result.failure(IllegalArgumentException("PET_TRANSFER_EXPIRE_CLIENT_FORBIDDEN"))

    override suspend fun listHistory(petId: PetId): List<PetTransfer> {
        listFailure?.let { throw it }
        return items.filter { it.petId == petId }
    }

    private fun resolve(
        transferId: PetTransferId,
        target: PetTransferStatus,
        atEpochMs: Long
    ): Result<Unit> {
        mutationFailure?.let { return Result.failure(it) }
        val idx = items.indexOfFirst { it.id == transferId }
        if (idx < 0) return Result.failure(IllegalArgumentException("PET_TRANSFER_NOT_FOUND"))
        items[idx] = items[idx].copy(status = target, resolvedAtEpochMs = atEpochMs)
        return Result.success(Unit)
    }
}

fun stage5Profile(id: String = "user_2", name: String = "Carlos Prueba"): PublicUserProfile =
    PublicUserProfile(
        id = id,
        displayName = name,
        username = "carlos.demo"
    )

fun stage5OrgHolder(orgId: String = "org-1"): PetPrincipalHolder =
    PetPrincipalHolder.Organization(OrganizationId(orgId))
