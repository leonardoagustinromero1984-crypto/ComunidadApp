package com.comunidapp.app.data.remote.supabase.canonical

import com.comunidapp.app.data.remote.supabase.SupabaseRowDecoding
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Chooses the canon_get_pet_health payload that actually carries signals.
 * supabase-kt may decodeAs an empty object while [rawData] still has the row.
 */
object PetHealthRpcPayload {

    fun resolve(
        decodedElement: JsonElement?,
        rawData: String?
    ): CanonicalPetHealthDto {
        val candidates = buildList {
            decodedElement?.let { add(it) }
            rawData?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
                runCatching { SupabaseRowDecoding.json.parseToJsonElement(raw) }.getOrNull()?.let { add(it) }
            }
        }.flatMap(::expand)
        val parsed = candidates.mapNotNull { element ->
            runCatching { CanonicalPetHealthParser.parse(element) }.getOrNull()
        }
        return parsed.firstOrNull(::hasSignals)
            ?: parsed.firstOrNull()
            ?: error("PET_HEALTH_EMPTY_BODY")
    }

    fun hasSignals(dto: CanonicalPetHealthDto): Boolean =
        !dto.sterilizedStatus.isNullOrBlank() ||
            !dto.lastVetVisit.isNullOrBlank() ||
            !dto.declaredNotes.isNullOrBlank() ||
            !dto.careInstructions?.specials.isNullOrBlank() ||
            dto.vaccinations.any { it.displayName().isNotEmpty() } ||
            dto.parasiteTreatments.any {
                !it.productName.isNullOrBlank() || !it.treatedOn.isNullOrBlank()
            } ||
            dto.allergies.any { it.displayName().isNotEmpty() } ||
            dto.medications.any { it.displayName().isNotEmpty() } ||
            dto.conditions.any { it.displayName().isNotEmpty() } ||
            dto.weights.any { it.kilograms != null }

    private fun expand(element: JsonElement): List<JsonElement> {
        val out = mutableListOf(element)
        when (element) {
            is JsonArray -> element.forEach { out += expand(it) }
            is JsonPrimitive -> if (element.isString) {
                runCatching { SupabaseRowDecoding.json.parseToJsonElement(element.content) }
                    .getOrNull()
                    ?.let { nested -> out += expand(nested) }
            }
            is JsonObject -> {
                listOf("data", "result", "canon_get_pet_health").forEach { key ->
                    element[key]?.let { out += expand(it) }
                }
                if (element.size == 1) {
                    val value = element.values.first()
                    if (value !is JsonNull) out += expand(value)
                }
            }
        }
        return out
    }
}
