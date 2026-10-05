package com.comunidapp.app.data.remote.supabase.m17

import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Canonical Staging help remote. It calls canon_* functions only.
 */
interface M17HelpRemote {
    suspend fun listPublicInKindNeeds(query: String?, category: String?, organizationId: String?): List<JsonObject>
    suspend fun getPublicInKindNeed(needId: String): JsonObject
    suspend fun createInKindPledge(needId: String, quantity: Int, message: String?): JsonObject
    suspend fun markInKindPledgeDelivered(pledgeId: String): JsonObject
    suspend fun listInKindPledges(needId: String): List<JsonObject>
    suspend fun listMyInKindPledges(): List<JsonObject>
    suspend fun listPublicVolunteerOpportunities(query: String?, type: String?, organizationId: String?): List<JsonObject>
    suspend fun getPublicVolunteerOpportunity(opportunityId: String): JsonObject
    suspend fun submitVolunteerApplication(opportunityId: String, message: String?): JsonObject
    suspend fun acceptVolunteerApplication(applicationId: String): JsonObject
    suspend fun listVolunteerApplicants(opportunityId: String): List<JsonObject>
    suspend fun listMyVolunteerApplications(): List<JsonObject>
}

object CanonicalVolunteerList {
    fun params(organizationId: String?, query: String?, type: String?): JsonObject = buildJsonObject {
        if (organizationId.isNullOrBlank()) put("p_organization_id", JsonNull) else put("p_organization_id", organizationId)
        if (query.isNullOrBlank()) put("p_query", JsonNull) else put("p_query", query)
        if (type.isNullOrBlank()) put("p_type", JsonNull) else put("p_type", type)
    }
}

class CanonicalM17RemoteDataSource : M17HelpRemote {

    override suspend fun listPublicInKindNeeds(
        query: String?,
        category: String?,
        organizationId: String?
    ): List<JsonObject> = rpcObjects(
        CanonicalBackend.RPC_LIST_IN_KIND_NEEDS,
        buildJsonObject {
            if (organizationId.isNullOrBlank()) put("p_organization_id", JsonNull)
            else put("p_organization_id", organizationId)
        }
    ).filter { row ->
        val title = row.primitive("title")
        val description = row.primitive("description")
        val matchesQuery = query.isNullOrBlank() ||
            title.contains(query, ignoreCase = true) ||
            description.contains(query, ignoreCase = true)
        val matchesCategory = category.isNullOrBlank() || row.primitive("category") == category
        matchesQuery && matchesCategory
    }

    override suspend fun getPublicInKindNeed(needId: String): JsonObject = rpcObject(
        CanonicalBackend.RPC_GET_IN_KIND_NEED,
        buildJsonObject { put("p_need_id", needId) }
    )

    override suspend fun createInKindPledge(needId: String, quantity: Int, message: String?): JsonObject = rpcObject(
        CanonicalBackend.RPC_PLEDGE_IN_KIND_NEED,
        buildJsonObject {
            put("p_need_id", needId)
            put("p_quantity", quantity)
            put("p_message", message)
        }
    )

    override suspend fun markInKindPledgeDelivered(pledgeId: String): JsonObject = rpcObject(
        CanonicalBackend.RPC_MARK_IN_KIND_PLEDGE_DELIVERED,
        buildJsonObject { put("p_pledge_id", pledgeId) }
    )

    override suspend fun listInKindPledges(needId: String): List<JsonObject> = rpcObjects(
        CanonicalBackend.RPC_LIST_IN_KIND_PLEDGES,
        buildJsonObject { put("p_need_id", needId) }
    )

    override suspend fun listMyInKindPledges(): List<JsonObject> = rpcObjects(
        CanonicalBackend.RPC_LIST_MY_IN_KIND_PLEDGES,
        buildJsonObject { }
    )

    override suspend fun listPublicVolunteerOpportunities(
        query: String?,
        type: String?,
        organizationId: String?
    ): List<JsonObject> = rpcObjects(
        CanonicalBackend.RPC_LIST_VOLUNTEER_OPPORTUNITIES,
        CanonicalVolunteerList.params(organizationId, query, type)
    )

    override suspend fun getPublicVolunteerOpportunity(opportunityId: String): JsonObject = rpcObject(
        CanonicalBackend.RPC_GET_VOLUNTEER_OPPORTUNITY,
        buildJsonObject { put("p_opportunity_id", opportunityId) }
    )

    override suspend fun submitVolunteerApplication(opportunityId: String, message: String?): JsonObject = rpcObject(
        CanonicalBackend.RPC_APPLY_VOLUNTEER_OPPORTUNITY,
        buildJsonObject {
            put("p_opportunity_id", opportunityId)
            put("p_message", message)
        }
    )

    override suspend fun acceptVolunteerApplication(applicationId: String): JsonObject = rpcObject(
        CanonicalBackend.RPC_ACCEPT_VOLUNTEER_APPLICATION,
        buildJsonObject { put("p_application_id", applicationId) }
    )

    override suspend fun listVolunteerApplicants(opportunityId: String): List<JsonObject> = rpcObjects(
        CanonicalBackend.RPC_LIST_VOLUNTEER_APPLICANTS,
        buildJsonObject { put("p_opportunity_id", opportunityId) }
    )

    override suspend fun listMyVolunteerApplications(): List<JsonObject> = rpcObjects(
        CanonicalBackend.RPC_LIST_MY_VOLUNTEER_APPLICATIONS,
        buildJsonObject { }
    )

    private suspend fun rpcObject(function: String, parameters: JsonObject): JsonObject {
        val element = Json.parseToJsonElement(
            supabase.postgrest.rpc(function = function, parameters = parameters).data
        )
        return element as? JsonObject ?: error("CANON_EMPTY")
    }

    private suspend fun rpcObjects(function: String, parameters: JsonObject): List<JsonObject> {
        val element = Json.parseToJsonElement(
            supabase.postgrest.rpc(function = function, parameters = parameters).data
        )
        return when (element) {
            is JsonArray -> element.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(element)
            else -> emptyList()
        }
    }

    private fun JsonObject.primitive(key: String): String =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
}
