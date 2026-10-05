package com.comunidapp.app.domain.adoption

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSpecies

/**
 * Shared language between an adopter profile and a publication's requirements.
 *
 * Existing columns already spoke part of this language:
 * housing_type, housing_tenure, animals_allowed, adults_count, children_count,
 * experience, hours_alone, motivation, notes.
 * Null is unknown. It is never false, and a missing count is never zero.
 *
 * Historical free text stays readable. It is not parsed into an explicit no.
 * Sex is stored on the pet. The adopter profile does not collect a sex preference.
 *
 * Allergies already exist as optional free text on the profile row. The form
 * does not ask for them, matching does not read them, and public listings do
 * not show them. The profile does not collect DNI, salary, pay stubs,
 * profession, marital status, medical data, a street address, or social networks.
 *
 * Matching is advisory. LeoVer does not assign an animal.
 *
 * A found animal (FOUND_CASE) is not eligible to publish. No legal waiting
 * period or found-to-adoption process is defined, so temporary custody is
 * not a publish right.
 */
enum class HousingKind { HOUSE, APARTMENT, OTHER }

enum class HousingTenure { OWN, RENT }

enum class ExperienceBand { NONE, SOME, SPECIAL_CARE }

/**
 * Approximate hours alone, stored as an inclusive range.
 * [toHours] null means "more than [fromHours]".
 * The screen offers three shortcuts. Another range, such as 6 to 6, is valid
 * without adding an enum value.
 */
data class HoursAloneEstimate(
    val fromHours: Int,
    val toHours: Int? = null
) {
    init {
        require(fromHours >= 0)
        require(toHours == null || toHours >= fromHours)
    }

    fun legacyBandToken(): String? = when (this) {
        UNDER_4 -> "UNDER_4"
        FROM_4_TO_8 -> "H4_TO_8"
        OVER_8 -> "OVER_8"
        else -> null
    }

    companion object {
        val UNDER_4 = HoursAloneEstimate(0, 3)
        val FROM_4_TO_8 = HoursAloneEstimate(4, 8)
        val OVER_8 = HoursAloneEstimate(9, null)
    }
}

enum class AdopterSpeciesPref { DOG, CAT, ANY }

enum class AdopterSizePref { SMALL, MEDIUM, LARGE }

enum class AdopterLifeStagePref { YOUNG, ADULT, SENIOR }

data class AdopterProfile(
    val housingKind: HousingKind? = null,
    val housingTenure: HousingTenure? = null,
    /** Permission when the home is rented. Column animals_allowed. Null is unknown. */
    val landlordAllowsPets: Boolean? = null,
    val hasOutdoorSpace: Boolean? = null,
    val hasSecureEnclosure: Boolean? = null,
    /**
     * Openings and outdoor access are protected against escape.
     * Cats: windows, balconies, terraces. Dogs: yard, gates, enclosure.
     * One answer. Species-specific wording is presentation.
     */
    val escapeProtection: Boolean? = null,
    val adultsCount: Int? = null,
    val childrenCount: Int? = null,
    val householdAgrees: Boolean? = null,
    val hasDogs: Boolean? = null,
    val hasCats: Boolean? = null,
    val hasOtherAnimals: Boolean? = null,
    val experienceBand: ExperienceBand? = null,
    val hoursAlone: HoursAloneEstimate? = null,
    val canVetFollowup: Boolean? = null,
    val canMedicate: Boolean? = null,
    val acceptsSpecialNeeds: Boolean? = null,
    val speciesPref: AdopterSpeciesPref? = null,
    val sizePref: AdopterSizePref? = null,
    val lifeStagePref: AdopterLifeStagePref? = null,
    val motivation: String? = null,
    val notes: String? = null,
    /** Historical housing prose that is not a structured token. */
    val housingNotes: String? = null,
    val legacyExperience: String? = null,
    val legacyHoursAlone: String? = null,
    val legacyOtherPets: String? = null
) {
    fun trackedAnswers(): List<Boolean> {
        val answers = mutableListOf(
            housingKind != null,
            housingTenure != null,
            hasOutdoorSpace != null,
            hasSecureEnclosure != null,
            escapeProtection != null,
            adultsCount != null,
            childrenCount != null,
            householdAgrees != null,
            hasDogs != null,
            hasCats != null,
            hasOtherAnimals != null,
            experienceBand != null,
            hoursAlone != null,
            canVetFollowup != null,
            canMedicate != null,
            acceptsSpecialNeeds != null,
            speciesPref != null,
            sizePref != null,
            lifeStagePref != null
        )
        if (housingTenure == HousingTenure.RENT) {
            answers += landlordAllowsPets != null
        }
        return answers
    }

    val answeredCount: Int get() = trackedAnswers().count { it }
    val trackedCount: Int get() = trackedAnswers().size
    val isComplete: Boolean get() = trackedAnswers().isNotEmpty() && trackedAnswers().all { it }

    /** Structured answers absent. Notes alone do not complete the profile. */
    val isStructurallyEmpty: Boolean get() = answeredCount == 0
}

object AdopterProfileCompleteness {
    /**
     * Saving a partial profile is always allowed.
     * Applying needs household agreement, housing type, current animals,
     * and approximate hours alone. A rented home also needs a true pet permission.
     * Experience, vet care, preferences and notes stay optional.
     */
    val APPLY_MINIMUMS: List<String> = listOf(
        "household_agrees",
        "housing_kind",
        "landlord_allows_pets_when_rent",
        "current_animals",
        "hours_alone"
    )

    fun saveFeedback(profile: AdopterProfile): String {
        val ignored = profile
        return "Perfil de adopción guardado."
    }
}

data class AdoptionRequirements(
    val acceptsChildren: Boolean? = null,
    val acceptsOtherDogs: Boolean? = null,
    val acceptsCats: Boolean? = null,
    val needsOutdoorSpace: Boolean? = null,
    val needsSecureEnclosure: Boolean? = null,
    /** Requires openings and outdoor access protected against escape. */
    val requiresEscapeProtection: Boolean? = null,
    /** When true, a rented home must have landlord pet permission. */
    val requiresLandlordPetPermission: Boolean? = null,
    val acceptsOtherAnimals: Boolean? = null,
    val maxHoursAlone: HoursAloneEstimate? = null,
    val experienceRequired: ExperienceBand? = null,
    val acceptsNoExperience: Boolean? = null,
    val requiresSpecialCareExperience: Boolean? = null,
    val additionalNotes: String? = null
) {
    val hasStructuredRequirement: Boolean
        get() = acceptsChildren != null ||
            acceptsOtherDogs != null ||
            acceptsCats != null ||
            needsOutdoorSpace != null ||
            needsSecureEnclosure != null ||
            requiresEscapeProtection != null ||
            requiresLandlordPetPermission != null ||
            acceptsOtherAnimals != null ||
            maxHoursAlone != null ||
            experienceRequired != null ||
            acceptsNoExperience != null ||
            requiresSpecialCareExperience != null
}

enum class AdoptionMatchAspect {
    OUTDOOR_SPACE,
    SECURE_ENCLOSURE,
    ESCAPE_PROTECTION,
    LANDLORD_PERMISSION,
    CHILDREN,
    OTHER_DOGS,
    CATS,
    OTHER_ANIMALS,
    HOURS_ALONE,
    EXPERIENCE,
    NO_EXPERIENCE,
    SPECIAL_CARE
}

enum class AdoptionMatchFact { MATCH, INCOMPATIBLE, UNKNOWN }

data class AdoptionMatchFinding(
    val aspect: AdoptionMatchAspect,
    val fact: AdoptionMatchFact
)

enum class AdoptionMatchOutcome { COMPATIBLE, INCOMPATIBLE }

/**
 * A known conflict with a listing requirement is not the same as a blank answer.
 * Missing data stays compatible: LeoVer does not treat unknown as a no.
 */
enum class AdoptionMatchClassification {
    COMPATIBLE,
    MISSING_REQUIRED_PROFILE_DATA,
    INCOMPATIBLE_WITH_LISTING_REQUIREMENT
}

data class AdoptionMatchResult(
    val outcome: AdoptionMatchOutcome,
    val classification: AdoptionMatchClassification,
    val findings: List<AdoptionMatchFinding>,
    val rank: Int,
    val autoAssigned: Boolean = false
) {
    val matches: List<AdoptionMatchFinding> get() = findings.filter { it.fact == AdoptionMatchFact.MATCH }
    val incompatibilities: List<AdoptionMatchFinding>
        get() = findings.filter { it.fact == AdoptionMatchFact.INCOMPATIBLE }
    val unknowns: List<AdoptionMatchFinding> get() = findings.filter { it.fact == AdoptionMatchFact.UNKNOWN }
}

object AdoptionMatchingPolicy {
    fun evaluate(profile: AdopterProfile, requirements: AdoptionRequirements): AdoptionMatchResult {
        val findings = mutableListOf<AdoptionMatchFinding>()
        flag(
            findings,
            AdoptionMatchAspect.OUTDOOR_SPACE,
            required = requirements.needsOutdoorSpace,
            actual = profile.hasOutdoorSpace
        )
        flag(
            findings,
            AdoptionMatchAspect.SECURE_ENCLOSURE,
            required = requirements.needsSecureEnclosure,
            actual = profile.hasSecureEnclosure
        )
        flag(
            findings,
            AdoptionMatchAspect.ESCAPE_PROTECTION,
            required = requirements.requiresEscapeProtection,
            actual = profile.escapeProtection
        )
        landlord(findings, requirements.requiresLandlordPetPermission, profile)
        company(
            findings,
            AdoptionMatchAspect.CHILDREN,
            accepts = requirements.acceptsChildren,
            present = when (profile.childrenCount) {
                null -> null
                0 -> false
                else -> true
            }
        )
        company(findings, AdoptionMatchAspect.OTHER_DOGS, requirements.acceptsOtherDogs, profile.hasDogs)
        company(findings, AdoptionMatchAspect.CATS, requirements.acceptsCats, profile.hasCats)
        company(findings, AdoptionMatchAspect.OTHER_ANIMALS, requirements.acceptsOtherAnimals, profile.hasOtherAnimals)
        hours(findings, requirements.maxHoursAlone, profile.hoursAlone)
        experience(findings, requirements, profile.experienceBand)
        val incompatible = findings.any { it.fact == AdoptionMatchFact.INCOMPATIBLE }
        val unknown = findings.any { it.fact == AdoptionMatchFact.UNKNOWN }
        val classification = when {
            incompatible -> AdoptionMatchClassification.INCOMPATIBLE_WITH_LISTING_REQUIREMENT
            unknown -> AdoptionMatchClassification.MISSING_REQUIRED_PROFILE_DATA
            else -> AdoptionMatchClassification.COMPATIBLE
        }
        return AdoptionMatchResult(
            outcome = if (incompatible) AdoptionMatchOutcome.INCOMPATIBLE else AdoptionMatchOutcome.COMPATIBLE,
            classification = classification,
            findings = findings,
            rank = findings.count { it.fact == AdoptionMatchFact.MATCH },
            autoAssigned = false
        )
    }

    fun <T> orderForReview(
        items: List<T>,
        profileOf: (T) -> AdopterProfile,
        requirements: AdoptionRequirements
    ): List<T> {
        return items.sortedWith(
            compareBy<T> {
                evaluate(profileOf(it), requirements).outcome == AdoptionMatchOutcome.INCOMPATIBLE
            }.thenByDescending { evaluate(profileOf(it), requirements).rank }
        )
    }

    private fun flag(
        findings: MutableList<AdoptionMatchFinding>,
        aspect: AdoptionMatchAspect,
        required: Boolean?,
        actual: Boolean?
    ) {
        if (required != true) return
        val fact = when (actual) {
            true -> AdoptionMatchFact.MATCH
            false -> AdoptionMatchFact.INCOMPATIBLE
            null -> AdoptionMatchFact.UNKNOWN
        }
        findings += AdoptionMatchFinding(aspect, fact)
    }

    /**
     * accepts == false means that company is not accepted.
     * A known presence is then incompatible. Unknown presence is not.
     * accepts == true does not require the company; a known presence is a match.
     */
    private fun company(
        findings: MutableList<AdoptionMatchFinding>,
        aspect: AdoptionMatchAspect,
        accepts: Boolean?,
        present: Boolean?
    ) {
        when (accepts) {
            null -> Unit
            false -> findings += AdoptionMatchFinding(
                aspect,
                when (present) {
                    true -> AdoptionMatchFact.INCOMPATIBLE
                    false -> AdoptionMatchFact.MATCH
                    null -> AdoptionMatchFact.UNKNOWN
                }
            )
            true -> if (present == true) {
                findings += AdoptionMatchFinding(aspect, AdoptionMatchFact.MATCH)
            }
        }
    }

    /**
     * A rented home must allow pets when the listing asks for that permission.
     * Owning the home makes the requirement inapplicable. Unknown tenure or
     * unknown permission is missing, not a known conflict.
     */
    private fun landlord(
        findings: MutableList<AdoptionMatchFinding>,
        required: Boolean?,
        profile: AdopterProfile
    ) {
        if (required != true) return
        val fact = when (profile.housingTenure) {
            null -> AdoptionMatchFact.UNKNOWN
            HousingTenure.OWN -> AdoptionMatchFact.MATCH
            HousingTenure.RENT -> when (profile.landlordAllowsPets) {
                true -> AdoptionMatchFact.MATCH
                false -> AdoptionMatchFact.INCOMPATIBLE
                null -> AdoptionMatchFact.UNKNOWN
            }
        }
        findings += AdoptionMatchFinding(AdoptionMatchAspect.LANDLORD_PERMISSION, fact)
    }

    /**
     * The whole range inside the maximum is a match.
     * The whole range above the maximum is incompatible.
     * A range that crosses the maximum, or a blank answer, is unknown.
     * An open-ended maximum accepts every known answer.
     */
    private fun hours(
        findings: MutableList<AdoptionMatchFinding>,
        max: HoursAloneEstimate?,
        actual: HoursAloneEstimate?
    ) {
        if (max == null) return
        val ceiling = max.toHours
        val fact = when {
            actual == null -> AdoptionMatchFact.UNKNOWN
            ceiling == null -> AdoptionMatchFact.MATCH
            actual.fromHours > ceiling -> AdoptionMatchFact.INCOMPATIBLE
            actual.toHours != null && actual.toHours <= ceiling -> AdoptionMatchFact.MATCH
            else -> AdoptionMatchFact.UNKNOWN
        }
        findings += AdoptionMatchFinding(AdoptionMatchAspect.HOURS_ALONE, fact)
    }

    private fun experience(
        findings: MutableList<AdoptionMatchFinding>,
        requirements: AdoptionRequirements,
        actual: ExperienceBand?
    ) {
        val minimum = requirements.experienceRequired
        if (minimum != null) {
            val fact = when {
                actual == null -> AdoptionMatchFact.UNKNOWN
                actual.rank >= minimum.rank -> AdoptionMatchFact.MATCH
                else -> AdoptionMatchFact.INCOMPATIBLE
            }
            findings += AdoptionMatchFinding(AdoptionMatchAspect.EXPERIENCE, fact)
        }
        if (requirements.acceptsNoExperience == false) {
            val fact = when (actual) {
                null -> AdoptionMatchFact.UNKNOWN
                ExperienceBand.NONE -> AdoptionMatchFact.INCOMPATIBLE
                else -> AdoptionMatchFact.MATCH
            }
            findings += AdoptionMatchFinding(AdoptionMatchAspect.NO_EXPERIENCE, fact)
        }
        if (requirements.requiresSpecialCareExperience == true) {
            val fact = when (actual) {
                null -> AdoptionMatchFact.UNKNOWN
                ExperienceBand.SPECIAL_CARE -> AdoptionMatchFact.MATCH
                else -> AdoptionMatchFact.INCOMPATIBLE
            }
            findings += AdoptionMatchFinding(AdoptionMatchAspect.SPECIAL_CARE, fact)
        }
    }
}

private val ExperienceBand.rank: Int
    get() = when (this) {
        ExperienceBand.NONE -> 0
        ExperienceBand.SOME -> 1
        ExperienceBand.SPECIAL_CARE -> 2
    }

object AdoptionMatchPresentation {
    fun summary(result: AdoptionMatchResult): String = when {
        result.incompatibilities.isNotEmpty() ->
            "Hay un requisito que no coincide. La decisión sigue siendo de quien publica."
        result.unknowns.isNotEmpty() ->
            "Falta información para comparar algunos requisitos. Eso no es un incumplimiento."
        result.matches.isNotEmpty() ->
            "El perfil coincide con los requisitos informados."
        else ->
            "No hay requisitos estructurados para comparar."
    }
}

enum class AdoptionApplyBlock {
    NOT_AUTHENTICATED,
    OWN_PUBLICATION,
    NOT_ACCEPTING,
    DUPLICATE_ACTIVE,
    NEEDS_PROFILE,
    HOUSEHOLD_DOES_NOT_AGREE,
    LANDLORD_DOES_NOT_ALLOW
}

object AdoptionApplyMinimum {
    fun missingLabels(profile: AdopterProfile): List<String> {
        val missing = mutableListOf<String>()
        if (profile.householdAgrees == null) missing += "el acuerdo del hogar"
        if (profile.housingKind == null) missing += "el tipo de vivienda"
        if (profile.housingTenure == HousingTenure.RENT && profile.landlordAllowsPets == null) {
            missing += "si permiten mascotas en el alquiler"
        }
        if (profile.hasDogs == null || profile.hasCats == null || profile.hasOtherAnimals == null) {
            missing += "si convivís con perros, gatos u otros animales"
        }
        if (profile.hoursAlone == null) missing += "el tiempo aproximado que el animal quedaría solo"
        return missing
    }

    fun isReady(profile: AdopterProfile): Boolean =
        profile.householdAgrees == true &&
            profile.housingKind != null &&
            (profile.housingTenure != HousingTenure.RENT || profile.landlordAllowsPets == true) &&
            profile.hasDogs != null &&
            profile.hasCats != null &&
            profile.hasOtherAnimals != null &&
            profile.hoursAlone != null
}

data class AdoptionApplyDecision(
    val allowed: Boolean,
    val block: AdoptionApplyBlock? = null,
    val message: String? = null,
    val openProfile: Boolean = false
)

object AdoptionApplyPolicy {
    fun evaluate(
        authenticated: Boolean,
        isOwnPublication: Boolean,
        publicationAccepting: Boolean,
        hasActiveApplication: Boolean,
        profile: AdopterProfile
    ): AdoptionApplyDecision {
        if (!authenticated) {
            return block(AdoptionApplyBlock.NOT_AUTHENTICATED, "Tenés que iniciar sesión.")
        }
        if (isOwnPublication) {
            return block(
                AdoptionApplyBlock.OWN_PUBLICATION,
                "No podés postularte a tu propia publicación."
            )
        }
        if (!publicationAccepting) {
            return block(
                AdoptionApplyBlock.NOT_ACCEPTING,
                "Esta publicación no está recibiendo postulaciones."
            )
        }
        if (hasActiveApplication) {
            return block(
                AdoptionApplyBlock.DUPLICATE_ACTIVE,
                "Ya tenés una postulación activa para esta publicación."
            )
        }
        if (profile.householdAgrees == false) {
            return block(
                AdoptionApplyBlock.HOUSEHOLD_DOES_NOT_AGREE,
                "Las personas del hogar no están de acuerdo con la adopción."
            )
        }
        if (profile.housingTenure == HousingTenure.RENT && profile.landlordAllowsPets == false) {
            return block(
                AdoptionApplyBlock.LANDLORD_DOES_NOT_ALLOW,
                "En el alquiler no permiten mascotas."
            )
        }
        val missing = AdoptionApplyMinimum.missingLabels(profile)
        if (missing.isNotEmpty()) {
            return AdoptionApplyDecision(
                allowed = false,
                block = AdoptionApplyBlock.NEEDS_PROFILE,
                message = "Para postularte falta: ${missing.joinToString(", ")}.",
                openProfile = true
            )
        }
        return AdoptionApplyDecision(allowed = true)
    }

    private fun block(kind: AdoptionApplyBlock, message: String) =
        AdoptionApplyDecision(allowed = false, block = kind, message = message)
}

enum class AdoptionPublishBlock {
    CAPABILITY,
    NOT_ACTIVE,
    NOT_RESPONSIBLE,
    FOUND_CASE
}

data class AdoptionPublishDecision(
    val eligible: Boolean,
    val block: AdoptionPublishBlock? = null
)

object AdoptionPublishEligibility {
    const val FOUND_ORIGIN = "FOUND_CASE"

    /**
     * No legal found-to-adoption process is defined. Temporary custody of a
     * found animal does not make it publishable.
     */
    const val FOUND_NOT_ELIGIBLE =
        "Una mascota encontrada no se publica en adopción desde la custodia temporal."

    fun evaluate(
        pet: Pet,
        capabilityAllowsPublish: Boolean,
        viewerUserId: String,
        organizationContextId: String? = null
    ): AdoptionPublishDecision {
        if (!capabilityAllowsPublish) {
            return AdoptionPublishDecision(false, AdoptionPublishBlock.CAPABILITY)
        }
        if (pet.originKind.equals(FOUND_ORIGIN, ignoreCase = true)) {
            return AdoptionPublishDecision(false, AdoptionPublishBlock.FOUND_CASE)
        }
        if (!pet.status.equals("ACTIVE", ignoreCase = true)) {
            return AdoptionPublishDecision(false, AdoptionPublishBlock.NOT_ACTIVE)
        }
        if (!isResponsible(pet, viewerUserId, organizationContextId)) {
            return AdoptionPublishDecision(false, AdoptionPublishBlock.NOT_RESPONSIBLE)
        }
        return AdoptionPublishDecision(true)
    }

    fun eligible(
        pets: List<Pet>,
        capabilityAllowsPublish: Boolean,
        viewerUserId: String,
        organizationContextId: String? = null
    ): List<Pet> = pets.filter {
        evaluate(it, capabilityAllowsPublish, viewerUserId, organizationContextId).eligible
    }

    /**
     * Responsibility is ownership, explicit co-ownership, the person management
     * context, or the organization the viewer is operating as.
     * [Pet.accessSubjectUserId] only means the pet was listed for that viewer.
     */
    fun isResponsible(pet: Pet, viewerUserId: String, organizationContextId: String?): Boolean {
        val org = organizationContextId?.trim()?.takeIf { it.isNotEmpty() }
        if (org != null) {
            if (pet.organizationResponsibleId?.trim() == org) return true
            val kind = pet.managementContextKind?.trim()?.uppercase().orEmpty()
            return kind == "ORGANIZATION" && pet.managementContextId?.trim() == org
        }
        if (viewerUserId.isBlank()) return false
        if (pet.ownerId?.trim() == viewerUserId) return true
        if (pet.ownerIds.any { it.trim() == viewerUserId }) return true
        val kind = pet.managementContextKind?.trim()?.uppercase().orEmpty().ifBlank { "PERSON" }
        return kind == "PERSON" && pet.managementContextId?.trim() == viewerUserId
    }
}

object AdoptionPrivacy {
    val notCollectedByDefault: List<String> = listOf(
        "DNI",
        "salario",
        "recibo de sueldo",
        "profesión",
        "estado civil",
        "información médica",
        "alergias",
        "domicilio particular",
        "redes sociales"
    )

    /** Column kept for historical rows. The form does not ask it. */
    const val ALLERGIES_COLUMN = "allergies"
    const val ALLERGIES_ASKED = false
    const val SHOWN_ON_PUBLIC_LISTING = false
}

/**
 * Questions worth checking later against real shelter forms.
 * This block did not research external forms.
 */
object EscapeProtectionCopy {
    const val PROFILE_QUESTION = "¿Los accesos y exteriores están protegidos contra escapes?"
    const val PROFILE_HINT =
        "En gatos: ventanas, balcones y terrazas. En perros: patio, cerramiento y portones. No se pide la dirección."
    const val REQUIREMENT_TITLE = "Requiere protección de accesos y exteriores"

    fun requirementHint(species: PetSpecies?): String = when (species) {
        PetSpecies.CAT -> "Para un gato: ventanas, balcones, terrazas y accesos exteriores protegidos."
        PetSpecies.DOG -> "Para un perro: patio, cerramiento y portones seguros."
        else -> "Para un gato: ventanas, balcones y terrazas. Para un perro: patio, cerramiento y portones."
    }
}

object AdoptionExternalValidation {
    val confirmedAgainstRealForms: List<String> = listOf(
        "Perfil reutilizable, requisitos de cada publicación y matching se mantienen.",
        "El acuerdo del hogar no alcanza solo para postularse.",
        "Horas solo se guardan como rango. Las tres opciones de pantalla son atajos.",
        "La protección contra escapes es una sola respuesta. La explicación cambia según la especie.",
        "Las alergias no entran al formulario, al matching ni al listado público."
    )
}

fun housingKindOf(raw: String?): HousingKind? = when (raw?.trim()?.uppercase()) {
    "HOUSE", "CASA" -> HousingKind.HOUSE
    "APARTMENT", "DEPARTAMENTO", "DEPTO" -> HousingKind.APARTMENT
    "OTHER", "OTRO" -> HousingKind.OTHER
    else -> null
}

fun housingTenureOf(raw: String?): HousingTenure? = when (raw?.trim()?.uppercase()) {
    "OWN", "PROPIA", "PROPIO" -> HousingTenure.OWN
    "RENT", "ALQUILADA", "ALQUILER" -> HousingTenure.RENT
    else -> null
}

fun experienceBandOf(raw: String?): ExperienceBand? = when (raw?.trim()?.uppercase()) {
    "NONE" -> ExperienceBand.NONE
    "SOME" -> ExperienceBand.SOME
    "SPECIAL_CARE" -> ExperienceBand.SPECIAL_CARE
    else -> null
}

fun hoursAloneEstimateOf(from: Int?, to: Int?, legacyBand: String?): HoursAloneEstimate? {
    if (from != null && from >= 0 && (to == null || to >= from)) {
        return HoursAloneEstimate(from, to)
    }
    return when (legacyBand?.trim()?.uppercase()) {
        "UNDER_4" -> HoursAloneEstimate.UNDER_4
        "H4_TO_8", "FROM_4_TO_8" -> HoursAloneEstimate.FROM_4_TO_8
        "OVER_8" -> HoursAloneEstimate.OVER_8
        else -> null
    }
}
