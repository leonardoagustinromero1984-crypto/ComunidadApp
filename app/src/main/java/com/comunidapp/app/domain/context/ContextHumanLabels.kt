package com.comunidapp.app.domain.context

import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.domain.onboarding.onb02.OrganizationKindOption
import com.comunidapp.app.domain.onboarding.onb02.ProfileActorTaxonomy
import com.comunidapp.app.domain.qa.PhysicalQaFix02Contracts

/**
 * User-visible context/category labels. Raw enums never reach UI.
 */
object ContextHumanLabels {

    const val RAW_ENUM_VISIBLE_TO_USER = PhysicalQaFix02Contracts.RAW_ENUM_VISIBLE_TO_USER

    fun homeBrandLine(context: OperationalContext): String =
        "LeoVer · ${shortLabel(context)}"

    fun shortLabel(context: OperationalContext): String = when (context) {
        is OperationalContext.Personal -> "Personal"
        is OperationalContext.Rescuer -> "Rescatista"
        is OperationalContext.Foster -> "Hogar de tránsito"
        is OperationalContext.Veterinary -> "Veterinaria"
        is OperationalContext.Shop -> "Tienda"
        is OperationalContext.Provider -> categoryLabel(context.category)
        is OperationalContext.Organization -> organizationLabel(context.organizationType)
    }

    fun switcherSubtitle(context: OperationalContext): String = shortLabel(context)

    fun categoryLabel(raw: String?): String {
        val token = raw.orEmpty().trim()
        if (token.isBlank()) return "Servicio"
        if (looksRawEnum(token) || token.contains('_')) {
            return humanCategory(token)
        }
        return when (token.uppercase()) {
            "WALKING", "WALKING_CARE", "WALKER" -> "Paseador / Cuidador"
            "CARE", "CAREGIVER" -> "Cuidador de mascotas"
            "GROOMING" -> "Peluquería"
            "TRAINING", "TRAINER" -> "Educador / Adiestrador"
            "DAYCARE", "BOARDING" -> "Guardería"
            "VETERINARY", "VET", "VETERINARY_CLINIC" -> "Veterinaria"
            "SHOP", "PET_SHOP" -> "Tienda"
            "PET_FRIENDLY", "PET_FRIENDLY_VENUE" -> "Lugar pet friendly"
            "SHELTER", "REFUGE" -> "Refugio"
            "NGO" -> "ONG"
            "BRAND" -> "Marca"
            else -> humanCategory(token)
        }
    }

    fun organizationLabel(raw: String?): String = categoryLabel(raw).let { label ->
        if (label == "Servicio") "Organización" else label
    }

    fun serviceCategoryLabel(category: ServiceCategory): String = when (category) {
        ServiceCategory.VET -> "Veterinaria"
        ServiceCategory.WALKER -> "Paseador / Cuidador"
        ServiceCategory.TRAINER -> "Educador / Adiestrador"
        ServiceCategory.CAREGIVER -> "Cuidador de mascotas"
        ServiceCategory.DAYCARE -> "Guardería"
        ServiceCategory.GROOMING -> "Peluquería"
        ServiceCategory.SHOP -> "Tienda"
        ServiceCategory.PET_FRIENDLY -> "Lugar pet friendly"
    }

    fun organizationKindLabel(kind: OrganizationKindOption): String =
        ProfileActorTaxonomy.businessVisibleLabel(kind)

    fun looksRawEnum(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed == trimmed.uppercase() && trimmed.any { it == '_' }) return true
        return trimmed in setOf(
            "SHELTER", "WALKING_CARE", "VETERINARY_CLINIC", "PROFESSIONAL",
            "WALKING", "GROOMING", "DAYCARE", "TRAINING", "NGO", "BRAND",
            "PET_FRIENDLY", "ORGANIZATION", "PROVIDER", "PERSONAL"
        )
    }

    private fun humanCategory(token: String): String {
        val upper = token.trim().uppercase()
        return when {
            ContextIdentityMapping.isVeterinary(upper) -> "Veterinaria"
            ContextIdentityMapping.isShop(upper) -> "Tienda"
            ContextIdentityMapping.isGrooming(upper) -> "Peluquería"
            ContextIdentityMapping.isDaycare(upper) -> "Guardería"
            ContextIdentityMapping.isWalkingCare(upper) -> "Paseador / Cuidador"
            ContextIdentityMapping.isTraining(upper) -> "Educador / Adiestrador"
            ContextIdentityMapping.isRefuge(upper) -> "Refugio"
            ContextIdentityMapping.isNgo(upper) -> "ONG"
            ContextIdentityMapping.isBrand(upper) -> "Marca"
            upper.contains("PET_FRIENDLY") -> "Lugar pet friendly"
            else -> token.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}
