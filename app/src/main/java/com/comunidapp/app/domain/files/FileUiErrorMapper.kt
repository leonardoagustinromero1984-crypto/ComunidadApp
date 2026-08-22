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
        val resolved = if (shouldAttachDiagnostic(signal)) {
            diagnostic ?: MediaDiagnostic.DECODE
        } else {
            diagnostic
        }
        val friendly = when {
            isMigrationUnavailable(signal) ->
                "El servicio de archivos no está disponible todavía. Intentá más tarde."
            "FORBIDDEN" in signal || kind == AppErrorKind.FORBIDDEN ->
                "No tenés permiso para usar este archivo."
            "SIZE" in signal || "SOURCE_MALICIOUS" in signal ->
                "El archivo supera el tamaño permitido después de procesarlo. Probá con otra foto."
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

    private fun shouldAttachDiagnostic(signal: String): Boolean =
        "CANCEL" !in signal &&
            "DOUBLE_SUBMIT" !in signal &&
            "FORBIDDEN" !in signal &&
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
