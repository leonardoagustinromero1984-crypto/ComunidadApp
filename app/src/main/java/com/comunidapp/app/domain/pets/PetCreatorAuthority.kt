package com.comunidapp.app.domain.pets

/**
 * Historical original creator. Does not grant control after a care transfer.
 * Privileges live in [PetCustodyAuthority] / server-side current custodian.
 */
object PetCreatorAuthority {

    fun isCreator(sessionUserId: String?, createdByUserId: String?): Boolean {
        val session = sessionUserId?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val creator = createdByUserId?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return session == creator
    }
}
