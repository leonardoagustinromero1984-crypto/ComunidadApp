package com.comunidapp.app.domain.vitacora

object VitaCoraUserHistoryFilter {
    val internalReasonMarkers = listOf(
        "CREATED_OWNER",
        "PUBLIC_CODE_ROTATED",
        "ACTIVATED",
        "GRANT",
        "REVOKE",
        "AUDIT",
        "ADDED_PERSON",
        "PERMISSION",
        "CREDENTIAL",
        "VERIFICATION",
        "ROTATED",
        "INTERNAL",
        "INFRASTRUCTURE"
    )

    fun isUserFacing(reason: String?, newStatus: String?): Boolean {
        val blob = "${reason.orEmpty()} ${newStatus.orEmpty()}".uppercase()
        if (internalReasonMarkers.any { blob.contains(it) }) return false
        if (reason.isNullOrBlank() && newStatus.equals("ACTIVE", ignoreCase = true)) return false
        return true
    }
}
