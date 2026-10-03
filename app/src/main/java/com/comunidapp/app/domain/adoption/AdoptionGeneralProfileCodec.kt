package com.comunidapp.app.domain.adoption

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The three fields the 17A adoption profile screen edits.
 * canon_list_my_adoption_general_profile returns the row or {}.
 */
object AdoptionGeneralProfileCodec {
    data class Fields(
        val housing: String = "",
        val motivation: String = "",
        val notes: String = ""
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun decode(raw: String?): Fields {
        val element = parseObject(raw) ?: return Fields()
        return Fields(
            housing = textOf(element, "housing_type"),
            motivation = textOf(element, "motivation"),
            notes = textOf(element, "notes")
        )
    }

    fun decodeProfile(raw: String?): AdopterProfile {
        val element = parseObject(raw) ?: return AdopterProfile()
        val housingRaw = textOrNull(element, "housing_type")
        val tenureRaw = textOrNull(element, "housing_tenure")
        val kind = housingKindOf(housingRaw)
        val tenure = housingTenureOf(tenureRaw)
        return AdopterProfile(
            housingKind = kind,
            housingTenure = tenure,
            landlordAllowsPets = boolOrNull(element, "animals_allowed"),
            hasOutdoorSpace = boolOrNull(element, "has_outdoor_space"),
            hasSecureEnclosure = boolOrNull(element, "has_secure_enclosure"),
            escapeProtection = boolOrNull(element, "escape_protection"),
            adultsCount = intOrNull(element, "adults_count"),
            childrenCount = intOrNull(element, "children_count"),
            householdAgrees = boolOrNull(element, "household_agrees"),
            hasDogs = boolOrNull(element, "has_dogs"),
            hasCats = boolOrNull(element, "has_cats"),
            hasOtherAnimals = boolOrNull(element, "has_other_animals"),
            experienceBand = experienceBandOf(textOrNull(element, "experience_band")),
            hoursAlone = hoursAloneEstimateOf(
                intOrNull(element, "hours_alone_from"),
                intOrNull(element, "hours_alone_to"),
                textOrNull(element, "hours_alone_band")
            ),
            canVetFollowup = boolOrNull(element, "can_vet_followup"),
            canMedicate = boolOrNull(element, "can_medicate"),
            acceptsSpecialNeeds = boolOrNull(element, "accepts_special_needs"),
            speciesPref = speciesPrefOf(textOrNull(element, "species_pref")),
            sizePref = sizePrefOf(textOrNull(element, "size_pref")),
            lifeStagePref = lifeStagePrefOf(textOrNull(element, "life_stage_pref")),
            motivation = textOrNull(element, "motivation"),
            notes = textOrNull(element, "notes"),
            housingNotes = if (kind == null) housingRaw else null,
            legacyExperience = textOrNull(element, "experience"),
            legacyHoursAlone = textOrNull(element, "hours_alone"),
            legacyOtherPets = textOrNull(element, "other_pets")
        )
    }

    /**
     * Writes the structured profile. Historical prose that is not a token is
     * kept when the person has not chosen a token. Allergies, caretaker and
     * vet reference are never asked and are echoed so a save does not erase them.
     */
    fun upsertProfile(existingRaw: String?, profile: AdopterProfile): JsonObject {
        val existing = parseObject(existingRaw)
        return buildJsonObject {
            putText("p_housing_type", housingToStore(profile.housingKind, textOrNull(existing, "housing_type")))
            putText("p_housing_tenure", tenureToStore(profile.housingTenure, textOrNull(existing, "housing_tenure")))
            putBool("p_animals_allowed", profile.landlordAllowsPets)
            putInt("p_adults_count", profile.adultsCount)
            putInt("p_children_count", profile.childrenCount)
            putPreserved(existing, "allergies", "p_allergies")
            putPreserved(existing, "other_pets", "p_other_pets")
            putPreserved(existing, "experience", "p_experience")
            putPreserved(existing, "hours_alone", "p_hours_alone")
            putPreserved(existing, "primary_caretaker", "p_primary_caretaker")
            putPreserved(existing, "vet_reference", "p_vet_reference")
            putText("p_motivation", profile.motivation?.trim()?.ifBlank { null })
            putText("p_notes", profile.notes?.trim()?.ifBlank { null })
            putBool("p_has_outdoor_space", profile.hasOutdoorSpace)
            putBool("p_has_secure_enclosure", profile.hasSecureEnclosure)
            putBool("p_escape_protection", profile.escapeProtection)
            putBool("p_household_agrees", profile.householdAgrees)
            putBool("p_has_dogs", profile.hasDogs)
            putBool("p_has_cats", profile.hasCats)
            putBool("p_has_other_animals", profile.hasOtherAnimals)
            putText("p_experience_band", profile.experienceBand?.name)
            putText("p_hours_alone_band", profile.hoursAlone?.legacyBandToken())
            putInt("p_hours_alone_from", profile.hoursAlone?.fromHours)
            putInt("p_hours_alone_to", profile.hoursAlone?.toHours)
            putBool("p_can_vet_followup", profile.canVetFollowup)
            putBool("p_can_medicate", profile.canMedicate)
            putBool("p_accepts_special_needs", profile.acceptsSpecialNeeds)
            putText("p_species_pref", profile.speciesPref?.name)
            putText("p_size_pref", profile.sizePref?.name)
            putText("p_life_stage_pref", profile.lifeStagePref?.name)
        }
    }

    fun decodeRequirements(
        acceptsChildren: Boolean? = null,
        acceptsOtherDogs: Boolean? = null,
        acceptsCats: Boolean? = null,
        needsOutdoorSpace: Boolean? = null,
        needsSecureEnclosure: Boolean? = null,
        requiresEscapeProtection: Boolean? = null,
        requiresLandlordPetPermission: Boolean? = null,
        acceptsOtherAnimals: Boolean? = null,
        maxHoursAlone: String? = null,
        maxHoursFrom: Int? = null,
        maxHoursTo: Int? = null,
        experienceRequired: String? = null,
        acceptsNoExperience: Boolean? = null,
        requiresSpecialCareExperience: Boolean? = null,
        additionalNotes: String? = null
    ): AdoptionRequirements = AdoptionRequirements(
        acceptsChildren = acceptsChildren,
        acceptsOtherDogs = acceptsOtherDogs,
        acceptsCats = acceptsCats,
        needsOutdoorSpace = needsOutdoorSpace,
        needsSecureEnclosure = needsSecureEnclosure,
        requiresEscapeProtection = requiresEscapeProtection,
        requiresLandlordPetPermission = requiresLandlordPetPermission,
        acceptsOtherAnimals = acceptsOtherAnimals,
        maxHoursAlone = hoursAloneEstimateOf(maxHoursFrom, maxHoursTo, maxHoursAlone),
        experienceRequired = experienceBandOf(experienceRequired),
        acceptsNoExperience = acceptsNoExperience,
        requiresSpecialCareExperience = requiresSpecialCareExperience,
        additionalNotes = additionalNotes?.trim()?.ifBlank { null }
    )

    private fun textOf(element: JsonObject, key: String): String =
        element[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

    /**
     * canon_upsert_adoption_general_profile writes every argument.
     * Omitted arguments default to null and erase columns the 17A screen does not edit.
     * Echo the stored row and overlay only housing, motivation and notes.
     */
    fun upsertPreserving(existingRaw: String?, housing: String, motivation: String, notes: String): JsonObject {
        val existing = parseObject(existingRaw)
        return buildJsonObject {
            PRESERVED_COLUMNS.forEach { (column, parameter) ->
                existing?.get(column)?.let { put(parameter, it) }
            }
            put("p_housing_type", housing)
            put("p_motivation", motivation)
            put("p_notes", notes)
        }
    }

    private fun parseObject(raw: String?): JsonObject? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == "null") return null
        return runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
    }

    private fun textOrNull(element: JsonObject?, key: String): String? {
        val node = element?.get(key) ?: return null
        if (node is JsonNull) return null
        return node.jsonPrimitive.contentOrNull?.trim()?.ifBlank { null }
    }

    private fun boolOrNull(element: JsonObject, key: String): Boolean? {
        val node = element[key] ?: return null
        if (node is JsonNull) return null
        val primitive = node.jsonPrimitive
        if (primitive.isString) {
            return when (primitive.content.trim().lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }
        return runCatching { primitive.content.toBooleanStrictOrNull() }.getOrNull()
            ?: when (primitive.content) {
                "true" -> true
                "false" -> false
                else -> null
            }
    }

    private fun intOrNull(element: JsonObject, key: String): Int? {
        val node = element[key] ?: return null
        if (node is JsonNull) return null
        return node.jsonPrimitive.intOrNull
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putText(key: String, value: String?) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putBool(key: String, value: Boolean?) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putInt(key: String, value: Int?) {
        if (value == null) put(key, JsonNull) else put(key, value)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putPreserved(
        existing: JsonObject?,
        column: String,
        parameter: String
    ) {
        val node = existing?.get(column)
        if (node == null || node is JsonNull) put(parameter, JsonNull) else put(parameter, node)
    }

    private fun housingToStore(selected: HousingKind?, existing: String?): String? {
        if (selected != null) return selected.name
        val raw = existing?.trim().orEmpty()
        if (raw.isEmpty() || housingKindOf(raw) != null) return null
        return raw
    }

    private fun tenureToStore(selected: HousingTenure?, existing: String?): String? {
        if (selected != null) return selected.name
        val raw = existing?.trim().orEmpty()
        if (raw.isEmpty() || housingTenureOf(raw) != null) return null
        return raw
    }

    private fun speciesPrefOf(raw: String?): AdopterSpeciesPref? = when (raw?.trim()?.uppercase()) {
        "DOG" -> AdopterSpeciesPref.DOG
        "CAT" -> AdopterSpeciesPref.CAT
        "ANY" -> AdopterSpeciesPref.ANY
        else -> null
    }

    private fun sizePrefOf(raw: String?): AdopterSizePref? = when (raw?.trim()?.uppercase()) {
        "SMALL" -> AdopterSizePref.SMALL
        "MEDIUM" -> AdopterSizePref.MEDIUM
        "LARGE" -> AdopterSizePref.LARGE
        else -> null
    }

    private fun lifeStagePrefOf(raw: String?): AdopterLifeStagePref? = when (raw?.trim()?.uppercase()) {
        "YOUNG" -> AdopterLifeStagePref.YOUNG
        "ADULT" -> AdopterLifeStagePref.ADULT
        "SENIOR" -> AdopterLifeStagePref.SENIOR
        else -> null
    }

    private val PRESERVED_COLUMNS = listOf(
        "housing_tenure" to "p_housing_tenure",
        "animals_allowed" to "p_animals_allowed",
        "adults_count" to "p_adults_count",
        "children_count" to "p_children_count",
        "allergies" to "p_allergies",
        "other_pets" to "p_other_pets",
        "experience" to "p_experience",
        "hours_alone" to "p_hours_alone",
        "primary_caretaker" to "p_primary_caretaker",
        "vet_reference" to "p_vet_reference"
    )
}
