package com.comunidapp.app.domain.canonical

import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.onboarding.onb02.LeoverFunction

data class CanonicalProviderHolder(
    val kind: String,
    val personId: String?,
    val organizationId: String?
) {
    init {
        require(kind == PERSON || kind == ORGANIZATION)
        if (kind == PERSON) {
            require(!personId.isNullOrBlank() && organizationId == null)
        } else {
            require(!organizationId.isNullOrBlank() && personId == null)
        }
    }

    companion object {
        const val PERSON = "PERSON"
        const val ORGANIZATION = "ORGANIZATION"
    }
}

object CanonicalProviderWrite {

    const val DIRECTORY_ONLY_V1 = true
    const val MARKETPLACE_FIELDS_REQUIRED = false
    const val INCOMPLETE_SETUP_MESSAGE =
        "Completá los datos de tu servicio para publicarlo."

    fun storageCategory(category: ServiceCategory): String = when (category) {
        ServiceCategory.VET -> "VETERINARY"
        ServiceCategory.WALKER -> "WALKING"
        ServiceCategory.TRAINER -> "TRAINING"
        ServiceCategory.CAREGIVER -> "CARE"
        ServiceCategory.DAYCARE -> "BOARDING"
        ServiceCategory.GROOMING -> "GROOMING"
        ServiceCategory.SHOP -> "SHOP"
        ServiceCategory.PET_FRIENDLY -> "PET_FRIENDLY"
    }

    fun fromStorageCategory(code: String): ServiceCategory? = when (code.trim().uppercase()) {
        "VETERINARY", "VET" -> ServiceCategory.VET
        "WALKING", "WALKER", "WALKING_CARE" -> ServiceCategory.WALKER
        "TRAINING", "TRAINER" -> ServiceCategory.TRAINER
        "CARE", "CAREGIVER", "CUIDADOR" -> ServiceCategory.CAREGIVER
        "DAYCARE", "BOARDING", "GUARDERIA", "GUARDERÍA" -> ServiceCategory.DAYCARE
        "GROOMING" -> ServiceCategory.GROOMING
        "SHOP", "STORE", "RETAIL", "BRAND" -> ServiceCategory.SHOP
        "VETERINARY_CLINIC" -> ServiceCategory.VET
        "PET_FRIENDLY", "PET_FRIENDLY_VENUE" -> ServiceCategory.PET_FRIENDLY
        else -> null
    }

    fun categoryFromContext(context: OperationalContext): ServiceCategory? = when (context) {
        is OperationalContext.Shop -> ServiceCategory.SHOP
        is OperationalContext.Veterinary -> ServiceCategory.VET
        is OperationalContext.Provider -> fromStorageCategory(context.category.orEmpty())
        is OperationalContext.Organization -> fromStorageCategory(context.organizationType.orEmpty())
        else -> null
    }

    fun resolveHolder(
        userId: String,
        context: OperationalContext,
        myOrganizationIds: Set<String>
    ): CanonicalProviderHolder {
        val orgId = when (context) {
            is OperationalContext.Organization -> eligibleOrganizationId(context.entityId, myOrganizationIds)
            is OperationalContext.Shop -> eligibleOrganizationId(context.entityId, myOrganizationIds)
            is OperationalContext.Veterinary -> eligibleOrganizationId(context.entityId, myOrganizationIds)
            is OperationalContext.Provider -> null
            else -> null
        }
        return if (!orgId.isNullOrBlank() && orgId in myOrganizationIds) {
            CanonicalProviderHolder(
                kind = CanonicalProviderHolder.ORGANIZATION,
                personId = null,
                organizationId = orgId
            )
        } else {
            CanonicalProviderHolder(
                kind = CanonicalProviderHolder.PERSON,
                personId = userId,
                organizationId = null
            )
        }
    }

    fun extraProviderContexts(
        userId: String,
        extras: Set<LeoverFunction>
    ): List<OperationalContext.Provider> = extras.mapNotNull { function ->
        val category = when (function) {
            LeoverFunction.WALKER -> "WALKING"
            LeoverFunction.CAREGIVER -> "CARE"
            LeoverFunction.TRAINER -> "TRAINING"
            LeoverFunction.GROOMING -> "GROOMING"
            LeoverFunction.DAYCARE -> "DAYCARE"
            else -> null
        } ?: return@mapNotNull null
        OperationalContext.Provider(
            entityId = "onb02:$category:$userId",
            displayName = function.visibleLabel,
            category = category
        )
    }

    fun isCanonicalUuid(value: String): Boolean =
        UUID_REGEX.matches(value)

    private fun eligibleOrganizationId(raw: String, membership: Set<String>): String? {
        if (raw.isBlank() || raw.startsWith("onb02:")) return null
        return raw.takeIf { it in membership }
    }

    private val UUID_REGEX =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
}
