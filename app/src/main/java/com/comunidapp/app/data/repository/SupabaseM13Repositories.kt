package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M13MatchCandidate
import com.comunidapp.app.data.model.M13MatchDecision
import com.comunidapp.app.data.model.M13MatchDecisionType
import com.comunidapp.app.data.model.M13MatchStatusHistoryEntry
import com.comunidapp.app.data.model.M13Sighting
import com.comunidapp.app.data.model.M13SightingPublic
import com.comunidapp.app.data.remote.supabase.m13.CreateM13SightingParamsJson
import com.comunidapp.app.data.remote.supabase.m13.M13ErrorMapper
import com.comunidapp.app.data.remote.supabase.m13.SupabaseM13RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m13.toDomain
import com.comunidapp.app.data.remote.supabase.m13.toPublic
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * LeoVer M13 Bloque 2 — repositorios Supabase (solo RPC; sin DML directo).
 * No implementa confirm/reject remoto (Bloque 3).
 */
class SupabaseM13SightingRepository(
    private val remote: SupabaseM13RemoteDataSource = SupabaseM13RemoteDataSource()
) : M13SightingRepository {

    override fun observeMySightings(): Flow<List<M13Sighting>> = flow {
        emit(
            try {
                remote.listMySightings().map { it.toDomain() }
            } catch (t: Throwable) {
                throw M13ErrorMapper.failure(t).exceptionOrNull()!!
            }
        )
    }

    override fun observePublicSightings(): Flow<List<M13SightingPublic>> = flow {
        emit(
            try {
                remote.listPublicSightings().map { it.toPublic() }
            } catch (t: Throwable) {
                throw M13ErrorMapper.failure(t).exceptionOrNull()!!
            }
        )
    }

    override suspend fun getSighting(id: String, forPublic: Boolean): Result<Any> =
        try {
            val row = remote.getSighting(id)
            Result.success(if (forPublic || row.reporterUserId.isNullOrBlank()) row.toPublic() else row.toDomain())
        } catch (t: Throwable) {
            M13ErrorMapper.failure(t)
        }

    override suspend fun createSighting(input: CreateM13SightingInput): Result<M13Sighting> {
        val caseId = input.lostFoundCaseId?.trim().orEmpty()
        if (caseId.isEmpty()) return resultFailM13("CASE_REQUIRED")
        M13Validators.validateCreate(
            description = input.description,
            zoneText = input.zoneText,
            primaryColor = input.primaryColor,
            mediaRefs = input.mediaRefs,
            latitudeApprox = input.latitudeApprox,
            longitudeApprox = input.longitudeApprox,
            accuracyMeters = input.accuracyMeters
        )?.let { return resultFailM13(it) }
        return try {
            Result.success(
                remote.createSighting(
                    CreateM13SightingParamsJson(
                        caseId = caseId,
                        species = input.species.name,
                        primaryColor = input.primaryColor.trim(),
                        zoneText = input.zoneText.trim(),
                        description = input.description.trim(),
                        observedAtIso = Instant.ofEpochMilli(input.observedAt).toString(),
                        breedText = input.breedText,
                        secondaryColor = input.secondaryColor,
                        sex = input.sex?.name,
                        size = input.size?.name,
                        latitudeApprox = input.latitudeApprox,
                        longitudeApprox = input.longitudeApprox,
                        accuracyMeters = input.accuracyMeters,
                        mediaRefs = input.mediaRefs
                    )
                ).toDomain()
            )
        } catch (t: Throwable) {
            M13ErrorMapper.failure(t)
        }
    }

    override suspend fun withdrawSighting(id: String): Result<M13Sighting> =
        try {
            Result.success(remote.withdrawMySighting(id).toDomain())
        } catch (t: Throwable) {
            M13ErrorMapper.failure(t)
        }

    suspend fun updateMySighting(params: kotlinx.serialization.json.JsonObject): Result<M13Sighting> =
        try {
            Result.success(remote.updateMySighting(params).toDomain())
        } catch (t: Throwable) {
            M13ErrorMapper.failure(t)
        }

    suspend fun listManaged(): Result<List<M13Sighting>> =
        try {
            Result.success(remote.listManagedSightings().map { it.toDomain() })
        } catch (t: Throwable) {
            M13ErrorMapper.failure(t)
        }
}

class SupabaseM13MatchRepository(
    private val remote: SupabaseM13RemoteDataSource = SupabaseM13RemoteDataSource()
) : M13MatchRepository {

    override fun observeMatchesForCase(caseId: String): Flow<List<M13MatchCandidate>> = flow {
        emit(
            try {
                remote.listCaseCandidates(caseId).map { it.toDomain() }
            } catch (t: Throwable) {
                throw M13ErrorMapper.failure(t).exceptionOrNull()!!
            }
        )
    }

    override fun observeMatch(candidateId: String): Flow<M13MatchCandidate?> = flow {
        emit(
            try {
                remote.getCandidate(candidateId).toDomain()
            } catch (_: Throwable) {
                null
            }
        )
    }

    override fun observeDecisions(candidateId: String): Flow<List<M13MatchDecision>> =
        flowOf(emptyList()) // RPC list_match_decisions → migración 049

    override fun observeStatusHistory(candidateId: String): Flow<List<M13MatchStatusHistoryEntry>> =
        flowOf(emptyList()) // RPC list_match_status_history → migración 049

    override suspend fun recalculateForSighting(sightingId: String): Result<List<M13MatchCandidate>> =
        try {
            Result.success(remote.generateForSighting(sightingId).map { it.toDomain() })
        } catch (t: Throwable) {
            M13ErrorMapper.failure(t)
        }

    override suspend fun openReview(candidateId: String): Result<M13MatchCandidate> =
        resultFailM13("MATCH_REVIEW_RPC_UNAVAILABLE")

    override suspend fun decide(
        candidateId: String,
        decision: M13MatchDecisionType,
        reasonCode: String,
        notePrivate: String?
    ): Result<M13MatchCandidate> =
        resultFailM13("MATCH_REVIEW_RPC_UNAVAILABLE")

    override suspend fun withdrawMatch(candidateId: String, reasonCode: String): Result<M13MatchCandidate> =
        resultFailM13("MATCH_REVIEW_RPC_UNAVAILABLE")

    override suspend fun expireMatch(candidateId: String, reasonCode: String): Result<M13MatchCandidate> =
        resultFailM13("MATCH_REVIEW_RPC_UNAVAILABLE")

    suspend fun generateForCase(caseId: String): Result<List<M13MatchCandidate>> =
        try {
            Result.success(remote.generateForCase(caseId).map { it.toDomain() })
        } catch (t: Throwable) {
            M13ErrorMapper.failure(t)
        }

    suspend fun recalculateCandidate(candidateId: String): Result<M13MatchCandidate> =
        try {
            Result.success(remote.recalculateCandidate(candidateId).toDomain())
        } catch (t: Throwable) {
            M13ErrorMapper.failure(t)
        }
}
