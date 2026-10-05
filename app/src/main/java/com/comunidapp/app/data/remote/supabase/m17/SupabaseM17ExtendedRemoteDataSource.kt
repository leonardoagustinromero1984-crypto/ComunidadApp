package com.comunidapp.app.data.remote.supabase.m17

import com.comunidapp.app.data.model.M17CampaignTransparencyReport
import com.comunidapp.app.data.model.M17FundUsageItem
import com.comunidapp.app.data.model.M17InKindCategory
import com.comunidapp.app.data.model.M17InKindNeedStatus
import com.comunidapp.app.data.model.M17InKindPledge
import com.comunidapp.app.data.model.M17InKindPledgeStatus
import com.comunidapp.app.data.model.M17PublicInKindNeed
import com.comunidapp.app.data.model.M17PublicVolunteerOpportunity
import com.comunidapp.app.data.model.M17TransparencyMilestone
import com.comunidapp.app.data.model.M17VolunteerApplication
import com.comunidapp.app.data.model.M17VolunteerApplicationStatus
import com.comunidapp.app.data.model.M17VolunteerOpportunityStatus
import com.comunidapp.app.data.model.M17VolunteerOpportunityType
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.m17.MyGoodsPledge
import com.comunidapp.app.domain.organization.CanonicalHelpRow
import com.comunidapp.app.domain.organization.CanonicalPublicHelp
import com.comunidapp.app.domain.m17.MyVolunteerInterest
import kotlinx.serialization.json.JsonNull
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private fun parseTs(value: String?): Long =
    value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: System.currentTimeMillis()

private fun JsonElement?.asStringOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.string(key: String): String? = this[key].asStringOrNull()

private fun JsonObject.int(key: String, default: Int = 0): Int =
    (this[key] as? JsonPrimitive)?.intOrNull ?: default

private fun JsonObject.long(key: String, default: Long = 0L): Long =
    (this[key] as? JsonPrimitive)?.longOrNull
        ?: (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: default

private inline fun <reified T : Enum<T>> safeEnum(raw: String?, default: T): T =
    runCatching { enumValueOf<T>(raw.orEmpty()) }.getOrDefault(default)

fun JsonObject.toMyGoodsPledge(): MyGoodsPledge = MyGoodsPledge(
    needTitle = string("need_title").orEmpty(),
    organizationName = string("organization_name").orEmpty(),
    quantity = int("quantity"),
    unit = string("unit").orEmpty(),
    status = safeEnum(string("status"), M17InKindPledgeStatus.PLEDGED),
    createdAt = parseTs(string("created_at"))
)

fun JsonObject.toMyVolunteerInterest(): MyVolunteerInterest = MyVolunteerInterest(
    opportunityTitle = string("opportunity_title").orEmpty(),
    organizationName = string("organization_name").orEmpty(),
    status = safeEnum(string("status"), M17VolunteerApplicationStatus.SUBMITTED),
    createdAt = parseTs(string("created_at"))
)

fun JsonObject.toM17PublicInKindNeed(): M17PublicInKindNeed = M17PublicInKindNeed(
    id = string("id").orEmpty(),
    title = string("title").orEmpty(),
    description = string("description").orEmpty(),
    organizationDisplayName = string("organization_display_name").orEmpty(),
    category = safeEnum(string("category"), M17InKindCategory.OTHER),
    status = safeEnum(string("status"), M17InKindNeedStatus.PUBLISHED),
    quantityRequested = int("quantity_requested"),
    quantityPledged = int("quantity_pledged"),
    quantityDelivered = int("quantity_delivered"),
    quantityUnit = string("quantity_unit") ?: "unidades",
    coveragePercent = int("coverage_percent"),
    publicLocationText = string("public_location_text")
)

fun JsonObject.toM17PublicVolunteerOpportunity(): M17PublicVolunteerOpportunity =
    M17PublicVolunteerOpportunity(
        id = string("id").orEmpty(),
        title = string("title").orEmpty(),
        description = string("description").orEmpty(),
        organizationDisplayName = string("organization_display_name").orEmpty(),
        type = safeEnum(string("opportunity_type"), M17VolunteerOpportunityType.OTHER),
        status = safeEnum(string("status"), M17VolunteerOpportunityStatus.PUBLISHED),
        slotsNeeded = int("slots_needed"),
        slotsFilled = int("slots_filled"),
        publicLocationText = string("public_location_text"),
        scheduleHint = string("schedule_hint")
    )

fun JsonObject.toM17CampaignTransparencyReport(): M17CampaignTransparencyReport {
    val usageItems = this["usage_items"]?.jsonArray?.mapNotNull { elem ->
        val o = elem.jsonObject
        val id = o.string("id") ?: return@mapNotNull null
        M17FundUsageItem(
            id = id,
            label = o.string("label") ?: o.string("category") ?: "",
            amountMinor = o.long("amount_minor"),
            currency = o.string("currency") ?: "ARS",
            receiptRef = o.string("receipt_ref")
        )
    }.orEmpty()
    val milestones = this["milestones"]?.jsonArray?.mapNotNull { elem ->
        val m = elem.jsonObject
        val id = m.string("id") ?: return@mapNotNull null
        M17TransparencyMilestone(
            id = id,
            title = m.string("title").orEmpty(),
            description = m.string("description").orEmpty(),
            achievedAt = parseTs(m.string("completed_at") ?: m.string("created_at"))
        )
    }.orEmpty()
    return M17CampaignTransparencyReport(
        campaignId = string("campaign_id").orEmpty(),
        summaryText = string("summary").orEmpty(),
        usageItems = usageItems,
        milestones = milestones,
        finalOutcome = string("public_notes"),
        updatedAt = parseTs(string("updated_at"))
    )
}

fun JsonObject.toM17InKindPledgeFromRpc(needId: String, userId: String): M17InKindPledge =
    M17InKindPledge(
        id = string("id").orEmpty(),
        needId = string("need_id") ?: needId,
        quantity = int("quantity"),
        status = safeEnum(string("status"), M17InKindPledgeStatus.PLEDGED),
        userId = string("pledged_by") ?: userId,
        createdAt = parseTs(string("created_at"))
    )

fun JsonObject.toM17VolunteerApplicationFromRpc(opportunityId: String, userId: String): M17VolunteerApplication =
    M17VolunteerApplication(
        id = string("id").orEmpty(),
        opportunityId = string("opportunity_id") ?: opportunityId,
        userId = string("applicant_user_id") ?: userId,
        status = safeEnum(string("status"), M17VolunteerApplicationStatus.SUBMITTED),
        message = string("message"),
        createdAt = parseTs(string("created_at"))
    )

class SupabaseM17ExtendedRemoteDataSource {

    private suspend inline fun <reified T : Any> decodeOne(function: String, parameters: JsonObject): T =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeSingle()

    private suspend inline fun <reified T : Any> decodeList(function: String, parameters: JsonObject): List<T> =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeList()

    suspend fun listPublicInKindNeeds(
        query: String? = null,
        category: String? = null,
        organizationId: String? = null
    ): List<JsonObject> = runCatching {
        decodeList<JsonObject>(
            "m17_list_public_in_kind_needs",
            buildJsonObject {
                put("p_query", query)
                put("p_category", category)
                put("p_organization_id", organizationId)
            }
        )
    }.getOrElse { canonicalInKind(organizationId) }

    suspend fun getPublicInKindNeed(needId: String): JsonObject = runCatching {
        decodeOne<JsonObject>(
            "m17_get_public_in_kind_need",
            buildJsonObject { put("p_need_id", needId) }
        )
    }.getOrElse {
        rpcObject(
            CanonicalBackend.RPC_GET_IN_KIND_NEED,
            buildJsonObject { put("p_need_id", needId) }
        )
    }

    suspend fun listPublicVolunteerOpportunities(
        query: String? = null,
        type: String? = null,
        organizationId: String? = null
    ): List<JsonObject> = runCatching {
        decodeList<JsonObject>(
            "m17_list_public_volunteer_opportunities",
            buildJsonObject {
                put("p_query", query)
                put("p_type", type)
                put("p_organization_id", organizationId)
            }
        )
    }.getOrElse { canonicalVolunteer(organizationId) }

    private suspend fun canonicalInKind(organizationId: String?): List<JsonObject> =
        keepOrganization(
            organizationId,
            rpcObjects(
                CanonicalBackend.RPC_LIST_IN_KIND_NEEDS,
                organizationParam(organizationId)
            )
        )

    private suspend fun canonicalVolunteer(organizationId: String?): List<JsonObject> =
        keepOrganization(
            organizationId,
            rpcObjects(
                CanonicalBackend.RPC_LIST_VOLUNTEER_OPPORTUNITIES,
                organizationParam(organizationId)
            )
        )

    private fun organizationParam(organizationId: String?): JsonObject = buildJsonObject {
        if (organizationId.isNullOrBlank()) put("p_organization_id", JsonNull)
        else put("p_organization_id", organizationId)
    }

    private fun keepOrganization(organizationId: String?, rows: List<JsonObject>): List<JsonObject> {
        val visible = CanonicalPublicHelp.visible(
            organizationId,
            rows.map { row ->
                CanonicalHelpRow(
                    id = row.string("id").orEmpty(),
                    organizationId = row.string("organization_id")
                )
            }
        ).map { it.id }.toSet()
        return rows.filter { it.string("id").orEmpty() in visible }
    }

    suspend fun getPublicVolunteerOpportunity(opportunityId: String): JsonObject = runCatching {
        decodeOne<JsonObject>(
            "m17_get_public_volunteer_opportunity",
            buildJsonObject { put("p_opportunity_id", opportunityId) }
        )
    }.getOrElse {
        rpcObject(
            CanonicalBackend.RPC_GET_VOLUNTEER_OPPORTUNITY,
            buildJsonObject { put("p_opportunity_id", opportunityId) }
        )
    }

    suspend fun getPublicCampaignTransparency(campaignId: String): JsonObject = decodeOne(
        "m17_get_public_campaign_transparency",
        buildJsonObject { put("p_campaign_id", campaignId) }
    )

    suspend fun createInKindPledge(needId: String, quantity: Int, message: String?): JsonObject = runCatching {
        decodeOne<JsonObject>(
            "m17_create_in_kind_pledge",
            buildJsonObject {
                put("p_need_id", needId)
                put("p_quantity", quantity)
                put("p_public_message", message)
            }
        )
    }.getOrElse {
        rpcObject(
            CanonicalBackend.RPC_PLEDGE_IN_KIND_NEED,
            buildJsonObject {
                put("p_need_id", needId)
                put("p_quantity", quantity)
                put("p_message", message)
            }
        )
    }

    suspend fun cancelOwnInKindPledge(pledgeId: String): JsonObject = decodeOne(
        "m17_cancel_own_in_kind_pledge",
        buildJsonObject { put("p_pledge_id", pledgeId) }
    )

    suspend fun markInKindPledgeDelivered(pledgeId: String): JsonObject = runCatching {
        decodeOne<JsonObject>(
            "m17_mark_in_kind_pledge_delivered",
            buildJsonObject { put("p_pledge_id", pledgeId) }
        )
    }.getOrElse {
        rpcObject(
            CanonicalBackend.RPC_MARK_IN_KIND_PLEDGE_DELIVERED,
            buildJsonObject { put("p_pledge_id", pledgeId) }
        )
    }

    suspend fun submitVolunteerApplication(opportunityId: String, message: String?): JsonObject = runCatching {
        decodeOne<JsonObject>(
            "m17_submit_volunteer_application",
            buildJsonObject {
                put("p_opportunity_id", opportunityId)
                put("p_message", message)
            }
        )
    }.getOrElse {
        rpcObject(
            CanonicalBackend.RPC_APPLY_VOLUNTEER_OPPORTUNITY,
            buildJsonObject {
                put("p_opportunity_id", opportunityId)
                put("p_message", message)
            }
        )
    }

    suspend fun withdrawVolunteerApplication(applicationId: String): JsonObject = decodeOne(
        "m17_withdraw_volunteer_application",
        buildJsonObject { put("p_application_id", applicationId) }
    )

    suspend fun acceptVolunteerApplication(applicationId: String): JsonObject = runCatching {
        decodeOne<JsonObject>(
            "m17_accept_volunteer_application",
            buildJsonObject { put("p_application_id", applicationId) }
        )
    }.getOrElse {
        rpcObject(
            CanonicalBackend.RPC_ACCEPT_VOLUNTEER_APPLICATION,
            buildJsonObject { put("p_application_id", applicationId) }
        )
    }

    suspend fun listVolunteerApplicants(opportunityId: String): List<JsonObject> = rpcObjects(
        CanonicalBackend.RPC_LIST_VOLUNTEER_APPLICANTS,
        buildJsonObject { put("p_opportunity_id", opportunityId) }
    )

    suspend fun listInKindPledges(needId: String): List<JsonObject> = rpcObjects(
        CanonicalBackend.RPC_LIST_IN_KIND_PLEDGES,
        buildJsonObject { put("p_need_id", needId) }
    )

    suspend fun listMyInKindPledges(): List<JsonObject> = runCatching {
        parseJsonObjectList(
            supabase.postgrest.rpc(
                function = "m17_list_my_in_kind_pledges",
                parameters = buildJsonObject { }
            ).data
        )
    }.getOrElse {
        rpcObjects(CanonicalBackend.RPC_LIST_MY_IN_KIND_PLEDGES, buildJsonObject { })
    }

    suspend fun listMyVolunteerApplications(): List<JsonObject> = runCatching {
        parseJsonObjectList(
            supabase.postgrest.rpc(
                function = "m17_list_my_volunteer_applications",
                parameters = buildJsonObject { }
            ).data
        )
    }.getOrElse {
        rpcObjects(CanonicalBackend.RPC_LIST_MY_VOLUNTEER_APPLICATIONS, buildJsonObject { })
    }

    private suspend fun rpcObject(function: String, parameters: JsonObject): JsonObject {
        val element = Json.parseToJsonElement(
            supabase.postgrest.rpc(function = function, parameters = parameters).data
        )
        return element as? JsonObject ?: error("CANON_EMPTY")
    }

    private suspend fun rpcObjects(function: String, parameters: JsonObject): List<JsonObject> =
        parseJsonObjectList(
            supabase.postgrest.rpc(function = function, parameters = parameters).data
        )

    private fun parseJsonObjectList(raw: String): List<JsonObject> =
        when (val el = Json.parseToJsonElement(raw)) {
            is JsonArray -> el.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(el)
            else -> emptyList()
        }
}
