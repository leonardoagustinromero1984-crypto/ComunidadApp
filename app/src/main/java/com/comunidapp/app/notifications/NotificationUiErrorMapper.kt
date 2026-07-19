package com.comunidapp.app.notifications

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind

object NotificationUiErrorMapper {

    fun userMessage(error: AppError?): String {
        if (error == null) return "No se pudo completar la acción. Reintentá."
        return when (error.code) {
            "IN_APP_MANDATORY", "SECURITY_IN_APP_REQUIRED" ->
                "Esta categoría in-app es obligatoria y no se puede desactivar."
            "PREFERENCE_INVALID" ->
                "La preferencia no es válida."
            "NOTIFICATION_CLIENT_INSERT_DENIED_M06_STAGE_3",
            "M06_CREATE_NOTIFICATION_CROSS_USER_DENIED",
            "NOTIFICATION_CLIENT_MATERIALIZE_DENIED" ->
                "Esta notificación solo se genera desde el servidor."
            "DENIED_NOT_AUTHENTICATED" ->
                "Iniciá sesión para continuar."
            "DENIED_PERMISSION", "DENIED_ORGANIZATION", "DENIED_DEEP_LINK" ->
                "No tenés acceso a este contenido."
            else -> when (error.kind) {
                AppErrorKind.NETWORK ->
                    "Problema de conexión. Reintentá."
                AppErrorKind.FORBIDDEN ->
                    "No tenés permiso para esta acción."
                AppErrorKind.NOT_FOUND ->
                    "No encontramos ese recurso."
                else -> error.userMessage.ifBlank { "No se pudo completar la acción. Reintentá." }
            }
        }
    }

    fun sanitizeTechnical(raw: String?): String =
        raw.orEmpty()
            .replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._\\-]+"), "Bearer [redacted]")
            .replace(Regex("(?i)eyJ[A-Za-z0-9_-]{10,}"), "[redacted]")
            .take(160)
}
