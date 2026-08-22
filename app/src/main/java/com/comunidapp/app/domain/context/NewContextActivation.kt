package com.comunidapp.app.domain.context

import com.comunidapp.app.domain.onboarding.onb02.FunctionSelection
import com.comunidapp.app.domain.onboarding.onb02.LeoverFunction
import com.comunidapp.app.domain.onboarding.onb02.OrganizationKindOption
import com.comunidapp.app.domain.onboarding.onb02.ProfileActorKind
import com.comunidapp.app.navigation.NavRoutes

/**
 * After creating a context, LeoVer must activate it and land on that dashboard.
 * Back never changes ActiveContext.
 */
object NewContextActivation {

    const val AUTO_ACTIVATED = true
    const val BACK_CHANGES_CONTEXT = false

    fun contextForOrganization(
        organizationId: String,
        publicName: String,
        typeOrCapability: String?
    ): OperationalContext = ContextIdentityMapping.contextForOrganization(
        organizationId = organizationId,
        publicName = publicName,
        typeOrCapability = typeOrCapability
    )

    fun contextForSelection(
        userId: String,
        selection: FunctionSelection,
        available: List<OperationalContext>,
        prefer: LeoverFunction? = null
    ): OperationalContext? {
        if (userId.isBlank()) return null
        if (prefer == LeoverFunction.FOSTER ||
            (LeoverFunction.FOSTER in selection.extras && LeoverFunction.RESCUER !in selection.extras)
        ) {
            return available.filterIsInstance<OperationalContext.Foster>().firstOrNull()
                ?: OperationalContext.Foster(entityId = userId, displayName = "Hogar de tránsito")
        }
        return when {
            selection.actorKind == ProfileActorKind.FOSTER ->
                available.filterIsInstance<OperationalContext.Foster>().firstOrNull()
                    ?: OperationalContext.Foster(entityId = userId, displayName = "Hogar de tránsito")
            selection.actorKind == ProfileActorKind.PERSON && selection.extras.isEmpty() ->
                OperationalContext.Personal
            LeoverFunction.RESCUER in selection.extras ->
                available.filterIsInstance<OperationalContext.Rescuer>().firstOrNull()
                    ?: OperationalContext.Rescuer(entityId = userId)
            LeoverFunction.FOSTER in selection.extras ->
                available.filterIsInstance<OperationalContext.Foster>().firstOrNull()
                    ?: OperationalContext.Foster(entityId = userId, displayName = "Hogar de tránsito")
            LeoverFunction.WALKER in selection.extras ->
                preferredProvider(available, "WALKING")
                    ?: OperationalContext.Provider(
                        entityId = "onb02:WALKING:$userId",
                        displayName = "Paseador",
                        category = "WALKING"
                    )
            LeoverFunction.GROOMING in selection.extras ->
                preferredProvider(available, "GROOMING")
            LeoverFunction.TRAINER in selection.extras ->
                preferredProvider(available, "TRAINING")
            LeoverFunction.CAREGIVER in selection.extras ->
                preferredProvider(available, "CARE")
            LeoverFunction.VETERINARY_PROFESSIONAL in selection.extras ->
                available.filterIsInstance<OperationalContext.Veterinary>().firstOrNull()
            selection.organizationKind != null ->
                available.firstOrNull { matchesKind(it, selection.organizationKind) }
            else -> null
        }
    }

    fun landingRoute(
        context: OperationalContext,
        organizationKind: OrganizationKindOption? = null
    ): String = when {
        context is OperationalContext.Veterinary ||
            organizationKind == OrganizationKindOption.VETERINARY_CLINIC -> NavRoutes.MY_BUSINESS
        ContextIdentityMapping.isRefugeNav(context) ||
            organizationKind == OrganizationKindOption.SHELTER -> NavRoutes.HOME
        context is OperationalContext.Rescuer -> NavRoutes.HOME
        context is OperationalContext.Foster -> NavRoutes.FOSTER_PLACEMENTS
        context is OperationalContext.Provider ||
            context is OperationalContext.Shop ||
            organizationKind != null -> NavRoutes.MY_BUSINESS
        else -> NavRoutes.HOME
    }

    private fun preferredProvider(
        available: List<OperationalContext>,
        category: String
    ): OperationalContext.Provider? {
        val matches = available.filterIsInstance<OperationalContext.Provider>()
            .filter { it.category.equals(category, ignoreCase = true) }
        return matches.firstOrNull { !it.entityId.startsWith("onb02:") } ?: matches.firstOrNull()
    }

    private fun matchesKind(context: OperationalContext, kind: OrganizationKindOption): Boolean {
        val token = when (context) {
            is OperationalContext.Organization -> context.organizationType.orEmpty()
            is OperationalContext.Veterinary -> "VETERINARY"
            is OperationalContext.Shop -> "SHOP"
            is OperationalContext.Provider -> context.category.orEmpty()
            else -> ""
        }.uppercase()
        return when (kind) {
            OrganizationKindOption.SHELTER -> ContextIdentityMapping.isRefuge(token)
            OrganizationKindOption.VETERINARY_CLINIC -> ContextIdentityMapping.isVeterinary(token)
            OrganizationKindOption.SHOP -> ContextIdentityMapping.isShop(token)
            OrganizationKindOption.DAYCARE -> ContextIdentityMapping.isDaycare(token)
            OrganizationKindOption.GROOMING -> ContextIdentityMapping.isGrooming(token)
            OrganizationKindOption.WALKING_CARE -> ContextIdentityMapping.isWalkingCare(token)
            OrganizationKindOption.TRAINING -> ContextIdentityMapping.isTraining(token)
            OrganizationKindOption.BRAND -> ContextIdentityMapping.isBrand(token)
            OrganizationKindOption.PET_FRIENDLY_VENUE -> token.contains("PET_FRIENDLY")
            OrganizationKindOption.OTHER_SERVICE -> true
        }
    }
}
