package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Canonical social saves via security-definer RPCs (1068).
 * Other platform surfaces stay on the existing mock until those modules are cut over.
 */
class CanonicalPlatformRepository(
    private val fallback: PlatformRepository = MockPlatformRepository()
) : PlatformRepository by fallback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val savedIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        scope.launch { refreshSavedIds() }
    }

    override fun observeSavedPostIds(userId: String): StateFlow<Set<String>> = savedIds.asStateFlow()

    override suspend fun toggleSavePost(postId: String, userId: String): Result<Boolean> = runCatching {
        val saved: Boolean = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_TOGGLE_SAVE_SOCIAL_POST,
            parameters = buildJsonObject { put("p_post_id", postId) }
        ).decodeAs()
        savedIds.value = if (saved) savedIds.value + postId else savedIds.value - postId
        saved
    }

    private suspend fun refreshSavedIds() {
        val element = supabase.postgrest.rpc(CanonicalBackend.RPC_LIST_SAVED_SOCIAL_POST_IDS).decodeAs<kotlinx.serialization.json.JsonElement>()
        val ids = when (element) {
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            else -> M08RpcDecoding.decodeRows<JsonPrimitive>(element).mapNotNull { it.contentOrNull }
        }.toSet()
        savedIds.value = ids
    }
}
