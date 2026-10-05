package com.comunidapp.app.domain.organization

/**
 * Lists opened from an organization card stay inside that organization.
 * A general entry, with no organization id, keeps every public row.
 */
data class CanonicalHelpRow(
    val id: String,
    val organizationId: String?
)

/**
 * Public money, goods and volunteering rows after the canonical read.
 * A blank organization id is the general list. A shelter id keeps only that shelter.
 */
object CanonicalPublicHelp {
    fun visible(organizationId: String?, rows: List<CanonicalHelpRow>): List<CanonicalHelpRow> =
        OrganizationScope.keep(organizationId, rows) { it.organizationId }
}

object OrganizationScope {
    fun <T> keep(organizationId: String?, items: List<T>, itemOrganizationId: (T) -> String?): List<T> {
        val id = organizationId?.trim().orEmpty()
        if (id.isEmpty()) return items
        return items.filter { itemOrganizationId(it) == id }
    }
}
