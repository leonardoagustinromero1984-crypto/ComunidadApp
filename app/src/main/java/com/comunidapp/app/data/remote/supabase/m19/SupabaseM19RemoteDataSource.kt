package com.comunidapp.app.data.remote.supabase.m19

import com.comunidapp.app.data.model.M19Comment
import com.comunidapp.app.data.model.M19EngagementSummary
import com.comunidapp.app.data.model.M19Post
import com.comunidapp.app.data.model.M19PostStatus
import com.comunidapp.app.data.model.M19PublicComment
import com.comunidapp.app.data.model.M19PublicPost
import com.comunidapp.app.data.model.M19Reaction
import com.comunidapp.app.data.model.M19ReactionType
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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

private fun JsonElement?.asBooleanOrNull(default: Boolean = false): Boolean =
    when (val p = this as? JsonPrimitive) {
        null -> default
        else -> when (p.contentOrNull?.lowercase()) {
            "true", "t", "1" -> true
            "false", "f", "0" -> false
            else -> default
        }
    }

private fun JsonObject.string(key: String): String? = this[key].asStringOrNull()

private fun JsonObject.int(key: String, default: Int = 0): Int = this[key].asIntOrNull(default)

private fun safeEnumPostStatus(raw: String?): M19PostStatus =
    runCatching { M19PostStatus.valueOf(raw.orEmpty()) }
        .getOrDefault(M19PostStatus.DRAFT)

private fun safeEnumReactionType(raw: String?): M19ReactionType =
    runCatching { M19ReactionType.valueOf(raw.orEmpty()) }
        .getOrDefault(M19ReactionType.LIKE)

fun JsonObject.toM19Post(): M19Post = M19Post(
    id = string("id").orEmpty(),
    organizationId = string("organization_id").orEmpty(),
    organizationDisplayName = string("organization_display_name").orEmpty(),
    authorUserId = string("author_user_id").orEmpty(),
    authorDisplayName = string("author_display_name").orEmpty(),
    title = string("title").orEmpty(),
    content = string("content").orEmpty(),
    status = safeEnumPostStatus(string("status") ?: string("post_status")),
    coverImageRef = string("cover_image_ref"),
    moderationStatus = string("moderation_status"),
    publishedAt = string("published_at")?.let { parseTs(it) },
    createdBy = string("created_by").orEmpty(),
    createdAt = parseTs(string("created_at")),
    updatedAt = parseTs(string("updated_at"))
)

fun JsonObject.toM19PublicPost(): M19PublicPost = M19PublicPost(
    id = string("id").orEmpty(),
    title = string("title").orEmpty(),
    content = string("content").orEmpty(),
    organizationDisplayName = string("organization_display_name").orEmpty(),
    authorDisplayName = string("author_display_name").orEmpty(),
    status = safeEnumPostStatus(string("status")),
    coverImageRef = string("cover_image_ref"),
    likeCount = int("like_count"),
    loveCount = int("love_count"),
    supportCount = int("support_count"),
    celebrateCount = int("celebrate_count"),
    commentCount = int("comment_count"),
    publishedAt = string("published_at")?.let { parseTs(it) },
    createdAt = parseTs(string("created_at"))
)

fun JsonObject.toM19PublicComment(): M19PublicComment = M19PublicComment(
    id = string("id").orEmpty(),
    postId = string("post_id").orEmpty(),
    authorDisplayName = string("author_display_name").orEmpty(),
    content = string("content").orEmpty(),
    createdAt = parseTs(string("created_at")),
    updatedAt = parseTs(string("updated_at") ?: string("created_at"))
)

fun JsonObject.toM19Comment(): M19Comment = M19Comment(
    id = string("id").orEmpty(),
    postId = string("post_id").orEmpty(),
    userId = string("user_id").orEmpty(),
    authorDisplayName = string("author_display_name").orEmpty(),
    content = string("content").orEmpty(),
    hidden = boolean("hidden"),
    createdAt = parseTs(string("created_at"))
)

private fun JsonObject.boolean(key: String, default: Boolean = false): Boolean =
    this[key].asBooleanOrNull(default)

fun JsonObject.toM19Reaction(): M19Reaction = M19Reaction(
    id = string("id").orEmpty(),
    postId = string("post_id").orEmpty(),
    userId = string("user_id").orEmpty(),
    reactionType = safeEnumReactionType(string("reaction_type")),
    createdAt = parseTs(string("created_at"))
)

fun JsonObject.toM19EngagementSummary(): M19EngagementSummary = M19EngagementSummary(
    likeCount = int("like_count"),
    loveCount = int("love_count"),
    supportCount = int("support_count"),
    celebrateCount = int("celebrate_count"),
    commentCount = int("comment_count")
)

class SupabaseM19RemoteDataSource {

    private suspend inline fun <reified T : Any> decodeOne(function: String, parameters: JsonObject): T =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeSingle()

    private suspend inline fun <reified T : Any> decodeList(function: String, parameters: JsonObject): List<T> =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeList()

    suspend fun listPublicFeed(params: JsonObject): List<JsonObject> =
        decodeList("m19_list_public_feed", params)

    suspend fun getPublicPost(postId: String): JsonObject = decodeOne(
        "m19_get_public_post",
        buildJsonObject { put("p_post_id", postId) }
    )

    suspend fun getPost(postId: String): JsonObject = decodeOne(
        "m19_get_post",
        buildJsonObject { put("p_post_id", postId) }
    )

    suspend fun listOrgPosts(organizationId: String): List<JsonObject> = decodeList(
        "m19_list_org_posts",
        buildJsonObject { put("p_organization_id", organizationId) }
    )

    suspend fun isOrganizationEligible(organizationId: String): Boolean = decodeOne(
        "m19_is_organization_eligible",
        buildJsonObject { put("p_organization_id", organizationId) }
    )

    suspend fun createPost(params: JsonObject): JsonObject = decodeOne("m19_create_post", params)

    suspend fun updatePost(params: JsonObject): JsonObject = decodeOne("m19_update_post", params)

    suspend fun transitionPost(postId: String, targetStatus: String): JsonObject = decodeOne(
        "m19_transition_post",
        buildJsonObject {
            put("p_post_id", postId)
            put("p_target_status", targetStatus)
        }
    )

    suspend fun listPublicComments(postId: String): List<JsonObject> = decodeList(
        "m19_list_public_comments",
        buildJsonObject { put("p_post_id", postId) }
    )

    suspend fun addComment(postId: String, content: String): JsonObject = decodeOne(
        "m19_add_comment",
        buildJsonObject {
            put("p_post_id", postId)
            put("p_content", content)
        }
    )

    suspend fun addReaction(postId: String, reactionType: String): JsonObject = decodeOne(
        "m19_add_reaction",
        buildJsonObject {
            put("p_post_id", postId)
            put("p_reaction_type", reactionType)
        }
    )

    suspend fun removeReaction(postId: String) {
        decodeOne<JsonObject>(
            "m19_remove_reaction",
            buildJsonObject { put("p_post_id", postId) }
        )
    }

    suspend fun getMyReaction(postId: String): JsonObject? = runCatching {
        decodeOne<JsonObject>(
            "m19_get_my_reaction",
            buildJsonObject { put("p_post_id", postId) }
        )
    }.getOrNull()

    suspend fun getEngagementSummary(postId: String): JsonObject = decodeOne(
        "m19_get_engagement_summary",
        buildJsonObject { put("p_post_id", postId) }
    )
}
