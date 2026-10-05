package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M13Sighting
import com.comunidapp.app.data.model.M13SightingPublic
import com.comunidapp.app.data.remote.supabase.m13.CanonSightingRecord
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Canonical contribute-info. Writes canon_contribute_lost_found_info and reads
 * the stored timestamp back through the canonical contribute operation.
 */
class CanonicalM13SightingRepository : M13SightingRepository {
    private val mine = MutableStateFlow<List<M13Sighting>>(emptyList())

    override fun observeMySightings(): Flow<List<M13Sighting>> = mine

    override fun observePublicSightings(): Flow<List<M13SightingPublic>> = flowOf(emptyList())

    override suspend fun getSighting(id: String, forPublic: Boolean): Result<Any> = runCatching {
        if (id.isBlank()) error("SIGHTING_NOT_FOUND")
        val cached = mine.value.firstOrNull { it.id == id }
        if (cached != null) return@runCatching cached
        CanonSightingRecord.read(
            rpcObject(
                CanonSightingRecord.RPC_GET,
                buildJsonObject { put("p_sighting_id", id) }
            )
        )
    }

    override suspend fun createSighting(input: CreateM13SightingInput): Result<M13Sighting> {
        M13Validators.validateCreate(
            description = input.description,
            zoneText = input.zoneText,
            primaryColor = input.primaryColor,
            mediaRefs = input.mediaRefs,
            latitudeApprox = input.latitudeApprox,
            longitudeApprox = input.longitudeApprox,
            accuracyMeters = input.accuracyMeters,
            observedAt = input.observedAt
        )?.let { return resultFailM13(it) }
        if (input.lostFoundCaseId.isNullOrBlank()) return resultFailM13("CASE_NOT_FOUND")
        return runCatching {
            val stored = CanonSightingRecord.read(
                rpcObject(CanonSightingRecord.RPC_CREATE, CanonSightingRecord.createParams(input))
            )
            mine.update { listOf(stored) + it.filterNot { row -> row.id == stored.id } }
            stored
        }
    }

    override suspend fun withdrawSighting(id: String): Result<M13Sighting> =
        resultFailM13("SIGHTING_NOT_FOUND")

    private suspend fun rpcObject(function: String, parameters: JsonObject): JsonObject {
        val element = Json.parseToJsonElement(
            supabase.postgrest.rpc(function = function, parameters = parameters).data
        )
        return element as? JsonObject ?: error("CANON_EMPTY")
    }
}
