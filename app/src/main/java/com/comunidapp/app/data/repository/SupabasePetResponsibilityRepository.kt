package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.m08.AssignPetResponsibilityParams
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toDomain
import com.comunidapp.app.data.remote.supabase.m08.PetM08RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m08.RevokePetResponsibilityParams
import com.comunidapp.app.data.remote.supabase.m08.SupabasePetM08RemoteDataSource
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetLinkStatus
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.app.domain.pets.PetResponsibility
import com.comunidapp.app.domain.pets.PetResponsibilityId
import com.comunidapp.app.domain.pets.PetResponsibilityRepository
import com.comunidapp.app.domain.pets.PetResponsibilityRole
import com.comunidapp.app.domain.pets.petFailure
import java.time.Instant

/**
 * LeoVer M08 — responsibility repository over RPC + SELECT RLS.
 */
class SupabasePetResponsibilityRepository(
    private val remote: PetM08RemoteDataSource = SupabasePetM08RemoteDataSource()
) : PetResponsibilityRepository {

    override suspend fun listForPet(petId: PetId): List<PetResponsibility> =
        remote.listResponsibilities(petId.value).map { it.toDomain() }

    override suspend fun listActiveForPet(
        petId: PetId,
        nowEpochMs: Long
    ): List<PetResponsibility> =
        listForPet(petId).filter { it.status == PetLinkStatus.ACTIVE }

    override suspend fun assignCoResponsible(
        responsibility: PetResponsibility
    ): Result<PetResponsibilityId> = assign(responsibility, PetResponsibilityRole.CO_RESPONSIBLE)

    override suspend fun revoke(
        responsibilityId: PetResponsibilityId,
        atEpochMs: Long,
        reason: String?
    ): Result<Unit> {
        return try {
            remote.revokeResponsibility(
                RevokePetResponsibilityParams(responsibilityId.value)
            )
            Result.success(Unit)
        } catch (e: Exception) {
            petFailure(e.message ?: "PET_RESPONSIBILITY_REVOKE_FAILED")
        }
    }

    override suspend fun assignTemporaryCustody(
        responsibility: PetResponsibility
    ): Result<PetResponsibilityId> =
        assign(responsibility, PetResponsibilityRole.TEMPORARY_CUSTODIAN)

    override suspend fun endCustody(
        responsibilityId: PetResponsibilityId,
        atEpochMs: Long
    ): Result<Unit> = revoke(responsibilityId, atEpochMs, reason = "END_CUSTODY")

    override suspend fun getActivePrincipal(
        petId: PetId,
        nowEpochMs: Long
    ): PetResponsibility? =
        listActiveForPet(petId, nowEpochMs)
            .firstOrNull { it.role == PetResponsibilityRole.PRINCIPAL }

    private suspend fun assign(
        responsibility: PetResponsibility,
        role: PetResponsibilityRole
    ): Result<PetResponsibilityId> {
        return try {
            val (personId, orgId) = when (val h = responsibility.holder) {
                is PetPrincipalHolder.Person -> h.userId to null
                is PetPrincipalHolder.Organization -> null to h.organizationId.value
            }
            val row = remote.assignResponsibility(
                AssignPetResponsibilityParams(
                    petId = responsibility.petId.value,
                    roleCode = role.name,
                    personId = personId,
                    organizationId = orgId,
                    endsAt = responsibility.validToEpochMs?.let {
                        Instant.ofEpochMilli(it).toString()
                    },
                    reason = responsibility.revokeReason
                )
            )
            Result.success(PetResponsibilityId(row.id))
        } catch (e: Exception) {
            petFailure(e.message ?: "PET_RESPONSIBILITY_ASSIGN_FAILED")
        }
    }
}
