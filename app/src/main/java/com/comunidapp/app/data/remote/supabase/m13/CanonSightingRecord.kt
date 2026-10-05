package com.comunidapp.app.data.remote.supabase.m13

import com.comunidapp.app.data.model.M13Sighting
import com.comunidapp.app.data.model.M13SightingStatus
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.repository.CreateM13SightingInput
import com.comunidapp.app.domain.canonical.CanonicalBackend
import java.time.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Canonical contribute-info payload. observed_at is an ISO timestamp, never a
 * phrase appended to the comment.
 */
object CanonSightingRecord {
    const val RPC_CREATE = CanonicalBackend.RPC_CONTRIBUTE_LOST_FOUND_INFO
    const val RPC_GET = CanonicalBackend.RPC_GET_LOST_FOUND_SIGHTING

    fun createParams(input: CreateM13SightingInput): JsonObject = buildJsonObject {
        put("p_alert_id", input.lostFoundCaseId.orEmpty())
        put("p_observed_at", Instant.ofEpochMilli(input.observedAt).toString())
        put("p_note", input.description.trim())
        put("p_zone_text", input.zoneText.trim())
        put("p_species_code", input.species.name)
        put("p_primary_color", input.primaryColor.trim())
        if (input.latitudeApprox == null) put("p_lat", JsonNull) else put("p_lat", input.latitudeApprox)
        if (input.longitudeApprox == null) put("p_lng", JsonNull) else put("p_lng", input.longitudeApprox)
        val media = input.mediaRefs.firstOrNull { it.isNotBlank() }
        if (media == null) put("p_media_ref", JsonNull) else put("p_media_ref", media)
    }

    fun read(json: JsonObject): M13Sighting {
        val observedRaw = json.string("observed_at")
            ?: error("OBSERVED_AT_MISSING")
        val observedAt = Instant.parse(observedRaw).toEpochMilli()
        val note = json.string("description") ?: json.string("note").orEmpty()
        val media = json.string("media_ref")?.let { listOf(it) }.orEmpty()
        val created = json.string("created_at")?.let {
            runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
        } ?: observedAt
        return M13Sighting(
            id = json.string("id").orEmpty(),
            reporterUserId = json.string("reporter_user_id").orEmpty(),
            lostFoundCaseId = json.string("lost_found_case_id") ?: json.string("alert_id"),
            species = PetSpecies.entries.find { it.name.equals(json.string("species"), true) }
                ?: PetSpecies.DOG,
            primaryColor = json.string("primary_color").orEmpty(),
            observedAt = observedAt,
            zoneText = json.string("zone_text").orEmpty(),
            latitudeApprox = json.doubleOrNull("latitude"),
            longitudeApprox = json.doubleOrNull("longitude"),
            description = note,
            mediaRefs = media,
            status = M13SightingStatus.ACTIVE,
            createdAt = created,
            updatedAt = created
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.doubleOrNull(key: String): Double? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
}
