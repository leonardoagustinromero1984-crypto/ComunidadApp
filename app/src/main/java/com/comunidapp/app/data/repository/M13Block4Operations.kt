package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M13AuditEvents
import com.comunidapp.app.data.model.M13ExpirationPolicy
import com.comunidapp.app.data.model.M13ExpirationResult
import com.comunidapp.app.data.model.M13M06Hooks
import com.comunidapp.app.data.model.M13MatchReason
import com.comunidapp.app.data.model.M13MatchStatus
import com.comunidapp.app.data.model.M13MatchStatusHistoryEntry
import com.comunidapp.app.data.model.M13OperationalMetrics
import com.comunidapp.app.data.model.M13SightingStatus
import com.comunidapp.app.data.remote.supabase.m13.M13ErrorMapper
import com.comunidapp.app.data.remote.supabase.m13.M13Exception
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * LeoVer M13 Bloque 4 — operaciones locales: expiración, métricas sin PII, hooks M06.
 * Sin SQL nuevo; cron real = REQUIERE_INFRA_EXTERNA.
 */
interface M13OperationsRepository {
    fun observePreparedM06Hooks(): Flow<List<Pair<String, String>>>
    suspend fun applyExpirations(
        nowEpochMs: Long = System.currentTimeMillis(),
        policy: M13ExpirationPolicy = M13ExpirationPolicy()
    ): Result<M13ExpirationResult>

    suspend fun getOperationalMetrics(
        fromEpochMs: Long,
        toEpochMs: Long,
        policy: M13ExpirationPolicy = M13ExpirationPolicy()
    ): Result<M13OperationalMetrics>
}

private val m06HooksByStore = mutableMapOf<M13MemoryStore, MutableStateFlow<List<Pair<String, String>>>>()
private val m06InfraByStore = mutableMapOf<M13MemoryStore, MutableStateFlow<Boolean>>()

val M13MemoryStore.m06PreparedHooks: StateFlow<List<Pair<String, String>>>
    get() = m06HooksByStore.getOrPut(this) { MutableStateFlow(emptyList()) }.asStateFlow()

var M13MemoryStore.m06InfrastructureAvailable: Boolean
    get() = m06InfraByStore.getOrPut(this) { MutableStateFlow(false) }.value
    set(value) {
        m06InfraByStore.getOrPut(this) { MutableStateFlow(false) }.value = value
    }

fun M13MemoryStore.recordM06Hook(eventKey: String, entityId: String) {
    val flow = m06HooksByStore.getOrPut(this) { MutableStateFlow(emptyList()) }
    val key = eventKey to entityId
    flow.update { list ->
        if (list.any { it == key }) list else listOf(key) + list
    }
    if (!m06InfrastructureAvailable) {
        val infra = M13M06Hooks.INFRASTRUCTURE to "infra"
        flow.update { list ->
            if (list.any { it.first == M13M06Hooks.INFRASTRUCTURE }) list else listOf(infra) + list
        }
    }
}

class MockM13OperationsRepository(
    private val store: M13MemoryStore,
    private val actorUserId: () -> String?,
    private val canViewMetrics: () -> Boolean = { true }
) : M13OperationsRepository {

    override fun observePreparedM06Hooks(): Flow<List<Pair<String, String>>> =
        store.m06PreparedHooks

    override suspend fun applyExpirations(
        nowEpochMs: Long,
        policy: M13ExpirationPolicy
    ): Result<M13ExpirationResult> =
        store.withTransitionLock {
            if (store.forceFailure) return@withTransitionLock resultFailM13("M13_REPOSITORY_FAILURE")
            actorUserId() ?: return@withTransitionLock resultFailM13("NOT_AUTHENTICATED")
            ZoneId.of(policy.zoneIdName) // valida TZ explícita
            var expiredSightings = 0
            var expiredMatches = 0
            val sightingTtl = TimeUnit.DAYS.toMillis(policy.sightingActiveTtlDays.toLong())
            val proposedTtl = TimeUnit.DAYS.toMillis(policy.matchProposedTtlDays.toLong())
            val reviewTtl = TimeUnit.DAYS.toMillis(policy.matchUnderReviewTtlDays.toLong())

            store.sightings.value.forEach { s ->
                if (s.status == M13SightingStatus.ACTIVE && nowEpochMs - s.createdAt >= sightingTtl) {
                    val updated = s.copy(status = M13SightingStatus.EXPIRED, updatedAt = nowEpochMs)
                    store.upsertSighting(updated)
                    store.audit(M13AuditEvents.SIGHTING_EXPIRED, s.id)
                    store.recordM06Hook(M13M06Hooks.SIGHTING_EXPIRED, s.id)
                    expiredSightings++
                }
            }

            store.candidates.value.forEach { c ->
                if (c.status.isTerminal) return@forEach
                val ttl = when (c.status) {
                    M13MatchStatus.PROPOSED -> proposedTtl
                    M13MatchStatus.UNDER_REVIEW -> reviewTtl
                    else -> return@forEach
                }
                val anchor = c.updatedAt
                if (nowEpochMs - anchor < ttl) return@forEach
                val from = c.status
                val updated = c.copy(status = M13MatchStatus.EXPIRED, updatedAt = nowEpochMs)
                store.updateCandidate(updated)
                store.appendStatusHistory(
                    M13MatchStatusHistoryEntry(
                        id = store.nextId("m13_hist"),
                        candidateId = c.id,
                        fromStatus = from,
                        toStatus = M13MatchStatus.EXPIRED,
                        changedByUserId = actorUserId(),
                        reason = "EXPIRED_POLICY",
                        createdAt = nowEpochMs
                    )
                )
                store.audit(M13AuditEvents.MATCH_EXPIRED, c.id)
                store.recordM06Hook(M13M06Hooks.MATCH_EXPIRED, c.id)
                expiredMatches++
            }

            Result.success(
                M13ExpirationResult(
                    expiredSightings = expiredSightings,
                    expiredMatches = expiredMatches
                )
            )
        }

    override suspend fun getOperationalMetrics(
        fromEpochMs: Long,
        toEpochMs: Long,
        policy: M13ExpirationPolicy
    ): Result<M13OperationalMetrics> {
        if (store.forceFailure) return resultFailM13("M13_REPOSITORY_FAILURE")
        actorUserId() ?: return resultFailM13("NOT_AUTHENTICATED")
        if (!canViewMetrics()) return resultFailM13("MATCH_FORBIDDEN")
        if (fromEpochMs >= toEpochMs) return resultFailM13("M13_METRICS_INVALID_RANGE")
        ZoneId.of(policy.zoneIdName)

        val sightings = store.sightings.value.filter { it.createdAt in fromEpochMs until toEpochMs }
        val candidates = store.candidates.value.filter { it.createdAt in fromEpochMs until toEpochMs }
        val decisions = store.decisions.value.filter { it.createdAt in fromEpochMs until toEpochMs }
        val history = store.statusHistory.value.filter { it.createdAt in fromEpochMs until toEpochMs }

        val sightingsByStatus = M13SightingStatus.entries.associate { st ->
            st.name to sightings.count { it.status == st }
        }
        val candidatesByLevel = candidates.groupingBy { it.level.name }.eachCount()
            .withDefault { 0 }
            .let { map ->
                mapOf(
                    "LOW" to map.getValue("LOW"),
                    "MEDIUM" to map.getValue("MEDIUM"),
                    "HIGH" to map.getValue("HIGH")
                )
            }
        val candidatesByStatus = M13MatchStatus.entries.associate { st ->
            st.name to candidates.count { it.status == st }
        }

        val decided = candidates.count {
            it.status == M13MatchStatus.CONFIRMED ||
                it.status == M13MatchStatus.REJECTED ||
                it.status == M13MatchStatus.INCONCLUSIVE
        }
        val confirmed = candidates.count { it.status == M13MatchStatus.CONFIRMED }
        val confirmationRate =
            if (decided == 0) null else confirmed.toDouble() / decided.toDouble()

        val toReviewMs = history.mapNotNull { h ->
            if (h.toStatus != M13MatchStatus.UNDER_REVIEW) return@mapNotNull null
            val proposedAt = store.candidates.value.find { it.id == h.candidateId }?.createdAt
                ?: return@mapNotNull null
            if (h.createdAt < fromEpochMs || h.createdAt >= toEpochMs) return@mapNotNull null
            (h.createdAt - proposedAt).coerceAtLeast(0L).toDouble() / 60000.0
        }
        val toDecisionMs = decisions.mapNotNull { d ->
            val underReviewAt = history
                .filter { it.candidateId == d.candidateId && it.toStatus == M13MatchStatus.UNDER_REVIEW }
                .minByOrNull { it.createdAt }?.createdAt
                ?: return@mapNotNull null
            (d.createdAt - underReviewAt).coerceAtLeast(0L).toDouble() / 60000.0
        }

        val reasonDistribution = mutableMapOf<String, Int>()
        candidates.forEach { c ->
            c.reasons.forEach { r: M13MatchReason ->
                reasonDistribution[r.name] = (reasonDistribution[r.name] ?: 0) + 1
            }
        }

        // Garantía sin PII: el DTO no incluye user ids / coords / notas.
        return Result.success(
            M13OperationalMetrics(
                fromEpochMs = fromEpochMs,
                toEpochMs = toEpochMs,
                zoneIdName = policy.zoneIdName,
                sightingsByStatus = sightingsByStatus,
                candidatesByLevel = candidatesByLevel,
                candidatesByStatus = candidatesByStatus,
                confirmationRate = confirmationRate,
                avgMinutesToReview = toReviewMs.takeIf { it.isNotEmpty() }?.average(),
                avgMinutesToDecision = toDecisionMs.takeIf { it.isNotEmpty() }?.average(),
                expiredSightings = sightingsByStatus[M13SightingStatus.EXPIRED.name] ?: 0,
                expiredMatches = candidatesByStatus[M13MatchStatus.EXPIRED.name] ?: 0,
                reasonDistribution = reasonDistribution.toSortedMap()
            )
        )
    }
}

class SupabaseM13OperationsRepository : M13OperationsRepository {
    override fun observePreparedM06Hooks(): Flow<List<Pair<String, String>>> =
        MutableStateFlow(emptyList())

    override suspend fun applyExpirations(
        nowEpochMs: Long,
        policy: M13ExpirationPolicy
    ): Result<M13ExpirationResult> =
        Result.failure(
            M13Exception(
                "M13_EXPIRATION_INFRASTRUCTURE_UNAVAILABLE",
                M13ErrorMapper.userMessage("M13_EXPIRATION_INFRASTRUCTURE_UNAVAILABLE")
            )
        )

    override suspend fun getOperationalMetrics(
        fromEpochMs: Long,
        toEpochMs: Long,
        policy: M13ExpirationPolicy
    ): Result<M13OperationalMetrics> {
        if (fromEpochMs >= toEpochMs) {
            return Result.failure(
                M13Exception(
                    "M13_METRICS_INVALID_RANGE",
                    M13ErrorMapper.userMessage("M13_METRICS_INVALID_RANGE")
                )
            )
        }
        return Result.failure(
            M13Exception(
                "M13_METRICS_INFRASTRUCTURE_UNAVAILABLE",
                M13ErrorMapper.userMessage("M13_METRICS_INFRASTRUCTURE_UNAVAILABLE")
            )
        )
    }
}
