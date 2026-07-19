package com.comunidapp.app.domain

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.User

enum class AppMode {
    PERSONA,
    SOLIDARIO,
    NEGOCIO
}

fun AccountType.toAppMode(): AppMode = when (this) {
    AccountType.PERSON -> AppMode.PERSONA
    AccountType.SHELTER, AccountType.FOSTER_HOME -> AppMode.SOLIDARIO
    AccountType.VET, AccountType.SHOP, AccountType.TRAINER, AccountType.WALKER -> AppMode.NEGOCIO
}

/**
 * Facade de permisos alineada al Documento Funcional §20.
 * Delega en [ModulePermissions] cuando hay contexto de usuario completo.
 */
object RolePermissions {

    fun canAccessSumate(accountType: AccountType): Boolean =
        accountType.toAppMode() != AppMode.NEGOCIO

    fun canAccessComunidad(accountType: AccountType): Boolean =
        accountType.toAppMode() != AppMode.NEGOCIO

    fun canManagePets(user: User): Boolean =
        ModulePermissions.canCreatePetProfile(user)

    fun canManagePets(accountType: AccountType): Boolean =
        ModulePermissions.canCreatePetProfile(accountType)

    fun canPublishAdoption(user: User): Boolean =
        ModulePermissions.canPublishAdoption(user)

    fun canPublishAdoption(accountType: AccountType): Boolean =
        ModulePermissions.canPublishAdoption(accountType)

    fun canPublishLostFound(user: User): Boolean =
        ModulePermissions.canPublishLostFound(user)

    fun canPublishLostFound(accountType: AccountType): Boolean =
        ModulePermissions.canPublishLostFound(accountType)

    fun canPublishFosterHome(accountType: AccountType): Boolean =
        ModulePermissions.canPublishFosterHome(accountType)

    fun canPublishShelterNeeds(accountType: AccountType): Boolean =
        ModulePermissions.canPublishShelterNeeds(accountType)

    fun canPublishEvent(accountType: AccountType): Boolean =
        ModulePermissions.canPublishEvent(accountType)

    fun canPublishDonation(accountType: AccountType): Boolean =
        ModulePermissions.canPublishDonation(accountType)

    fun canPublishPromo(accountType: AccountType): Boolean =
        ModulePermissions.canPublishPromo(accountType)

    fun canPublishQuestion(accountType: AccountType): Boolean =
        ModulePermissions.canPublishQuestion(accountType)

    fun canCreateCampaigns(user: User): Boolean =
        ModulePermissions.canCreateCampaigns(user)

    fun canManageMultiplePets(user: User): Boolean =
        ModulePermissions.canManageMultiplePets(user)

    fun canModerateContent(user: User): Boolean =
        // D-M02-08 / D-M02-03: AccountType y modules no conceden. Ver PermissionRepository.
        false

    fun businessPanelTitle(accountType: AccountType): String = when (accountType) {
        AccountType.VET -> "Mi consultorio"
        AccountType.SHOP -> "Mi tienda"
        AccountType.TRAINER -> "Mi servicio"
        AccountType.WALKER -> "Mi perfil profesional"
        else -> "Mi negocio"
    }
}
