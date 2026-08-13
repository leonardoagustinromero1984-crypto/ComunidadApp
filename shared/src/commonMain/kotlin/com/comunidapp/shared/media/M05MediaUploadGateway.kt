package com.comunidapp.shared.media

import com.comunidapp.shared.poc.m08.model.FileRef
import com.comunidapp.shared.ui.ErrorSanitizer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Request M05 para LOST_FOUND_MEDIA / LOST_FOUND_CASE.
 */
data class M05MediaUploadRequest(
    val caseId: String,
    val actorUserId: String,
    val file: FileRef
)

data class M05PreparedUpload(
    val sessionId: String,
    val assetId: String,
    val physicalBucket: String,
    val storagePath: String
)

/**
 * Slice M05 reutilizable — create → upload → complete.
 * internal gateways no exportan SupabaseClient.
 */
internal interface M05MediaUploadGateway {
    suspend fun uploadLostFoundMedia(request: M05MediaUploadRequest): Result<String>
}

internal class SupabaseM05MediaUploadGateway(
    private val client: SupabaseClient,
    private val fileContentReader: FileContentReader
) : M05MediaUploadGateway {

    override suspend fun uploadLostFoundMedia(request: M05MediaUploadRequest): Result<String> {
        if (request.caseId.isBlank() || request.actorUserId.isBlank()) {
            return Result.failure(IllegalArgumentException("MEDIA_REQUEST_INVALID"))
        }
        val content = fileContentReader.read(request.file).getOrElse { return Result.failure(it) }
        M05LostFoundMediaRules.validate(content).getOrElse { return Result.failure(it) }
        val safeName = M05LostFoundMediaRules.sanitizeFilename(content.name)
            .getOrElse { return Result.failure(it) }

        var sessionId: String? = null
        return try {
            val prepared = createSession(
                actorUserId = request.actorUserId,
                caseId = request.caseId,
                originalFilename = content.name,
                safeFilename = safeName,
                mimeType = content.mimeType.trim().lowercase(),
                sizeBytes = content.sizeBytes
            ).getOrElse { return Result.failure(it) }
            sessionId = prepared.sessionId

            transitionUploading(prepared.sessionId).getOrElse {
                failSession(prepared.sessionId, "TRANSITION_FAILED")
                return Result.failure(it)
            }

            uploadBytes(prepared, content.bytes, content.mimeType.trim().lowercase()).getOrElse {
                failSession(prepared.sessionId, "UPLOAD_FAILED")
                return Result.failure(it)
            }

            updateProgress(prepared.sessionId, 100)
            completeSession(prepared.sessionId).getOrElse {
                return Result.failure(it)
            }
            Result.success(prepared.assetId)
        } catch (t: Throwable) {
            sessionId?.let { failSession(it, "UPLOAD_FAILED") }
            Result.failure(t)
        }
    }

    private suspend fun createSession(
        actorUserId: String,
        caseId: String,
        originalFilename: String,
        safeFilename: String,
        mimeType: String,
        sizeBytes: Long
    ): Result<M05PreparedUpload> = try {
        val element = client.postgrest.rpc(
            function = "create_file_upload_session",
            parameters = buildJsonObject {
                put("p_purpose", M05LostFoundMediaRules.PURPOSE)
                put("p_owner_kind", M05LostFoundMediaRules.OWNER_KIND)
                put("p_owner_user_id", actorUserId)
                put("p_owner_organization_id", JsonNull)
                put("p_visibility", M05LostFoundMediaRules.VISIBILITY)
                put("p_resource_type", M05LostFoundMediaRules.RESOURCE_TYPE)
                put("p_resource_id", caseId)
                put("p_original_filename", originalFilename)
                put("p_declared_mime_type", mimeType)
                put("p_size_bytes", sizeBytes)
                put("p_safe_filename", safeFilename)
                put("p_storage_path", JsonNull)
            }
        ).decodeAs<JsonObject>()
        val session = element["session"]?.jsonObject
            ?: return Result.failure(IllegalStateException("CREATE_UPLOAD_SESSION_EMPTY"))
        val version = element["version"]?.jsonObject
            ?: return Result.failure(IllegalStateException("CREATE_UPLOAD_VERSION_EMPTY"))
        val sessionId = session.string("id")
            ?: return Result.failure(IllegalStateException("CREATE_UPLOAD_SESSION_ID_EMPTY"))
        val assetId = session.string("asset_id")
            ?: return Result.failure(IllegalStateException("CREATE_UPLOAD_ASSET_EMPTY"))
        val bucket = version.string("storage_bucket")
            ?: return Result.failure(IllegalStateException("CREATE_UPLOAD_BUCKET_EMPTY"))
        if (bucket.equals("leover", ignoreCase = true)) {
            return Result.failure(IllegalStateException("LEGACY_BUCKET_DENIED"))
        }
        val path = version.string("storage_path")
            ?: return Result.failure(IllegalStateException("CREATE_UPLOAD_PATH_EMPTY"))
        Result.success(
            M05PreparedUpload(
                sessionId = sessionId,
                assetId = assetId,
                physicalBucket = bucket,
                storagePath = path
            )
        )
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private suspend fun transitionUploading(sessionId: String): Result<Unit> = try {
        client.postgrest.rpc(
            function = "transition_file_upload_session",
            parameters = buildJsonObject {
                put("p_session_id", sessionId)
                put("p_state", "UPLOADING")
            }
        )
        Result.success(Unit)
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private suspend fun uploadBytes(
        prepared: M05PreparedUpload,
        bytes: ByteArray,
        mimeType: String
    ): Result<Unit> = try {
        if (prepared.physicalBucket.isBlank() || prepared.storagePath.isBlank() || bytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("MEDIA_UPLOAD_VALIDATION"))
        }
        client.storage.from(prepared.physicalBucket).upload(prepared.storagePath, bytes) {
            contentType = ContentType.parse(mimeType)
            upsert = false
        }
        Result.success(Unit)
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private suspend fun updateProgress(sessionId: String, progress: Int) {
        runCatching {
            client.postgrest.rpc(
                function = "update_file_upload_progress",
                parameters = buildJsonObject {
                    put("p_session_id", sessionId)
                    put("p_progress", progress)
                }
            )
        }
    }

    private suspend fun completeSession(sessionId: String): Result<Unit> = try {
        client.postgrest.rpc(
            function = "complete_file_upload",
            parameters = buildJsonObject { put("p_session_id", sessionId) }
        )
        Result.success(Unit)
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private suspend fun failSession(sessionId: String, code: String) {
        runCatching {
            client.postgrest.rpc(
                function = "fail_file_upload",
                parameters = buildJsonObject {
                    put("p_session_id", sessionId)
                    put("p_failure_code", code)
                }
            )
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

internal class FakeM05MediaUploadGateway(
    var succeedWithAssetId: String? = "asset-test-1",
    var error: Throwable? = null,
    var calls: Int = 0,
    var lastRequest: M05MediaUploadRequest? = null
) : M05MediaUploadGateway {
    override suspend fun uploadLostFoundMedia(request: M05MediaUploadRequest): Result<String> {
        calls++
        lastRequest = request
        error?.let { return Result.failure(it) }
        val id = succeedWithAssetId
            ?: return Result.failure(IllegalStateException("MEDIA_UPLOAD_FAILED"))
        return Result.success(id)
    }
}

internal fun mapMediaThrowable(t: Throwable): String {
    val code = t.message.orEmpty()
    val raw = code.lowercase()
    return when {
        "MEDIA_FILE_EMPTY" in code || ("empty" in raw && "file" in raw) ->
            "El archivo de foto no es válido."
        "MEDIA_FILE_TOO_LARGE" in code || "too large" in raw ->
            "La foto supera el tamaño permitido."
        "MEDIA_MIME_REJECTED" in code || "mime" in raw ->
            "El formato de la foto no está permitido."
        "FILENAME_" in code ->
            "El nombre del archivo no es válido."
        "403" in raw || "rls" in raw || "permission" in raw || "forbidden" in raw ->
            "No tenés permiso para subir la foto."
        "401" in raw || "not authenticated" in raw ||
            ("session" in raw && "jwt" !in raw) ||
            ("jwt" in raw && "403" !in raw && "rls" !in raw) ->
            "Tu sesión no está disponible."
        "network" in raw || "timeout" in raw || "unable to resolve" in raw ->
            "Problema de conexión al subir la foto."
        else -> ErrorSanitizer.sanitize(t)
    }
}
