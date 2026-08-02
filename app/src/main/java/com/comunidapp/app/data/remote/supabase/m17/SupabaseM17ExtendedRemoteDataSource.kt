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
import io.github.jan.supabase.postgrest.postgrest
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
        needId = needId,
        quantity = int("quantity"),
        status = safeEnum(string("status"), M17InKindPledgeStatus.PLEDGED),
        userId = userId,
        createdAt = System.currentTimeMillis()
    )

fun JsonObject.toM17VolunteerApplicationFromRpc(opportunityId: String, userId: String): M17VolunteerApplication =
    M17VolunteerApplication(
        id = string("id").orEmpty(),
        opportunityId = opportunityId,
        userId = userId,
        status = safeEnum(string("status"), M17VolunteerApplicationStatus.SUBMITTED),
        createdAt = System.currentTimeMillis()
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
    ): List<JsonObject> = decodeList(
        "m17_list_public_in_kind_needs",
        buildJsonObject {
            put("p_query", query)
            put("p_category", category)
            put("p_organization_id", organizationId)
        }
    )

    suspend fun getPublicInKindNeed(needId: String): JsonObject = decodeOne(
        "m17_get_public_in_kind_need",
        buildJsonObject { put("p_need_id", needId) }
    )

    suspend fun listPublicVolunteerOpportunities(
        query: String? = null,
        type: String? = null,
        organizationId: String? = null
    ): List<JsonObject> = decodeList(
        "m17_list_public_volunteer_opportunities",
        buildJsonObject {
            put("p_query", query)
            put("p_type", type)
            put("p_organization_id", organizationId)
        }
    )

    suspend fun getPublicVolunteerOpportunity(opportunityId: String): JsonObject = decodeOne(
        "m17_get_public_volunteer_opportunity",
        buildJsonObject { put("p_opportunity_id", opportunityId) }
    )

    suspend fun getPublicCampaignTransparency(campaignId: String): JsonObject = decodeOne(
        "m17_get_public_campaign_transparency",
        buildJsonObject { put("p_campaign_id", campaignId) }
    )

    suspend fun createInKindPledge(needId: String, quantity: Int, message: String?): JsonObject = decodeOne(
        "m17_create_in_kind_pledge",
        buildJsonObject {
            put("p_need_id", needId)
            put("p_quantity", quantity)
            put("p_public_message", message)
        }
    )

    suspend fun cancelOwnInKindPledge(pledgeId: String): JsonObject = decodeOne(
        "m17_cancel_own_in_kind_pledge",
        buildJsonObject { put("p_pledge_id", pledgeId) }
    )

    suspend fun markInKindPledgeDelivered(pledgeId: String): JsonObject = decodeOne(
        "m17_mark_in_kind_pledge_delivered",
        buildJsonObject { put("p_pledge_id", pledgeId) }
    )

    suspend fun submitVolunteerApplication(opportunityId: String, message: String?): JsonObject = decodeOne(
        "m17_submit_volunteer_application",
        buildJsonObject {
            put("p_opportunity_id", opportunityId)
            put("p_message", message)
        }
    )

    suspend fun withdrawVolunteerApplication(applicationId: String): JsonObject = decodeOne(
        "m17_withdraw_volunteer_application",
        buildJsonObject { put("p_application_id", applicationId) }
    )

    suspend fun acceptVolunteerApplication(applicationId: String): JsonObject = decodeOne(
        "m17_accept_volunteer_application",
        buildJsonObject { put("p_application_id", applicationId) }
    )
}
