package com.comunidapp.app.domain.observability

/**
 * M07 Etapa 2 — taxonomía de observabilidad (contratos puros, sin persistencia).
 * AccountType / active_modules nunca otorgan autoridad aquí.
 */

enum class ObservabilityModule {
    M00, M01, M02, M03, M04, M05, M06, M07;

    companion object {
        fun fromString(raw: String?): ObservabilityModule? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

enum class ObservabilityCategory {
    AUDIT,
    SECURITY,
    AUTHORIZATION,
    ERROR,
    PERFORMANCE,
    HEALTH,
    BUSINESS,
    PRODUCT_ANALYTICS,
    DATA_ACCESS,
    EXPORT,
    INTEGRATION,
    JOB,
    NOTIFICATION,
    FILE,
    MODERATION,
    SUPPORT,
    SYSTEM,
    OTHER;

    companion object {
        fun fromString(raw: String?): ObservabilityCategory =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: OTHER
    }
}

enum class ObservabilitySeverity {
    DEBUG,
    INFO,
    NOTICE,
    WARNING,
    ERROR,
    CRITICAL;

    companion object {
        fun fromString(raw: String?): ObservabilitySeverity? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

enum class ObservabilitySensitivity {
    PUBLIC_AGGREGATE,
    INTERNAL,
    CONFIDENTIAL,
    RESTRICTED,
    SECURITY_SENSITIVE;

    companion object {
        fun fromString(raw: String?): ObservabilitySensitivity? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

enum class ObservabilityResult {
    SUCCESS,
    FAILURE,
    DENIED,
    PARTIAL,
    SKIPPED,
    RETRYING,
    DEAD_LETTER,
    UNKNOWN;

    companion object {
        fun fromString(raw: String?): ObservabilityResult =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

enum class ObservabilityActorType {
    ANONYMOUS,
    AUTHENTICATED_USER,
    PLATFORM_STAFF,
    ORGANIZATION_MEMBER,
    SYSTEM,
    EDGE_FUNCTION,
    DATABASE_TRIGGER,
    CI,
    EXTERNAL_INTEGRATION,
    UNKNOWN;

    companion object {
        fun fromString(raw: String?): ObservabilityActorType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/**
 * Códigos de error seguros M07. No contienen SQL, stack, tokens ni PII.
 */
enum class ObservabilityErrorCode {
    OBS_EVENT_UNKNOWN,
    OBS_METADATA_DENIED,
    OBS_SENSITIVE_DATA_REDACTED,
    OBS_CORRELATION_INVALID,
    OBS_PERMISSION_DENIED,
    OBS_EXPORT_DENIED,
    OBS_RETENTION_UNDEFINED,
    OBS_SAMPLING_REJECTED,
    OBS_REPOSITORY_UNAVAILABLE,
    OBS_WRITE_DENIED,
    OBS_WRITE_FAILED,
    OBS_READ_DENIED,
    OBS_METRIC_UNKNOWN,
    OBS_METRIC_DIMENSION_DENIED,
    OBS_HEALTH_CHECK_UNKNOWN,
    OBS_HEALTH_EXECUTION_DENIED,
    OBS_ALERT_RULE_INVALID,
    OBS_ALERT_EVALUATION_FAILED,
    OBS_INCIDENT_NOT_FOUND,
    OBS_INCIDENT_TRANSITION_DENIED,
    OBS_FILTER_INVALID,
    OBS_DASHBOARD_PERMISSION_DENIED,
    OBS_DATA_UNAVAILABLE,
    OBS_RETENTION_POLICY_UNKNOWN,
    OBS_RETENTION_PREVIEW_REQUIRED,
    OBS_RETENTION_PREVIEW_EXPIRED,
    OBS_RETENTION_LEGAL_HOLD,
    OBS_RETENTION_EXECUTION_DENIED,
    OBS_RETENTION_RUN_FAILED,
    OBS_EXPORT_NOT_READY,
    OBS_EXPORT_EXPIRED,
    OBS_EXPORT_SCOPE_DENIED,
    OBS_PERMISSION_MAPPING_INVALID,
    OBS_M06_NOTIFICATION_PENDING,
    OBS_CI_QUALITY_CHECK_FAILED,
    OBS_UNKNOWN;

    companion object {
        fun fromString(raw: String?): ObservabilityErrorCode =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: OBS_UNKNOWN
    }
}
