package com.comunidapp.app.domain.onboarding.onb02

/**
 * Product functions for ONB-02. Not AccountType, not AppMode, not security authority.
 */
enum class FunctionDomainStatus {
    IMPLEMENTED_CANONICAL_DOMAIN,
    PENDING_CANONICAL_DOMAIN
}

enum class LeoverFunction(
    val visibleLabel: String,
    val subtitle: String,
    val domainStatus: FunctionDomainStatus,
    val tutorialId: TutorialId
) {
    PROFILE_PERSONAL(
        visibleLabel = Onb02Copy.VISIBLE_BASE_PROFILE_NAME,
        subtitle = "Tus mascotas, VitaCora y la comunidad.",
        domainStatus = FunctionDomainStatus.IMPLEMENTED_CANONICAL_DOMAIN,
        tutorialId = TutorialId.T01_PROFILE_PERSONAL
    ),
    RESCUER(
        visibleLabel = "Rescatista",
        subtitle = "Casos de animales que necesitan ayuda.",
        domainStatus = FunctionDomainStatus.IMPLEMENTED_CANONICAL_DOMAIN,
        tutorialId = TutorialId.T02_RESCUER
    ),
    FOSTER(
        visibleLabel = "Hogar de tránsito",
        subtitle = "Cuidado temporal sin cambiar responsables.",
        domainStatus = FunctionDomainStatus.IMPLEMENTED_CANONICAL_DOMAIN,
        tutorialId = TutorialId.T03_FOSTER
    ),
    VETERINARY_PROFESSIONAL(
        visibleLabel = "Profesional veterinario",
        subtitle = "Perfil profesional y vínculo con una veterinaria.",
        domainStatus = FunctionDomainStatus.IMPLEMENTED_CANONICAL_DOMAIN,
        tutorialId = TutorialId.T04_VETERINARY_PROFESSIONAL
    ),
    WALKER(
        visibleLabel = "Paseador",
        subtitle = "Servicios de paseo y zona de trabajo.",
        domainStatus = FunctionDomainStatus.IMPLEMENTED_CANONICAL_DOMAIN,
        tutorialId = TutorialId.T05_WALKER
    ),
    CAREGIVER(
        visibleLabel = "Cuidador de mascotas",
        subtitle = "Cuidado responsable por mascota.",
        domainStatus = FunctionDomainStatus.IMPLEMENTED_CANONICAL_DOMAIN,
        tutorialId = TutorialId.T06_CAREGIVER
    ),
    TRAINER(
        visibleLabel = "Educador / Adiestrador",
        subtitle = "Educación y acompañamiento conductual.",
        domainStatus = FunctionDomainStatus.IMPLEMENTED_CANONICAL_DOMAIN,
        tutorialId = TutorialId.T07_TRAINER
    ),
    GROOMING(
        visibleLabel = "Peluquería",
        subtitle = "Cuidado estético y peluquería.",
        domainStatus = FunctionDomainStatus.IMPLEMENTED_CANONICAL_DOMAIN,
        tutorialId = TutorialId.T08_GROOMING
    ),
    DAYCARE(
        visibleLabel = "Guardería",
        subtitle = "Estadías temporales y cuidados durante la permanencia.",
        domainStatus = FunctionDomainStatus.IMPLEMENTED_CANONICAL_DOMAIN,
        tutorialId = TutorialId.T09_DAYCARE
    ),
    ORGANIZATION(
        visibleLabel = "Represento una organización o negocio",
        subtitle = "Veterinaria, tienda, guardería, peluquería, equipo o marca.",
        domainStatus = FunctionDomainStatus.IMPLEMENTED_CANONICAL_DOMAIN,
        tutorialId = TutorialId.T10_ORGANIZATION
    );

    val isBaseProfile: Boolean get() = this == PROFILE_PERSONAL
    val isSelectableExtra: Boolean get() = !isBaseProfile
    val activationIsPendingIntent: Boolean get() = domainStatus == FunctionDomainStatus.PENDING_CANONICAL_DOMAIN
}

enum class OrganizationSetupAction {
    CREATE,
    JOIN
}

enum class OrganizationKindOption(
    val visibleLabel: String,
    val microTutorialId: TutorialId
) {
    VETERINARY_CLINIC("Veterinaria", TutorialId.T10A_VETERINARY_CLINIC),
    SHOP("Tienda", TutorialId.T10C_SHOP),
    DAYCARE("Guardería", TutorialId.T10D_ORG_DAYCARE),
    GROOMING("Peluquería", TutorialId.T16_GROOMING_ORG),
    WALKING_CARE("Paseos y cuidado", TutorialId.T17_WALKING_CARE_ORG),
    TRAINING("Educación / Adiestramiento", TutorialId.T18_TRAINING_ORG),
    BRAND("Empresa o marca", TutorialId.T19_BRAND_ORG),
    PET_FRIENDLY_VENUE("Lugar pet friendly", TutorialId.T10E_OTHER_SERVICE),
    SHELTER("Refugio / ONG", TutorialId.T10B_SHELTER),
    OTHER_SERVICE("Otro servicio", TutorialId.T10E_OTHER_SERVICE);

    companion object {
        val commercial: List<OrganizationKindOption> = listOf(
            VETERINARY_CLINIC, SHOP, DAYCARE, GROOMING, WALKING_CARE, TRAINING, BRAND, PET_FRIENDLY_VENUE
        )
        val welfare: List<OrganizationKindOption> = listOf(SHELTER)
    }
}

object Onb02Copy {
    const val VISIBLE_BASE_PROFILE_NAME = "Perfil personal"
    const val PERSON_INTERNAL_IDENTITY = true
    const val PROFILE_PERSONAL_ALWAYS_ACTIVE = true
    const val PROFILE_PERSONAL_REMOVABLE = false
    const val PROFILE_PERSONAL_EDITABLE_SELECTION = false
    const val SELECTOR_TITLE = "¿Cómo querés usar LeoVer?"
    const val SELECTOR_SUBTITLE =
        "Tu perfil Personal es la base de tu cuenta. Aunque uses LeoVer como profesional, refugio u organización, siempre vas a tener tu perfil personal."
    const val PERSON_BASE_EXPLANATION = SELECTOR_SUBTITLE
    const val PROFESSIONAL_SERVICE_TITLE = "¿Qué servicio ofrecés?"
    const val BUSINESS_KIND_TITLE = "¿Qué tipo de organización o negocio es?"
    const val PROFILE_EXPLANATION_SLIDE_TITLE = "Elegí cómo querés usar LeoVer"
    const val SELECTOR_ALWAYS_ACTIVE = "Siempre activo"
    const val ADD_FUNCTION_LABEL = "Agregar función o perfil"
    const val USE_LEOVER_AS = "Usar LeoVer como"
    const val TUTORIAL_SWITCH_PATH = "Perfil → Usar LeoVer como"
    const val TUTORIAL_ADD_FUNCTION_PATH = "Perfil → Configuración → Agregar función o perfil"
    const val HELP_TUTORIALS = "Tutoriales"
    const val PENDING_SETUP = "Configuración pendiente"
    const val ORG_SETUP_TITLE = "¿Qué querés hacer?"
    const val ORG_CREATE = "Crear una organización"
    const val ORG_JOIN = "Unirme a una existente"
}

data class FunctionSelection(
    val extras: Set<LeoverFunction> = emptySet(),
    val organizationAction: OrganizationSetupAction? = null,
    val organizationKind: OrganizationKindOption? = null,
    val actorKind: ProfileActorKind? = null,
    val professionalSpecialty: IndependentProfessionalSpecialty? = null,
    val petFriendlySubtype: String? = null
) {
    val personalAlwaysIncluded: Boolean = true
    val personalEditable: Boolean = false

    init {
        require(LeoverFunction.PROFILE_PERSONAL !in extras) {
            "PROFILE_PERSONAL is the base identity, not an extra selection"
        }
        extras.forEach { require(it.isSelectableExtra) }
    }

    fun withToggled(function: LeoverFunction): FunctionSelection {
        if (function.isBaseProfile) return this
        val next = extras.toMutableSet()
        if (!next.add(function)) next.remove(function)
        val clearedOrg = if (LeoverFunction.ORGANIZATION !in next) {
            copy(extras = next, organizationAction = null, organizationKind = null)
        } else {
            copy(extras = next)
        }
        return clearedOrg
    }

    fun allProductFunctions(): Set<LeoverFunction> =
        setOf(LeoverFunction.PROFILE_PERSONAL) + extras
}
