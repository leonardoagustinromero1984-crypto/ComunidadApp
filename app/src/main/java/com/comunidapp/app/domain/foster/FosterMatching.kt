package com.comunidapp.app.domain.foster

import com.comunidapp.app.domain.ux.HumanLocationLabel

/**
 * Shared language between a foster home and a found animal.
 *
 * Existing canonical columns already spoke this language, as nullable facts:
 * species_pref, age_pref, accepts_treatment, other_animals_ok, capacity,
 * locality, active, notes. Structured columns added in migration 1107 use the
 * same tri-state: null is unknown or no preference, never an implicit no.
 *
 * Sex is already stored on the pet and no home declares a sex preference, so
 * it is not a matching dimension.
 *
 * Notification today is the eligible open-request list
 * (`canon_list_open_foster_requests`): a verified, active foster home with a
 * base location. There is no foster push fan-out. This policy narrows that
 * list on the device and the apply RPC rejects an explicit incompatibility.
 * Unknown animal facts never remove a home from the pool.
 * LeoVer does not select a home.
 */
enum class FosterSizeBand { SMALL, MEDIUM, LARGE }

enum class FosterLifeStage { YOUNG, ADULT, SENIOR }

enum class FosterSpeciesKind { DOG, CAT, OTHER }

data class FoundPetFosterNeeds(
    val species: FosterSpeciesKind? = null,
    val size: FosterSizeBand? = null,
    val lifeStage: FosterLifeStage? = null,
    val needsMedication: Boolean? = null,
    val cohabitsDogs: Boolean? = null,
    val cohabitsCats: Boolean? = null,
    val cohabitsChildren: Boolean? = null,
    val reducedMobility: Boolean? = null,
    val needsIsolation: Boolean? = null,
    val additionalInfo: String? = null
) {
    fun isCompleteEnoughToSend(): Boolean = true
}

data class FosterHomeCapabilities(
    val active: Boolean = true,
    val hasBaseLocation: Boolean = true,
    val localityId: String? = null,
    val capacity: Int = 1,
    val acceptsDogs: Boolean? = null,
    val acceptsCats: Boolean? = null,
    val acceptsSmall: Boolean? = null,
    val acceptsMedium: Boolean? = null,
    val acceptsLarge: Boolean? = null,
    val acceptsYoung: Boolean? = null,
    val acceptsAdult: Boolean? = null,
    val acceptsSenior: Boolean? = null,
    val acceptsMedication: Boolean? = null,
    val livesWithDogs: Boolean? = null,
    val livesWithCats: Boolean? = null,
    val livesWithChildren: Boolean? = null,
    val acceptsReducedMobility: Boolean? = null,
    val canIsolate: Boolean? = null,
    val speciesPref: String? = null,
    val agePref: String? = null
)

enum class FosterMatchOutcome { ELIGIBLE, EXCLUDED }

data class FosterMatchResult(
    val outcome: FosterMatchOutcome,
    val rank: Int
) {
    val eligible: Boolean get() = outcome == FosterMatchOutcome.ELIGIBLE
}

object FosterMatchingPolicy {
    fun evaluate(needs: FoundPetFosterNeeds, home: FosterHomeCapabilities): FosterMatchResult {
        if (!home.active || !home.hasBaseLocation || home.capacity <= 0) {
            return excluded()
        }
        var rank = 0
        val resolved = resolveLegacy(home)

        rank = speciesRank(needs.species, resolved) ?: return excluded()
        var total = rank
        sizeRank(needs.size, resolved)?.let { total += it } ?: return excluded()
        lifeStageRank(needs.lifeStage, resolved)?.let { total += it } ?: return excluded()
        flagRank(needs.needsMedication, resolved.acceptsMedication)?.let { total += it } ?: return excluded()
        coexistRank(needs.cohabitsDogs, resolved.livesWithDogs)?.let { total += it } ?: return excluded()
        coexistRank(needs.cohabitsCats, resolved.livesWithCats)?.let { total += it } ?: return excluded()
        coexistRank(needs.cohabitsChildren, resolved.livesWithChildren)?.let { total += it } ?: return excluded()
        flagRank(needs.reducedMobility, resolved.acceptsReducedMobility)?.let { total += it }
            ?: return excluded()
        flagRank(needs.needsIsolation, resolved.canIsolate)?.let { total += it } ?: return excluded()
        return FosterMatchResult(FosterMatchOutcome.ELIGIBLE, total)
    }

    /**
     * Structured nulls fall back to the historical free-text preferences.
     * Free text only adds a positive preference. It never becomes an explicit no.
     */
    fun resolveLegacy(home: FosterHomeCapabilities): FosterHomeCapabilities {
        val species = parseSpeciesPref(home.speciesPref)
        val age = parseAgePref(home.agePref)
        return home.copy(
            acceptsDogs = home.acceptsDogs ?: species.dogs,
            acceptsCats = home.acceptsCats ?: species.cats,
            acceptsYoung = home.acceptsYoung ?: age.young,
            acceptsAdult = home.acceptsAdult ?: age.adult,
            acceptsSenior = home.acceptsSenior ?: age.senior
        )
    }

    fun <T> rankCandidates(candidates: List<T>, needs: (T) -> FoundPetFosterNeeds, home: FosterHomeCapabilities): List<T> {
        return candidates
            .mapNotNull { item ->
                val result = evaluate(needs(item), home)
                if (!result.eligible) null else item to result.rank
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun speciesRank(species: FosterSpeciesKind?, home: FosterHomeCapabilities): Int? {
        return when (species) {
            null, FosterSpeciesKind.OTHER -> 0
            FosterSpeciesKind.DOG -> preferenceRank(home.acceptsDogs)
            FosterSpeciesKind.CAT -> preferenceRank(home.acceptsCats)
        }
    }

    private fun sizeRank(size: FosterSizeBand?, home: FosterHomeCapabilities): Int? = when (size) {
        null -> 0
        FosterSizeBand.SMALL -> preferenceRank(home.acceptsSmall)
        FosterSizeBand.MEDIUM -> preferenceRank(home.acceptsMedium)
        FosterSizeBand.LARGE -> preferenceRank(home.acceptsLarge)
    }

    private fun lifeStageRank(stage: FosterLifeStage?, home: FosterHomeCapabilities): Int? = when (stage) {
        null -> 0
        FosterLifeStage.YOUNG -> preferenceRank(home.acceptsYoung)
        FosterLifeStage.ADULT -> preferenceRank(home.acceptsAdult)
        FosterLifeStage.SENIOR -> preferenceRank(home.acceptsSenior)
    }

    /** Animal needs the thing. Home null does not exclude. Home false does. */
    private fun flagRank(animalNeeds: Boolean?, homeAccepts: Boolean?): Int? {
        if (animalNeeds != true) return 0
        return preferenceRank(homeAccepts)
    }

    /**
     * False on the animal means it cannot share that company.
     * A home that explicitly has that company is incompatible.
     * Unknown on either side does not exclude.
     */
    private fun coexistRank(animalCanCohabit: Boolean?, homeHasCompany: Boolean?): Int? {
        if (animalCanCohabit == false && homeHasCompany == true) return null
        if (animalCanCohabit == true && homeHasCompany == true) return 1
        return 0
    }

    private fun preferenceRank(accepts: Boolean?): Int? = when (accepts) {
        false -> null
        true -> 1
        null -> 0
    }

    private fun excluded() = FosterMatchResult(FosterMatchOutcome.EXCLUDED, 0)
}

internal data class SpeciesHint(val dogs: Boolean?, val cats: Boolean?)

internal data class AgeHint(val young: Boolean?, val adult: Boolean?, val senior: Boolean?)

internal fun parseSpeciesPref(raw: String?): SpeciesHint {
    val text = raw?.trim()?.lowercase().orEmpty()
    if (text.isEmpty() || text == "both" || text == "ambos" || text == "any" || text == "none") {
        return SpeciesHint(null, null)
    }
    val dogs = containsAny(text, "perro", "perros", "dog", "dogs")
    val cats = containsAny(text, "gato", "gatos", "cat", "cats")
    return SpeciesHint(dogs = if (dogs) true else null, cats = if (cats) true else null)
}

internal fun parseAgePref(raw: String?): AgeHint {
    val text = raw?.trim()?.lowercase().orEmpty()
    if (text.isEmpty() || text == "any" || text == "ambos") return AgeHint(null, null, null)
    return AgeHint(
        young = if (containsAny(text, "cachorr", "joven", "young", "kitten", "puppy")) true else null,
        adult = if (containsAny(text, "adult")) true else null,
        senior = if (containsAny(text, "mayor", "senior", "viej")) true else null
    )
}

private fun containsAny(text: String, vararg tokens: String): Boolean = tokens.any { text.contains(it) }

fun fosterSpeciesKind(code: String?): FosterSpeciesKind? = when (code?.trim()?.uppercase()) {
    null, "" -> null
    "DOG" -> FosterSpeciesKind.DOG
    "CAT" -> FosterSpeciesKind.CAT
    "UNKNOWN" -> null
    else -> FosterSpeciesKind.OTHER
}

fun fosterSizeBand(code: String?): FosterSizeBand? = when (code?.trim()?.uppercase()) {
    "SMALL" -> FosterSizeBand.SMALL
    "MEDIUM" -> FosterSizeBand.MEDIUM
    "LARGE" -> FosterSizeBand.LARGE
    else -> null
}

fun fosterLifeStage(code: String?): FosterLifeStage? = when (code?.trim()?.uppercase()) {
    "YOUNG" -> FosterLifeStage.YOUNG
    "ADULT" -> FosterLifeStage.ADULT
    "SENIOR" -> FosterLifeStage.SENIOR
    else -> null
}

object FosterTransitVisibility {
    const val FOUND_ORIGIN = "FOUND_CASE"
    const val SEARCH = "Buscar hogar de tránsito"
    const val VIEW = "Ver solicitud de tránsito"

    fun forOrigin(originKind: String?): Boolean =
        originKind.equals(FOUND_ORIGIN, ignoreCase = true)

    fun isActiveRequest(status: String?): Boolean =
        status?.trim()?.uppercase() in setOf("REQUESTED", "MATCHED", "ACTIVE")

    fun primaryLabel(hasActiveRequest: Boolean): String = if (hasActiveRequest) VIEW else SEARCH
}

object FosterRequestPresentation {
    fun requestStatus(status: String?, interestedHomes: Int = 0): String = when (status?.trim()?.uppercase()) {
        "CANCELLED" -> "Solicitud cancelada"
        "COMPLETED" -> "Tránsito finalizado"
        "ACTIVE" -> "Hogar seleccionado"
        "MATCHED" -> "Buscando hogares"
        "REQUESTED" -> if (interestedHomes > 0) "Hay hogares interesados" else "Solicitud enviada"
        else -> "Solicitud de tránsito"
    }

    fun applicationStatus(status: String?): String = when (status?.trim()?.uppercase()) {
        "PENDING" -> "En espera"
        "SELECTED" -> "Hogar seleccionado"
        "NOT_SELECTED" -> "No elegido"
        "WITHDRAWN" -> "Postulación retirada"
        else -> "Postulación"
    }

    fun species(code: String?): String = when (code?.trim()?.uppercase()) {
        "DOG" -> "Perro"
        "CAT" -> "Gato"
        null, "" -> ""
        else -> "Otro"
    }

    fun size(band: FosterSizeBand?): String = when (band) {
        FosterSizeBand.SMALL -> "Pequeño"
        FosterSizeBand.MEDIUM -> "Mediano"
        FosterSizeBand.LARGE -> "Grande"
        null -> ""
    }

    fun leaksTechnical(text: String): Boolean {
        val upper = text.uppercase()
        return upper.contains("REQUESTED") ||
            upper.contains("MATCHED") ||
            upper.contains("PENDING") ||
            upper.contains("WITHDRAWN") ||
            upper.contains("NOT_SELECTED") ||
            upper.contains("SERVIDOR") ||
            upper.contains("IDENTIFICADOR")
    }
}

object FosterApplicantSelection {
    fun canChoose(
        callerIsManager: Boolean,
        applicationExists: Boolean,
        applicationStatus: String?,
        homeActive: Boolean,
        homeHasBaseLocation: Boolean
    ): Boolean {
        if (!callerIsManager || !applicationExists) return false
        if (!applicationStatus.equals("PENDING", ignoreCase = true)) return false
        if (!homeActive || !homeHasBaseLocation) return false
        return true
    }
}

data class FosterApplicantView(
    val title: String,
    val zone: String,
    val availability: String,
    val capacity: String,
    val compatibility: String,
    val canChoose: Boolean
)

fun presentFosterApplicant(
    name: String?,
    localityId: String?,
    capacity: Int?,
    profileActive: Boolean?,
    hasBaseLocation: Boolean?,
    applicationStatus: String?,
    needs: FoundPetFosterNeeds,
    home: FosterHomeCapabilities,
    callerIsManager: Boolean,
    applicationExists: Boolean
): FosterApplicantView {
    val active = profileActive == true
    val located = hasBaseLocation == true
    val match = FosterMatchingPolicy.evaluate(needs, home.copy(active = active, hasBaseLocation = located))
    val zone = HumanLocationLabel.visible(localityId).ifBlank { "Zona no informada" }
    return FosterApplicantView(
        title = name?.trim().orEmpty().ifBlank { "Hogar de tránsito" },
        zone = zone,
        availability = if (active && located) "Disponible" else "No disponible",
        capacity = capacity?.takeIf { it > 0 }?.let { "Puede recibir $it" }.orEmpty(),
        compatibility = when {
            !active || !located -> "No disponible"
            !match.eligible -> "No coincide con lo que sabemos"
            match.rank > 0 -> "Coincide con lo que sabemos"
            else -> "Puede recibirlo"
        },
        canChoose = FosterApplicantSelection.canChoose(
            callerIsManager = callerIsManager,
            applicationExists = applicationExists,
            applicationStatus = applicationStatus,
            homeActive = active,
            homeHasBaseLocation = located
        ) && match.eligible
    )
}

const val FOSTER_APPLICANTS_EMPTY = "Todavía no hay postulantes"
const val FOSTER_APPLICANTS_EMPTY_HINT = "Cuando un hogar se postule, vas a poder elegirlo."
const val FOSTER_CHOOSE_HOME = "Elegí un hogar de tránsito"
const val FOSTER_CHOOSE_ACTION = "Elegir hogar de tránsito"
