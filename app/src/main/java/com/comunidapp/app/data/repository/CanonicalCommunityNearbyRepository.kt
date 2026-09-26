package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.verification.VerificationDisplayPolicy
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

data class CommunityNearbyItem(
    val id: String,
    val kind: String,
    val name: String,
    val localityId: String?,
    val verificationStatus: String?,
    val meters: Double?,
    val publicAddress: Boolean
) {
    val verified: Boolean get() = verificationStatus.equals("VERIFIED", true)
    val badge: String get() = VerificationDisplayPolicy.badgeLabel(verified)
}

class CanonicalCommunityNearbyRepository {
    suspend fun list(lat: Double, lng: Double, filter: String): Result<List<CommunityNearbyItem>> =
        runCatching {
            val element: JsonElement = supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_LIST_COMMUNITY_NEARBY,
                parameters = buildJsonObject {
                    put("p_lat", lat)
                    put("p_lng", lng)
                    put("p_filter", filter)
                }
            ).decodeAs()
            val array = element as? JsonArray ?: return@runCatching emptyList()
            array.mapNotNull { item ->
                val obj = item.jsonObject
                CommunityNearbyItem(
                    id = obj.string("id") ?: return@mapNotNull null,
                    kind = obj.string("kind").orEmpty(),
                    name = obj.string("name").orEmpty(),
                    localityId = obj.string("locality_id"),
                    verificationStatus = obj.string("verification_status"),
                    meters = (obj["meters"] as? JsonPrimitive)?.doubleOrNull,
                    publicAddress = obj.string("public_address") == "true"
                )
            }
        }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
