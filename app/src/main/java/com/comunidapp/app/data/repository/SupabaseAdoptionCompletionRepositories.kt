package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AdoptionAgreement
import com.comunidapp.app.data.model.AdoptionDocumentRequirement
import com.comunidapp.app.data.model.AdoptionDocumentType
import com.comunidapp.app.data.model.AdoptionFollowUpCheck
import com.comunidapp.app.data.model.AdoptionFollowUpPlan
import com.comunidapp.app.data.model.AdoptionInterview
import com.comunidapp.app.data.model.AdoptionInterviewType
import com.comunidapp.app.data.model.AdoptionProcessSnapshot
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.AdoptionWelfareStatus
import com.comunidapp.app.data.model.FinalizedAdoption
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionErrorMapper
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionException
import com.comunidapp.app.data.remote.supabase.m09.SupabaseAdoptionM09CompletionRemoteDataSource
import com.comunidapp.app.data.remote.supabase.m09.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant

class SupabaseAdoptionInterviewRepository(
    private val remote: SupabaseAdoptionM09CompletionRemoteDataSource =
        SupabaseAdoptionM09CompletionRemoteDataSource()
) : AdoptionInterviewRepository {
    override fun observeInterviews(adoptionId: String): Flow<List<AdoptionInterview>> =
        flow { emit(emptyList()) }

    override suspend fun getInterviewById(id: String): Result<AdoptionInterview> =
        if (id.isBlank()) fail("INTERVIEW_NOT_FOUND") else fail("INTERVIEW_NOT_FOUND")

    override suspend fun scheduleInterview(
        adoptionId: String,
        applicationId: String,
        scheduledAt: Long,
        type: AdoptionInterviewType,
        locationOrLink: String?,
        notes: String?
    ): Result<AdoptionInterview> = try {
        Result.success(
            remote.scheduleInterview(
                adoptionId,
                applicationId,
                Instant.ofEpochMilli(scheduledAt).toString(),
                type.name,
                locationOrLink,
                notes
            ).toDomain()
        )
    } catch (e: Exception) {
        M09AdoptionErrorMapper.failure(e)
    }

    override suspend fun confirmInterview(id: String): Result<AdoptionInterview> = try {
        Result.success(remote.confirmInterview(id).toDomain())
    } catch (e: Exception) {
        M09AdoptionErrorMapper.failure(e)
    }

    override suspend fun completeInterview(id: String, outcome: String?): Result<AdoptionInterview> =
        try {
            Result.success(remote.completeInterview(id, outcome).toDomain())
        } catch (e: Exception) {
            M09AdoptionErrorMapper.failure(e)
        }

    override suspend fun cancelInterview(id: String): Result<AdoptionInterview> = try {
        Result.success(remote.cancelInterview(id).toDomain())
    } catch (e: Exception) {
        M09AdoptionErrorMapper.failure(e)
    }

    private fun <T> fail(code: String): Result<T> =
        Result.failure(M09AdoptionException(code, M09AdoptionErrorMapper.userMessage(code)))
}

class SupabaseAdoptionDocumentRepository(
    private val remote: SupabaseAdoptionM09CompletionRemoteDataSource =
        SupabaseAdoptionM09CompletionRemoteDataSource()
) : AdoptionDocumentRepository {
    override fun observeDocuments(adoptionId: String): Flow<List<AdoptionDocumentRequirement>> =
        flow { emit(emptyList()) }

    override suspend fun requestDocument(
        adoptionId: String,
        applicationId: String,
        type: AdoptionDocumentType,
        required: Boolean
    ): Result<AdoptionDocumentRequirement> = try {
        Result.success(remote.requestDocument(adoptionId, applicationId, type.name, required).toDomain())
    } catch (e: Exception) {
        M09AdoptionErrorMapper.failure(e)
    }

    override suspend fun submitDocumentReference(
        requirementId: String,
        storagePath: String
    ): Result<AdoptionDocumentRequirement> {
        if (AdoptionDocumentRefValidator.isUnsafePublicReference(storagePath)) {
            return Result.failure(
                M09AdoptionException(
                    "DOCUMENT_UNSAFE_REFERENCE",
                    M09AdoptionErrorMapper.userMessage("DOCUMENT_UNSAFE_REFERENCE")
                )
            )
        }
        return try {
            Result.success(remote.submitDocumentReference(requirementId, storagePath).toDomain())
        } catch (e: Exception) {
            M09AdoptionErrorMapper.failure(e)
        }
    }

    override suspend fun reviewDocument(
        requirementId: String,
        approve: Boolean,
        rejectionReason: String?
    ): Result<AdoptionDocumentRequirement> = try {
        Result.success(remote.reviewDocument(requirementId, approve, rejectionReason).toDomain())
    } catch (e: Exception) {
        M09AdoptionErrorMapper.failure(e)
    }
}

class SupabaseAdoptionAgreementRepository(
    private val remote: SupabaseAdoptionM09CompletionRemoteDataSource =
        SupabaseAdoptionM09CompletionRemoteDataSource()
) : AdoptionAgreementRepository {
    override fun observeAgreement(adoptionId: String): Flow<AdoptionAgreement?> =
        flow { emit(null) }

    override suspend fun getAgreement(adoptionId: String): Result<AdoptionAgreement> =
        Result.failure(
            M09AdoptionException("AGREEMENT_NOT_FOUND", M09AdoptionErrorMapper.userMessage("AGREEMENT_NOT_FOUND"))
        )

    override suspend fun createAgreement(
        adoptionId: String,
        applicationId: String,
        termsVersion: String,
        termsSnapshot: String
    ): Result<AdoptionAgreement> = try {
        Result.success(
            remote.createAgreement(adoptionId, applicationId, termsVersion, termsSnapshot).toDomain()
        )
    } catch (e: Exception) {
        M09AdoptionErrorMapper.failure(e)
    }

    override suspend fun acceptAgreement(agreementId: String): Result<AdoptionAgreement> = try {
        Result.success(remote.acceptAgreement(agreementId).toDomain())
    } catch (e: Exception) {
        M09AdoptionErrorMapper.failure(e)
    }

    override suspend fun cancelAgreement(agreementId: String): Result<AdoptionAgreement> = try {
        Result.success(remote.cancelAgreement(agreementId).toDomain())
    } catch (e: Exception) {
        M09AdoptionErrorMapper.failure(e)
    }
}

class SupabaseAdoptionCompletionRepository(
    private val remote: SupabaseAdoptionM09CompletionRemoteDataSource =
        SupabaseAdoptionM09CompletionRemoteDataSource(),
    private val localSnapshot: AdoptionCompletionRepository? = null
) : AdoptionCompletionRepository {
    override suspend fun getProcessSnapshot(adoptionId: String): Result<AdoptionProcessSnapshot> =
        localSnapshot?.getProcessSnapshot(adoptionId)
            ?: Result.success(
                AdoptionProcessSnapshot(
                    adoptionId = adoptionId,
                    adoptionStatus = AdoptionStatus.PAUSED,
                    acceptedApplication = null,
                    interviews = emptyList(),
                    documents = emptyList(),
                    agreement = null,
                    finalized = null,
                    followUpPlan = null,
                    followUpChecks = emptyList(),
                    canFinalize = false,
                    finalizeBlockers = listOf("Sincronización pendiente")
                )
            )

    override suspend fun finalizeAdoption(adoptionId: String): Result<FinalizedAdoption> = try {
        Result.success(remote.finalizeAdoption(adoptionId).toDomain())
    } catch (e: Exception) {
        M09AdoptionErrorMapper.failure(e)
    }
}

class SupabaseAdoptionFollowUpRepository(
    private val remote: SupabaseAdoptionM09CompletionRemoteDataSource =
        SupabaseAdoptionM09CompletionRemoteDataSource()
) : AdoptionFollowUpRepository {
    override fun observePlan(adoptionId: String): Flow<AdoptionFollowUpPlan?> = flow { emit(null) }
    override fun observeChecks(adoptionId: String): Flow<List<AdoptionFollowUpCheck>> =
        flow { emit(emptyList()) }

    override suspend fun getPlan(adoptionId: String): Result<AdoptionFollowUpPlan> = try {
        Result.success(remote.getFollowUpPlan(adoptionId).toDomain())
    } catch (e: Exception) {
        M09AdoptionErrorMapper.failure(e)
    }

    override suspend fun getCheckById(checkId: String): Result<AdoptionFollowUpCheck> =
        Result.failure(
            M09AdoptionException("FOLLOWUP_NOT_FOUND", M09AdoptionErrorMapper.userMessage("FOLLOWUP_NOT_FOUND"))
        )

    override suspend fun completeCheck(
        checkId: String,
        notes: String?,
        welfareStatus: AdoptionWelfareStatus,
        evidenceRef: String?
    ): Result<AdoptionFollowUpCheck> = try {
        Result.success(
            remote.completeFollowUpCheck(checkId, notes, welfareStatus.name, evidenceRef).toDomain()
        )
    } catch (e: Exception) {
        M09AdoptionErrorMapper.failure(e)
    }
}
