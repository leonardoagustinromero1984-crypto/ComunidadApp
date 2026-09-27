package com.comunidapp.app.domain.files

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.domain.media.MediaDiagnostic

object FileUiErrorMapper {

    fun message(error: AppError): String =
        message(error.code, error.technicalMessage, error.kind)

    fun message(
        code: String?,
        technicalMessage: String? = null,
        kind: AppErrorKind? = null
    ): String {
        val signal = "${code.orEmpty()} ${technicalMessage.orEmpty()}".uppercase()
        val diagnostic = MediaDiagnostic.fromSignal(code, technicalMessage)
            ?: MediaDiagnostic.fromCanonicalStep(code)
        val resolved = if (isMigrationUnavailable(signal) || isLimitSignal(signal, kind, code)) {
            null
        } else if (shouldAttachDiagnostic(signal)) {
            diagnostic ?: MediaDiagnostic.DECODE
        } else {
            diagnostic
        }
        val friendly = when {
            isMigrationUnavailable(signal) ->
                "El servicio de archivos no está disponible todavía. Intentá más tarde."
            kind == AppErrorKind.FEATURE_TEMPORARILY_DISABLED ||
                code.equals("FEATURE_TEMPORARILY_DISABLED", ignoreCase = true) ->
                "Esta función no está disponible por ahora."
            "FILE_TOO_LARGE" in signal ||
                ((kind == AppErrorKind.VALIDATION || kind == AppErrorKind.QUOTA_EXCEEDED) &&
                    ("FILE_TOO_LARGE" in signal || "TOO_LARGE" in signal) &&
                    "QUOTA" !in signal) ->
                "El video supera el tamaño máximo permitido."
            kind == AppErrorKind.QUOTA_EXCEEDED ||
                code.equals("QUOTA_EXCEEDED", ignoreCase = true) ->
                quotaMessage(signal)
            kind == AppErrorKind.RATE_LIMITED ||
                code.equals("RATE_LIMITED", ignoreCase = true) ->
                rateMessage(signal)
            code.equals("TIMEOUT", ignoreCase = true) || "TIMEOUT" in signal ->
                "La publicación tardó demasiado. Intentá de nuevo."
            "FORBIDDEN" in signal || kind == AppErrorKind.FORBIDDEN ->
                "No tenés permiso para usar este archivo."
            "413" in signal || "PAYLOAD TOO LARGE" in signal ||
                "SIZE" in signal || "SOURCE_MALICIOUS" in signal || "TOO_LARGE" in signal ->
                "El video supera el tamaño máximo permitido."
            "PHOTO_GPS" in signal ->
                "No pudimos guardar la foto porque todavía traía ubicación. Intentá de nuevo."
            "PHOTO_DECODE" in signal || "CORRUPT" in signal || "MEDIA-DECODE" in signal ->
                "El archivo está dañado o no se pudo leer. Probá con otra foto."
            "MIME" in signal || "EXTENSION" in signal || "FORMAT" in signal ->
                "Ese formato no está soportado. Usá una foto o video compatible."
            "COUNT" in signal ->
                "Alcanzaste la cantidad máxima de archivos."
            "DOUBLE_SUBMIT" in signal || "UPLOAD_IN_PROGRESS" in signal ->
                "La carga ya está en curso."
            "EXPIRED" in signal ->
                "La carga venció. Seleccioná el archivo nuevamente."
            "CANCEL" in signal ->
                "La carga fue cancelada."
            "NETWORK" in signal || kind == AppErrorKind.NETWORK ->
                "No pudimos subir el archivo. Revisá tu conexión e intentá de nuevo."
            "STORAGE" in signal || "UPLOAD" in signal || "TUS" in signal ->
                "No pudimos subir el archivo. Intentá de nuevo."
            "VALIDATION" in signal || kind == AppErrorKind.VALIDATION ->
                "El archivo seleccionado no es válido."
            else -> "No pudimos procesar el archivo. Intentá de nuevo."
        }
        return if (resolved == MediaDiagnostic.DB) {
            MediaDiagnostic.formatDbUserMessage(friendly, code, technicalMessage)
        } else {
            MediaDiagnostic.formatUserMessage(friendly, resolved)
        }
    }

    const val STORY_DAILY_LIMIT =
        "Alcanzaste el límite diario de historias. Podés volver a publicar mañana."
    const val VIDEO_DAILY_LIMIT =
        "Alcanzaste el límite diario de videos. Podés volver a publicar mañana."
    const val UPLOAD_DAILY_LIMIT =
        "Alcanzaste el límite diario de subidas. Intentá más tarde."

    private fun quotaMessage(signal: String): String = when {
        "STORY" in signal || "SOCIAL.STORY" in signal -> STORY_DAILY_LIMIT
        "VIDEO.COUNT" in signal || "MEDIA.VIDEO" in signal -> VIDEO_DAILY_LIMIT
        else -> UPLOAD_DAILY_LIMIT
    }

    private fun rateMessage(signal: String): String =
        if ("STORY" in signal || "SOCIAL.STORY" in signal) STORY_DAILY_LIMIT
        else "Estás publicando demasiado rápido. Intentá nuevamente más tarde."

    fun storyLimitMessage(error: AppError): String {
        val signal = "${error.code.orEmpty()} ${error.technicalMessage.orEmpty()}".uppercase()
        return when {
            "FILE_TOO_LARGE" in signal || "TOO_LARGE" in signal ->
                "El video supera el tamaño máximo permitido."
            "VIDEO.COUNT" in signal || "MEDIA.VIDEO" in signal -> VIDEO_DAILY_LIMIT
            "BYTES.DAILY" in signal -> UPLOAD_DAILY_LIMIT
            "STORY" in signal ||
                error.kind == AppErrorKind.RATE_LIMITED ||
                "RATE_LIMITED" in signal -> STORY_DAILY_LIMIT
            error.kind == AppErrorKind.QUOTA_EXCEEDED || "QUOTA" in signal -> UPLOAD_DAILY_LIMIT
            else -> message(error)
        }
    }

    private fun isLimitSignal(signal: String, kind: AppErrorKind?, code: String?): Boolean =
        kind == AppErrorKind.RATE_LIMITED ||
            kind == AppErrorKind.QUOTA_EXCEEDED ||
            code.equals("RATE_LIMITED", ignoreCase = true) ||
            code.equals("QUOTA_EXCEEDED", ignoreCase = true) ||
            code.equals("FILE_TOO_LARGE", ignoreCase = true) ||
            code.equals("TIMEOUT", ignoreCase = true) ||
            "FILE_TOO_LARGE" in signal ||
            "RATE_LIMITED" in signal ||
            "QUOTA_EXCEEDED" in signal ||
            "TIMEOUT" in signal

    private fun shouldAttachDiagnostic(signal: String): Boolean =
        "CANCEL" !in signal &&
            "DOUBLE_SUBMIT" !in signal &&
            "FORBIDDEN" !in signal &&
            "413" !in signal &&
            "PAYLOAD TOO LARGE" !in signal &&
            "FILE_TOO_LARGE" !in signal &&
            "RATE_LIMITED" !in signal &&
            "QUOTA_EXCEEDED" !in signal &&
            "TIMEOUT" !in signal &&
            !isMigrationUnavailable(signal)

    fun isMigrationUnavailable(message: String?): Boolean {
        val signal = message.orEmpty().uppercase()
        return "MIGRATION_UNAVAILABLE" in signal ||
            "PGRST202" in signal ||
            "SCHEMA CACHE" in signal ||
            ("FUNCTION" in signal && ("NOT FOUND" in signal || "DOES NOT EXIST" in signal)) ||
            ("CREATE_FILE_UPLOAD_SESSION" in signal && "404" in signal)
    }
}
