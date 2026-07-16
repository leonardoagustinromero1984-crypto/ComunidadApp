package com.comunidapp.app.domain.organization

/**
 * Vinculación futura de recursos legacy a una organización (D-M03-01 / D-M03-02).
 * No modifica tablas ni crea vínculos desde AccountType.
 */
enum class OrganizationResourceType {
    SHELTER_LISTING,
    SERVICE_PROFILE
}

enum class OrganizationResourceOwnerKind {
    /** Listing/perfil sin organización (válido). */
    PERSONAL,
    /** Vinculado a una organización. */
    ORGANIZATION
}

data class OrganizationResourceLink(
    val resourceType: OrganizationResourceType,
    val resourceId: String,
    val ownerKind: OrganizationResourceOwnerKind,
    val organizationId: OrganizationId? = null,
    val legacyOwnerUserId: String
) {
    init {
        when (ownerKind) {
            OrganizationResourceOwnerKind.PERSONAL ->
                require(organizationId == null) { "personal link cannot have organizationId" }
            OrganizationResourceOwnerKind.ORGANIZATION ->
                require(organizationId != null) { "organization link requires organizationId" }
        }
        require(resourceId.isNotBlank()) { "resourceId blank" }
        require(legacyOwnerUserId.isNotBlank()) { "legacyOwnerUserId blank" }
    }
}

object OrganizationResourceLinkRules {

    /**
     * Propiedad dual (persona + org como dueños principales simultáneos) es inválida.
     * Un recurso es PERSONAL **o** ORGANIZATION, no ambos.
     */
    fun validateExclusiveOwnership(link: OrganizationResourceLink): Result<Unit> =
        runCatching {
            // init del data class ya valida forma del vínculo
            link
            Unit
        }

    /**
     * Rechaza dos vínculos principales del mismo recurso con owner kinds distintos.
     */
    fun assertNoDualPrimaryOwnership(
        links: List<OrganizationResourceLink>
    ): Result<Unit> {
        val grouped = links.groupBy { it.resourceType to it.resourceId }
        for ((key, group) in grouped) {
            val kinds = group.map { it.ownerKind }.toSet()
            if (kinds.contains(OrganizationResourceOwnerKind.PERSONAL) &&
                kinds.contains(OrganizationResourceOwnerKind.ORGANIZATION)
            ) {
                return Result.failure(
                    IllegalStateException(
                        "dual_primary_ownership:${key.first}:${key.second}"
                    )
                )
            }
        }
        return Result.success(Unit)
    }

    /** AccountType no genera vínculo (siempre null). */
    fun linkFromAccountType(): OrganizationResourceLink? = null

    fun personalShelter(resourceId: String, ownerUserId: String): OrganizationResourceLink =
        OrganizationResourceLink(
            resourceType = OrganizationResourceType.SHELTER_LISTING,
            resourceId = resourceId,
            ownerKind = OrganizationResourceOwnerKind.PERSONAL,
            organizationId = null,
            legacyOwnerUserId = ownerUserId
        )

    fun organizationShelter(
        resourceId: String,
        organizationId: OrganizationId,
        legacyOwnerUserId: String
    ): OrganizationResourceLink =
        OrganizationResourceLink(
            resourceType = OrganizationResourceType.SHELTER_LISTING,
            resourceId = resourceId,
            ownerKind = OrganizationResourceOwnerKind.ORGANIZATION,
            organizationId = organizationId,
            legacyOwnerUserId = legacyOwnerUserId
        )

    fun personalService(resourceId: String, ownerUserId: String): OrganizationResourceLink =
        OrganizationResourceLink(
            resourceType = OrganizationResourceType.SERVICE_PROFILE,
            resourceId = resourceId,
            ownerKind = OrganizationResourceOwnerKind.PERSONAL,
            organizationId = null,
            legacyOwnerUserId = ownerUserId
        )

    fun organizationService(
        resourceId: String,
        organizationId: OrganizationId,
        legacyOwnerUserId: String
    ): OrganizationResourceLink =
        OrganizationResourceLink(
            resourceType = OrganizationResourceType.SERVICE_PROFILE,
            resourceId = resourceId,
            ownerKind = OrganizationResourceOwnerKind.ORGANIZATION,
            organizationId = organizationId,
            legacyOwnerUserId = legacyOwnerUserId
        )
}
