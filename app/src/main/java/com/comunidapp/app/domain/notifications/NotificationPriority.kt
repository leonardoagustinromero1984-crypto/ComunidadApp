package com.comunidapp.app.domain.notifications

/**
 * Prioridad de notificación M06.
 * URGENT no se usa para engagement; no evita revalidación de permisos.
 */
enum class NotificationPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT;

    companion object {
        fun fromString(raw: String?): NotificationPriority? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}
