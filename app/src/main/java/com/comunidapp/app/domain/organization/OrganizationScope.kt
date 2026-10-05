package com.comunidapp.app.domain.organization

/**
 * Lists opened from an organization card stay inside that organization.
 * A general entry, with no organization id, keeps every public row.
 */
object OrganizationScope {
    fun <T> keep(organizationId: String?, items: List<T>, itemOrganizationId: (T) -> String?): List<T> {
        val id = organizationId?.trim().orEmpty()
        if (id.isEmpty()) return items
        return items.filter { itemOrganizationId(it) == id }
    }
}
