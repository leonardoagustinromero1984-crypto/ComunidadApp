package com.comunidapp.app.domain.m23

object M23BookingResilience {
    fun safeUserMessage(error: Throwable): String = when {
        error.message?.contains("M23_SLOT_UNAVAILABLE") == true -> "Ese horario ya no está disponible."
        error.message?.contains("M23_PERMISSION_DENIED") == true -> "No tenés permiso para realizar esta acción."
        error.message?.contains("M23_") == true -> "No pudimos procesar la reserva. Revisá los datos e intentá nuevamente."
        else -> "Ocurrió un problema temporal. Intentá nuevamente."
    }
}
