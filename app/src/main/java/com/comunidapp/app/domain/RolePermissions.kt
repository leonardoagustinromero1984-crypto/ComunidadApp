package com.comunidapp.app.domain

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.domain.context.OperationalContext

/**
 * Facade de permisos alineada al Documento Funcional §20.
 * Delega en [ModulePermissions] cuando hay contexto de usuario completo.
 */
object RolePermissions {

    fun canAccessSumate(context: OperationalContext): Boolean =
        context is OperationalContext.Personal ||
            context is OperationalContext.Organization ||
            context is OperationalContext.Foster ||
            context is OperationalContext.Rescuer

    fun canAccessComunidad(context: OperationalContext): Boolean =
        context is OperationalContext.Personal

    fun canPublishAdoption(context: OperationalContext): Boolean =
        context is OperationalContext.Organization ||
            context is OperationalContext.Rescuer ||
            context is OperationalContext.Foster

    fun canPublishLostFound(context: OperationalContext): Boolean =
        context is OperationalContext.Personal ||
            context is OperationalContext.Organization ||
            context is OperationalContext.Foster ||
            context is OperationalContext.Rescuer

    fun canPublishFosterHome(context: OperationalContext): Boolean =
        context is OperationalContext.Personal ||
            context is OperationalContext.Foster ||
            context is OperationalContext.Rescuer

    fun canPublishShelterNeeds(context: OperationalContext): Boolean =
        context is OperationalContext.Organization ||
            context is OperationalContext.Rescuer

    fun canPublishEvent(context: OperationalContext): Boolean =
        context is OperationalContext.Organization ||
            context is OperationalContext.Rescuer

    fun canPublishDonation(context: OperationalContext): Boolean =
        context is OperationalContext.Personal || context is OperationalContext.Organization

    fun canPublishPromo(context: OperationalContext): Boolean =
        context is OperationalContext.Provider ||
            context is OperationalContext.Veterinary ||
            context is OperationalContext.Shop

    fun canPublishQuestion(context: OperationalContext): Boolean =
        context is OperationalContext.Personal ||
            (context is OperationalContext.Provider && context.category == "TRAINING")

    fun canManagePets(context: OperationalContext): Boolean =
        context is OperationalContext.Personal ||
            context is OperationalContext.Foster ||
            context is OperationalContext.Organization ||
            context is OperationalContext.Rescuer

    fun businessPanelTitle(context: OperationalContext): String = when (context) {
        is OperationalContext.Veterinary -> "Mi perfil profesional"
        is OperationalContext.Shop -> "Mi negocio"
        is OperationalContext.Provider -> "Mi perfil profesional"
        is OperationalContext.Organization ->
            if (context.organizationType.equals("SHELTER", ignoreCase = true)) "Mi organización"
            else "Mi negocio"
        else -> "Mi negocio"
    }

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canAccessSumate(accountType: AccountType): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignored = accountType
        return canAccessSumate(OperationalContext.Personal)
    }

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canAccessComunidad(accountType: AccountType): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignored = accountType
        return canAccessComunidad(OperationalContext.Personal)
    }

    fun canManagePets(user: User): Boolean =
        ModulePermissions.canCreatePetProfile(user)

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canManagePets(accountType: AccountType): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignored = accountType
        return canManagePets(OperationalContext.Personal)
    }

    fun canPublishAdoption(user: User): Boolean =
        ModulePermissions.canPublishAdoption(user)

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canPublishAdoption(accountType: AccountType): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignored = accountType
        return canPublishAdoption(OperationalContext.Personal)
    }

    fun canPublishLostFound(user: User): Boolean =
        ModulePermissions.canPublishLostFound(user)

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canPublishLostFound(accountType: AccountType): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignored = accountType
        return canPublishLostFound(OperationalContext.Personal)
    }

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canPublishFosterHome(accountType: AccountType): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignored = accountType
        return canPublishFosterHome(OperationalContext.Personal)
    }

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canPublishShelterNeeds(accountType: AccountType): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignored = accountType
        return canPublishShelterNeeds(OperationalContext.Personal)
    }

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canPublishEvent(accountType: AccountType): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignored = accountType
        return canPublishEvent(OperationalContext.Personal)
    }

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canPublishDonation(accountType: AccountType): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignored = accountType
        return canPublishDonation(OperationalContext.Personal)
    }

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canPublishPromo(accountType: AccountType): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignored = accountType
        return canPublishPromo(OperationalContext.Personal)
    }

    @Deprecated("AccountType is LEGACY. Identity is PERSON; use OperationalContext.")
    fun canPublishQuestion(accountType: AccountType): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignored = accountType
        return canPublishQuestion(OperationalContext.Personal)
    }

    fun canCreateCampaigns(user: User): Boolean =
        ModulePermissions.canCreateCampaigns(user)

    fun canCreateCampaigns(context: OperationalContext): Boolean =
        context is OperationalContext.Organization ||
            context is OperationalContext.Veterinary ||
            context is OperationalContext.Shop ||
            context is OperationalContext.Rescuer

    fun canCreateCampaignsForContext(context: OperationalContext): Boolean =
        canCreateCampaigns(context)

    fun canManageMultiplePets(user: User): Boolean =
        ModulePermissions.canManageMultiplePets(user)

    fun canModerateContent(user: User): Boolean =
        // D-M02-08 / D-M02-03: AccountType y modules no conceden. Ver PermissionRepository.
        false

    @Deprecated("AccountType is LEGACY. Use OperationalContext.")
    fun businessPanelTitle(accountType: AccountType): String {
        @Suppress("UNUSED_PARAMETER")
        val ignored = accountType
        return businessPanelTitle(OperationalContext.Personal)
    }
}
