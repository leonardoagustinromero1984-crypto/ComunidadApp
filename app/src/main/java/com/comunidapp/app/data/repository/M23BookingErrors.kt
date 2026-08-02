package com.comunidapp.app.data.repository

class M23BookingException(val code: String) : IllegalStateException(code)

object M23BookingErrors {
    fun failure(error: Throwable): Result<Nothing> =
        Result.failure(if (error is M23BookingException) error else M23BookingException(error.message ?: "M23_UNKNOWN_ERROR"))
    fun userMessage(code: String): String = when (code) {
        "NOT_AUTHENTICATED" -> "Iniciá sesión para continuar."
        "M23_PERMISSION_DENIED" -> "No tenés permiso para gestionar esta reserva."
        "M23_SLOT_UNAVAILABLE" -> "Ese horario ya no está disponible."
        else -> "No se pudo completar la operación de agenda."
    }
}
