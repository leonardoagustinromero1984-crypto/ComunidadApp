package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.decodeObject
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.failureFromThrowable
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.runRpc
import com.comunidapp.app.data.repository.M05SupabaseRpcSupport.parseSession
import com.comunidapp.app.domain.files.FileNameSanitizer
import com.comunidapp.app.domain.files.FileUploadRequest
import com.comunidapp.app.domain.files.FileUploadSession
import com.comunidapp.app.domain.files.FileUploadSessionState
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseFileUploadRepository : FileUploadRepository {

    override suspend fun createUploadSession(
        request: FileUploadRequest,
        createdByUserId: String,
        nowEpochMs: Long,
        clockExpiresAtEpochMs: Long?
    ): AppResult<FileUploadSession> {
        val safeFilename = FileNameSanitizer.sanitize(request.originalFilename)
            .getOrElse { return failureFromThrowable(it) }
        return runRpc {
            val element = rpc(
                "create_file_upload_session",
                buildJsonObject {
                    put("p_purpose", request.purpose.name)
                    putOwner(request.owner)
                    put("p_visibility", request.requestedVisibility.name)
                    putNullable("p_resource_type", request.resourceRef?.type?.name)
                    putNullable("p_resource_id", request.resourceRef?.resourceId)
                    put("p_original_filename", request.originalFilename)
                    putNullable("p_declared_mime_type", request.declaredMimeType)
                    put("p_size_bytes", request.sizeBytes)
                    put("p_safe_filename", safeFilename)
                    put("p_storage_path", JsonNull)
                }
            )
            val root = decodeObject(element) ?: error("CREATE_UPLOAD_EMPTY")
            parseSession(root["session"]?.let(::decodeObject) ?: error("CREATE_UPLOAD_SESSION_EMPTY"))
        }
    }

    override suspend fun validateUpload(sessionId: String): AppResult<FileUploadSession> =
        transition(sessionId, FileUploadSessionState.READY_TO_UPLOAD)

    override suspend fun startUpload(sessionId: String): AppResult<FileUploadSession> =
        transition(sessionId, FileUploadSessionState.UPLOADING)

    override suspend fun updateProgress(
        sessionId: String,
        progressPercent: Int
    ): AppResult<FileUploadSession> = runRpc {
        parseSession(
            decodeObject(
                rpc(
                    "update_file_upload_progress",
                    buildJsonObject {
                        put("p_session_id", sessionId)
                        put("p_progress", progressPercent)
                    }
                )
            ) ?: error("UPDATE_UPLOAD_EMPTY")
        )
    }

    override suspend fun completeUpload(
        sessionId: String,
        nowEpochMs: Long
    ): AppResult<FileUploadSession> = runRpc {
        val root = decodeObject(
            rpc(
                "complete_file_upload",
                buildJsonObject { put("p_session_id", sessionId) }
            )
        ) ?: error("COMPLETE_UPLOAD_EMPTY")
        parseSession(root["session"]?.let(::decodeObject) ?: error("COMPLETE_UPLOAD_SESSION_EMPTY"))
    }

    override suspend fun failUpload(
        sessionId: String,
        failureCode: String
    ): AppResult<FileUploadSession> = sessionMutation(
        "fail_file_upload",
        buildJsonObject {
            put("p_session_id", sessionId)
            put("p_failure_code", failureCode)
        }
    )

    override suspend fun cancelUpload(
        sessionId: String,
        nowEpochMs: Long
    ): AppResult<FileUploadSession> = sessionMutation(
        "cancel_file_upload",
        buildJsonObject { put("p_session_id", sessionId) }
    )

    private suspend fun transition(
        sessionId: String,
        state: FileUploadSessionState
    ): AppResult<FileUploadSession> = sessionMutation(
        "transition_file_upload_session",
        buildJsonObject {
            put("p_session_id", sessionId)
            put("p_state", state.name)
        }
    )

    private suspend fun sessionMutation(
        function: String,
        parameters: JsonObject
    ): AppResult<FileUploadSession> = runRpc {
        parseSession(decodeObject(rpc(function, parameters)) ?: error("${function.uppercase()}_EMPTY"))
    }

    private suspend fun rpc(function: String, parameters: JsonObject): JsonElement =
        supabase.postgrest.rpc(function = function, parameters = parameters).decodeAs()
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: String?) {
    if (value == null) put(key, JsonNull) else put(key, value)
}
