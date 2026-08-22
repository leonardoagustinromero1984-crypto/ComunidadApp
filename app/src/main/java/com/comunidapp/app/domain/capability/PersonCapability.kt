package com.comunidapp.app.domain.capability

/**
 * Canonical PERSON capability codes. Not AccountType, not AppMode, not security authority.
 * Visible Spanish labels live in UX only.
 */
enum class PersonCapabilityCode(val storageValue: String) {
    RESCUER("RESCUER"),
    FOSTER("FOSTER");

    companion object {
        fun fromStorage(raw: String): PersonCapabilityCode? =
            entries.firstOrNull { it.storageValue == raw }
    }
}

data class PersonCapabilityRecord(
    val code: PersonCapabilityCode,
    val active: Boolean,
    val verificationStatus: String = "NOT_REQUESTED"
)

object RescuerCapabilityRules {
    const val VISIBLE_LABEL = "Rescatista"
    const val STORAGE_CODE = "RESCUER"
    const val ACCOUNT_TYPE_CREATED = false
    const val ORGANIZATION_TYPE_CREATED = false
    const val ACTIVE_CONTEXT_SECURITY_AUTHORITY = false
    const val ACCOUNT_TYPE_RUNTIME_AUTHORITY = 0
    const val APPMODE_RUNTIME_AUTHORITY = 0

    /**
     * Full rescue operations are not implemented yet, so there are no
     * active rescue-domain responsibilities that would be orphaned.
     * Deactivation is allowed. When rescue cases exist, block if any
     * remain open.
     */
    const val DEACTIVATION_ALLOWED_WHILE_NO_RESCUE_RESPONSIBILITIES = true

    fun hasOpenRescueResponsibilities(): Boolean = false

    fun canDeactivate(): Boolean =
        DEACTIVATION_ALLOWED_WHILE_NO_RESCUE_RESPONSIBILITIES &&
            !hasOpenRescueResponsibilities()

    fun capabilityGrantsPrivilegedOperations(): Boolean = false

    fun activeContextGrantsPermission(code: String): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignored = code
        return false
    }
}
