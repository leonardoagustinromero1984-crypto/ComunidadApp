package com.comunidapp.app.domain.observability

import com.comunidapp.app.core.logging.AppLog
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.ObservabilityClientReporter
import com.comunidapp.app.domain.observability.correlation.CorrelationId
import com.comunidapp.app.domain.observability.sanitization.ThrowableSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Instrumentación crítica M07 Etapa 3 (best-effort, sin abortar dominio, sin loops).
 * No modifica AuthRepository / domain/auth / UsernameValidators.
 */
object ObservabilityInstrumentation {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun correlationOrNew(): String {
        val provider = runCatching { DataProvider.correlationContextRepository.provider() }.getOrNull()
        val current = provider?.current()?.correlationId?.value
        if (current != null) return current
        val started = provider?.startRoot()?.correlationId?.value
        return started ?: CorrelationId.parseOrNull(
            java.util.UUID.randomUUID().toString().replace("-", "").take(32)
        )?.value ?: "corr00000001"
    }

    fun clearSessionCorrelation() {
        runCatching { DataProvider.correlationContextRepository.onLogout() }
    }

    fun onAccountChanged() {
        runCatching { DataProvider.correlationContextRepository.onAccountChanged() }
    }

    fun reportLoginFailure() {
        val cid = correlationOrNew()
        scope.launch {
            ObservabilityClientReporter.reportSecurity(
                eventKey = "m01.auth.login_failure",
                result = "DENIED",
                correlationId = cid,
                reasonCode = "INVALID_CREDENTIALS",
                metadata = mapOf("result" to "DENIED")
            )
        }
    }

    fun reportLogout() {
        val cid = correlationOrNew()
        scope.launch {
            ObservabilityClientReporter.reportSecurity(
                eventKey = "m01.auth.logout",
                result = "SUCCESS",
                correlationId = cid,
                metadata = mapOf("result" to "SUCCESS")
            )
        }
        clearSessionCorrelation()
    }

    fun reportConsentGateUnavailable() {
        val cid = correlationOrNew()
        val sanitized = ThrowableSanitizer.sanitize(
            IllegalStateException("M01_CONSENT_GATE_UNAVAILABLE")
        )
        scope.launch {
            ObservabilityClientReporter.reportError(
                errorCode = "M01_CONSENT_GATE_UNAVAILABLE",
                module = "M01",
                correlationId = cid,
                sanitizedMessage = sanitized.safeMessage.ifBlank { "CONSENT_GATE_UNAVAILABLE" },
                fingerprint = sanitized.fingerprint
            )
        }
    }

    fun reportPermissionDenied(permissionCode: String) {
        val cid = correlationOrNew()
        val safePerm = permissionCode.take(64).replace(Regex("[^A-Za-z0-9._-]"), "")
        scope.launch {
            ObservabilityClientReporter.reportSecurity(
                eventKey = "m02.permission.denied",
                result = "DENIED",
                correlationId = cid,
                permissionCode = safePerm,
                metadata = mapOf(
                    "result" to "DENIED",
                    "permission_code" to safePerm
                )
            )
        }
    }

    fun reportDeepLinkDenied(permissionCode: String?) {
        val cid = correlationOrNew()
        val safePerm = (permissionCode ?: "UNKNOWN").take(64).replace(Regex("[^A-Za-z0-9._-]"), "")
        scope.launch {
            ObservabilityClientReporter.reportSecurity(
                eventKey = "m06.deep_link.permission_denied",
                result = "DENIED",
                correlationId = cid,
                permissionCode = safePerm,
                metadata = mapOf(
                    "result" to "DENIED",
                    "permission_code" to safePerm
                )
            )
        }
    }

    fun reportAllowlistedRemoteError(
        errorCode: String,
        module: String,
        message: String,
        correlationId: CorrelationId?
    ) {
        if (errorCode !in setOf(
                "OBS_UNKNOWN", "OBS_WRITE_FAILED", "OBS_CORRELATION_INVALID",
                "OBS_METADATA_DENIED", "OBS_EVENT_UNKNOWN", "OBS_PERMISSION_DENIED",
                "OBS_RETENTION_RUN_FAILED", "OBS_CI_QUALITY_CHECK_FAILED",
                "M01_CONSENT_GATE_UNAVAILABLE", "M05_STORAGE_ERROR",
                "M05_SIGNED_URL_ERROR", "M06_DEEP_LINK_DENIED"
            )
        ) {
            AppLog.debug("M07Obs", "skip remote error not allowlisted")
            return
        }
        val cid = correlationId?.value ?: correlationOrNew()
        val sanitized = SensitiveSafe(message)
        scope.launch {
            ObservabilityClientReporter.reportError(
                errorCode = errorCode,
                module = module,
                correlationId = cid,
                sanitizedMessage = sanitized,
                fingerprint = "fp_${errorCode.hashCode().toUInt().toString(16)}"
            )
        }
    }

    /** M00/CI — aggregated quality outcome only (no secrets, no file contents). */
    fun reportCiQualityCheck(result: String, jobName: String = "m07_quality_checks") {
        val safeResult = result.take(32).replace(Regex("[^A-Za-z0-9_]"), "")
        val safeJob = jobName.take(64).replace(Regex("[^A-Za-z0-9._-]"), "")
        AppLog.info("M07CI", "quality_check result=$safeResult job=$safeJob")
        if (safeResult == "FAILED") {
            reportAllowlistedRemoteError(
                errorCode = "OBS_CI_QUALITY_CHECK_FAILED",
                module = "M00",
                message = "QUALITY_CHECK_FAILED",
                correlationId = null
            )
        }
    }

    /** Selective M07 operational signal — opaque resource only. */
    fun reportRetentionAction(action: String, result: String) {
        AppLog.info(
            "M07Retention",
            "action=${action.take(32)} result=${result.take(32)} cid=${correlationOrNew()}"
        )
    }

    fun reportExportAction(result: String) {
        AppLog.info("M07Export", "result=${result.take(32)} file_pending=true")
    }

    private fun SensitiveSafe(message: String): String =
        com.comunidapp.app.domain.observability.sanitization.SensitiveDataSanitizer.sanitize(message, 240)
}
