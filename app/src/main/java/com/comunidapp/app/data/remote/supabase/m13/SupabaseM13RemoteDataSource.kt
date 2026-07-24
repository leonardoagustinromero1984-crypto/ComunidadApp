package com.comunidapp.app.data.remote.supabase.m13

import com.comunidapp.app.data.model.M13MatchCandidate
import com.comunidapp.app.data.model.M13MatchLevel
import com.comunidapp.app.data.model.M13MatchReason
import com.comunidapp.app.data.model.M13MatchStatus
import com.comunidapp.app.data.model.M13Sighting
import com.comunidapp.app.data.model.M13SightingPublic
import com.comunidapp.app.data.model.M13SightingStatus
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * LeoVer M13 Bloque 2 — DTOs JSON desde RPC (sin DML directo).
 */
@Serializable
data class M13SightingRow(
    val id: String,
    @SerialName("reporter_user_id") val reporterUserId: String? = null,
    @SerialName("lost_found_case_id") val lostFoundCaseId: String? = null,
    val species: String? = null,
    @SerialName("breed_text") val breedText: String? = null,
    @SerialName("primary_color") val primaryColor: String? = null,
    @SerialName("secondary_color") val secondaryColor: String? = null,
    val sex: String? = null,
    val size: String? = null,
    @SerialName("observed_at") val observedAt: String? = null,
    @SerialName("zone_text") val zoneText: String? = null,
    @SerialName("latitude_approx") val latitudeApprox: Double? = null,
    @SerialName("longitude_approx") val longitudeApprox: Double? = null,
    @SerialName("accuracy_meters") val accuracyMeters: Double? = null,
    val description: String? = null,
    @SerialName("description_preview") val descriptionPreview: String? = null,
    @SerialName("media_refs") val mediaRefs: List<String> = emptyList(),
    val status: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("has_approximate_location") val hasApproximateLocation: Boolean? = null,
    @SerialName("observed_at_approx_day") val observedAtApproxDay: String? = null
)

@Serializable
data class M13MatchCandidateRow(
    val id: String,
    @SerialName("case_id") val caseId: String,
    @SerialName("sighting_id") val sightingId: String,
    val score: Int = 0,
    val level: String = "LOW",
    val reasons: List<String> = emptyList(),
    val status: String = "PROPOSED",
    @SerialName("algorithm_version") val algorithmVersion: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

fun M13SightingRow.toDomain(): M13Sighting = M13Sighting(
    id = id,
    reporterUserId = reporterUserId.orEmpty(),
    lostFoundCaseId = lostFoundCaseId,
    species = PetSpecies.entries.find { it.name.equals(species, true) } ?: PetSpecies.DOG,
    breedText = breedText,
    primaryColor = primaryColor.orEmpty(),
    secondaryColor = secondaryColor,
    sex = sex?.let { s -> PetSex.entries.find { it.name.equals(s, true) } },
    size = size?.let { s -> PetSize.entries.find { it.name.equals(s, true) } },
    observedAt = parseTs(observedAt),
    zoneText = zoneText.orEmpty(),
    latitudeApprox = latitudeApprox,
    longitudeApprox = longitudeApprox,
    accuracyMeters = accuracyMeters,
    description = description ?: descriptionPreview.orEmpty(),
    mediaRefs = mediaRefs,
    status = M13SightingStatus.entries.find { it.name.equals(status, true) }
        ?: M13SightingStatus.ACTIVE,
    createdAt = parseTs(createdAt),
    updatedAt = parseTs(updatedAt)
)

fun M13SightingRow.toPublic(): M13SightingPublic {
    val dayMs = 24L * 60L * 60L * 1000L
    val observed = parseTs(observedAtApproxDay ?: observedAt)
    return M13SightingPublic(
        id = id,
        lostFoundCaseId = lostFoundCaseId,
        species = PetSpecies.entries.find { it.name.equals(species, true) } ?: PetSpecies.DOG,
        breedText = breedText,
        primaryColor = primaryColor.orEmpty(),
        secondaryColor = secondaryColor,
        sex = sex?.let { s -> PetSex.entries.find { it.name.equals(s, true) } },
        size = size?.let { s -> PetSize.entries.find { it.name.equals(s, true) } },
        observedAtApproxDay = (observed / dayMs) * dayMs,
        zoneText = zoneText.orEmpty(),
        descriptionPreview = descriptionPreview ?: description.orEmpty(),
        mediaRefs = mediaRefs,
        status = M13SightingStatus.entries.find { it.name.equals(status, true) }
            ?: M13SightingStatus.ACTIVE,
        hasApproximateLocation = hasApproximateLocation
            ?: (latitudeApprox != null && longitudeApprox != null)
    )
}

fun M13MatchCandidateRow.toDomain(): M13MatchCandidate = M13MatchCandidate(
    id = id,
    caseId = caseId,
    sightingId = sightingId,
    score = score.coerceIn(0, 100),
    level = M13MatchLevel.entries.find { it.name.equals(level, true) }
        ?: M13MatchLevel.fromScore(score),
    reasons = reasons.mapNotNull { r ->
        M13MatchReason.entries.find { it.name.equals(r, true) }
    },
    status = M13MatchStatus.entries.find { it.name.equals(status, true) }
        ?: M13MatchStatus.PROPOSED,
    createdAt = parseTs(createdAt),
    updatedAt = parseTs(updatedAt)
)

private fun parseTs(raw: String?): Long {
    if (raw.isNullOrBlank()) return System.currentTimeMillis()
    return runCatching { Instant.parse(raw).toEpochMilli() }.getOrElse {
        raw.toLongOrNull() ?: System.currentTimeMillis()
    }
}

class SupabaseM13RemoteDataSource {
    private suspend inline fun <reified T : Any> rpc(
        name: String,
        params: JsonObject = buildJsonObject { }
    ): T = supabase.postgrest.rpc(function = name, parameters = params).decodeSingle()

    private suspend inline fun <reified T : Any> rpcList(
        name: String,
        params: JsonObject = buildJsonObject { }
    ): List<T> = supabase.postgrest.rpc(function = name, parameters = params).decodeList()

    suspend fun createSighting(params: JsonObject): M13SightingRow =
        rpc("m13_create_sighting", params)

    suspend fun updateMySighting(params: JsonObject): M13SightingRow =
        rpc("m13_update_my_sighting", params)

    suspend fun withdrawMySighting(sightingId: String): M13SightingRow =
        rpc("m13_withdraw_my_sighting", buildJsonObject { put("p_sighting_id", sightingId) })

    suspend fun getSighting(sightingId: String): M13SightingRow =
        rpc("m13_get_sighting", buildJsonObject { put("p_sighting_id", sightingId) })

    suspend fun listPublicSightings(limit: Int = 50, offset: Int = 0): List<M13SightingRow> =
        rpcList(
            "m13_list_public_sightings",
            buildJsonObject {
                put("p_limit", limit)
                put("p_offset", offset)
            }
        )

    suspend fun listMySightings(): List<M13SightingRow> =
        rpcList("m13_list_my_sightings")

    suspend fun listManagedSightings(): List<M13SightingRow> =
        rpcList("m13_list_managed_sightings")

    suspend fun generateForSighting(sightingId: String): List<M13MatchCandidateRow> =
        rpcList(
            "m13_generate_match_candidates_for_sighting",
            buildJsonObject { put("p_sighting_id", sightingId) }
        )

    suspend fun generateForCase(caseId: String): List<M13MatchCandidateRow> =
        rpcList(
            "m13_generate_match_candidates_for_case",
            buildJsonObject { put("p_case_id", caseId) }
        )

    suspend fun listCaseCandidates(caseId: String): List<M13MatchCandidateRow> =
        rpcList(
            "m13_list_case_match_candidates",
            buildJsonObject { put("p_case_id", caseId) }
        )

    suspend fun listSightingCandidates(sightingId: String): List<M13MatchCandidateRow> =
        rpcList(
            "m13_list_sighting_match_candidates",
            buildJsonObject { put("p_sighting_id", sightingId) }
        )

    suspend fun getCandidate(candidateId: String): M13MatchCandidateRow =
        rpc("m13_get_match_candidate", buildJsonObject { put("p_candidate_id", candidateId) })

    suspend fun recalculateCandidate(candidateId: String): M13MatchCandidateRow =
        rpc(
            "m13_recalculate_match_candidate",
            buildJsonObject { put("p_candidate_id", candidateId) }
        )
}

fun CreateM13SightingParamsJson(
    caseId: String,
    species: String,
    primaryColor: String,
    zoneText: String,
    description: String,
    observedAtIso: String,
    breedText: String? = null,
    secondaryColor: String? = null,
    sex: String? = null,
    size: String? = null,
    latitudeApprox: Double? = null,
    longitudeApprox: Double? = null,
    accuracyMeters: Double? = null,
    mediaRefs: List<String> = emptyList()
): JsonObject = buildJsonObject {
    put("p_case_id", caseId)
    put("p_species", species)
    putNullable("p_breed_text", breedText)
    put("p_primary_color", primaryColor)
    putNullable("p_secondary_color", secondaryColor)
    putNullable("p_sex", sex)
    putNullable("p_size", size)
    put("p_observed_at", observedAtIso)
    put("p_zone_text", zoneText)
    putNullableNumber("p_latitude_approx", latitudeApprox)
    putNullableNumber("p_longitude_approx", longitudeApprox)
    putNullableNumber("p_accuracy_meters", accuracyMeters)
    put("p_description", description)
    put(
        "p_media_refs",
        JsonArray(mediaRefs.map { JsonPrimitive(it) })
    )
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: String?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableNumber(key: String, value: Double?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}
