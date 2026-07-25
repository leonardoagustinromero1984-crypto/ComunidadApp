package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM14CredentialInput
import com.comunidapp.app.data.model.CreateM14PassportInput
import com.comunidapp.app.data.model.M14Credential
import com.comunidapp.app.data.model.M14PassportHistory
import com.comunidapp.app.data.model.M14PassportStatus
import com.comunidapp.app.data.model.M14PetPassport
import com.comunidapp.app.data.model.M14PublicPassportProjection
import com.comunidapp.app.data.model.M14VerificationRequest
import com.comunidapp.app.data.model.UpdateM14PassportInput
import com.comunidapp.app.data.remote.supabase.m14.M14ErrorMapper
import com.comunidapp.app.data.remote.supabase.m14.SupabaseM14RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m14.createM14CredentialParams
import com.comunidapp.app.data.remote.supabase.m14.createM14PassportParams
import com.comunidapp.app.data.remote.supabase.m14.toDomain
import com.comunidapp.app.data.remote.supabase.m14.updateM14PassportParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** LeoVer M14 repositories backed exclusively by migration 050 RPCs. */
class SupabaseM14PassportRepository(
    private val remote: SupabaseM14RemoteDataSource = SupabaseM14RemoteDataSource()
) : M14PassportRepository {
    override fun observeMyPassports(): Flow<List<M14PetPassport>> = flow {
        try {
            emit(remote.listMyPetPassports().map { it.toDomain() })
        } catch (t: Throwable) {
            throw M14ErrorMapper.failure(t).exceptionOrNull()!!
        }
    }

    override fun observePassport(passportId: String): Flow<M14PetPassport?> = flow {
        emit(runCatching { remote.getPetPassport(passportId).toDomain() }.getOrNull())
    }

    override fun observePassportForPet(petId: String): Flow<M14PetPassport?> = flow {
        emit(runCatching { remote.getPetPassportByPet(petId).toDomain() }.getOrNull())
    }

    override suspend fun getPassport(passportId: String): Result<M14PetPassport> = try {
        Result.success(remote.getPetPassport(passportId).toDomain())
    } catch (t: Throwable) {
        M14ErrorMapper.failure(t)
    }

    override suspend fun createPassport(input: CreateM14PassportInput): Result<M14PetPassport> {
        M14Validators.validateCreatePassport(input)?.let { return resultFailM14(it) }
        return try {
            Result.success(remote.createPetPassport(createM14PassportParams(input)).toDomain())
        } catch (t: Throwable) {
            M14ErrorMapper.failure(t)
        }
    }

    override suspend fun updatePassport(
        passportId: String,
        input: UpdateM14PassportInput
    ): Result<M14PetPassport> {
        M14Validators.validateUpdatePassport(input)?.let { return resultFailM14(it) }
        return try {
            Result.success(remote.updateMyPetPassport(updateM14PassportParams(passportId, input)).toDomain())
        } catch (t: Throwable) {
            M14ErrorMapper.failure(t)
        }
    }

    override suspend fun activatePassport(passportId: String): Result<M14PetPassport> = try {
        Result.success(remote.activateMyPetPassport(passportId).toDomain())
    } catch (t: Throwable) {
        M14ErrorMapper.failure(t)
    }

    override suspend fun transitionPassport(
        passportId: String,
        to: M14PassportStatus,
        reason: String?
    ): Result<M14PetPassport> = when (to) {
        M14PassportStatus.ARCHIVED -> archivePassport(passportId, reason)
        M14PassportStatus.ACTIVE -> activatePassport(passportId)
        else -> resultFailM14("INVALID_TRANSITION")
    }

    suspend fun archivePassport(passportId: String, reason: String?): Result<M14PetPassport> = try {
        Result.success(remote.archiveMyPetPassport(passportId, reason).toDomain())
    } catch (t: Throwable) {
        M14ErrorMapper.failure(t)
    }

    // Migration 050 intentionally has no history-read RPC.
    override fun observeHistory(passportId: String): Flow<List<M14PassportHistory>> = flow { emit(emptyList()) }

    override suspend fun getPublicProjection(publicCode: String): Result<M14PublicPassportProjection> = try {
        Result.success(remote.getPublicPetPassport(publicCode).toDomain(publicCode))
    } catch (t: Throwable) {
        M14ErrorMapper.failure(t)
    }
}

class SupabaseM14CredentialRepository(
    private val remote: SupabaseM14RemoteDataSource = SupabaseM14RemoteDataSource()
) : M14CredentialRepository {
    override fun observeCredentials(passportId: String): Flow<List<M14Credential>> = flow {
        try {
            emit(remote.listPassportCredentials(passportId).map { it.toDomain() })
        } catch (t: Throwable) {
            throw M14ErrorMapper.failure(t).exceptionOrNull()!!
        }
    }

    override fun observeCredential(credentialId: String): Flow<M14Credential?> = flow {
        emit(runCatching { remote.getPassportCredential(credentialId).toDomain() }.getOrNull())
    }

    override suspend fun getCredential(credentialId: String): Result<M14Credential> = try {
        Result.success(remote.getPassportCredential(credentialId).toDomain())
    } catch (t: Throwable) {
        M14ErrorMapper.failure(t)
    }

    override suspend fun createCredential(input: CreateM14CredentialInput): Result<M14Credential> {
        M14Validators.validateCredential(input)?.let { return resultFailM14(it) }
        return try {
            Result.success(remote.createPassportCredential(createM14CredentialParams(input)).toDomain())
        } catch (t: Throwable) {
            M14ErrorMapper.failure(t)
        }
    }

    suspend fun updateCredential(params: kotlinx.serialization.json.JsonObject): Result<M14Credential> = try {
        Result.success(remote.updateMyPassportCredential(params).toDomain())
    } catch (t: Throwable) {
        M14ErrorMapper.failure(t)
    }

    suspend fun withdrawCredential(credentialId: String): Result<M14Credential> = try {
        Result.success(remote.withdrawMyPassportCredential(credentialId).toDomain())
    } catch (t: Throwable) {
        M14ErrorMapper.failure(t)
    }

    override suspend fun requestVerification(
        credentialId: String,
        targetOrganizationId: String?
    ): Result<M14VerificationRequest> = try {
        Result.success(remote.createVerificationRequest(credentialId, targetOrganizationId).toDomain())
    } catch (t: Throwable) {
        M14ErrorMapper.failure(t)
    }
}

class SupabaseM14VerificationRepository(
    private val remote: SupabaseM14RemoteDataSource = SupabaseM14RemoteDataSource()
) : M14VerificationRepository {
    override fun observeRequests(passportId: String): Flow<List<M14VerificationRequest>> = flow {
        // The queue RPC is scoped to the authenticated requester; credential/passport joins stay server-side.
        try {
            emit(remote.listMyVerificationRequests().map { it.toDomain() })
        } catch (t: Throwable) {
            throw M14ErrorMapper.failure(t).exceptionOrNull()!!
        }
    }

    suspend fun getRequest(requestId: String): Result<M14VerificationRequest> = try {
        Result.success(remote.getVerificationRequest(requestId).toDomain())
    } catch (t: Throwable) {
        M14ErrorMapper.failure(t)
    }

    suspend fun cancelRequest(requestId: String): Result<M14VerificationRequest> = try {
        Result.success(remote.cancelMyVerificationRequest(requestId).toDomain())
    } catch (t: Throwable) {
        M14ErrorMapper.failure(t)
    }

    suspend fun listManagedRequests(): Result<List<M14VerificationRequest>> = try {
        Result.success(remote.listManagedVerificationRequests().map { it.toDomain() })
    } catch (t: Throwable) {
        M14ErrorMapper.failure(t)
    }

    // Resolution is deliberately a future trusted-server workflow (no client RPC in 050).
    override suspend fun resolveLocal(
        requestId: String,
        approve: Boolean,
        reasonCode: String,
        notePrivate: String?
    ): Result<M14VerificationRequest> = resultFailM14("ISSUER_NOT_AUTHORIZED")
}
