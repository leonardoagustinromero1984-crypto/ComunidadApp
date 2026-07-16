package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.decodeObject
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.runRpc
import com.comunidapp.app.data.repository.M05SupabaseRpcSupport.parseAsset
import com.comunidapp.app.domain.files.FileAsset
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileRetentionPolicy
import com.comunidapp.app.domain.files.FileRetentionRules
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseFileRetentionRepository : FileRetentionRepository {

    override suspend fun getRetentionPolicy(
        purpose: FileAssetPurpose
    ): AppResult<FileRetentionPolicy> =
        AppResult.Success(FileRetentionRules.defaultPolicy(purpose))

    override suspend fun canPhysicallyDelete(
        assetId: String,
        nowEpochMs: Long
    ): AppResult<Boolean> = runRpc {
        val root = decodeObject(
            rpc(
                "can_physically_delete_file_asset",
                buildJsonObject { put("p_asset_id", assetId) }
            )
        ) ?: error("CAN_PHYSICALLY_DELETE_EMPTY")
        (root["can_physically_delete"] as? JsonPrimitive)?.booleanOrNull
            ?: error("CAN_PHYSICALLY_DELETE_INVALID")
    }

    override suspend fun requestLegalHold(assetId: String): AppResult<FileAsset> =
        assetMutation("place_file_legal_hold", assetId)

    override suspend fun releaseLegalHold(assetId: String): AppResult<FileAsset> =
        assetMutation("release_file_legal_hold", assetId)

    private suspend fun assetMutation(function: String, assetId: String): AppResult<FileAsset> =
        runRpc {
            val root = decodeObject(
                rpc(function, buildJsonObject { put("p_asset_id", assetId) })
            ) ?: error("${function.uppercase()}_EMPTY")
            parseAsset(root)
        }

    private suspend fun rpc(function: String, parameters: JsonObject): JsonElement =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeAs()
}
