package com.comunidapp.app.domain.notifications

/**
 * Canales de entrega M06.
 * IN_APP es fuente de verdad; PUSH/EMAIL son deliveries independientes.
 * LOCAL solo para recordatorios aprobados. EMAIL no implica marketing.
 */
enum class NotificationChannel {
    IN_APP,
    PUSH,
    EMAIL,
    LOCAL;

    companion object {
        fun fromString(raw: String?): NotificationChannel? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}
