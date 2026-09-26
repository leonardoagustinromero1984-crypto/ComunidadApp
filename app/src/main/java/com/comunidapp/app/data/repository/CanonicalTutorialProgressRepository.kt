package com.comunidapp.app.data.repository

import com.comunidapp.app.data.local.Onb02Store
import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.onboarding.onb02.Onb02RemoteKeys
import com.comunidapp.app.domain.onboarding.onb02.TutorialId
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class TutorialProgressRow(
    @SerialName("tutorial_key") val tutorialKey: String,
    val version: String? = null,
    val state: String? = null
)

object CanonicalTutorialProgressRepository {

    suspend fun hydrate(store: Onb02Store, userId: String): Result<Boolean> = runCatching {
        var flowCompleted = false
        val element: JsonElement = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_LIST_TUTORIAL_PROGRESS
        ).decodeAs()
        M08RpcDecoding.decodeRows<TutorialProgressRow>(element).forEach { row ->
            val state = row.state.orEmpty().uppercase()
            if (row.tutorialKey == Onb02RemoteKeys.FLOW && state == "COMPLETED") {
                store.markCompleted(userId)
                store.markSelectionConfirmed(userId)
                flowCompleted = true
                return@forEach
            }
            val id = TutorialId.fromKey(row.tutorialKey) ?: return@forEach
            if (row.version != id.version.toString()) return@forEach
            store.applyRemoteProgress(
                userId = userId,
                id = id,
                viewed = state in setOf("VIEWED", "COMPLETED", "SKIPPED"),
                skipped = state == "SKIPPED",
                completed = state == "COMPLETED"
            )
        }
        flowCompleted
    }

    suspend fun upsert(tutorial: TutorialId, state: String): Result<Unit> =
        upsertRaw(tutorial.key, tutorial.version.toString(), state)

    suspend fun upsertFlowCompleted(): Result<Unit> =
        upsertRaw(Onb02RemoteKeys.FLOW, Onb02RemoteKeys.FLOW_VERSION, "COMPLETED")

    private suspend fun upsertRaw(key: String, version: String, state: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_UPSERT_TUTORIAL_PROGRESS,
            parameters = buildJsonObject {
                put("p_tutorial_key", key)
                put("p_version", version)
                put("p_state", state)
            }
        )
    }
}
