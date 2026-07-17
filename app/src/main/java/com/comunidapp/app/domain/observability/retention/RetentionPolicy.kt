package com.comunidapp.app.domain.observability.retention

/**
 * Políticas de retención tipadas (sin SQL). Plazos legales = sujetos a revisión.
 */
enum class RetentionPolicyKey {
    NO_REMOTE,
    DEBUG_7_DAYS,
    TECHNICAL_30_DAYS,
    TECHNICAL_90_DAYS,
    AUDIT_12_MONTHS,
    SECURITY_24_MONTHS,
    AGGREGATE_24_MONTHS,
    UNTIL_RESOLUTION,
    LEGAL_REVIEW_REQUIRED;

    companion object {
        fun fromString(raw: String?): RetentionPolicyKey? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

data class RetentionPolicy(
    val key: RetentionPolicyKey,
    val remoteAllowed: Boolean,
    val maxRetentionDays: Int?,
    val legalReviewRequired: Boolean,
    val description: String
)

enum class RetentionDecision {
    ALLOW_REMOTE,
    LOCAL_ONLY,
    REJECTED_UNDEFINED,
    REJECTED_SENSITIVITY,
    REJECTED_DEBUG_DEFAULT
}

object RetentionPolicies {

    private val policies: Map<RetentionPolicyKey, RetentionPolicy> = mapOf(
        RetentionPolicyKey.NO_REMOTE to RetentionPolicy(
            key = RetentionPolicyKey.NO_REMOTE,
            remoteAllowed = false,
            maxRetentionDays = 0,
            legalReviewRequired = false,
            description = "No remote persistence"
        ),
        RetentionPolicyKey.DEBUG_7_DAYS to RetentionPolicy(
            key = RetentionPolicyKey.DEBUG_7_DAYS,
            remoteAllowed = false,
            maxRetentionDays = 7,
            legalReviewRequired = false,
            description = "Debug local only by default"
        ),
        RetentionPolicyKey.TECHNICAL_30_DAYS to RetentionPolicy(
            key = RetentionPolicyKey.TECHNICAL_30_DAYS,
            remoteAllowed = true,
            maxRetentionDays = 30,
            legalReviewRequired = false,
            description = "Technical observability 30 days"
        ),
        RetentionPolicyKey.TECHNICAL_90_DAYS to RetentionPolicy(
            key = RetentionPolicyKey.TECHNICAL_90_DAYS,
            remoteAllowed = true,
            maxRetentionDays = 90,
            legalReviewRequired = false,
            description = "Technical observability 90 days"
        ),
        RetentionPolicyKey.AUDIT_12_MONTHS to RetentionPolicy(
            key = RetentionPolicyKey.AUDIT_12_MONTHS,
            remoteAllowed = true,
            maxRetentionDays = 365,
            legalReviewRequired = true,
            description = "Audit trail ~12 months (legal review)"
        ),
        RetentionPolicyKey.SECURITY_24_MONTHS to RetentionPolicy(
            key = RetentionPolicyKey.SECURITY_24_MONTHS,
            remoteAllowed = true,
            maxRetentionDays = 730,
            legalReviewRequired = true,
            description = "Security events ~24 months (legal review)"
        ),
        RetentionPolicyKey.AGGREGATE_24_MONTHS to RetentionPolicy(
            key = RetentionPolicyKey.AGGREGATE_24_MONTHS,
            remoteAllowed = true,
            maxRetentionDays = 730,
            legalReviewRequired = false,
            description = "Aggregated metrics ~24 months"
        ),
        RetentionPolicyKey.UNTIL_RESOLUTION to RetentionPolicy(
            key = RetentionPolicyKey.UNTIL_RESOLUTION,
            remoteAllowed = true,
            maxRetentionDays = null,
            legalReviewRequired = false,
            description = "Until incident/dead-letter resolution"
        ),
        RetentionPolicyKey.LEGAL_REVIEW_REQUIRED to RetentionPolicy(
            key = RetentionPolicyKey.LEGAL_REVIEW_REQUIRED,
            remoteAllowed = false,
            maxRetentionDays = null,
            legalReviewRequired = true,
            description = "Blocked until legal review"
        )
    )

    fun get(key: RetentionPolicyKey): RetentionPolicy =
        policies[key] ?: policies.getValue(RetentionPolicyKey.NO_REMOTE)

    fun decide(
        key: RetentionPolicyKey?,
        severityIsDebug: Boolean,
        sensitivityIsSecurity: Boolean
    ): RetentionDecision {
        if (key == null) return RetentionDecision.REJECTED_UNDEFINED
        if (severityIsDebug && key == RetentionPolicyKey.NO_REMOTE) {
            return RetentionDecision.LOCAL_ONLY
        }
        if (severityIsDebug && (key == RetentionPolicyKey.DEBUG_7_DAYS || key == RetentionPolicyKey.NO_REMOTE)) {
            return RetentionDecision.REJECTED_DEBUG_DEFAULT
        }
        val policy = get(key)
        if (sensitivityIsSecurity && !policy.remoteAllowed && key != RetentionPolicyKey.SECURITY_24_MONTHS) {
            return RetentionDecision.REJECTED_SENSITIVITY
        }
        return if (policy.remoteAllowed) RetentionDecision.ALLOW_REMOTE else RetentionDecision.LOCAL_ONLY
    }
}

enum class SamplingPolicyKind {
    ALWAYS,
    NEVER,
    RATE,
    FIRST_PER_WINDOW,
    ERROR_ONLY
}

data class SamplingPolicy(
    val kind: SamplingPolicyKind,
    val ratePerThousand: Int = 1000,
    val windowMs: Long = 60_000L
)

data class SamplingDecision(
    val accepted: Boolean,
    val reason: String
)

/**
 * Sampling local determinista. Clock/random inyectables.
 * Permission-denied never fully silenced (ALWAYS or ERROR_ONLY minimum).
 */
object SamplingEvaluator {

    fun evaluate(
        policy: SamplingPolicy,
        isErrorOrDenied: Boolean,
        isCriticalAuditOrSecurity: Boolean,
        randomPerThousand: () -> Int,
        nowMs: () -> Long,
        windowSeen: MutableSet<Long>
    ): SamplingDecision {
        if (isCriticalAuditOrSecurity) {
            return SamplingDecision(true, "ALWAYS_CRITICAL")
        }
        return when (policy.kind) {
            SamplingPolicyKind.ALWAYS -> SamplingDecision(true, "ALWAYS")
            SamplingPolicyKind.NEVER -> {
                if (isErrorOrDenied) SamplingDecision(true, "ERROR_OVERRIDE")
                else SamplingDecision(false, "NEVER")
            }
            SamplingPolicyKind.ERROR_ONLY -> {
                if (isErrorOrDenied) SamplingDecision(true, "ERROR_ONLY")
                else SamplingDecision(false, "ERROR_ONLY_SKIP")
            }
            SamplingPolicyKind.RATE -> {
                val roll = randomPerThousand().coerceIn(0, 999)
                if (roll < policy.ratePerThousand.coerceIn(0, 1000)) {
                    SamplingDecision(true, "RATE_HIT")
                } else if (isErrorOrDenied) {
                    SamplingDecision(true, "RATE_ERROR_OVERRIDE")
                } else {
                    SamplingDecision(false, "RATE_MISS")
                }
            }
            SamplingPolicyKind.FIRST_PER_WINDOW -> {
                val bucket = nowMs() / policy.windowMs.coerceAtLeast(1L)
                if (windowSeen.add(bucket)) SamplingDecision(true, "FIRST_WINDOW")
                else if (isErrorOrDenied) SamplingDecision(true, "WINDOW_ERROR_OVERRIDE")
                else SamplingDecision(false, "WINDOW_SKIP")
            }
        }
    }
}
