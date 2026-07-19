package com.comunidapp.app.domain.notifications

import java.time.Instant
import java.util.Locale

enum class NotificationInstallationPlatform {
    ANDROID,
    IOS,
    WEB,
    UNKNOWN;

    companion object {
        fun fromString(raw: String?): NotificationInstallationPlatform =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/**
 * Instalación de dispositivo. El token raw nunca forma parte del modelo público.
 * Solo [tokenFingerprint] para igualdad y logs.
 */
data class NotificationInstallation(
    val installationId: String,
    val userId: String,
    val platform: NotificationInstallationPlatform,
    val tokenFingerprint: String,
    val enabled: Boolean = true,
    val appVersion: String? = null,
    val deviceLabel: String? = null,
    val lastSeenAt: Instant,
    val revokedAt: Instant? = null
) {
    val isActive: Boolean get() = enabled && revokedAt == null

    override fun toString(): String =
        "NotificationInstallation(installationId=$installationId, userId=$userId, " +
            "platform=$platform, tokenFingerprint=$tokenFingerprint, enabled=$enabled, " +
            "appVersion=$appVersion, deviceLabel=$deviceLabel, lastSeenAt=$lastSeenAt, " +
            "revokedAt=$revokedAt)"
}

object NotificationInstallationRules {

    private val idPattern = Regex("^[A-Za-z0-9_\\-.:]{1,128}$")
    private val fingerprintPattern = Regex("^[a-f0-9]{8,128}$")

    fun fingerprintOf(rawToken: String): String {
        val bytes = rawToken.toByteArray(Charsets.UTF_8)
        var hash = 0x811c9dc5.toInt()
        for (b in bytes) {
            hash = hash xor (b.toInt() and 0xff)
            hash = (hash * 0x01000193)
        }
        return Integer.toHexString(hash).padStart(8, '0').lowercase(Locale.US)
    }

    fun validate(installation: NotificationInstallation): Result<NotificationInstallation> {
        if (!idPattern.matches(installation.installationId.trim())) {
            return Result.failure(IllegalArgumentException("INSTALLATION_ID_INVALID"))
        }
        if (!idPattern.matches(installation.userId.trim())) {
            return Result.failure(IllegalArgumentException("USER_ID_INVALID"))
        }
        if (!fingerprintPattern.matches(installation.tokenFingerprint.trim())) {
            return Result.failure(IllegalArgumentException("TOKEN_FINGERPRINT_INVALID"))
        }
        if (installation.toString().contains("token=", ignoreCase = true) &&
            !installation.toString().contains("tokenFingerprint")
        ) {
            return Result.failure(IllegalArgumentException("RAW_TOKEN_IN_TOSTRING"))
        }
        return Result.success(installation)
    }

    fun register(
        installationId: String,
        userId: String,
        platform: NotificationInstallationPlatform,
        tokenFingerprint: String,
        now: Instant,
        appVersion: String? = null,
        deviceLabel: String? = null
    ): Result<NotificationInstallation> =
        validate(
            NotificationInstallation(
                installationId = installationId.trim(),
                userId = userId.trim(),
                platform = platform,
                tokenFingerprint = tokenFingerprint.trim().lowercase(Locale.US),
                enabled = true,
                appVersion = appVersion,
                deviceLabel = deviceLabel,
                lastSeenAt = now,
                revokedAt = null
            )
        )

    fun rotateFingerprint(
        current: NotificationInstallation,
        newFingerprint: String,
        now: Instant
    ): Result<NotificationInstallation> {
        if (current.revokedAt != null) {
            return Result.failure(IllegalStateException("INSTALLATION_REVOKED"))
        }
        return validate(
            current.copy(
                tokenFingerprint = newFingerprint.trim().lowercase(Locale.US),
                lastSeenAt = now
            )
        )
    }

    /** Logout / revoke solo la instalación actual. */
    fun revokeCurrent(
        current: NotificationInstallation,
        installationId: String,
        now: Instant
    ): Result<NotificationInstallation> {
        if (current.installationId != installationId) {
            return Result.failure(IllegalArgumentException("REVOKE_CURRENT_ONLY"))
        }
        if (current.revokedAt != null) {
            return Result.success(current)
        }
        return Result.success(
            current.copy(enabled = false, revokedAt = now, lastSeenAt = now)
        )
    }

    /**
     * Cambio de cuenta: desvincula la instalación del usuario anterior
     * y la asocia al nuevo (misma installationId).
     */
    fun switchAccount(
        current: NotificationInstallation,
        newUserId: String,
        newFingerprint: String,
        now: Instant
    ): Result<Pair<NotificationInstallation, NotificationInstallation>> {
        val unbound = current.copy(enabled = false, revokedAt = now, lastSeenAt = now)
        val rebound = register(
            installationId = current.installationId,
            userId = newUserId,
            platform = current.platform,
            tokenFingerprint = newFingerprint,
            now = now,
            appVersion = current.appVersion,
            deviceLabel = current.deviceLabel
        ).getOrElse { return Result.failure(it) }
        return Result.success(unbound to rebound)
    }

    fun containsRawTokenLeak(text: String, rawToken: String): Boolean =
        rawToken.isNotBlank() && text.contains(rawToken)
}
