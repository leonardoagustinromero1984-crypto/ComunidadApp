package com.comunidapp.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.comunidapp.app.domain.notifications.NotificationCategory
import com.comunidapp.app.domain.notifications.NotificationPriority

/**
 * Canales Android estables M06. No crea un canal por notificación.
 * Conserva [LEGACY_DEFAULT] para compatibilidad.
 */
object NotificationChannelRegistry {

    const val LEGACY_DEFAULT = "leover_default"
    const val SECURITY = "leover_security"
    const val ORGANIZATIONS = "leover_organizations"
    const val MODERATION_SUPPORT = "leover_moderation_support"
    const val PETS_ADOPTIONS = "leover_pets_adoptions"
    const val SOCIAL_MESSAGES = "leover_social_messages"
    const val SYSTEM = "leover_system"

    data class ChannelSpec(
        val id: String,
        val name: String,
        val description: String,
        val importance: Int
    )

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        specs().forEach { spec ->
            val existing = manager.getNotificationChannel(spec.id)
            if (existing == null) {
                manager.createNotificationChannel(
                    NotificationChannel(spec.id, spec.name, spec.importance).apply {
                        description = spec.description
                        enableVibration(spec.importance >= NotificationManager.IMPORTANCE_DEFAULT)
                        setShowBadge(true)
                    }
                )
            }
            // Nunca borrar ni recrear canales ya configurados por el usuario.
        }
    }

    fun channelIdForCategory(category: NotificationCategory?): String =
        when (category) {
            NotificationCategory.ACCOUNT, NotificationCategory.SECURITY -> SECURITY
            NotificationCategory.ORGANIZATION, NotificationCategory.INVITATION -> ORGANIZATIONS
            NotificationCategory.MODERATION,
            NotificationCategory.APPEAL,
            NotificationCategory.VERIFICATION,
            NotificationCategory.SUPPORT -> MODERATION_SUPPORT
            NotificationCategory.PET,
            NotificationCategory.ADOPTION,
            NotificationCategory.FOSTER,
            NotificationCategory.SHELTER,
            NotificationCategory.LOST_FOUND -> PETS_ADOPTIONS
            NotificationCategory.SOCIAL, NotificationCategory.MESSAGE -> SOCIAL_MESSAGES
            null -> SYSTEM
            else -> SYSTEM
        }

    fun channelIdOrSystem(raw: String?): String {
        val id = raw?.trim().orEmpty()
        return if (id in knownIds()) id else SYSTEM
    }

    fun importanceForPriority(priority: NotificationPriority): Int =
        when (priority) {
            NotificationPriority.URGENT, NotificationPriority.HIGH ->
                NotificationCompatImportance.HIGH
            NotificationPriority.NORMAL -> NotificationCompatImportance.DEFAULT
            NotificationPriority.LOW -> NotificationCompatImportance.LOW
        }

    fun knownIds(): Set<String> = setOf(
        LEGACY_DEFAULT, SECURITY, ORGANIZATIONS, MODERATION_SUPPORT,
        PETS_ADOPTIONS, SOCIAL_MESSAGES, SYSTEM
    )

    private fun specs(): List<ChannelSpec> = listOf(
        ChannelSpec(
            LEGACY_DEFAULT,
            "LeoVer",
            "Avisos generales de LeoVer",
            NotificationManager.IMPORTANCE_DEFAULT
        ),
        ChannelSpec(
            SECURITY,
            "Seguridad y cuenta",
            "Alertas de seguridad y cuenta",
            NotificationManager.IMPORTANCE_HIGH
        ),
        ChannelSpec(
            ORGANIZATIONS,
            "Organizaciones",
            "Invitaciones y actividad de organizaciones",
            NotificationManager.IMPORTANCE_DEFAULT
        ),
        ChannelSpec(
            MODERATION_SUPPORT,
            "Moderación y soporte",
            "Casos, apelaciones, verificación y tickets",
            NotificationManager.IMPORTANCE_HIGH
        ),
        ChannelSpec(
            PETS_ADOPTIONS,
            "Mascotas y adopciones",
            "Mascotas, adopciones, hogares y casos",
            NotificationManager.IMPORTANCE_DEFAULT
        ),
        ChannelSpec(
            SOCIAL_MESSAGES,
            "Social y mensajes",
            "Amistades y mensajes",
            NotificationManager.IMPORTANCE_DEFAULT
        ),
        ChannelSpec(
            SYSTEM,
            "Sistema",
            "Avisos del sistema LeoVer",
            NotificationManager.IMPORTANCE_LOW
        )
    )

    /** Wrapper para no acoplar tests a android.os Build. */
    object NotificationCompatImportance {
        const val HIGH = 4
        const val DEFAULT = 3
        const val LOW = 2
    }

    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
