package com.comunidapp.app.domain

import com.comunidapp.app.data.model.AccountType

/**
 * Módulos activables sobre el perfil único del usuario (Documento Funcional §5).
 * La red social y el perfil base están siempre disponibles; el resto se activa según el tipo de cuenta.
 */
enum class LeoverModule {
    SOCIAL,
    PET_PROFILE,
    ADOPTIONS,
    FOSTER,
    SHELTERS,
    VETERINARY,
    EDUCATOR,
    WALKER,
    SHOP,
    LOST_FOUND,
    DONATIONS,
    EVENTS,
    REPUTATION,
    BADGES,
    ADMIN
}

enum class UserCategory {
    USUARIO,
    ORGANIZACION,
    PROFESIONAL,
    EMPRESA
}

@Deprecated("AccountType is LEGACY. Identity is always PERSON.")
fun AccountType.toUserCategory(): UserCategory {
    @Suppress("UNUSED_PARAMETER")
    val ignored = this
    return UserCategory.USUARIO
}

/** Módulos habilitados por defecto. AccountType no expande módulos. */
@Deprecated("AccountType is LEGACY. Always PERSON defaults.")
fun AccountType.defaultModules(): Set<LeoverModule> {
    @Suppress("UNUSED_PARAMETER")
    val ignored = this
    return setOf(
        LeoverModule.SOCIAL,
        LeoverModule.PET_PROFILE,
        LeoverModule.LOST_FOUND,
        LeoverModule.EVENTS,
        LeoverModule.DONATIONS,
        LeoverModule.FOSTER,
        LeoverModule.REPUTATION,
        LeoverModule.BADGES
    )
}

fun resolveActiveModules(
    accountType: AccountType,
    storedModules: Set<LeoverModule>?
): Set<LeoverModule> {
    // Identidad = PERSON. account_type no expande módulos ni permisos.
    @Suppress("UNUSED_PARAMETER")
    val ignoredLegacyType = accountType
    return storedModules?.takeIf { it.isNotEmpty() } ?: AccountType.PERSON.defaultModules()
}
