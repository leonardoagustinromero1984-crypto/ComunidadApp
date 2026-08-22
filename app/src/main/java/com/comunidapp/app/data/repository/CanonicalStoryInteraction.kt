package com.comunidapp.app.data.repository

import com.comunidapp.app.core.logging.AppLog
import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class StoryViewerPerson(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String? = null,
    val username: String? = null,
    @SerialName("seen_at") val seenAt: String? = null
)

@Serializable
data class StoryCommentRow(
    val id: String,
    @SerialName("author_user_id") val authorUserId: String,
    @SerialName("display_name") val displayName: String? = null,
    val username: String? = null,
    val body: String,
    @SerialName("created_at") val createdAt: String? = null
)

object CanonicalStoryInteraction {
    suspend fun recordView(storyId: String) {
        runCatching {
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_RECORD_STORY_VIEW,
                parameters = buildJsonObject { put("p_story_id", storyId) }
            )
        }.onFailure { AppLog.warning("StoryView", "record failed", it) }
    }

    suspend fun listViewers(storyId: String): List<StoryViewerPerson> =
        runCatching {
            val element: JsonElement = supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_LIST_STORY_VIEWERS,
                parameters = buildJsonObject { put("p_story_id", storyId) }
            ).decodeAs()
            M08RpcDecoding.decodeRows<StoryViewerPerson>(element)
        }.onFailure { AppLog.warning("StoryView", "list failed", it) }.getOrDefault(emptyList())

    suspend fun toggleHeart(storyId: String): Boolean =
        runCatching {
            val liked: Boolean = supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_REACT_STORY,
                parameters = buildJsonObject {
                    put("p_story_id", storyId)
                    put("p_kind", "HEART")
                }
            ).decodeAs()
            liked
        }.onFailure { AppLog.warning("StoryReact", "toggle failed", it) }.getOrDefault(false)

    suspend fun comment(storyId: String, body: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_COMMENT_STORY,
            parameters = buildJsonObject {
                put("p_story_id", storyId)
                put("p_body", body)
            }
        )
        Unit
    }.onFailure { AppLog.warning("StoryComment", "send failed", it) }

    suspend fun listComments(storyId: String): List<StoryCommentRow> =
        runCatching {
            val element: JsonElement = supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_LIST_STORY_COMMENTS,
                parameters = buildJsonObject { put("p_story_id", storyId) }
            ).decodeAs()
            M08RpcDecoding.decodeRows<StoryCommentRow>(element)
        }.onFailure { AppLog.warning("StoryComment", "list failed", it) }.getOrDefault(emptyList())
}
