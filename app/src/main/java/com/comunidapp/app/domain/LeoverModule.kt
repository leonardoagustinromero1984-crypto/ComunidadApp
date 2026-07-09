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

fun AccountType.toUserCategory(): UserCategory = when (this) {
    AccountType.PERSON,
    AccountType.FOSTER_HOME -> UserCategory.USUARIO
    AccountType.SHELTER -> UserCategory.ORGANIZACION
    AccountType.VET,
    AccountType.TRAINER,
    AccountType.WALKER -> UserCategory.PROFESIONAL
    AccountType.SHOP -> UserCategory.EMPRESA
}

/** Módulos habilitados por defecto según el tipo de cuenta (§4 y §5). */
fun AccountType.defaultModules(): Set<LeoverModule> = when (this) {
    AccountType.PERSON -> setOf(
        LeoverModule.SOCIAL,
        LeoverModule.PET_PROFILE,
        LeoverModule.LOST_FOUND,
        LeoverModule.EVENTS
    )
    AccountType.SHELTER -> setOf(
        LeoverModule.SOCIAL,
        LeoverModule.PET_PROFILE,
        LeoverModule.ADOPTIONS,
        LeoverModule.SHELTERS,
        LeoverModule.LOST_FOUND,
        LeoverModule.DONATIONS,
        LeoverModule.EVENTS
    )
    AccountType.FOSTER_HOME -> setOf(
        LeoverModule.SOCIAL,
        LeoverModule.PET_PROFILE,
        LeoverModule.FOSTER,
        LeoverModule.ADOPTIONS,
        LeoverModule.LOST_FOUND,
        LeoverModule.EVENTS
    )
    AccountType.VET -> setOf(
        LeoverModule.SOCIAL,
        LeoverModule.PET_PROFILE,
        LeoverModule.VETERINARY,
        LeoverModule.LOST_FOUND,
        LeoverModule.DONATIONS,
        LeoverModule.EVENTS
    )
    AccountType.TRAINER -> setOf(
        LeoverModule.SOCIAL,
        LeoverModule.PET_PROFILE,
        LeoverModule.EDUCATOR,
        LeoverModule.LOST_FOUND,
        LeoverModule.EVENTS
    )
    AccountType.WALKER -> setOf(
        LeoverModule.SOCIAL,
        LeoverModule.PET_PROFILE,
        LeoverModule.WALKER,
        LeoverModule.LOST_FOUND,
        LeoverModule.EVENTS
    )
    AccountType.SHOP -> setOf(
        LeoverModule.SOCIAL,
        LeoverModule.SHOP,
        LeoverModule.LOST_FOUND,
        LeoverModule.DONATIONS,
        LeoverModule.EVENTS
    )
}

fun resolveActiveModules(
    accountType: AccountType,
    storedModules: Set<LeoverModule>?
): Set<LeoverModule> =
    storedModules?.takeIf { it.isNotEmpty() } ?: accountType.defaultModules()
