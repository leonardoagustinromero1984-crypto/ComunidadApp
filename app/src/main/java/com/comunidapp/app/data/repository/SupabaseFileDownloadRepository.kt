package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.decodeObject
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.failureFromThrowable
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.requireString
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.runRpc
import com.comunidapp.app.data.repository.M05SupabaseRpcSupport.parseAsset
import com.comunidapp.app.domain.files.FileAccessRequest
import com.comunidapp.app.domain.files.FileAsset
import com.comunidapp.app.domain.files.FilePurposePolicy
import com.comunidapp.app.domain.files.FileSignedAccess
import com.comunidapp.app.domain.files.FileSignedTtlClass
import com.comunidapp.app.domain.files.authorization.FileAccessDecision
import com.comunidapp.app.domain.files.authorization.FileAuthContext
import com.comunidapp.app.domain.files.authorization.FileAuthorization
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds

class SupabaseFileDownloadRepository(
    private val assets: FileAssetRepository = SupabaseFileAssetRepository()
) : FileDownloadRepository {

    override suspend fun requestAccess(
        request: FileAccessRequest,
        context: FileAuthContext
    ): AppResult<FileAccessDecision> =
        when (val result = assets.getAsset(request.assetId)) {
            is AppResult.Success ->
                AppResult.Success(FileAuthorization.canRead(context, result.data))
            is AppResult.Failure -> result
        }

    override suspend fun resolvePublicAsset(assetId: String): AppResult<FileAsset> = runRpc {
        val root = decodeObject(
            rpc(
                "resolve_public_file_asset",
                buildJsonObject { put("p_asset_id", assetId) }
            )
        ) ?: error("NOT_FOUND")
        parseAsset(root["asset"]?.let(::decodeObject) ?: error("NOT_FOUND"))
    }

    override suspend fun requestSignedUrl(
        request: FileAccessRequest,
        context: FileAuthContext,
        nowEpochMs: Long
    ): AppResult<FileSignedAccess> {
        val asset = when (val result = assets.getAsset(request.assetId)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        val decision = FileAuthorization.canRead(context, asset)
        if (decision != FileAccessDecision.ALLOWED) {
            return failureFromThrowable(IllegalStateException("FORBIDDEN_${decision.name}"))
        }
        return runRpc {
            val location = if (request.ttlClass == FileSignedTtlClass.PUBLIC_RESOLUTION) {
                decodeObject(
                    rpc(
                        "resolve_public_file_asset",
                        buildJsonObject { put("p_asset_id", request.assetId) }
                    )
                ) ?: error("NOT_PUBLICLY_RESOLVABLE")
            } else {
                decodeObject(
                    rpc(
                        "request_file_signed_url",
                        buildJsonObject {
                            put("p_asset_id", request.assetId)
                            put("p_ttl_class", request.ttlClass.name)
                        }
                    )
                ) ?: error("SIGNED_URL_EMPTY")
            }
            val bucket = location.requireString("bucket")
            if (bucket.equals("leover", ignoreCase = true) &&
                FilePurposePolicy.isSensitive(asset.purpose)
            ) {
                error("SENSITIVE_LEGACY_BUCKET_DENIED")
            }
            val path = location.requireString("path")
            val expiresSeconds = (location["expires_in_seconds"] as? JsonPrimitive)
                ?.intOrNull
                ?: if (request.ttlClass == FileSignedTtlClass.PUBLIC_RESOLUTION) 300 else 600
            val temporaryUrl = supabase.storage.from(bucket)
                .createSignedUrl(path = path, expiresIn = expiresSeconds.seconds)
            // M07: note signed URL issuance without recording URL or full path.
            runCatching {
                val cid = com.comunidapp.app.domain.observability.ObservabilityInstrumentation.correlationOrNew()
                supabase.postgrest.rpc(
                    "m07_client_note_data_access",
                    buildJsonObject {
                        put("p_event_key", "m05.signed_url.issued")
                        put("p_correlation_id", cid)
                        put("p_result", "SUCCESS")
                        put("p_resource_id", request.assetId)
                        put(
                            "p_metadata",
                            buildJsonObject {
                                put("result", "SUCCESS")
                                put("resource_id", request.assetId)
                                put("file_type", asset.purpose.name)
                            }
                        )
                    }
                )
            }
            FileSignedAccess(
                assetId = asset.id,
                temporaryUrl = temporaryUrl,
                expiresAtEpochMs = nowEpochMs + expiresSeconds * 1000L,
                ttlClass = request.ttlClass
            )
        }
    }

    private suspend fun rpc(function: String, parameters: JsonObject): JsonElement =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeAs()
}
