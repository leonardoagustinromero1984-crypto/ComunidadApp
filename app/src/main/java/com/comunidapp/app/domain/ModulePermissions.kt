package com.comunidapp.app.domain

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.User

/**
 * Matriz de permisos del Documento Funcional §20, aplicada sobre módulos activos del usuario.
 */
object ModulePermissions {

    fun activeModules(user: User): Set<LeoverModule> =
        resolveActiveModules(user.accountType, user.activeModules)

    fun canPublishContent(user: User): Boolean =
        LeoverModule.SOCIAL in activeModules(user)

    fun canCreatePetProfile(user: User): Boolean =
        LeoverModule.PET_PROFILE in activeModules(user)

    fun canPublishAdoption(user: User): Boolean =
        LeoverModule.ADOPTIONS in activeModules(user)

    fun canManageMultiplePets(user: User): Boolean =
        LeoverModule.SHELTERS in activeModules(user)

    fun canCreateCampaigns(user: User): Boolean =
        activeModules(user).any {
            it in setOf(LeoverModule.SHELTERS, LeoverModule.VETERINARY, LeoverModule.SHOP)
        }

    fun canManageAppointments(user: User): Boolean =
        activeModules(user).any {
            it in setOf(LeoverModule.VETERINARY, LeoverModule.EDUCATOR, LeoverModule.WALKER)
        }

    fun canPublishProducts(user: User): Boolean =
        LeoverModule.SHOP in activeModules(user)

    fun canManagePayments(user: User): Boolean =
        activeModules(user).any {
            it in setOf(
                LeoverModule.VETERINARY,
                LeoverModule.EDUCATOR,
                LeoverModule.WALKER,
                LeoverModule.SHOP
            )
        }

    fun canCreateEvents(user: User): Boolean =
        LeoverModule.EVENTS in activeModules(user)

    fun canPublishLostFound(user: User): Boolean =
        LeoverModule.LOST_FOUND in activeModules(user)

    fun canModerateContent(user: User): Boolean =
        // D-M02-08: active_modules / LeoverModule.ADMIN no otorgan moderación.
        // Usar AuthorizationService / PermissionRepository (moderation.view).
        false

    // Compatibilidad con chequeos basados solo en AccountType
    fun canPublishContent(accountType: AccountType): Boolean =
        canPublishContent(accountType.toSyntheticUser())

    fun canCreatePetProfile(accountType: AccountType): Boolean =
        canCreatePetProfile(accountType.toSyntheticUser())

    fun canPublishAdoption(accountType: AccountType): Boolean =
        canPublishAdoption(accountType.toSyntheticUser())

    fun canPublishLostFound(accountType: AccountType): Boolean =
        canPublishLostFound(accountType.toSyntheticUser())

    fun canPublishPromo(accountType: AccountType): Boolean =
        accountType.toAppMode() == AppMode.NEGOCIO

    fun canPublishQuestion(accountType: AccountType): Boolean = when (accountType.toAppMode()) {
        AppMode.PERSONA, AppMode.SOLIDARIO -> true
        AppMode.NEGOCIO -> accountType == AccountType.TRAINER
    }

    fun canPublishFosterHome(accountType: AccountType): Boolean =
        LeoverModule.FOSTER in accountType.defaultModules() ||
            accountType == AccountType.PERSON

    fun canPublishShelterNeeds(accountType: AccountType): Boolean =
        LeoverModule.SHELTERS in accountType.defaultModules()

    fun canPublishEvent(accountType: AccountType): Boolean =
        LeoverModule.EVENTS in accountType.defaultModules() ||
            accountType == AccountType.SHELTER ||
            accountType == AccountType.PERSON

    fun canPublishDonation(accountType: AccountType): Boolean =
        LeoverModule.DONATIONS in accountType.defaultModules() ||
            accountType == AccountType.SHELTER ||
            accountType == AccountType.PERSON

    private fun AccountType.toSyntheticUser(): User = User(
        id = "",
        name = "",
        email = "",
        accountType = this
    )
}
