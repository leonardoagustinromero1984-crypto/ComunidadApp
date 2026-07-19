package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.decodeArray
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.decodeObject
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.runRpc
import com.comunidapp.app.data.repository.M05SupabaseRpcSupport.parseAsset
import com.comunidapp.app.data.repository.M05SupabaseRpcSupport.parseLink
import com.comunidapp.app.domain.files.FileAsset
import com.comunidapp.app.domain.files.FileAssetLink
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileRelationType
import com.comunidapp.app.domain.files.FileResourceRef
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseFileAssetRepository : FileAssetRepository {

    override suspend fun createDraftAsset(
        purpose: FileAssetPurpose,
        owner: FileAssetOwner,
        visibility: FileAssetVisibility,
        createdByUserId: String,
        nowEpochMs: Long
    ): AppResult<FileAsset> = runRpc {
        val element = rpc(
            "create_file_asset_draft",
            buildJsonObject {
                put("p_purpose", purpose.name)
                putOwner(owner)
                put("p_visibility", visibility.name)
            }
        )
        parseAsset(decodeObject(element) ?: error("CREATE_ASSET_EMPTY"))
    }

    override suspend fun getAsset(assetId: String): AppResult<FileAsset> = runRpc {
        val element = rpc(
            "get_file_asset",
            buildJsonObject { put("p_asset_id", assetId) }
        )
        parseAsset(decodeObject(element) ?: error("NOT_FOUND"))
    }

    override suspend fun listAssetsForResource(
        resource: FileResourceRef
    ): AppResult<List<FileAsset>> = runRpc {
        val element = rpc(
            "list_file_assets_for_resource",
            buildJsonObject {
                put("p_resource_type", resource.type.name)
                put("p_resource_id", resource.resourceId)
            }
        )
        decodeArray(element).mapNotNull { item ->
            decodeObject(item)?.get("asset")?.let(::decodeObject)?.let(::parseAsset)
        }
    }

    override suspend fun linkAsset(
        assetId: String,
        link: FileAssetLink
    ): AppResult<FileAssetLink> = runRpc {
        val element = rpc(
            "link_file_asset",
            buildJsonObject {
                put("p_asset_id", assetId)
                put("p_resource_type", link.resource.type.name)
                put("p_resource_id", link.resource.resourceId)
                put("p_relation_type", link.relationType.name)
                put("p_is_primary", link.isPrimary)
                put("p_sort_order", link.sortOrder ?: 0)
            }
        )
        parseLink(decodeObject(element) ?: error("LINK_ASSET_EMPTY"))
    }

    override suspend fun unlinkAsset(
        assetId: String,
        resource: FileResourceRef,
        relationType: FileRelationType
    ): AppResult<Unit> = runRpc {
        rpc(
            "unlink_file_asset",
            buildJsonObject {
                put("p_asset_id", assetId)
                put("p_resource_type", resource.type.name)
                put("p_resource_id", resource.resourceId)
                put("p_relation_type", relationType.name)
            }
        )
        Unit
    }

    override suspend fun markDeleted(assetId: String, nowEpochMs: Long): AppResult<FileAsset> =
        assetMutation("request_file_asset_delete", assetId)

    override suspend fun restoreAsset(assetId: String, nowEpochMs: Long): AppResult<FileAsset> =
        assetMutation("restore_file_asset", assetId)

    private suspend fun assetMutation(function: String, assetId: String): AppResult<FileAsset> =
        runRpc {
            val element = rpc(function, buildJsonObject { put("p_asset_id", assetId) })
            parseAsset(decodeObject(element) ?: error("${function.uppercase()}_EMPTY"))
        }

    private suspend fun rpc(function: String, parameters: kotlinx.serialization.json.JsonObject): JsonElement =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeAs()
}

internal fun kotlinx.serialization.json.JsonObjectBuilder.putOwner(owner: FileAssetOwner) {
    put("p_owner_kind", owner.kind.name)
    when (owner) {
        is FileAssetOwner.User -> {
            put("p_owner_user_id", owner.userId)
            put("p_owner_organization_id", JsonNull)
        }
        is FileAssetOwner.Organization -> {
            put("p_owner_user_id", JsonNull)
            put("p_owner_organization_id", owner.organizationId)
        }
        is FileAssetOwner.Platform -> {
            put("p_owner_user_id", JsonNull)
            put("p_owner_organization_id", JsonNull)
        }
    }
}
