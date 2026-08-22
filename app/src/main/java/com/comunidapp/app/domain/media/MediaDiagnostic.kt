package com.comunidapp.app.domain.media

import com.comunidapp.app.BuildConfig
import com.comunidapp.app.core.logging.AppLog
import com.comunidapp.app.core.result.AppError

class MediaIngestException(val code: String, cause: Throwable? = null) : Exception(code, cause)

/**
 * Short STAGING diagnostic codes shown under the friendly media error.
 * Never attach a stack trace to the user message.
 */
object MediaDiagnostic {
    const val URI = "MEDIA-URI-01"
    const val MIME = "MEDIA-MIME-01"
    const val DECODE = "MEDIA-DECODE-01"
    const val EXIF = "MEDIA-EXIF-01"
    const val CROP = "MEDIA-CROP-01"
    const val ENCODE = "MEDIA-ENCODE-01"
    const val SIZE = "MEDIA-SIZE-01"
    const val UPLOAD = "MEDIA-UPLOAD-01"
    const val DB = "MEDIA-DB-01"
    const val DB_REGISTER = "MEDIA-DB-REGISTER"
    const val DB_PERSON = "MEDIA-DB-PERSON"
    const val DB_RLS = "MEDIA-DB-RLS"
    const val DB_RPC = "MEDIA-DB-RPC"
    const val DB_PARSE = "MEDIA-DB-PARSE"
    const val RENDER = "MEDIA-RENDER-01"

    private const val TAG = "MediaPipeline"

    fun fromThrowable(error: Throwable?): String {
        var current: Throwable? = error
        while (current != null) {
            if (current is MediaIngestException) return current.code
            val mapped = fromSignal(current.message, current::class.java.simpleName)
            if (mapped != null) return mapped
            current = current.cause
        }
        return DECODE
    }

    fun fromSignal(code: String?, technicalMessage: String? = null): String? {
        val signal = "${code.orEmpty()} ${technicalMessage.orEmpty()}".uppercase()
        if (signal.isBlank()) return null
        Regex("MEDIA-UPLOAD-(\\d{3})").find(signal)?.let { match ->
            return "MEDIA-UPLOAD-${match.groupValues[1]}"
        }
        Regex("MEDIA-[A-Z]+-\\d{2}").find(signal)?.let { return it.value }
        val status = Regex("(?:TUS_(?:CREATE|PATCH)_|HTTP_|STATUS_)(\\d{3})").find(signal)
        if (status != null) return "MEDIA-UPLOAD-${status.groupValues[1]}"
        return when {
            "CANCEL" in signal || "DOUBLE_SUBMIT" in signal -> null
            "URI" in signal || "FILE_READ" in signal -> URI
            "MIME" in signal || "EXTENSION" in signal || "FORMAT" in signal -> MIME
            "EXIF" in signal || "PHOTO_GPS" in signal || "GPS" in signal -> EXIF
            "CROP" in signal -> CROP
            "ENCODE" in signal || "PHOTO_ENCODE" in signal -> ENCODE
            "SIZE" in signal || "SOURCE_MALICIOUS" in signal || "TOO_LARGE" in signal -> SIZE
            "PGRST" in signal || "COMPLETE" in signal ||
                ("SESSION" in signal && "UPLOAD" !in signal) ||
                "REGISTER_MEDIA" in signal || "MEDIA_SELECT" in signal ||
                "SET_PERSON_AVATAR" in signal || "SET_PET_AVATAR" in signal -> DB
            "STORAGE_UPLOAD" in signal || "UPLOAD" in signal || "TUS" in signal ||
                "STORAGE" in signal || "NETWORK" in signal -> UPLOAD
            "DECODE" in signal || "CORRUPT" in signal || "PHOTO_DECODE" in signal ||
                "UNKNOWN" in signal -> DECODE
            else -> null
        }
    }

    fun fromCanonicalStep(code: String?): String? = when (code?.uppercase()) {
        "REGISTER_MEDIA", "MEDIA_SELECT", "SET_PET_AVATAR", "SET_PERSON_AVATAR" -> DB
        "STORAGE_UPLOAD" -> UPLOAD
        "VALIDATION", "UNKNOWN" -> DECODE
        else -> null
    }

    fun classifyDb(code: String?, technicalMessage: String? = null): String {
        val signal = "${code.orEmpty()} ${technicalMessage.orEmpty()}".uppercase()
        return when {
            "SET_PERSON_AVATAR" in signal -> DB_PERSON
            "REGISTER_MEDIA_PARSE" in signal ||
                signal.contains("PARSE / REGISTER") -> DB_PARSE
            "42501" in signal || "RLS" in signal || "MEDIA_ASSET_NOT_OWNED" in signal ||
                "PERMISSION" in signal -> DB_RLS
            "PGRST202" in signal || "SCHEMA CACHE" in signal ||
                ("FUNCTION" in signal && "NOT FOUND" in signal) ||
                ("DOES NOT EXIST" in signal) -> DB_RPC
            "PERSON_NOT_FOUND" in signal ||
                "MEDIA_ASSET_NOT_FOUND" in signal || "MEDIA_ASSET_NOT_READY" in signal ||
                "MEDIA_ASSET_NOT_IMAGE" in signal -> DB_PERSON
            "REGISTER_MEDIA" in signal || "MEDIA_SELECT" in signal -> DB_REGISTER
            "PGRST" in signal -> DB_RPC
            else -> DB
        }
    }

    fun formatDbUserMessage(friendly: String, code: String?, technicalMessage: String? = null): String {
        val signal = "${code.orEmpty()} ${technicalMessage.orEmpty()}".uppercase()
        val visible = when {
            "SET_PERSON_AVATAR" in signal -> "$DB / PERSON"
            "REGISTER_MEDIA_PARSE_PRIMITIVE" in signal -> "$DB / PARSE / REGISTER-PRIMITIVE"
            "REGISTER_MEDIA_PARSE_OBJECT" in signal -> "$DB / PARSE / REGISTER-OBJECT"
            "REGISTER_MEDIA_PARSE_ARRAY" in signal -> "$DB / PARSE / REGISTER-ARRAY"
            "REGISTER_MEDIA_PARSE_EMPTY" in signal -> "$DB / PARSE / REGISTER-EMPTY"
            "REGISTER_MEDIA_PARSE_UNKNOWN" in signal -> "$DB / PARSE / REGISTER-UNKNOWN"
            "REGISTER_MEDIA_PARSE" in signal -> "$DB / PARSE / REGISTER"
            "REGISTER_MEDIA_AUTH" in signal -> "$DB / REGISTER / AUTH"
            "REGISTER_MEDIA_RLS" in signal -> "$DB / REGISTER / RLS"
            "REGISTER_MEDIA_CONFLICT" in signal -> "$DB / REGISTER / CONFLICT"
            "REGISTER_MEDIA_RECOVERY" in signal -> "$DB / REGISTER / RECOVERY"
            "REGISTER_MEDIA_STORAGE" in signal -> "$DB / REGISTER / STORAGE"
            "REGISTER_MEDIA_RPC" in signal -> "$DB / REGISTER / RPC"
            "REGISTER_MEDIA_UNKNOWN" in signal -> "$DB / REGISTER / UNKNOWN"
            "42501" in signal || "RLS" in signal || "MEDIA_ASSET_NOT_OWNED" in signal -> "$DB / RLS"
            "PGRST202" in signal || "SCHEMA CACHE" in signal ||
                ("FUNCTION" in signal && "NOT FOUND" in signal) -> "$DB / RPC"
            "PERSON_NOT_FOUND" in signal || "MEDIA_ASSET_NOT_FOUND" in signal ||
                "MEDIA_ASSET_NOT_READY" in signal || "MEDIA_ASSET_NOT_IMAGE" in signal ->
                "$DB / PERSON"
            "REGISTER_MEDIA" in signal || "MEDIA_SELECT" in signal -> "$DB / REGISTER / UNKNOWN"
            "PGRST" in signal -> "$DB / RPC"
            else -> DB
        }
        return formatUserMessage(friendly, visible)
    }

    fun formatUserMessage(friendly: String, diagnostic: String?): String {
        val code = diagnostic?.takeIf { it.isNotBlank() } ?: return friendly
        if (!isStaging) return friendly
        if (friendly.contains(code)) return friendly
        return "$friendly\n$code"
    }

    fun logStaging(code: String) {
        if (!isStaging) return
        AppLog.info(TAG, code)
    }

    fun logUpload(error: AppError) {
        val diagnostic = fromSignal(error.code, error.technicalMessage) ?: UPLOAD
        logStaging(diagnostic)
    }

    fun logRenderFailure() {
        logStaging(RENDER)
    }

    val isStaging: Boolean
        get() = BuildConfig.LEOVER_ENV.equals("staging", ignoreCase = true)
}
