package com.comunidapp.app.domain.pets

/**
 * Contratos de repositorio de dominio M08 (Etapa 2).
 * Sin Supabase, DTO SQL, Postgrest ni Android. Implementaciones llegan en Etapa 4.
 */
interface PetDomainRepository {
    suspend fun getById(petId: PetId): PetAggregate?
    suspend fun listAccessibleForActor(actorUserId: String, nowEpochMs: Long): List<PetAggregate>
    suspend fun createAggregate(
        pet: PetAggregate,
        principalResponsibility: PetResponsibility
    ): Result<PetId>
    suspend fun updateBasicProfile(pet: PetAggregate): Result<Unit>
    suspend fun changeLifecycleStatus(
        petId: PetId,
        to: PetLifecycleStatus,
        actorUserId: String,
        atEpochMs: Long,
        reasonCode: String?
    ): Result<Unit>
    suspend fun listStatusHistory(petId: PetId): List<PetStatusHistoryEntry>
    suspend fun findDuplicateCandidates(
        microchipNormalized: String?,
        excludePetId: PetId?
    ): List<PetAggregate>
    suspend fun existsActiveMicrochip(microchipNormalized: String, excludePetId: PetId?): Boolean
}

interface PetResponsibilityRepository {
    suspend fun listForPet(petId: PetId): List<PetResponsibility>
    suspend fun listActiveForPet(petId: PetId, nowEpochMs: Long): List<PetResponsibility>
    suspend fun assignCoResponsible(responsibility: PetResponsibility): Result<PetResponsibilityId>
    suspend fun revoke(responsibilityId: PetResponsibilityId, atEpochMs: Long, reason: String?): Result<Unit>
    suspend fun assignTemporaryCustody(responsibility: PetResponsibility): Result<PetResponsibilityId>
    suspend fun endCustody(responsibilityId: PetResponsibilityId, atEpochMs: Long): Result<Unit>
    suspend fun getActivePrincipal(petId: PetId, nowEpochMs: Long): PetResponsibility?
}

interface PetAuthorizationRepository {
    suspend fun listForPet(petId: PetId): List<PetAuthorization>
    suspend fun create(authorization: PetAuthorization): Result<PetAuthorizationId>
    suspend fun revoke(authorizationId: PetAuthorizationId, atEpochMs: Long): Result<Unit>
    suspend fun listEffectiveCapabilities(
        petId: PetId,
        granteeUserId: String,
        nowEpochMs: Long
    ): Set<PetCapability>
}

interface PetTransferRepository {
    suspend fun create(transfer: PetTransfer): Result<PetTransferId>
    suspend fun getPending(petId: PetId): PetTransfer?
    suspend fun accept(transferId: PetTransferId, atEpochMs: Long): Result<Unit>
    suspend fun reject(transferId: PetTransferId, atEpochMs: Long): Result<Unit>
    suspend fun cancel(transferId: PetTransferId, atEpochMs: Long): Result<Unit>
    suspend fun expire(transferId: PetTransferId, atEpochMs: Long): Result<Unit>
    suspend fun listHistory(petId: PetId): List<PetTransfer>
}
