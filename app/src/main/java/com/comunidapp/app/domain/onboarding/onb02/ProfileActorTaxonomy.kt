package com.comunidapp.app.domain.onboarding.onb02

/**
 * First-level onboarding is actor type, never activity/service category.
 * Activities live only on the second level of the matching actor.
 */
enum class ProfileActorKind(
    val visibleLabel: String,
    val subtitle: String
) {
    PERSON(
        visibleLabel = "Personal",
        subtitle = "Perfil personal y tus mascotas."
    ),
    INDEPENDENT_RESCUER(
        visibleLabel = "Rescatista independiente",
        subtitle = "Rescatás animales por tu cuenta."
    ),
    REFUGE(
        visibleLabel = "Refugio / Organización de rescate",
        subtitle = "Entidad dedicada al rescate y adopción."
    ),
    INDEPENDENT_PROFESSIONAL(
        visibleLabel = "Profesional independiente",
        subtitle = "Ofrecés servicios por tu cuenta."
    ),
    BUSINESS(
        visibleLabel = "Organización / Negocio",
        subtitle = "Local, clínica, tienda, equipo o marca."
    ),
    FOSTER(
        visibleLabel = "Hogar de tránsito",
        subtitle = "Cuidado temporal sin cambiar responsables."
    );

    val isRefuge: Boolean get() = this == REFUGE
    val isCommercialOrganization: Boolean get() = this == BUSINESS
}

enum class IndependentProfessionalSpecialty(
    val visibleLabel: String
) {
    WALKER_CAREGIVER("Paseador / Cuidador"),
    GROOMER("Peluquero/a de mascotas"),
    TRAINER("Adiestrador/a"),
    VETERINARIAN("Veterinario/a profesional");

    fun extras(): Set<LeoverFunction> = when (this) {
        WALKER_CAREGIVER -> setOf(LeoverFunction.WALKER)
        GROOMER -> setOf(LeoverFunction.GROOMING)
        TRAINER -> setOf(LeoverFunction.TRAINER)
        VETERINARIAN -> setOf(LeoverFunction.VETERINARY_PROFESSIONAL)
    }
}

object ProfileActorTaxonomy {
    const val FIRST_LEVEL_ACTOR_COUNT = 6
    const val SERVICE_CATEGORIES_IN_FIRST_LEVEL = false
    const val REFUGE_GENERAL_SELECTOR = true
    const val REFUGE_COMMERCIAL_SELECTOR = false
    const val INDEPENDENT_BOARDING_DUPLICATE = false

    /** Visual order only. Enum ids, applyActor, and storage are unchanged. */
    val firstLevel: List<ProfileActorKind> = listOf(
        ProfileActorKind.PERSON,
        ProfileActorKind.INDEPENDENT_RESCUER,
        ProfileActorKind.FOSTER,
        ProfileActorKind.REFUGE,
        ProfileActorKind.INDEPENDENT_PROFESSIONAL,
        ProfileActorKind.BUSINESS
    )

    val professionalSpecialties: List<IndependentProfessionalSpecialty> =
        IndependentProfessionalSpecialty.entries

    val businessKinds: List<OrganizationKindOption> = OrganizationKindOption.commercial

    fun businessVisibleLabel(kind: OrganizationKindOption): String = when (kind) {
        OrganizationKindOption.VETERINARY_CLINIC -> "Veterinaria / Clínica veterinaria"
        OrganizationKindOption.SHOP -> "Tienda de mascotas"
        OrganizationKindOption.DAYCARE -> "Guardería / Hospedaje"
        OrganizationKindOption.GROOMING -> "Peluquería"
        OrganizationKindOption.WALKING_CARE -> "Paseos / Cuidado"
        OrganizationKindOption.TRAINING -> "Adiestramiento"
        OrganizationKindOption.BRAND -> "Marca"
        OrganizationKindOption.PET_FRIENDLY_VENUE -> "Lugar pet friendly"
        OrganizationKindOption.SHELTER -> "Refugio / Organización de rescate"
        OrganizationKindOption.OTHER_SERVICE -> kind.visibleLabel
    }

    fun firstLevelLabels(): List<String> = firstLevel.map { it.visibleLabel }

    fun forbiddenFirstLevelLabels(): List<String> = listOf(
        "Veterinaria",
        "Peluquería",
        "Guardería",
        "Paseador",
        "Cuidador",
        "Adiestramiento",
        "Tienda",
        "Marca"
    )

    fun applyActor(kind: ProfileActorKind, current: FunctionSelection = FunctionSelection()): FunctionSelection =
        when (kind) {
            ProfileActorKind.PERSON -> current.copy(
                actorKind = kind,
                extras = emptySet(),
                organizationAction = null,
                organizationKind = null,
                professionalSpecialty = null
            )
            ProfileActorKind.INDEPENDENT_RESCUER -> current.copy(
                actorKind = kind,
                extras = setOf(LeoverFunction.RESCUER),
                organizationAction = null,
                organizationKind = null,
                professionalSpecialty = null
            )
            ProfileActorKind.REFUGE -> current.copy(
                actorKind = kind,
                extras = setOf(LeoverFunction.ORGANIZATION),
                organizationKind = OrganizationKindOption.SHELTER,
                professionalSpecialty = null
            )
            ProfileActorKind.INDEPENDENT_PROFESSIONAL -> current.copy(
                actorKind = kind,
                extras = emptySet(),
                organizationAction = null,
                organizationKind = null,
                professionalSpecialty = null
            )
            ProfileActorKind.BUSINESS -> current.copy(
                actorKind = kind,
                extras = setOf(LeoverFunction.ORGANIZATION),
                organizationKind = null,
                professionalSpecialty = null
            )
            ProfileActorKind.FOSTER -> current.copy(
                actorKind = kind,
                extras = setOf(LeoverFunction.FOSTER),
                organizationAction = null,
                organizationKind = null,
                professionalSpecialty = null
            )
        }

    fun applyProfessional(
        specialty: IndependentProfessionalSpecialty,
        current: FunctionSelection
    ): FunctionSelection = current.copy(
        actorKind = ProfileActorKind.INDEPENDENT_PROFESSIONAL,
        professionalSpecialty = specialty,
        extras = specialty.extras(),
        organizationAction = null,
        organizationKind = null
    )

    fun applyBusiness(
        kind: OrganizationKindOption,
        current: FunctionSelection
    ): FunctionSelection {
        require(kind in businessKinds) { "Refuge/other is not a commercial business category" }
        return current.copy(
            actorKind = ProfileActorKind.BUSINESS,
            extras = setOf(LeoverFunction.ORGANIZATION),
            organizationKind = kind,
            professionalSpecialty = null
        )
    }

    fun needsProfessionalLevel(kind: ProfileActorKind): Boolean =
        kind == ProfileActorKind.INDEPENDENT_PROFESSIONAL

    fun needsBusinessLevel(kind: ProfileActorKind): Boolean =
        kind == ProfileActorKind.BUSINESS

    fun needsOrgCreateJoin(selection: FunctionSelection): Boolean =
        selection.actorKind == ProfileActorKind.REFUGE ||
            selection.actorKind == ProfileActorKind.BUSINESS

    fun showCommercialKindPicker(selection: FunctionSelection): Boolean =
        when (selection.actorKind) {
            ProfileActorKind.REFUGE -> false
            ProfileActorKind.BUSINESS -> selection.organizationKind == null
            null -> LeoverFunction.ORGANIZATION in selection.extras &&
                selection.organizationKind == null
            else -> false
        }
}
