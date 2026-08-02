package com.comunidapp.app.data.repository

object M21ReputationErrors {

    fun userMessage(code: String): String = when (code) {
        "NOT_AUTHENTICATED" -> "Tenés que iniciar sesión."
        "M21_PERMISSION_DENIED" -> "No tenés permiso para esta acción."
        "M21_REVIEW_NOT_FOUND" -> "No encontramos la reseña."
        "M21_INVALID_REVIEW" -> "La reseña no es válida."
        "M21_INVALID_RATING" -> "La calificación debe ser entre 1 y 5."
        "M21_INVALID_APPEAL" -> "El motivo de apelación no es válido."
        "M21_INVALID_VERIFICATION" -> "La solicitud de verificación no es válida."
        "M21_LICENSE_REQUIRED" -> "Necesitamos los datos de la matrícula."
        "M21_DUPLICATE_REVIEW" -> "Ya dejaste una reseña para esta transacción."
        "M21_APPEAL_EXISTS" -> "Ya existe una apelación abierta."
        "M21_SELF_REVIEW" -> "No podés reseñarte a vos mismo."
        "M21_NOT_ELIGIBLE" -> "Todavía no podés dejar una reseña para esta experiencia."
        "M21_REVIEW_ELIGIBILITY_UNAVAILABLE" -> "No pudimos verificar tu elegibilidad."
        "M21_INVALID_RESPONSE" -> "La respuesta no es válida."
        "M21_INVALID_DISPUTE" -> "La disputa no es válida."
        "M21_DISPUTE_EXISTS" -> "Ya existe una disputa abierta."
        "M21_RESPONSE_EXISTS" -> "Ya existe una respuesta activa."
        "M21_NOTIFICATIONS_UNAVAILABLE" -> "Las notificaciones no están disponibles."
        else -> "No pudimos completar la operación."
    }

    fun fail(code: String): Result<Nothing> =
        Result.failure(
            com.comunidapp.app.data.remote.supabase.m21.M21Exception(code, userMessage(code))
        )

    fun <T> failure(t: Throwable): Result<T> =
        com.comunidapp.app.data.remote.supabase.m21.M21ReputationErrorMapper.failure(t)
}
