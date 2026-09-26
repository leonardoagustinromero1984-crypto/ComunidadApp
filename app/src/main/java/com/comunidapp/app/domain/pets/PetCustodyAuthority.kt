package com.comunidapp.app.domain.pets

/**
 * Current-custodian authority. Original creator is historical only and
 * does not keep control after an accepted care transfer.
 */
object PetCustodyAuthority {

    fun isCurrentPersonCustodian(
        sessionUserId: String?,
        custodianKind: String?,
        custodianPersonId: String?
    ): Boolean {
        if (!custodianKind.isNullOrBlank() &&
            !custodianKind.equals("PERSON", ignoreCase = true)
        ) {
            return false
        }
        val session = sessionUserId?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val custodian = custodianPersonId?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return session == custodian
    }

    fun canArchive(sessionUserId: String?, custodianKind: String?, custodianPersonId: String?): Boolean =
        isCurrentPersonCustodian(sessionUserId, custodianKind, custodianPersonId)

    fun canManageResponsibilities(
        sessionUserId: String?,
        custodianKind: String?,
        custodianPersonId: String?
    ): Boolean = isCurrentPersonCustodian(sessionUserId, custodianKind, custodianPersonId)

    fun canMarkDeceased(
        sessionUserId: String?,
        custodianKind: String?,
        custodianPersonId: String?
    ): Boolean = isCurrentPersonCustodian(sessionUserId, custodianKind, custodianPersonId)

    fun canInitiateTransfer(
        sessionUserId: String?,
        custodianKind: String?,
        custodianPersonId: String?
    ): Boolean = isCurrentPersonCustodian(sessionUserId, custodianKind, custodianPersonId)

    fun canResponsibleTransfer(): Boolean = false
}
