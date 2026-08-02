package com.comunidapp.app.data.remote.supabase.m20

class M20Exception(
    val code: String,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

object M20MessagingErrors {

    private val knownCodes = listOf(
        "M20_CONVERSATION_NOT_FOUND",
        "M20_MESSAGE_NOT_FOUND",
        "M20_CONVERSATION_BLOCKED",
        "M20_CONVERSATION_ARCHIVED",
        "M20_INVALID_MESSAGE",
        "M20_INVALID_ATTACHMENT_REF",
        "M20_ATTACHMENT_NOT_ALLOWED",
        "M20_PERMISSION_DENIED",
        "M20_USER_ALREADY_BLOCKED",
        "NOT_AUTHENTICATED"
    )

    fun userMessage(code: String): String = when (code) {
        "M20_CONVERSATION_NOT_FOUND" -> "No encontramos esa conversación."
        "M20_MESSAGE_NOT_FOUND" -> "No encontramos ese mensaje."
        "M20_CONVERSATION_BLOCKED" -> "Esta conversación está bloqueada."
        "M20_CONVERSATION_ARCHIVED" -> "Esta conversación está archivada."
        "M20_INVALID_MESSAGE" -> "El mensaje no es válido."
        "M20_INVALID_ATTACHMENT_REF" -> "La referencia de adjunto no es válida."
        "M20_ATTACHMENT_NOT_ALLOWED" -> "Los adjuntos aún no están disponibles."
        "M20_PERMISSION_DENIED" -> "No tenés permiso para esta acción."
        "M20_USER_ALREADY_BLOCKED" -> "Este usuario ya está bloqueado."
        "NOT_AUTHENTICATED" -> "Tenés que iniciar sesión."
        else -> "No se pudo completar la operación."
    }

    fun codeOf(throwable: Throwable): String {
        if (throwable is M20Exception) return throwable.code
        knownCodes.forEach { code ->
            if (throwable.message?.contains(code, ignoreCase = true) == true) return code
        }
        return "UNKNOWN"
    }

    fun <T> failure(throwable: Throwable): Result<T> {
        val code = codeOf(throwable)
        return Result.failure(M20Exception(code, userMessage(code), throwable))
    }

    fun <T> fail(code: String): Result<T> =
        Result.failure(M20Exception(code, userMessage(code)))
}
