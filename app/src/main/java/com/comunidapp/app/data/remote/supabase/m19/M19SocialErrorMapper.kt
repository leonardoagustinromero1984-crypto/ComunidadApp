package com.comunidapp.app.data.remote.supabase.m19

class M19Exception(
    val code: String,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

object M19SocialErrorMapper {

    private val knownCodes = listOf(
        "M19_POST_NOT_FOUND",
        "M19_ORGANIZATION_NOT_ELIGIBLE",
        "M19_PERMISSION_DENIED",
        "M19_INVALID_TITLE",
        "M19_INVALID_CONTENT",
        "M19_INVALID_COMMENT",
        "M19_INVALID_STATE_TRANSITION",
        "M19_STATE_ALREADY_FINAL",
        "M19_POST_NOT_PUBLIC",
        "M19_POST_REMOVED",
        "M19_COMMENT_NOT_FOUND",
        "M19_REACTION_NOT_FOUND",
        "M19_DUPLICATE_REACTION",
        "NOT_AUTHENTICATED"
    )

    fun userMessage(code: String): String = when (code) {
        "M19_POST_NOT_FOUND" -> "No encontramos esa publicación."
        "M19_ORGANIZATION_NOT_ELIGIBLE" -> "Esta organización no puede publicar contenido."
        "M19_PERMISSION_DENIED" -> "No tenés permiso para esta acción."
        "M19_INVALID_TITLE" -> "El título no es válido."
        "M19_INVALID_CONTENT" -> "El contenido no es válido."
        "M19_INVALID_COMMENT" -> "El comentario no es válido."
        "M19_INVALID_STATE_TRANSITION" -> "Ese cambio de estado no está permitido."
        "M19_STATE_ALREADY_FINAL" -> "Esta publicación ya fue eliminada."
        "M19_POST_NOT_PUBLIC" -> "Esta publicación no está visible."
        "M19_POST_REMOVED" -> "Esta publicación fue eliminada."
        "M19_COMMENT_NOT_FOUND" -> "No encontramos ese comentario."
        "M19_REACTION_NOT_FOUND" -> "No encontramos esa reacción."
        "M19_DUPLICATE_REACTION" -> "Ya reaccionaste a esta publicación."
        "NOT_AUTHENTICATED" -> "Tenés que iniciar sesión."
        else -> "No se pudo completar la operación."
    }

    fun codeOf(throwable: Throwable): String {
        if (throwable is M19Exception) return throwable.code
        knownCodes.forEach { code ->
            if (throwable.message?.contains(code, ignoreCase = true) == true) return code
        }
        return "UNKNOWN"
    }

    fun <T> failure(throwable: Throwable): Result<T> {
        val code = codeOf(throwable)
        return Result.failure(M19Exception(code, userMessage(code), throwable))
    }

    fun <T> fail(code: String): Result<T> =
        Result.failure(M19Exception(code, userMessage(code)))
}
