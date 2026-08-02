package com.comunidapp.app.data.repository

class M26AiException(val code: String, message: String) : IllegalStateException(message)

object M26AiErrors {
    fun userMessage(code: String): String = when (code) {
        "NOT_AUTHENTICATED" -> "Tenés que iniciar sesión."
        "M26_PERMISSION_DENIED" -> "No tenés permiso para esta operación de inteligencia asistida."
        "M26_MATCH_NOT_FOUND" -> "No encontramos la sugerencia de matching."
        "M26_DUPLICATE_NOT_FOUND" -> "No encontramos el candidato duplicado."
        "M26_SESSION_NOT_FOUND" -> "No encontramos la sesión de asistencia."
        "M26_RECOMMENDATION_NOT_FOUND" -> "No encontramos la recomendación."
        "M26_INVALID_MATCH" -> "Los datos del matching visual no son válidos."
        "M26_INVALID_DUPLICATE" -> "Los datos del candidato duplicado no son válidos."
        "M26_INVALID_ASSISTANCE" -> "Los datos de la sesión de asistencia no son válidos."
        "M26_INVALID_RECOMMENDATION" -> "Los datos de la recomendación no son válidos."
        "M26_INVALID_SCORE" -> "El puntaje informado no es válido."
        "M26_SESSION_ALREADY_CLOSED" -> "La sesión de asistencia ya está cerrada."
        "M26_RECOMMENDATION_NOT_ELIGIBLE" -> "La recomendación no está apta para mostrarse."
        else -> "No pudimos completar la operación."
    }

    fun <T> failure(error: Throwable): Result<T> = Result.failure(error)
}
