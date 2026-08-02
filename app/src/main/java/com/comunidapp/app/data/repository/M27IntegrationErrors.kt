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
        "M27_APP_NOT_FOUND" -> "No encontramos la aplicación integradora."
        "M27_APP_NOT_ACTIVE" -> "La aplicación no está activa."
        "M27_APP_TERMINAL" -> "La aplicación está en un estado terminal."
        "M27_INVALID_APP_TRANSITION" -> "Transición de estado no permitida."
        "M27_UNKNOWN_SCOPE" -> "Scope no reconocido."
        "M27_SCOPE_DENIED" -> "Scope no concedido."
        "M27_INVALID_SCOPE" -> "Lista de scopes inválida."
        "M27_UNSAFE_WEBHOOK_URL" -> "URL de webhook no permitida."
        "M27_PRODUCTION_DISABLED" -> "Producción no habilitada en este entorno."
        "M27_DELIVERY_NOT_FOUND" -> "No encontramos la entrega."
        "M27_DELIVERY_TERMINAL" -> "La entrega ya finalizó."
        "M27_RATE_LIMIT" -> "Límite de solicitudes alcanzado."
        "M27_OAUTH_STATE_REQUIRED" -> "OAuth requiere state."
        "M27_INVALID_PAYLOAD" -> "Payload no válido."
        else -> "No pudimos completar la operación."
    }

    fun <T> failure(error: Throwable): Result<T> = Result.failure(error)
}
