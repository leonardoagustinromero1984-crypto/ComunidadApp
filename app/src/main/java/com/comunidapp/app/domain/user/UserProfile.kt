package com.comunidapp.app.domain.user

/**
 * Perfil social completo (M02). Separado de [com.comunidapp.app.domain.auth.AuthUser].
 * No inventa columnas remotas aún; mapea sobre el modelo legacy [com.comunidapp.app.data.model.User].
 */
data class UserProfile(
    val id: String,
    /** Compatibilidad: columna `name` hasta Etapa 3. */
    val name: String,
    val displayName: String,
    val username: Username?,
    val email: String,
    val avatarUrl: String? = null,
    val avatarPath: String? = null,
    val bio: String? = null,
    val locationText: String? = null,
    val phone: String? = null,
    val phonePublic: Boolean = false,
    val profilePrivate: Boolean = true,
    val privacy: UserPrivacySettings = UserPrivacySettings(),
    val setupStatus: ProfileSetupStatus = ProfileSetupStatus.NOT_STARTED,
    val accountStatus: AccountStatus = AccountStatus.ACTIVE,
    val emailVerified: Boolean = false,
    /** Legacy / capacidad de negocio — no otorga permisos de plataforma. */
    val legacyAccountType: String? = null,
    val createdAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long? = null
) {
    val isProfilePublic: Boolean get() = !profilePrivate
}

/**
 * Proyección pública allowlist — sin email, phone, modules ni campos internos.
 */
data class PublicUserProfile(
    val id: String,
    val displayName: String,
    val username: String?,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val locationText: String? = null,
    val profilePrivate: Boolean = true
)

enum class ProfileSetupStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    BLOCKED
}

enum class AccountStatus {
    ACTIVE,
    RESTRICTED,
    SUSPENDED,
    BANNED
}

/**
 * Preferencias de visibilidad (contrato). Persistencia SQL en Etapa 3 (`user_privacy_settings`).
 */
data class UserPrivacySettings(
    val profilePrivate: Boolean = true,
    val showLocation: Boolean = true,
    /** Contrato M20 — no implementa chat. */
    val allowMessagesFrom: MessageAudience = MessageAudience.FOLLOWERS,
    /** Contrato M19 — no implementa follow. */
    val allowFollowRequests: Boolean = true,
    val showActivity: Boolean = false,
    /** Contrato M11. */
    val allowPublicIndexing: Boolean = false
)

enum class MessageAudience {
    ANYONE,
    FOLLOWERS,
    NOBODY
}

/**
 * Username normalizado (siempre lowercase).
 */
@JvmInline
value class Username private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        fun ofNormalized(normalized: String): Username = Username(normalized)
    }
}
