package com.comunidapp.app.domain.adoption

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == "null") return Fields()
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: return Fields()
        return Fields(
            housing = textOf(element, "housing_type"),
            motivation = textOf(element, "motivation"),
            notes = textOf(element, "notes")
        )
    }

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
