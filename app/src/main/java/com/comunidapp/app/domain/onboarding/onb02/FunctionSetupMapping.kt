package com.comunidapp.app.domain.onboarding.onb02

import com.comunidapp.app.navigation.NavRoutes

/**
 * Explicit onboarding-option → setup destination.
 * The function catalog is not the active-context list.
 */
enum class FunctionSetupKind {
    NONE,
    PERSONAL_CAPABILITY,
    PERSONAL_FOSTER,
    PROFESSIONAL_PERSON,
    PROVIDER_PERSON,
    PROVIDER_OR_ORGANIZATION,
    ORGANIZATION
}

data class FunctionSetupRoute(
    val function: LeoverFunction,
    val kind: FunctionSetupKind,
    val route: String?
)

object FunctionSetupMapping {

    const val CATALOG_IS_NOT_ACTIVE_CONTEXT_LIST = true
    const val FOSTER_IS_PERSONAL_FUNCTION = true
    const val FOSTER_CREATES_ORGANIZATION = false
    const val COUNTRY_UI_V1_HIDDEN = true
    const val COUNTRY_INTERNAL_DEFAULT = "AR"

    fun routeFor(function: LeoverFunction): FunctionSetupRoute = when (function) {
        LeoverFunction.PROFILE_PERSONAL -> FunctionSetupRoute(
            function, FunctionSetupKind.NONE, null
        )
        LeoverFunction.RESCUER -> FunctionSetupRoute(
            function, FunctionSetupKind.PERSONAL_CAPABILITY, NavRoutes.HOME
        )
        LeoverFunction.FOSTER -> FunctionSetupRoute(
            function, FunctionSetupKind.PERSONAL_FOSTER, NavRoutes.FOSTER_PLACEMENTS
        )
        LeoverFunction.VETERINARY_PROFESSIONAL -> FunctionSetupRoute(
            function, FunctionSetupKind.PROFESSIONAL_PERSON, NavRoutes.MY_VETERINARY_CLINICS
        )
        LeoverFunction.WALKER,
        LeoverFunction.CAREGIVER,
        LeoverFunction.TRAINER -> FunctionSetupRoute(
            function, FunctionSetupKind.PROVIDER_PERSON, NavRoutes.MY_BUSINESS
        )
        LeoverFunction.GROOMING -> FunctionSetupRoute(
            function, FunctionSetupKind.PROVIDER_PERSON, NavRoutes.MY_BUSINESS
        )
        LeoverFunction.DAYCARE -> FunctionSetupRoute(
            function, FunctionSetupKind.PROVIDER_OR_ORGANIZATION, NavRoutes.MY_BUSINESS
        )
        LeoverFunction.ORGANIZATION -> FunctionSetupRoute(
            function, FunctionSetupKind.ORGANIZATION, NavRoutes.createOrganization()
        )
    }

    fun neverUsesOrganizationForm(function: LeoverFunction): Boolean =
        function == LeoverFunction.FOSTER ||
            function == LeoverFunction.RESCUER ||
            function == LeoverFunction.PROFILE_PERSONAL ||
            function == LeoverFunction.WALKER ||
            function == LeoverFunction.CAREGIVER ||
            function == LeoverFunction.TRAINER ||
            function == LeoverFunction.VETERINARY_PROFESSIONAL ||
            function == LeoverFunction.GROOMING

    /**
     * Setup screens after in-host tutorials, in product order.
     * Organization is last and only present when explicitly selected.
     */
    fun setupRoutesInOrder(
        extras: Set<LeoverFunction>,
        organizationAction: OrganizationSetupAction?
    ): List<String> {
        val routes = mutableListOf<String>()
        Onb02FunctionOrder.extras.forEach { fn ->
            if (fn !in extras) return@forEach
            if (fn == LeoverFunction.ORGANIZATION) return@forEach
            routeFor(fn).route?.let { routes += it }
        }
        if (LeoverFunction.ORGANIZATION in extras) {
            routes += when (organizationAction) {
                OrganizationSetupAction.JOIN -> NavRoutes.MY_ORGANIZATIONS
                OrganizationSetupAction.CREATE -> NavRoutes.createOrganization()
                null -> NavRoutes.createOrganization()
            }
        }
        return routes.distinct()
    }

    fun setupRouteAfterSelection(
        extras: Set<LeoverFunction>,
        organizationAction: OrganizationSetupAction?
    ): String? = setupRoutesInOrder(extras, organizationAction).firstOrNull()

    fun landingRouteForOrganization(kind: OrganizationKindOption?): String = when (kind) {
        OrganizationKindOption.VETERINARY_CLINIC -> NavRoutes.MY_BUSINESS
        OrganizationKindOption.DAYCARE -> NavRoutes.DAYCARE_RESERVATIONS
        OrganizationKindOption.SHELTER -> NavRoutes.HOME
        OrganizationKindOption.PET_FRIENDLY_VENUE,
        OrganizationKindOption.SHOP,
        OrganizationKindOption.GROOMING,
        OrganizationKindOption.WALKING_CARE,
        OrganizationKindOption.TRAINING,
        OrganizationKindOption.BRAND -> NavRoutes.MY_BUSINESS
        else -> NavRoutes.MY_ORGANIZATIONS
    }

    fun catalogOptions(): List<LeoverFunction> = Onb02Planner.extraOptions()
}

enum class ProductOrganizationCategory(
    val visibleLabel: String,
    val capability: String
) {
    VETERINARY("Veterinaria", "VETERINARY_CLINIC"),
    SHOP("Tienda", "PROVIDER"),
    BOARDING("Guardería", "DAYCARE"),
    GROOMING("Peluquería", "GROOMING"),
    WALKING_CARE("Paseos y cuidado", "WALKING_CARE"),
    TRAINING("Educación / Adiestramiento", "TRAINING"),
    BRAND("Empresa o marca", "BRAND"),
    PET_FRIENDLY("Lugar pet friendly", "PET_FRIENDLY"),
    SHELTER_NGO("Refugio / ONG", "SHELTER"),
    DAYCARE("Guardería", "DAYCARE"),
    OTHER_SERVICE("Otro servicio", "OTHER");

    companion object {
        val commercialVisible: List<ProductOrganizationCategory> = listOf(
            VETERINARY, SHOP, BOARDING, GROOMING, WALKING_CARE, TRAINING, BRAND, PET_FRIENDLY
        )
        val visibleInitial: List<ProductOrganizationCategory> = commercialVisible
        val welfareVisible: List<ProductOrganizationCategory> = listOf(SHELTER_NGO)

        fun fromOrganizationKind(kind: OrganizationKindOption): ProductOrganizationCategory = when (kind) {
            OrganizationKindOption.VETERINARY_CLINIC -> VETERINARY
            OrganizationKindOption.SHOP -> SHOP
            OrganizationKindOption.DAYCARE -> BOARDING
            OrganizationKindOption.GROOMING -> GROOMING
            OrganizationKindOption.WALKING_CARE -> WALKING_CARE
            OrganizationKindOption.TRAINING -> TRAINING
            OrganizationKindOption.BRAND -> BRAND
            OrganizationKindOption.PET_FRIENDLY_VENUE -> PET_FRIENDLY
            OrganizationKindOption.SHELTER -> SHELTER_NGO
            OrganizationKindOption.OTHER_SERVICE -> OTHER_SERVICE
        }
    }
}
