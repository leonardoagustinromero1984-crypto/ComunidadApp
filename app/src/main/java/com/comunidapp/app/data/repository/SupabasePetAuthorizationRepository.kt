package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.m08.GrantPetAuthorizationParams
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toDomain
import com.comunidapp.app.data.remote.supabase.m08.PetM08RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m08.RevokePetAuthorizationParams
import com.comunidapp.app.data.remote.supabase.m08.SupabasePetM08RemoteDataSource
import com.comunidapp.app.domain.pets.PetAuthorization
import com.comunidapp.app.domain.pets.PetAuthorizationId
import com.comunidapp.app.domain.pets.PetAuthorizationRepository
import com.comunidapp.app.domain.pets.PetCapability
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetLinkStatus
import com.comunidapp.app.domain.pets.petFailure
import java.time.Instant

/**
 * LeoVer M08 — authorization repository over RPC + SELECT RLS.
 */
class SupabasePetAuthorizationRepository(
    private val remote: PetM08RemoteDataSource = SupabasePetM08RemoteDataSource()
) : PetAuthorizationRepository {

    override suspend fun listForPet(petId: PetId): List<PetAuthorization> =
        remote.listAuthorizations(petId.value).mapNotNull { row ->
            runCatching { row.toDomain() }.getOrNull()
        }

    override suspend fun create(authorization: PetAuthorization): Result<PetAuthorizationId> {
        return try {
            val row = remote.grantAuthorization(
                GrantPetAuthorizationParams(
                    petId = authorization.petId.value,
                    personId = authorization.granteeUserId,
                    capabilities = authorization.capabilities.map { it.code },
                    validUntil = authorization.validToEpochMs?.let {
                        Instant.ofEpochMilli(it).toString()
                    }
                )
            )
            Result.success(PetAuthorizationId(row.id))
        } catch (e: Exception) {
            petFailure(e.message ?: "PET_AUTHORIZATION_CREATE_FAILED")
        }
    }

    override suspend fun revoke(
        authorizationId: PetAuthorizationId,
        atEpochMs: Long
    ): Result<Unit> {
        return try {
            remote.revokeAuthorization(RevokePetAuthorizationParams(authorizationId.value))
            Result.success(Unit)
        } catch (e: Exception) {
            petFailure(e.message ?: "PET_AUTHORIZATION_REVOKE_FAILED")
        }
    }

    override suspend fun listEffectiveCapabilities(
        petId: PetId,
        granteeUserId: String,
        nowEpochMs: Long
    ): Set<PetCapability> {
        return listForPet(petId)
            .filter {
                it.granteeUserId == granteeUserId &&
                    it.status == PetLinkStatus.ACTIVE &&
                    (it.validToEpochMs == null || it.validToEpochMs > nowEpochMs)
            }
            .flatMap { it.capabilities }
            .toSet()
    }
}
