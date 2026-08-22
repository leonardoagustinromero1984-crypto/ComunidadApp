package com.comunidapp.app.domain.social

/**
 * User-facing friendship errors. Never forward PostgREST/RestException text,
 * URLs, headers, or tokens to Compose.
 */
object FriendshipErrorMapper {

    enum class Operation {
        SEND,
        LOAD,
        RESPOND,
        CANCEL
    }

    fun userMessage(throwable: Throwable, operation: Operation): String {
        val domain = throwable as? IllegalArgumentException
        val domainMessage = domain?.message?.trim().orEmpty()
        if (domainMessage.isNotEmpty() && !looksTechnical(domainMessage)) {
            return domainMessage
        }
        val raw = buildString {
            append(throwable.message.orEmpty())
            append(' ')
            append(throwable.cause?.message.orEmpty())
            append(' ')
            append(throwable.toString())
        }
        if (isDuplicate(raw) || raw.contains("REQUEST_PENDING", ignoreCase = true)) {
            return "Ya hay una solicitud pendiente"
        }
        if (raw.contains("ALREADY_FRIENDS", ignoreCase = true)) {
            return "Ya son amigos"
        }
        if (raw.contains("PERSON_REQUIRED", ignoreCase = true)) {
            return "Completá tu perfil antes de enviar solicitudes."
        }
        if (raw.contains("FRIEND_SELF", ignoreCase = true)) {
            return "No podés enviarte una solicitud a vos mismo"
        }
        return friendly(operation)
    }

    fun looksTechnical(raw: String): Boolean {
        val t = raw.lowercase()
        return t.contains("authorization") ||
            t.contains("bearer") ||
            t.contains("eyj") ||
            t.contains("rest/v1") ||
            t.contains("/friendships") ||
            t.contains("permission denied") ||
            t.contains("42501") ||
            t.contains("grant select") ||
            t.contains("apikey") ||
            t.contains("postgrest") ||
            t.contains("jwt") ||
            t.contains("stack trace") ||
            t.contains("content-type") ||
            t.contains("http/1") ||
            (t.contains("http") && (t.contains("header") || t.contains("request")))
    }

    private fun isDuplicate(raw: String): Boolean {
        val t = raw.lowercase()
        return t.contains("23505") ||
            t.contains("duplicate key") ||
            t.contains("unique constraint") ||
            t.contains("friendships_requester_id_addressee_id")
    }

    private fun friendly(operation: Operation): String = when (operation) {
        Operation.SEND ->
            "No pudimos completar la solicitud de amistad. Intentá nuevamente."
        Operation.LOAD ->
            "No pudimos cargar tus amistades. Intentá nuevamente."
        Operation.RESPOND ->
            "No pudimos actualizar la solicitud. Intentá nuevamente."
        Operation.CANCEL ->
            "No pudimos cancelar la solicitud. Intentá nuevamente."
    }
}
