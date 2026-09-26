package com.comunidapp.app.domain.canonical

import com.comunidapp.app.domain.pets.PetResponsibilityRole

/**
 * Maps canonical holder rows (OWNER / AUTHORIZED / RESPONSIBLE + PERSON|ORGANIZATION)
 * onto the existing family UI roles without collapsing to a single owner.
 */
object CanonicalHolderMapping {

    data class HolderInput(
        val linkId: String,
        val holderKind: String,
        val role: String,
        val status: String,
        val personId: String?,
        val organizationId: String?,
        val displayName: String?
    )

    data class MappedHolder(
        val linkId: String,
        val uiRole: PetResponsibilityRole,
        val holderKind: String,
        val canonicalRole: String,
        val status: String,
        val personId: String?,
        val organizationId: String?,
        val displayName: String?
    )

    fun map(
        holders: List<HolderInput>,
        creatorPersonId: String? = null
    ): List<MappedHolder> {
        val creatorId = creatorPersonId?.trim()?.takeIf { it.isNotEmpty() }
        val principalLinkId = holders.firstOrNull {
            isPersonOwner(it) && creatorId != null && it.personId == creatorId
        }?.linkId ?: holders.firstOrNull { isPersonOwner(it) }?.linkId
        return holders.map { holder ->
            MappedHolder(
                linkId = holder.linkId,
                uiRole = uiRole(holder, principalLinkId),
                holderKind = holder.holderKind.trim().uppercase(),
                canonicalRole = holder.role.trim().uppercase(),
                status = normalizeStatus(holder.status),
                personId = holder.personId?.takeIf { it.isNotBlank() },
                organizationId = holder.organizationId?.takeIf { it.isNotBlank() },
                displayName = holder.displayName?.trim()?.takeIf { it.isNotEmpty() }
            )
        }
    }

    fun uiRole(holder: HolderInput, firstPersonOwnerLinkId: String?): PetResponsibilityRole {
        val kind = holder.holderKind.trim().uppercase()
        val role = holder.role.trim().uppercase()
        return when {
            kind == "ORGANIZATION" -> PetResponsibilityRole.CO_RESPONSIBLE
            holder.linkId == firstPersonOwnerLinkId -> PetResponsibilityRole.PRINCIPAL
            role == "OWNER" || role == "AUTHORIZED" || role == "RESPONSIBLE" ->
                PetResponsibilityRole.CO_RESPONSIBLE
            else -> PetResponsibilityRole.TEMPORARY_CUSTODIAN
        }
    }

    private fun isPersonOwner(holder: HolderInput): Boolean =
        holder.holderKind.equals("PERSON", ignoreCase = true) &&
            holder.role.equals("OWNER", ignoreCase = true)

    private fun normalizeStatus(raw: String): String =
        when (raw.trim().uppercase()) {
            "ACTIVE" -> "ACTIVE"
            "ENDED", "REVOKED" -> "REVOKED"
            "EXPIRED" -> "EXPIRED"
            "PENDING", "PENDING_ACCEPTANCE" -> "PENDING_ACCEPTANCE"
            else -> "ACTIVE"
        }
}
