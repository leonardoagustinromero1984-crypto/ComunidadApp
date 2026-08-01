package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.model.M16ShelterProfile
import com.comunidapp.app.data.model.M16ShelterVerificationDecision
import com.comunidapp.app.data.model.M16ShelterVerificationRequest
import com.comunidapp.app.data.model.M16ShelterVerificationRequestStatus
import com.comunidapp.app.data.model.M16ShelterVerificationStatus
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m16.M16ShelterErrorMapper
import com.comunidapp.app.data.remote.supabase.m16.SupabaseM16RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m16.toM16ShelterProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface M16ShelterVerificationRepository {
    suspend fun listPendingRequests(): AppResult<List<M16ShelterVerificationRequest>>
    suspend fun getRequest(requestId: String): AppResult<M16ShelterVerificationRequest>
    suspend fun decide(
        requestId: String,
        decision: M16ShelterVerificationDecision,
        notes: String?
    ): AppResult<M16ShelterProfile>
}

class MockM16ShelterVerificationRepository(
    private val store: M16MemoryStore,
    private val profileRepo: M16ShelterRepository
) : M16ShelterVerificationRepository {

    override suspend fun listPendingRequests(): AppResult<List<M16ShelterVerificationRequest>> =
        AppResult.Success(store.listPendingVerificationRequests())

    override suspend fun getRequest(requestId: String): AppResult<M16ShelterVerificationRequest> {
        val req = store.verificationRequests.value.find { it.id == requestId }
            ?: return AppResult.Failure(notFound())
        return AppResult.Success(req)
    }

    override suspend fun decide(
        requestId: String,
        decision: M16ShelterVerificationDecision,
        notes: String?
    ): AppResult<M16ShelterProfile> = store.withLock {
        val req = store.verificationRequests.value.find { it.id == requestId }
            ?: return@withLock AppResult.Failure(notFound())
        if (req.status == M16ShelterVerificationRequestStatus.APPROVED ||
            req.status == M16ShelterVerificationRequestStatus.REJECTED
        ) {
            return@withLock profileRepo.getProfileById(req.shelterProfileId).fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = { AppResult.Failure(notFound()) }
            )
        }
        val profile = store.profiles.value.find { it.id == req.shelterProfileId }
            ?: return@withLock AppResult.Failure(notFound())
        val newStatus = when (decision) {
            M16ShelterVerificationDecision.VERIFIED -> M16ShelterVerificationStatus.VERIFIED
            M16ShelterVerificationDecision.REJECTED -> M16ShelterVerificationStatus.REJECTED
        }
        val now = System.currentTimeMillis()
        store.upsertVerificationRequest(
            req.copy(
                status = if (decision == M16ShelterVerificationDecision.VERIFIED) {
                    M16ShelterVerificationRequestStatus.APPROVED
                } else {
                    M16ShelterVerificationRequestStatus.REJECTED
                },
                decisionNotes = notes?.trim()?.takeIf { it.isNotEmpty() },
                decidedAt = now
            )
        )
        store.upsert(profile.copy(verificationStatus = newStatus, updatedAt = now))
        AppResult.Success(profile.copy(verificationStatus = newStatus, updatedAt = now))
    }

    private fun notFound() = com.comunidapp.app.core.result.AppError(
        kind = com.comunidapp.app.core.result.AppErrorKind.UNKNOWN,
        userMessage = M16ShelterErrorMapper.userMessage("M16_SHELTER_NOT_FOUND"),
        technicalMessage = "M16_VERIFICATION_NOT_FOUND",
        code = "M16_SHELTER_NOT_FOUND"
    )
}

@Serializable
data class M16VerificationRequestRow(
    val id: String,
    @SerialName("shelter_profile_id") val shelterProfileId: String,
    @SerialName("requested_by") val requestedBy: String,
    val status: String,
    @SerialName("decision_notes") val decisionNotes: String? = null,
    @SerialName("requested_at") val requestedAt: String? = null,
    @SerialName("decided_at") val decidedAt: String? = null
)

class SupabaseM16ShelterVerificationRepository(
    private val remote: SupabaseM16RemoteDataSource = SupabaseM16RemoteDataSource(),
    private val profileRepo: M16ShelterRepository = DataProvider.m16ShelterRepository
) : M16ShelterVerificationRepository {

    override suspend fun listPendingRequests(): AppResult<List<M16ShelterVerificationRequest>> =
        runCatching {
            remote.listPendingVerificationRequests().map { row ->
                row.toDomain(profileRepo)
            }
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { m16AppFailure(it) }
        )

    override suspend fun getRequest(requestId: String): AppResult<M16ShelterVerificationRequest> =
        runCatching {
            remote.getVerificationRequest(requestId).toDomain(profileRepo)
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { m16AppFailure(it) }
        )

    override suspend fun decide(
        requestId: String,
        decision: M16ShelterVerificationDecision,
        notes: String?
    ): AppResult<M16ShelterProfile> = runCatching {
        remote.decideShelterVerification(requestId, decision.name, notes).toM16ShelterProfile()
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { m16AppFailure(it) }
    )
}

private suspend fun M16VerificationRequestRow.toDomain(
    profiles: M16ShelterRepository
): M16ShelterVerificationRequest {
    val profile = profiles.getProfileById(shelterProfileId).getOrNull()
    return M16ShelterVerificationRequest(
        id = id,
        shelterProfileId = shelterProfileId,
        shelterDisplayName = profile?.displayName ?: "Refugio",
        organizationId = profile?.organizationId.orEmpty(),
        requestedBy = requestedBy,
        status = runCatching {
            M16ShelterVerificationRequestStatus.valueOf(status.uppercase())
        }.getOrDefault(M16ShelterVerificationRequestStatus.PENDING),
        requestedAt = parseVerificationTs(requestedAt),
        decisionNotes = decisionNotes,
        decidedAt = decidedAt?.let { parseVerificationTs(it) }
    )
}

private fun parseVerificationTs(value: String?): Long =
    value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: System.currentTimeMillis()

private fun m16AppFailure(t: Throwable): AppResult.Failure =
    AppResult.Failure(
        com.comunidapp.app.core.result.AppError(
            kind = com.comunidapp.app.core.result.AppErrorKind.UNKNOWN,
            userMessage = M16ShelterErrorMapper.userMessage(t.message ?: "M16_REMOTE_ERROR"),
            technicalMessage = t.message ?: "M16_REMOTE_ERROR",
            code = t.message ?: "M16_REMOTE_ERROR"
        )
    )
