package com.comunidapp.app.data.remote.supabase.m17

import com.comunidapp.app.data.model.M17CampaignFinancialSummary
import com.comunidapp.app.data.model.M17CampaignGoal
import com.comunidapp.app.data.model.M17CampaignReference
import com.comunidapp.app.data.model.M17CampaignStatus
import com.comunidapp.app.data.model.M17CampaignType
import com.comunidapp.app.data.model.M17CampaignUpdate
import com.comunidapp.app.data.model.M17Contribution
import com.comunidapp.app.data.model.M17ContributionStatus
import com.comunidapp.app.data.model.M17DonationCampaign
import com.comunidapp.app.data.model.M17DonorVisibility
import com.comunidapp.app.data.model.M17PublicCampaign
import com.comunidapp.app.data.model.M17PublicContribution
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
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

private fun JsonElement?.asLongOrNull(): Long? =
    (this as? JsonPrimitive)?.longOrNull
        ?: (this as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

private fun JsonElement?.asIntOrNull(default: Int = 0): Int =
    (this as? JsonPrimitive)?.intOrNull ?: default

private fun JsonObject.string(key: String): String? = this[key].asStringOrNull()

private fun JsonObject.long(key: String, default: Long = 0L): Long = this[key].asLongOrNull() ?: default

private fun JsonObject.int(key: String, default: Int = 0): Int = this[key].asIntOrNull(default)

private fun parseReference(obj: JsonObject?): M17CampaignReference {
    val ref = obj ?: return M17CampaignReference()
    return M17CampaignReference(
        petId = ref.string("pet_id"),
        petPublicName = ref.string("pet_public_name"),
        shelterProfileId = ref.string("shelter_profile_id"),
        shelterPublicName = ref.string("shelter_public_name"),
        needDescription = ref.string("need_description"),
        publicLocationText = ref.string("public_location_text")
    )
}

private fun parseUpdates(arr: JsonArray?): List<M17CampaignUpdate> =
    arr?.mapNotNull { elem ->
        val u = elem.jsonObject
        val id = u.string("id") ?: return@mapNotNull null
        val msg = u.string("message") ?: return@mapNotNull null
        M17CampaignUpdate(id = id, message = msg, createdAt = parseTs(u.string("created_at")))
    }.orEmpty()

private fun parseGallery(arr: JsonElement?): List<String> =
    arr?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.filter { it.isNotBlank() }.orEmpty()

private fun safeEnumCampaignStatus(raw: String?): M17CampaignStatus =
    runCatching { M17CampaignStatus.valueOf(raw.orEmpty()) }
        .getOrDefault(M17CampaignStatus.DRAFT)

private fun safeEnumCampaignType(raw: String?): M17CampaignType =
    runCatching { M17CampaignType.valueOf(raw.orEmpty()) }
        .getOrDefault(M17CampaignType.GENERAL_SUPPORT)

fun JsonObject.toM17DonationCampaign(): M17DonationCampaign {
    val ref = parseReference(this["reference"]?.jsonObject)
    return M17DonationCampaign(
        id = string("id").orEmpty(),
        organizationId = string("organization_id").orEmpty(),
        organizationDisplayName = string("organization_display_name").orEmpty(),
        title = string("title").orEmpty(),
        description = string("description").orEmpty(),
        campaignType = safeEnumCampaignType(string("campaign_type") ?: string("type")),
        status = safeEnumCampaignStatus(string("status") ?: string("campaign_status")),
        goal = M17CampaignGoal(
            amountMinor = long("goal_amount_minor"),
            currency = string("currency") ?: "ARS"
        ),
        reference = ref,
        coverImageRef = string("cover_image_ref"),
        galleryImageRefs = parseGallery(this["gallery_image_refs"]),
        publicUpdates = parseUpdates(this["public_updates"]?.jsonArray),
        internalNotes = string("internal_notes"),
        moderationStatus = string("moderation_status"),
        startsAt = parseTs(string("starts_at")),
        endsAt = string("ends_at")?.let { parseTs(it) },
        createdBy = string("created_by").orEmpty(),
        createdAt = parseTs(string("created_at")),
        updatedAt = parseTs(string("updated_at"))
    )
}

fun JsonObject.toM17PublicCampaign(): M17PublicCampaign {
    val ref = parseReference(this["reference"]?.jsonObject)
    return M17PublicCampaign(
        id = string("id").orEmpty(),
        title = string("title").orEmpty(),
        description = string("description").orEmpty(),
        organizationDisplayName = string("organization_display_name").orEmpty(),
        campaignType = safeEnumCampaignType(string("campaign_type")),
        status = safeEnumCampaignStatus(string("status")),
        goalAmountMinor = long("goal_amount_minor"),
        currency = string("currency") ?: "ARS",
        confirmedAmountMinor = long("confirmed_amount_minor"),
        progressPercent = int("progress_percent"),
        reference = ref,
        coverImageRef = string("cover_image_ref"),
        publicUpdates = parseUpdates(this["public_updates"]?.jsonArray),
        startsAt = parseTs(string("starts_at")),
        endsAt = string("ends_at")?.let { parseTs(it) },
        confirmedContributionCount = int("confirmed_contribution_count")
    )
}

fun JsonObject.toM17PublicContribution(): M17PublicContribution? {
    val id = string("id") ?: return null
    return M17PublicContribution(
        id = id,
        amountMinor = long("amount_minor"),
        currency = string("currency") ?: "ARS",
        donorLabel = string("donor_label") ?: "Donante",
        message = string("message"),
        createdAt = parseTs(string("created_at"))
    )
}

fun JsonObject.toM17CampaignFinancialSummary(): M17CampaignFinancialSummary =
    M17CampaignFinancialSummary(
        confirmedAmountMinor = long("confirmed_amount_minor"),
        currency = string("currency") ?: "ARS",
        goalAmountMinor = long("goal_amount_minor"),
        confirmedContributionCount = int("confirmed_contribution_count"),
        pendingContributionCount = int("pending_contribution_count"),
        progressPercent = int("progress_percent")
    )

fun JsonObject.toM17ContributionInternal(): M17Contribution {
    val visibilityRaw = string("visibility") ?: "PUBLIC"
    val visibility = runCatching { M17DonorVisibility.valueOf(visibilityRaw) }
        .getOrDefault(M17DonorVisibility.PUBLIC)
    val statusRaw = string("status") ?: "PENDING"
    val status = runCatching { M17ContributionStatus.valueOf(statusRaw) }
        .getOrDefault(M17ContributionStatus.PENDING)
    return M17Contribution(
        id = string("id").orEmpty(),
        campaignId = string("campaign_id").orEmpty(),
        amountMinor = long("amount_minor"),
        currency = string("currency") ?: "ARS",
        status = status,
        visibility = visibility,
        donorDisplayName = string("donor_display_name"),
        message = string("public_message") ?: string("message"),
        providerReference = string("provider_reference"),
        createdAt = parseTs(string("created_at"))
    )
}

class SupabaseM17RemoteDataSource {

    private suspend inline fun <reified T : Any> decodeOne(function: String, parameters: JsonObject): T =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeSingle()

    private suspend inline fun <reified T : Any> decodeList(function: String, parameters: JsonObject): List<T> =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeList()

    suspend fun listPublic(params: JsonObject): List<JsonObject> =
        decodeList("m17_list_public_campaigns", params)

    suspend fun getPublic(campaignId: String): JsonObject = decodeOne(
        "m17_get_public_campaign",
        buildJsonObject { put("p_campaign_id", campaignId) }
    )

    suspend fun listPublicContributions(campaignId: String): List<JsonObject> = decodeList(
        "m17_list_public_contributions",
        buildJsonObject { put("p_campaign_id", campaignId) }
    )

    suspend fun getFinancialSummary(campaignId: String): JsonObject = decodeOne(
        "m17_get_financial_summary",
        buildJsonObject { put("p_campaign_id", campaignId) }
    )

    suspend fun getCampaign(campaignId: String): JsonObject = decodeOne(
        "m17_get_campaign",
        buildJsonObject { put("p_campaign_id", campaignId) }
    )

    suspend fun listOrgCampaigns(organizationId: String): List<JsonObject> = decodeList(
        "m17_list_org_campaigns",
        buildJsonObject { put("p_organization_id", organizationId) }
    )

    suspend fun isOrganizationEligible(organizationId: String): Boolean = decodeOne(
        "m17_is_organization_eligible",
        buildJsonObject { put("p_organization_id", organizationId) }
    )

    suspend fun createCampaign(params: JsonObject): JsonObject = decodeOne("m17_create_campaign", params)

    suspend fun updateCampaignDetails(params: JsonObject): JsonObject =
        decodeOne("m17_update_campaign_details", params)

    suspend fun updateCampaignGoal(params: JsonObject): JsonObject =
        decodeOne("m17_update_campaign_goal", params)

    suspend fun updateCampaignImages(params: JsonObject): JsonObject =
        decodeOne("m17_update_campaign_images", params)

    suspend fun transitionCampaign(campaignId: String, targetStatus: String): JsonObject = decodeOne(
        "m17_transition_campaign",
        buildJsonObject {
            put("p_campaign_id", campaignId)
            put("p_target_status", targetStatus)
        }
    )

    suspend fun addCampaignUpdate(campaignId: String, message: String): JsonObject = decodeOne(
        "m17_add_campaign_update",
        buildJsonObject {
            put("p_campaign_id", campaignId)
            put("p_message", message)
        }
    )
}
