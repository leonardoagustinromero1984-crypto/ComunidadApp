package com.comunidapp.app.data.repository

class M22ProviderException(val code: String, message: String) : IllegalStateException(message)

object M22ProviderErrors {
    fun userMessage(code: String): String = when (code) {
        "NOT_AUTHENTICATED" -> "Tenés que iniciar sesión."
        "M22_PERMISSION_DENIED" -> "No tenés permiso para gestionar este prestador."
        "M22_PROVIDER_NOT_FOUND" -> "No encontramos el prestador."
        "M22_BRANCH_NOT_FOUND" -> "No encontramos la sede."
        "M22_OFFERING_NOT_FOUND" -> "No encontramos el servicio."
        "M22_INVALID_PROVIDER" -> "Los datos del prestador no son válidos."
        "M22_INVALID_BRANCH" -> "Los datos de la sede no son válidos."
        "M22_INVALID_OFFERING" -> "Los datos del servicio no son válidos."
        "M22_INVALID_PRICE" -> "El precio informado no es válido."
        "M22_PROVIDER_NOT_PUBLIC" -> "Este prestador no está disponible públicamente."
        else -> "No pudimos completar la operación."
    }

    fun <T> failure(error: Throwable): Result<T> = Result.failure(error)
}
