package com.comunidapp.app.domain.files

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind

object FileUiErrorMapper {

    fun message(error: AppError): String =
        message(error.code, error.technicalMessage, error.kind)

    fun message(
        code: String?,
        technicalMessage: String? = null,
        kind: AppErrorKind? = null
    ): String {
        val signal = "${code.orEmpty()} ${technicalMessage.orEmpty()}".uppercase()
        return when {
            isMigrationUnavailable(signal) ->
                "El servicio de archivos no está disponible todavía. Intentá más tarde."
            "FORBIDDEN" in signal || kind == AppErrorKind.FORBIDDEN ->
                "No tenés permiso para usar este archivo."
            "SIZE" in signal ->
                "El archivo supera el tamaño permitido."
            "MIME" in signal || "EXTENSION" in signal ->
                "El tipo de archivo no está permitido."
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
            "VALIDATION" in signal || kind == AppErrorKind.VALIDATION ->
                "El archivo seleccionado no es válido."
            else -> "No pudimos procesar el archivo. Intentá de nuevo."
        }
    }

    fun isMigrationUnavailable(message: String?): Boolean {
        val signal = message.orEmpty().uppercase()
        return "MIGRATION_UNAVAILABLE" in signal ||
            "PGRST202" in signal ||
            "SCHEMA CACHE" in signal ||
            ("FUNCTION" in signal && ("NOT FOUND" in signal || "DOES NOT EXIST" in signal)) ||
            ("CREATE_FILE_UPLOAD_SESSION" in signal && "404" in signal)
    }
}
