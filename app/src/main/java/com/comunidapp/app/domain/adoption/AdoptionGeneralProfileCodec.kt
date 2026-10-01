package com.comunidapp.app.domain.adoption

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

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
}
