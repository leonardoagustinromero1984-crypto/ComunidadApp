package com.comunidapp.app.data.repository

class M27IntegrationException(val code: String, message: String) : IllegalStateException(message)

object M27IntegrationErrors {
    fun userMessage(code: String): String = when (code) {
        "NOT_AUTHENTICATED" -> "Tenés que iniciar sesión."
        "M27_PERMISSION_DENIED" -> "No tenés permiso para esta operación de integraciones."
        "M27_WEBHOOK_NOT_FOUND" -> "No encontramos el webhook."
        "M27_OAUTH_NOT_FOUND" -> "No encontramos la aplicación OAuth."
        "M27_KEY_NOT_FOUND" -> "No encontramos la credencial API."
        "M27_INVALID_WEBHOOK" -> "Los datos del webhook no son válidos."
        "M27_INVALID_OAUTH" -> "Los datos de OAuth no son válidos."
        "M27_INVALID_API_KEY" -> "Los datos de la clave API no son válidos."
        "M27_SANDBOX_ISOLATION" -> "Sandbox no puede acceder a recursos de producción."
        "M27_REMOTE_NOT_READY" -> "Operación remota de integraciones aún no disponible."
        else -> "No pudimos completar la operación."
    }

    fun <T> failure(error: Throwable): Result<T> = Result.failure(error)
}
