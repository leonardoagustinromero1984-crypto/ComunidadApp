package com.comunidapp.app.data.remote.supabase.m08

/**
 * LeoVer M08 — access context for the authenticated actor on a pet
 * (from `m08_get_pet_access_context`).
 */
data class PetAccessContext(
    val petId: String,
    val relationCode: String,
    val principalPersonId: String?,
    val principalOrganizationId: String?,
    val capabilities: List<String>,
    val canRead: Boolean,
    val canUpdate: Boolean,
    val canManageHealth: Boolean,
    val canManageMedia: Boolean,
    val canManageResponsibilities: Boolean = false,
    val canManageAuthorizations: Boolean = false,
    val canInitiateTransfer: Boolean = false,
    val canAcceptTransfer: Boolean = false,
    val canCancelTransfer: Boolean = false,
    val canArchive: Boolean,
    val canRestore: Boolean = false,
    val canMarkDeceased: Boolean,
    val canViewHistory: Boolean = false
)
