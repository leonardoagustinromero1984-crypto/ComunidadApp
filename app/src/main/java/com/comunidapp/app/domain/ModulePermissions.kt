package com.comunidapp.app.domain

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.domain.context.OperationalContext

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

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canPublishContent(accountType: AccountType): Boolean =
        canPublishContent(personIdentityUser(accountType))

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canCreatePetProfile(accountType: AccountType): Boolean =
        canCreatePetProfile(personIdentityUser(accountType))

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canPublishAdoption(accountType: AccountType): Boolean =
        canPublishAdoption(personIdentityUser(accountType))

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canPublishLostFound(accountType: AccountType): Boolean =
        canPublishLostFound(personIdentityUser(accountType))

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canPublishPromo(accountType: AccountType): Boolean =
        RolePermissions.canPublishPromo(OperationalContext.Personal)

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canPublishQuestion(accountType: AccountType): Boolean =
        RolePermissions.canPublishQuestion(OperationalContext.Personal)

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canPublishFosterHome(accountType: AccountType): Boolean =
        RolePermissions.canPublishFosterHome(OperationalContext.Personal)

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canPublishShelterNeeds(accountType: AccountType): Boolean =
        RolePermissions.canPublishShelterNeeds(OperationalContext.Personal)

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canPublishEvent(accountType: AccountType): Boolean =
        RolePermissions.canPublishEvent(OperationalContext.Personal)

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canPublishDonation(accountType: AccountType): Boolean =
        RolePermissions.canPublishDonation(OperationalContext.Personal)

    private fun personIdentityUser(accountType: AccountType): User {
        @Suppress("UNUSED_PARAMETER")
        val ignored = accountType
        return User(id = "", name = "", email = "", accountType = AccountType.PERSON)
    }
}
