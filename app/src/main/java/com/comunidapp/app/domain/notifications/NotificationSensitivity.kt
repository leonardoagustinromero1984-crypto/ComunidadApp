package com.comunidapp.app.domain.notifications

/**
 * Sensibilidad M06. Controla copy en push y lock screen.
 * unknown → deny (vía [fromString] null).
 */
enum class NotificationSensitivity {
    PUBLIC_SUMMARY,
    PRIVATE,
    SENSITIVE,
    SECURITY_CRITICAL;

    companion object {
        fun fromString(raw: String?): NotificationSensitivity? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

/**
 * Reglas de exposición genérica para push / lock screen.
 * SENSITIVE y SECURITY_CRITICAL nunca exponen body completo.
 */
object NotificationSensitivityRules {

    const val GENERIC_PUSH_TITLE = "LeoVer"
    const val GENERIC_PUSH_BODY = "Tenés una actualización."
    const val GENERIC_LOCKSCREEN_TEXT = "Tenés una actualización."

    fun requiresGenericPushCopy(sensitivity: NotificationSensitivity): Boolean =
        sensitivity == NotificationSensitivity.SENSITIVE ||
            sensitivity == NotificationSensitivity.SECURITY_CRITICAL

    fun requiresGenericLockScreen(sensitivity: NotificationSensitivity): Boolean =
        sensitivity == NotificationSensitivity.SENSITIVE ||
            sensitivity == NotificationSensitivity.SECURITY_CRITICAL

    fun allowsFullBodyOnPush(sensitivity: NotificationSensitivity): Boolean =
        !requiresGenericPushCopy(sensitivity)

    fun allowsLockScreenContent(sensitivity: NotificationSensitivity): Boolean =
        sensitivity == NotificationSensitivity.PUBLIC_SUMMARY ||
            sensitivity == NotificationSensitivity.PRIVATE

    /**
     * Resuelve título/cuerpo seguros para push según sensibilidad.
     * Nunca devuelve el body original si es SENSITIVE o SECURITY_CRITICAL.
     */
    fun resolvePushCopy(
        sensitivity: NotificationSensitivity,
        title: String,
        body: String
    ): Pair<String, String> =
        if (requiresGenericPushCopy(sensitivity)) {
            GENERIC_PUSH_TITLE to GENERIC_PUSH_BODY
        } else {
            title to body
        }

    fun resolveLockScreenText(
        sensitivity: NotificationSensitivity,
        summary: String
    ): String =
        if (requiresGenericLockScreen(sensitivity)) {
            GENERIC_LOCKSCREEN_TEXT
        } else {
            summary
        }
}
