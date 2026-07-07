package com.comunidapp.app.domain

import com.comunidapp.app.data.model.AccountType

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

object RolePermissions {

    fun canAccessSumate(accountType: AccountType): Boolean =
        accountType.toAppMode() != AppMode.NEGOCIO

    fun canAccessComunidad(accountType: AccountType): Boolean =
        accountType.toAppMode() != AppMode.NEGOCIO

    fun canManagePets(accountType: AccountType): Boolean =
        accountType == AccountType.PERSON

    fun canPublishAdoption(accountType: AccountType): Boolean =
        accountType == AccountType.SHELTER

    fun canPublishLostFound(accountType: AccountType): Boolean =
        accountType in setOf(AccountType.PERSON, AccountType.SHELTER, AccountType.FOSTER_HOME)

    fun canPublishFosterHome(accountType: AccountType): Boolean =
        accountType == AccountType.FOSTER_HOME

    fun canPublishShelterNeeds(accountType: AccountType): Boolean =
        accountType == AccountType.SHELTER

    fun canPublishPromo(accountType: AccountType): Boolean =
        accountType.toAppMode() == AppMode.NEGOCIO

    fun canPublishQuestion(accountType: AccountType): Boolean = when (accountType.toAppMode()) {
        AppMode.PERSONA, AppMode.SOLIDARIO -> true
        AppMode.NEGOCIO -> accountType == AccountType.TRAINER
    }

    fun businessPanelTitle(accountType: AccountType): String = when (accountType) {
        AccountType.VET -> "Mi consultorio"
        AccountType.SHOP -> "Mi tienda"
        AccountType.TRAINER -> "Mi servicio"
        AccountType.WALKER -> "Mi perfil profesional"
        else -> "Mi negocio"
    }
}
