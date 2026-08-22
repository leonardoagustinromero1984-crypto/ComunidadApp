package com.comunidapp.app.domain.onboarding.onb02

import com.comunidapp.app.domain.capability.PersonCapabilityCode
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.context.ContextIdentityMapping
import com.comunidapp.app.domain.qa.PhysicalQaFix02Contracts

/**
 * Extra functions offered from "Agregar función / Usar LeoVer como".
 * Never first-level actors. Never Persona. Never already-active capabilities.
 */
data class AddFunctionOption(
    val id: String,
    val visibleLabel: String,
    val subtitle: String,
    val function: LeoverFunction,
    val organizationKind: OrganizationKindOption? = null,
    val professionalSpecialty: IndependentProfessionalSpecialty? = null
)

data class OccupiedFunctions(
    val extras: Set<LeoverFunction> = emptySet(),
    val capabilities: Set<PersonCapabilityCode> = emptySet(),
    val contexts: List<OperationalContext> = emptyList()
)

enum class AddFunctionGroupKind {
    RESCUER,
    SHELTER,
    PROFESSIONAL,
    BUSINESS,
    FOSTER
}

data class AddFunctionGroup(
    val kind: AddFunctionGroupKind,
    val visibleLabel: String,
    val subtitle: String,
    val leaf: AddFunctionOption? = null
)

object AddFunctionCatalog {

    const val PERSON_IN_ADD_FUNCTION = PhysicalQaFix02Contracts.PERSON_IN_ADD_FUNCTION
    const val FOSTER_IN_ADD_FUNCTION = PhysicalQaFix02Contracts.FOSTER_IN_ADD_FUNCTION
    const val ADD_FUNCTION_GROUPED = true
    const val PROFESSIONAL_CATEGORIES_FLAT = false
    const val BUSINESS_CATEGORIES_FLAT = false
    const val ADD_FUNCTION_TOP_LEVEL_COUNT = 5

    fun catalog(): List<AddFunctionOption> = listOf(
        AddFunctionOption(
            id = "RESCUER",
            visibleLabel = LeoverFunction.RESCUER.visibleLabel,
            subtitle = LeoverFunction.RESCUER.subtitle,
            function = LeoverFunction.RESCUER
        ),
        AddFunctionOption(
            id = "FOSTER",
            visibleLabel = LeoverFunction.FOSTER.visibleLabel,
            subtitle = LeoverFunction.FOSTER.subtitle,
            function = LeoverFunction.FOSTER
        ),
        AddFunctionOption(
            id = "WALKER",
            visibleLabel = IndependentProfessionalSpecialty.WALKER_CAREGIVER.visibleLabel,
            subtitle = LeoverFunction.WALKER.subtitle,
            function = LeoverFunction.WALKER,
            professionalSpecialty = IndependentProfessionalSpecialty.WALKER_CAREGIVER
        ),
        AddFunctionOption(
            id = "GROOMING",
            visibleLabel = IndependentProfessionalSpecialty.GROOMER.visibleLabel,
            subtitle = LeoverFunction.GROOMING.subtitle,
            function = LeoverFunction.GROOMING,
            professionalSpecialty = IndependentProfessionalSpecialty.GROOMER
        ),
        AddFunctionOption(
            id = "TRAINER",
            visibleLabel = IndependentProfessionalSpecialty.TRAINER.visibleLabel,
            subtitle = LeoverFunction.TRAINER.subtitle,
            function = LeoverFunction.TRAINER,
            professionalSpecialty = IndependentProfessionalSpecialty.TRAINER
        ),
        AddFunctionOption(
            id = "VETERINARY_PROFESSIONAL",
            visibleLabel = IndependentProfessionalSpecialty.VETERINARIAN.visibleLabel,
            subtitle = LeoverFunction.VETERINARY_PROFESSIONAL.subtitle,
            function = LeoverFunction.VETERINARY_PROFESSIONAL,
            professionalSpecialty = IndependentProfessionalSpecialty.VETERINARIAN
        ),
        AddFunctionOption(
            id = "SHELTER",
            visibleLabel = "Refugio / Organización de rescate",
            subtitle = "Entidad dedicada al rescate y adopción.",
            function = LeoverFunction.ORGANIZATION,
            organizationKind = OrganizationKindOption.SHELTER
        ),
        AddFunctionOption(
            id = "VETERINARY_CLINIC",
            visibleLabel = ProfileActorTaxonomy.businessVisibleLabel(OrganizationKindOption.VETERINARY_CLINIC),
            subtitle = "Clínica u organización veterinaria.",
            function = LeoverFunction.ORGANIZATION,
            organizationKind = OrganizationKindOption.VETERINARY_CLINIC
        ),
        AddFunctionOption(
            id = "SHOP",
            visibleLabel = ProfileActorTaxonomy.businessVisibleLabel(OrganizationKindOption.SHOP),
            subtitle = "Tienda de productos para mascotas.",
            function = LeoverFunction.ORGANIZATION,
            organizationKind = OrganizationKindOption.SHOP
        ),
        AddFunctionOption(
            id = "DAYCARE",
            visibleLabel = ProfileActorTaxonomy.businessVisibleLabel(OrganizationKindOption.DAYCARE),
            subtitle = "Guardería u hospedaje.",
            function = LeoverFunction.ORGANIZATION,
            organizationKind = OrganizationKindOption.DAYCARE
        ),
        AddFunctionOption(
            id = "GROOMING_ORG",
            visibleLabel = "Peluquería",
            subtitle = "Local de peluquería.",
            function = LeoverFunction.ORGANIZATION,
            organizationKind = OrganizationKindOption.GROOMING
        ),
        AddFunctionOption(
            id = "WALKING_CARE",
            visibleLabel = ProfileActorTaxonomy.businessVisibleLabel(OrganizationKindOption.WALKING_CARE),
            subtitle = "Paseos y cuidado a cargo de un equipo.",
            function = LeoverFunction.ORGANIZATION,
            organizationKind = OrganizationKindOption.WALKING_CARE
        ),
        AddFunctionOption(
            id = "TRAINING_ORG",
            visibleLabel = ProfileActorTaxonomy.businessVisibleLabel(OrganizationKindOption.TRAINING),
            subtitle = "Adiestramiento u educación.",
            function = LeoverFunction.ORGANIZATION,
            organizationKind = OrganizationKindOption.TRAINING
        ),
        AddFunctionOption(
            id = "BRAND",
            visibleLabel = ProfileActorTaxonomy.businessVisibleLabel(OrganizationKindOption.BRAND),
            subtitle = "Marca o empresa.",
            function = LeoverFunction.ORGANIZATION,
            organizationKind = OrganizationKindOption.BRAND
        ),
        AddFunctionOption(
            id = "PET_FRIENDLY",
            visibleLabel = ProfileActorTaxonomy.businessVisibleLabel(OrganizationKindOption.PET_FRIENDLY_VENUE),
            subtitle = "Hotel, café, restaurante u otro lugar que admite mascotas.",
            function = LeoverFunction.ORGANIZATION,
            organizationKind = OrganizationKindOption.PET_FRIENDLY_VENUE
        )
    )

    fun groupedAvailable(occupied: OccupiedFunctions): List<AddFunctionGroup> {
        require(!PERSON_IN_ADD_FUNCTION)
        require(com.comunidapp.app.domain.context.ContextRegistry.FOSTER_IN_ADD_FUNCTION)
        val leaves = catalog()
        fun leaf(id: String) = leaves.first { it.id == id }
        return com.comunidapp.app.domain.context.ContextRegistry.addFunctionTopLevel().map { top ->
            AddFunctionGroup(
                kind = top.kind,
                visibleLabel = top.visibleLabel,
                subtitle = top.subtitle,
                leaf = top.leafId?.let { leaf(it) }
            )
        }.filter { group ->
            when (group.kind) {
                AddFunctionGroupKind.RESCUER,
                AddFunctionGroupKind.SHELTER,
                AddFunctionGroupKind.FOSTER ->
                    group.leaf != null && !isOccupied(group.leaf, occupied)
                AddFunctionGroupKind.PROFESSIONAL ->
                    availableProfessional(occupied).isNotEmpty()
                AddFunctionGroupKind.BUSINESS ->
                    availableBusiness(occupied).isNotEmpty()
            }
        }
    }

    fun availableProfessional(occupied: OccupiedFunctions): List<AddFunctionOption> =
        catalog().filter { it.professionalSpecialty != null && !isOccupied(it, occupied) }

    fun availableBusiness(occupied: OccupiedFunctions): List<AddFunctionOption> =
        catalog().filter {
            it.organizationKind != null &&
                it.organizationKind != OrganizationKindOption.SHELTER &&
                !isOccupied(it, occupied)
        }

    fun topLevelIds(): List<String> = listOf(
        AddFunctionGroupKind.RESCUER.name,
        AddFunctionGroupKind.SHELTER.name,
        AddFunctionGroupKind.PROFESSIONAL.name,
        AddFunctionGroupKind.BUSINESS.name,
        AddFunctionGroupKind.FOSTER.name
    )

    fun available(occupied: OccupiedFunctions): List<AddFunctionOption> {
        require(!PERSON_IN_ADD_FUNCTION)
        return catalog().filter { option -> !isOccupied(option, occupied) }
    }

    fun isOccupied(option: AddFunctionOption, occupied: OccupiedFunctions): Boolean {
        if (option.function == LeoverFunction.PROFILE_PERSONAL) return true
        return when (option.id) {
            "RESCUER" ->
                LeoverFunction.RESCUER in occupied.extras ||
                    PersonCapabilityCode.RESCUER in occupied.capabilities ||
                    occupied.contexts.any { it is OperationalContext.Rescuer }
            "FOSTER" ->
                LeoverFunction.FOSTER in occupied.extras ||
                    PersonCapabilityCode.FOSTER in occupied.capabilities ||
                    occupied.contexts.any { it is OperationalContext.Foster }
            "WALKER" -> hasProvider(occupied, "WALKING") || LeoverFunction.WALKER in occupied.extras
            "GROOMING" -> hasProvider(occupied, "GROOMING") || LeoverFunction.GROOMING in occupied.extras
            "TRAINER" -> hasProvider(occupied, "TRAINING") || LeoverFunction.TRAINER in occupied.extras
            "VETERINARY_PROFESSIONAL" ->
                LeoverFunction.VETERINARY_PROFESSIONAL in occupied.extras ||
                    occupied.contexts.any { it is OperationalContext.Veterinary }
            "SHELTER" -> occupied.contexts.any { ContextIdentityMapping.isRefugeNav(it) }
            "VETERINARY_CLINIC" -> occupied.contexts.any { ctx ->
                ctx is OperationalContext.Veterinary ||
                    (ctx is OperationalContext.Organization &&
                        ContextIdentityMapping.isVeterinary(ctx.organizationType.orEmpty()))
            }
            "SHOP" -> occupied.contexts.any { it is OperationalContext.Shop } ||
                occupied.contexts.any { ctx ->
                    ctx is OperationalContext.Organization &&
                        ContextIdentityMapping.isShop(ctx.organizationType.orEmpty())
                }
            "DAYCARE" -> hasProvider(occupied, "DAYCARE") ||
                occupied.contexts.any { ctx ->
                    ctx is OperationalContext.Organization &&
                        ContextIdentityMapping.isDaycare(ctx.organizationType.orEmpty())
                }
            "GROOMING_ORG" -> occupied.contexts.any { ctx ->
                ctx is OperationalContext.Organization &&
                    ContextIdentityMapping.isGrooming(ctx.organizationType.orEmpty())
            }
            "WALKING_CARE" -> occupied.contexts.any { ctx ->
                ctx is OperationalContext.Organization &&
                    ContextIdentityMapping.isWalkingCare(ctx.organizationType.orEmpty())
            }
            "TRAINING_ORG" -> occupied.contexts.any { ctx ->
                ctx is OperationalContext.Organization &&
                    ContextIdentityMapping.isTraining(ctx.organizationType.orEmpty())
            }
            "BRAND" -> occupied.contexts.any { ctx ->
                val token = when (ctx) {
                    is OperationalContext.Organization -> ctx.organizationType.orEmpty()
                    else -> ""
                }
                ContextIdentityMapping.isBrand(token)
            }
            "PET_FRIENDLY" -> occupied.contexts.any { ctx ->
                val token = when (ctx) {
                    is OperationalContext.Organization -> ctx.organizationType.orEmpty()
                    is OperationalContext.Provider -> ctx.category.orEmpty()
                    else -> ""
                }
                token.contains("PET_FRIENDLY", ignoreCase = true) ||
                    token.contains("PET FRIENDLY", ignoreCase = true)
            }
            else -> option.function in occupied.extras
        }
    }

    private fun hasProvider(occupied: OccupiedFunctions, category: String): Boolean =
        occupied.contexts.any { ctx ->
            ctx is OperationalContext.Provider &&
                ctx.category.equals(category, ignoreCase = true)
        } || occupied.contexts.any { ctx ->
            ctx is OperationalContext.Organization &&
                ctx.organizationType.equals(category, ignoreCase = true)
        }
}
