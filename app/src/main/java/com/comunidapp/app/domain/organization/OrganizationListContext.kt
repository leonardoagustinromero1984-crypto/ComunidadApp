package com.comunidapp.app.domain.organization

/**
 * Set only when a list is opened from an organization card.
 * A general entry clears it, so the same screens can show every public row.
 */
object OrganizationListContext {
    var organizationId: String? = null
        private set

    fun open(id: String?) {
        organizationId = id?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun clear() {
        organizationId = null
    }
}
