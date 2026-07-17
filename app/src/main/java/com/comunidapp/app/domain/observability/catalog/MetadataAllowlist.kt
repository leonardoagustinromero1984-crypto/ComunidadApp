package com.comunidapp.app.domain.observability.catalog

import com.comunidapp.app.domain.observability.ObservabilityErrorCode

/**
 * Metadata allowlist central. Deny-by-default.
 */
object MetadataAllowlist {

    val GLOBAL_ALLOWED_KEYS: Set<String> = setOf(
        "event_key",
        "module",
        "result",
        "reason_code",
        "permission_code",
        "resource_type",
        "resource_id",
        "organization_id",
        "channel",
        "attempt_count",
        "error_code",
        "app_version",
        "platform",
        "environment",
        "build_type",
        "job_name",
        "duration_ms",
        "status_code",
        "feature_flag",
        "correlation_id",
        "request_id",
        "installation_fingerprint",
        "file_type",
        "file_size_bucket"
    )

    private val forbiddenKeyFragments: Set<String> = setOf(
        "password", "passwd", "pwd", "token", "jwt", "bearer", "secret",
        "service_role", "apikey", "api_key", "signed_url", "signedurl",
        "stack", "sql", "email", "phone", "chat", "internal", "document",
        "base64", "authorization", "provider_message", "fcm_message",
        "access_token", "refresh_token", "coordinate", "latitude", "longitude"
    )

    sealed class MetadataValidation {
        data class Accepted(val metadata: Map<String, String>) : MetadataValidation()
        data class Rejected(val code: ObservabilityErrorCode, val detail: String) : MetadataValidation()
    }

    fun validate(
        metadata: Map<String, String>,
        allowedKeys: Set<String>,
        requiredKeys: Set<String>
    ): MetadataValidation {
        val effectiveAllowed = allowedKeys.intersect(GLOBAL_ALLOWED_KEYS).ifEmpty { GLOBAL_ALLOWED_KEYS }
        for (key in metadata.keys) {
            val normalized = key.trim().lowercase()
            if (normalized !in effectiveAllowed && key !in effectiveAllowed) {
                return MetadataValidation.Rejected(
                    ObservabilityErrorCode.OBS_METADATA_DENIED,
                    "EXTRA:$key"
                )
            }
            if (forbiddenKeyFragments.any { normalized.contains(it) }) {
                return MetadataValidation.Rejected(
                    ObservabilityErrorCode.OBS_METADATA_DENIED,
                    "FORBIDDEN_KEY:$key"
                )
            }
        }
        for (req in requiredKeys) {
            if (req !in metadata || metadata.getValue(req).isBlank()) {
                return MetadataValidation.Rejected(
                    ObservabilityErrorCode.OBS_METADATA_DENIED,
                    "REQUIRED:$req"
                )
            }
        }
        return MetadataValidation.Accepted(metadata)
    }
}
