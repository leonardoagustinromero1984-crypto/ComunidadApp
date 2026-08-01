package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M14AuditEvents
import com.comunidapp.app.data.model.M14CredentialStatus
import com.comunidapp.app.data.model.M14CredentialType
import com.comunidapp.app.data.model.M14ExpirationPolicy
import com.comunidapp.app.data.model.M14ExpirationResult
import com.comunidapp.app.data.model.M14M06Hooks
import com.comunidapp.app.data.model.M14OperationalMetrics
import com.comunidapp.app.data.model.M14PassportStatus
import com.comunidapp.app.data.model.M14VerificationRequestStatus
import com.comunidapp.app.data.remote.supabase.m14.M14ErrorMapper
import com.comunidapp.app.data.remote.supabase.m14.M14Exception
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * LeoVer M14 Bloque 4 — operaciones locales: expiración, métricas sin PII, hooks M06.
 * Sin SQL nuevo; cron real = REQUIERE_INFRA_EXTERNA. Remoto 052 = PENDIENTE_EXTERNO.
 */
interface M14OperationsRepository {
    fun observePreparedM06Hooks(): Flow<List<Pair<String, String>>>
    suspend fun applyExpirations(
        nowEpochMs: Long = System.currentTimeMillis(),
        policy: M14ExpirationPolicy = M14ExpirationPolicy()
    ): Result<M14ExpirationResult>

    suspend fun getOperationalMetrics(
        fromEpochMs: Long,
        toEpochMs: Long,
        policy: M14ExpirationPolicy = M14ExpirationPolicy()
    ): Result<M14OperationalMetrics>
}

private val conflictCountByStore = mutableMapOf<M14MemoryStore, AtomicInteger>()
private val idempotentCountByStore = mutableMapOf<M14MemoryStore, AtomicInteger>()

fun M14MemoryStore.recordConflict() {
    conflictCountByStore.getOrPut(this) { AtomicInteger(0) }.incrementAndGet()
}

fun M14MemoryStore.recordIdempotentRetry() {
    idempotentCountByStore.getOrPut(this) { AtomicInteger(0) }.incrementAndGet()
}

fun M14MemoryStore.conflictCount(): Int =
    conflictCountByStore[this]?.get() ?: 0

fun M14MemoryStore.idempotentRetryCount(): Int =
    idempotentCountByStore[this]?.get() ?: 0

class MockM14OperationsRepository(
    private val store: M14MemoryStore,
    private val actorUserId: () -> String?,
    private val canViewMetrics: () -> Boolean = { true }
) : M14OperationsRepository {

    override fun observePreparedM06Hooks(): Flow<List<Pair<String, String>>> =
        store.m06PreparedHooks

    override suspend fun applyExpirations(
        nowEpochMs: Long,
        policy: M14ExpirationPolicy
    ): Result<M14ExpirationResult> =
        store.withLock {
            if (store.forceFailure) return@withLock resultFailM14("M14_REPOSITORY_FAILURE")
            actorUserId() ?: return@withLock resultFailM14("NOT_AUTHENTICATED")
            ZoneId.of(policy.zoneIdName)
            val pendingTtl = TimeUnit.DAYS.toMillis(policy.pendingRequestTtlDays.toLong())
            val reviewTtl = TimeUnit.DAYS.toMillis(policy.underReviewRequestTtlDays.toLong())
            var expiredRequests = 0
            var expiredCredentials = 0
            var alreadyApplied = 0
            var preservedTerminal = 0
            val requestSnapshot = store.verificationRequests.value.toList()
            val credentialSnapshot = store.credentials.value.toList()

            requestSnapshot.forEach { req ->
                when {
                    req.status.isTerminal && req.status != M14VerificationRequestStatus.EXPIRED -> {
                        preservedTerminal++
                    }
                    req.status == M14VerificationRequestStatus.EXPIRED -> {
                        alreadyApplied++
                        store.recordIdempotentRetry()
                    }
                    req.status.isExpirable -> {
                        val ttl = when (req.status) {
                            M14VerificationRequestStatus.PENDING -> pendingTtl
                            M14VerificationRequestStatus.UNDER_REVIEW -> reviewTtl
                            else -> return@forEach
                        }
                        val anchor = req.resolvedAt ?: req.requestedAt
                        if (nowEpochMs - anchor < ttl) return@forEach
                        val cred = store.credentials.value.find { it.id == req.credentialId }
                        val updated = req.copy(
                            status = M14VerificationRequestStatus.EXPIRED,
                            resolvedAt = nowEpochMs,
                            resolutionReason = "EXPIRED_POLICY"
                        )
                        store.upsertRequest(updated)
                        if (cred != null && cred.status == M14CredentialStatus.PENDING_VERIFICATION) {
                            val nextStatus = if (cred.expiresAt != null && cred.expiresAt <= nowEpochMs) {
                                M14CredentialStatus.EXPIRED
                            } else {
                                M14CredentialStatus.DRAFT
                            }
                            store.upsertCredential(
                                cred.copy(status = nextStatus, updatedAt = nowEpochMs)
                            )
                            if (nextStatus == M14CredentialStatus.EXPIRED) {
                                store.audit(M14AuditEvents.CREDENTIAL_EXPIRED, cred.id)
                                store.recordM06(M14M06Hooks.CREDENTIAL_EXPIRED, cred.id)
                                expiredCredentials++
                            }
                        }
                        store.audit(M14AuditEvents.VERIFICATION_EXPIRED, req.id)
                        store.recordM06(M14M06Hooks.VERIFICATION_EXPIRED, req.id)
                        expiredRequests++
                    }
                }
            }

            credentialSnapshot.forEach { cred ->
                when {
                    cred.status.isTerminal -> {
                        if (cred.status == M14CredentialStatus.EXPIRED) {
                            alreadyApplied++
                            store.recordIdempotentRetry()
                        } else {
                            preservedTerminal++
                        }
                    }
                    cred.status == M14CredentialStatus.VERIFIED ||
                        cred.status == M14CredentialStatus.PENDING_VERIFICATION -> {
                        // Ya expirada en el paso de solicitudes de este mismo apply.
                        val current = store.credentials.value.find { it.id == cred.id } ?: return@forEach
                        if (current.status == M14CredentialStatus.EXPIRED) return@forEach
                        if (cred.expiresAt != null && cred.expiresAt <= nowEpochMs) {
                            store.upsertCredential(
                                current.copy(
                                    status = M14CredentialStatus.EXPIRED,
                                    updatedAt = nowEpochMs
                                )
                            )
                            store.audit(M14AuditEvents.CREDENTIAL_EXPIRED, cred.id)
                            store.recordM06(M14M06Hooks.CREDENTIAL_EXPIRED, cred.id)
                            expiredCredentials++
                        }
                    }
                }
            }

            Result.success(
                M14ExpirationResult(
                    expiredRequests = expiredRequests,
                    expiredCredentials = expiredCredentials,
                    alreadyApplied = alreadyApplied,
                    preservedTerminal = preservedTerminal
                )
            )
        }

    override suspend fun getOperationalMetrics(
        fromEpochMs: Long,
        toEpochMs: Long,
        policy: M14ExpirationPolicy
    ): Result<M14OperationalMetrics> {
        if (store.forceFailure) return resultFailM14("M14_REPOSITORY_FAILURE")
        actorUserId() ?: return resultFailM14("NOT_AUTHENTICATED")
        if (!canViewMetrics()) return resultFailM14("UNAUTHORIZED")
        if (fromEpochMs >= toEpochMs) return resultFailM14("METRICS_INVALID_RANGE")
        ZoneId.of(policy.zoneIdName)

        val passports = store.passports.value.filter { it.createdAt in fromEpochMs until toEpochMs }
        val credentials = store.credentials.value.filter { it.createdAt in fromEpochMs until toEpochMs }
        val requests = store.verificationRequests.value.filter {
            it.requestedAt in fromEpochMs until toEpochMs
        }
        val decisions = store.decisions.value.filter { it.createdAt in fromEpochMs until toEpochMs }
        val audit = store.auditLog.value

        val passportsByStatus = M14PassportStatus.entries.associate { st ->
            st.name to passports.count { it.status == st }
        }
        val credentialsByStatus = M14CredentialStatus.entries.associate { st ->
            st.name to credentials.count { it.status == st }
        }
        val credentialsByType = M14CredentialType.entries.associate { t ->
            t.name to credentials.count { it.type == t }
        }
        val requestsByStatus = M14VerificationRequestStatus.entries.associate { st ->
            st.name to requests.count { it.status == st }
        }

        val approvals = decisions.count { it.decision == M14VerificationRequestStatus.APPROVED } +
            requests.count { it.status == M14VerificationRequestStatus.APPROVED }
        val rejections = decisions.count { it.decision == M14VerificationRequestStatus.REJECTED } +
            requests.count { it.status == M14VerificationRequestStatus.REJECTED }
        val expirations =
            requests.count { it.status == M14VerificationRequestStatus.EXPIRED } +
                credentials.count { it.status == M14CredentialStatus.EXPIRED }
        val revocations = credentials.count { it.status == M14CredentialStatus.REVOKED }
        val rotations = audit.count { it.first == M14AuditEvents.PUBLIC_CODE_ROTATED }

        val resolutionMinutes = requests.mapNotNull { r ->
            val resolved = r.resolvedAt ?: return@mapNotNull null
            if (resolved < fromEpochMs || resolved >= toEpochMs) return@mapNotNull null
            (resolved - r.requestedAt).coerceAtLeast(0L).toDouble() / 60000.0
        }

        // DTO agregado: sin userId, petId, publicCode, notas, contacto, microchip.
        return Result.success(
            M14OperationalMetrics(
                fromEpochMs = fromEpochMs,
                toEpochMs = toEpochMs,
                zoneIdName = policy.zoneIdName,
                passportsByStatus = passportsByStatus,
                credentialsByStatus = credentialsByStatus,
                credentialsByType = credentialsByType,
                requestsByStatus = requestsByStatus,
                approvals = approvals,
                rejections = rejections,
                expirations = expirations,
                revocations = revocations,
                publicCodeRotations = rotations,
                conflicts = store.conflictCount(),
                idempotentRetries = store.idempotentRetryCount(),
                avgMinutesToResolution = resolutionMinutes.takeIf { it.isNotEmpty() }?.average()
            )
        )
    }
}

class SupabaseM14OperationsRepository : M14OperationsRepository {
    override fun observePreparedM06Hooks(): Flow<List<Pair<String, String>>> =
        MutableStateFlow<List<Pair<String, String>>>(emptyList()).asStateFlow()

    override suspend fun applyExpirations(
        nowEpochMs: Long,
        policy: M14ExpirationPolicy
    ): Result<M14ExpirationResult> =
        Result.failure(
            M14Exception(
                "REMOTE_VALIDATION_PENDING",
                M14ErrorMapper.userMessage("REMOTE_VALIDATION_PENDING")
            )
        )

    override suspend fun getOperationalMetrics(
        fromEpochMs: Long,
        toEpochMs: Long,
        policy: M14ExpirationPolicy
    ): Result<M14OperationalMetrics> {
        if (fromEpochMs >= toEpochMs) {
            return Result.failure(
                M14Exception(
                    "METRICS_INVALID_RANGE",
                    M14ErrorMapper.userMessage("METRICS_INVALID_RANGE")
                )
            )
        }
        return Result.failure(
            M14Exception(
                "REMOTE_VALIDATION_PENDING",
                M14ErrorMapper.userMessage("REMOTE_VALIDATION_PENDING")
            )
        )
    }
}
