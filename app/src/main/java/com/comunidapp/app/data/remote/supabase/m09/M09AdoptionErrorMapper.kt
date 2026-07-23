package com.comunidapp.app.data.remote.supabase.m09

/**
 * LeoVer M09 — typed adoption failure with stable domain code.
 */
class M09AdoptionException(
    val code: String,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

object M09AdoptionErrorMapper {

    private val knownCodes = listOf(
        "ADOPTION_NOT_FOUND",
        "ADOPTION_ALREADY_EXISTS",
        "PET_NOT_ADOPTABLE",
        "ADOPTION_NOT_EDITABLE",
        "ADOPTION_ALREADY_PAUSED",
        "ADOPTION_ALREADY_CLOSED",
        "ADOPTION_ALREADY_ADOPTED",
        "ADOPTION_TITLE_REQUIRED",
        "ADOPTION_DESCRIPTION_REQUIRED",
        "ADOPTION_STATUS_INVALID",
        "ADOPTION_USE_MARK_ADOPTED",
        "PET_NOT_FOUND",
        "NOT_AUTHENTICATED",
        "FORBIDDEN",
        "NETWORK",
        "TIMEOUT",
        "SERIALIZATION"
    )

    fun codeOf(throwable: Throwable): String {
        if (throwable is M09AdoptionException) return throwable.code
        val signal = listOfNotNull(throwable.message, throwable.cause?.message)
            .joinToString(" ")
            .uppercase()
        knownCodes.forEach { code ->
            if (signal.contains(code)) return code
        }
        return when {
            "UNIQUE" in signal || "ADOPTIONS_ONE_OPEN" in signal -> "ADOPTION_ALREADY_EXISTS"
            "NETWORK" in signal || "UNABLE TO RESOLVE" in signal -> "NETWORK"
            "TIMEOUT" in signal -> "TIMEOUT"
            "SERIALIZ" in signal || "JSON" in signal -> "SERIALIZATION"
            else -> "FORBIDDEN"
        }
    }

    fun userMessage(code: String): String = when (code) {
        "ADOPTION_NOT_FOUND" -> "No encontramos esa publicación de adopción."
        "ADOPTION_ALREADY_EXISTS" -> "Ya hay una publicación abierta para esa mascota."
        "PET_NOT_ADOPTABLE" -> "Esa mascota no se puede publicar en adopción (fallecida, archivada o no activa)."
        "ADOPTION_NOT_EDITABLE" -> "Esta publicación no se puede editar en su estado actual."
        "ADOPTION_ALREADY_PAUSED" -> "La publicación ya está pausada."
        "ADOPTION_ALREADY_CLOSED" -> "La publicación ya está cerrada."
        "ADOPTION_ALREADY_ADOPTED" -> "La mascota ya figura como adoptada."
        "ADOPTION_TITLE_REQUIRED" -> "Indicá un título para la publicación."
        "ADOPTION_DESCRIPTION_REQUIRED" -> "Indicá una descripción."
        "ADOPTION_STATUS_INVALID" -> "Estado de publicación no válido."
        "PET_NOT_FOUND" -> "No encontramos la mascota."
        "NOT_AUTHENTICATED" -> "Tenés que iniciar sesión."
        "NETWORK" -> "Problema de conexión. Intentá de nuevo."
        "TIMEOUT" -> "La operación tardó demasiado. Intentá de nuevo."
        "SERIALIZATION" -> "No pudimos interpretar la respuesta del servidor."
        "FORBIDDEN" -> "No tenés permiso para esta acción."
        else -> "No se pudo completar la operación."
    }

    fun <T> failure(throwable: Throwable): Result<T> {
        val code = codeOf(throwable)
        return Result.failure(M09AdoptionException(code, userMessage(code), throwable))
    }
}
