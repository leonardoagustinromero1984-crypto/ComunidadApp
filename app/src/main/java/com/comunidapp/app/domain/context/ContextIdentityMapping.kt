package com.comunidapp.app.domain.context

/**
 * Exhaustive ActiveContext mapping. No ordinal, no first-item fallback, no Refuge default.
 * ActiveContext is never authorization.
 */
object ContextIdentityMapping {

    const val ORDINAL_MAPPING_USED = false
    const val FALLBACK_TO_REFUGE = false
    const val VETERINARY_TO_REFUGE_ROOT_CAUSE =
        "ContextNavigation.organizationItems() always used shelter/refuge bottom nav " +
            "for OperationalContextKind.ORGANIZATION; veterinary orgs resolved as Organization " +
            "because AvailableContextsResolver did not map VETERINARY capability to Veterinary kind."

    fun label(kind: OperationalContextKind, entityName: String, organizationType: String? = null): String {
        val name = entityName.trim()
        val token = (organizationType ?: "").uppercase()
        return when (kind) {
            OperationalContextKind.PERSONAL -> "Perfil personal"
            OperationalContextKind.RESCUER -> "Rescatista"
            OperationalContextKind.FOSTER -> "Hogar de tránsito"
            OperationalContextKind.VETERINARY ->
                if (name.isBlank() || name.equals("Profesional veterinario", true)) {
                    "Profesional veterinario"
                } else {
                    "Veterinaria · $name"
                }
            OperationalContextKind.SHOP -> if (name.isBlank()) "Tienda" else "Tienda · $name"
            OperationalContextKind.PROVIDER -> providerLabel(token, name)
            OperationalContextKind.ORGANIZATION -> organizationLabel(token, name)
        }
    }

    fun contextForOrganization(
        organizationId: String,
        publicName: String,
        typeOrCapability: String?
    ): OperationalContext {
        val token = (typeOrCapability ?: "").uppercase()
        val name = publicName.trim().ifBlank { "Organización" }
        return when {
            isVeterinary(token) -> OperationalContext.Veterinary(
                entityId = organizationId,
                displayName = "Veterinaria · $name"
            )
            isShop(token) -> OperationalContext.Shop(
                entityId = organizationId,
                displayName = "Tienda · $name"
            )
            isGrooming(token) -> OperationalContext.Provider(
                entityId = organizationId,
                displayName = "Peluquería · $name",
                category = "GROOMING"
            )
            isDaycare(token) -> OperationalContext.Provider(
                entityId = organizationId,
                displayName = "Guardería · $name",
                category = "DAYCARE"
            )
            isWalkingCare(token) -> OperationalContext.Organization(
                entityId = organizationId,
                displayName = name,
                organizationType = "WALKING_CARE"
            )
            isTraining(token) -> OperationalContext.Organization(
                entityId = organizationId,
                displayName = name,
                organizationType = "TRAINING"
            )
            isBrand(token) -> OperationalContext.Organization(
                entityId = organizationId,
                displayName = name,
                organizationType = "BRAND"
            )
            isNgo(token) -> OperationalContext.Organization(
                entityId = organizationId,
                displayName = "ONG · $name",
                organizationType = "NGO"
            )
            isRefuge(token) -> OperationalContext.Organization(
                entityId = organizationId,
                displayName = "Refugio · $name",
                organizationType = "SHELTER"
            )
            else -> OperationalContext.Organization(
                entityId = organizationId,
                displayName = name,
                organizationType = typeOrCapability
            )
        }
    }

    fun isRefugeNav(context: OperationalContext): Boolean {
        if (context !is OperationalContext.Organization) return false
        val token = (context.organizationType ?: "").uppercase()
        return isRefuge(token) || isNgo(token)
    }

    fun isVeterinary(token: String): Boolean =
        token.contains("VET") && !token.contains("INVET")

    fun isShop(token: String): Boolean =
        token.contains("SHOP") || token.contains("TIENDA") || token.contains("PET_SHOP")

    fun isGrooming(token: String): Boolean =
        token.contains("GROOM") || token.contains("PELUQ")

    fun isDaycare(token: String): Boolean =
        token.contains("DAYCARE") || token.contains("BOARDING") ||
            token.contains("GUARDERIA") || token.contains("GUARDERÍA")

    fun isWalkingCare(token: String): Boolean =
        token.contains("WALKING") || token.contains("PASEO")

    fun isTraining(token: String): Boolean =
        token.contains("TRAIN") || token.contains("EDUC") || token.contains("ADIESTR")

    fun isBrand(token: String): Boolean =
        token.contains("BRAND") || token.contains("EMPRESA") || token.contains("MARCA")

    fun isNgo(token: String): Boolean =
        token == "NGO" || token == "ONG" || token.contains("NGO") ||
            token.endsWith("_ONG") || token.startsWith("ONG")

    fun isRefuge(token: String): Boolean =
        token.contains("SHELTER") || token.contains("REFUG") || token.contains("RESCUE")

    private fun providerLabel(token: String, name: String): String = when {
        isGrooming(token) -> if (name.isBlank()) "Peluquería" else "Peluquería · $name"
        isDaycare(token) -> if (name.isBlank()) "Guardería" else "Guardería · $name"
        isWalkingCare(token) -> name.ifBlank { "Paseador" }
        isTraining(token) -> name.ifBlank { "Educador / Adiestrador" }
        token.contains("WALK") || token.contains("PASEADOR") -> name.ifBlank { "Paseador" }
        token.contains("CARE") || token.contains("CUIDADOR") -> name.ifBlank { "Cuidador de mascotas" }
        else -> name.ifBlank { "Servicio" }
    }

    private fun organizationLabel(token: String, name: String): String = when {
        isVeterinary(token) -> "Veterinaria · $name"
        isShop(token) -> "Tienda · $name"
        isGrooming(token) -> "Peluquería · $name"
        isDaycare(token) -> "Guardería · $name"
        isNgo(token) -> "ONG · $name"
        isRefuge(token) -> "Refugio · $name"
        isBrand(token) || isWalkingCare(token) || isTraining(token) -> name
        else -> name
    }
}
