package com.comunidapp.app.domain.ux

import com.comunidapp.app.domain.observability.sanitization.SensitiveDataSanitizer

/**
 * User-facing errors for onboarding / organization / foster / community.
 * Never expose SQL, constraints, JWT, keys, or stack traces.
 */
enum class CanonicalUiErrorKind {
    VALIDATION,
    ALREADY_EXISTS,
    PERMISSION,
    NETWORK,
    SERVER,
    NOT_FOUND,
    CONFLICT,
    UNKNOWN
}

object CanonicalUiErrorMapper {

    const val NETWORK =
        "No pudimos conectar. Revisá tu conexión e intentá de nuevo."
    const val PERMISSION =
        "No tenés permiso para esta acción. Si acabás de crear algo, abrilo desde tu perfil."
    const val NOT_FOUND =
        "No encontramos lo que buscabas. Volvé e intentá de nuevo."
    const val VALIDATION =
        "Revisá los datos e intentá de nuevo."
    const val CONFLICT =
        "Esa información ya está en uso. Cambiá el dato e intentá de nuevo."
    const val ALREADY_EXISTS_ORG =
        "Esta organización ya fue creada. La abrimos para que puedas continuar."
    const val DUPLICATE_PUBLIC_IDENTIFIER =
        "Ese identificador ya está en uso. Elegí otro."
    const val STALE_CONTEXT =
        "El contexto cambió. Volvé a Perfil y elegí cómo querés usar LeoVer."
    const val GENERIC =
        "No pudimos completar la acción. Intentá de nuevo."
    const val CREATE_SUCCEEDED_OPEN =
        "La organización ya está creada. Continuá desde su perfil."

    fun userMessage(error: Throwable, fallback: String = GENERIC): String =
        userMessage(blobOf(error), fallback)

    fun kind(error: Throwable): CanonicalUiErrorKind = kind(blobOf(error))

    fun kind(raw: String?): CanonicalUiErrorKind {
        val blob = raw.orEmpty()
        val upper = blob.uppercase()
        return when {
            looksLikeNetwork(blob) -> CanonicalUiErrorKind.NETWORK
            containsAny(upper, "ORGANIZATION_ALREADY_EXISTS", "ALREADY_EXISTS_FOR_REQUEST") ->
                CanonicalUiErrorKind.ALREADY_EXISTS
            containsAny(upper, "ORGANIZATION_SLUG_TAKEN", "DUPLICATE_PUBLIC_IDENTIFIER") ->
                CanonicalUiErrorKind.ALREADY_EXISTS
            containsAny(upper, "23505", "DUPLICATE KEY", "UNIQUE CONSTRAINT", "ALREADY EXISTS") ->
                CanonicalUiErrorKind.ALREADY_EXISTS
            containsAny(upper, "42501", "FORBIDDEN", "NOT_AUTHORIZED", "PERMISSION", "403") ->
                CanonicalUiErrorKind.PERMISSION
            containsAny(upper, "409", "CONFLICT") -> CanonicalUiErrorKind.CONFLICT
            containsAny(upper, "NOT_FOUND", "PGRST116", "404") -> CanonicalUiErrorKind.NOT_FOUND
            containsAny(upper, "NOT_AUTHENTICATED", "JWT", "401") -> CanonicalUiErrorKind.PERMISSION
            containsAny(upper, "VALIDATION", "INVALID") -> CanonicalUiErrorKind.VALIDATION
            containsAny(upper, "500", "502", "503", "INTERNAL") -> CanonicalUiErrorKind.SERVER
            else -> CanonicalUiErrorKind.UNKNOWN
        }
    }

    fun userMessage(raw: String?, fallback: String = GENERIC): String {
        val blob = raw.orEmpty()
        val upper = blob.uppercase()
        return when {
            looksLikeNetwork(blob) -> NETWORK
            containsAny(upper, "ORGANIZATION_ALREADY_EXISTS", "ALREADY_EXISTS_FOR_REQUEST") ->
                ALREADY_EXISTS_ORG
            containsAny(upper, "ORGANIZATION_SLUG_TAKEN", "DUPLICATE_PUBLIC_IDENTIFIER") ->
                DUPLICATE_PUBLIC_IDENTIFIER
            containsAny(upper, "23505", "DUPLICATE KEY", "UNIQUE CONSTRAINT", "ALREADY EXISTS") ->
                mapDuplicate(upper)
            containsAny(upper, "42501", "FORBIDDEN", "NOT_AUTHORIZED", "PERMISSION", "403") ->
                PERMISSION
            containsAny(upper, "409", "PGRST116", "CONFLICT") -> CONFLICT
            containsAny(upper, "NOT_FOUND", "PGRST116", "404") -> NOT_FOUND
            containsAny(upper, "NOT_AUTHENTICATED", "JWT", "401") ->
                "Iniciá sesión e intentá de nuevo."
            containsAny(upper, "VALIDATION", "INVALID") -> VALIDATION
            containsAny(
                upper,
                "PROVIDER_NAME_REQUIRED",
                "PROVIDER_CATEGORY_UNSUPPORTED",
                "PROVIDER_HOLDER_REQUIRED",
                "CANONICAL_PROVIDER_WRITE_NOT_FROM_DIRECTORY",
                "NOT A BUSINESS",
                "NO ES UN NEGOCIO"
            ) -> "Completá los datos de tu servicio para publicarlo."
            containsAny(upper, "PROVIDER_HOLDER_FORBIDDEN") -> PERMISSION
            isUnsafeToShow(blob) -> fallback
            blob.isBlank() -> fallback
            blob.length <= 140 -> blob
            else -> fallback
        }
    }

    fun mapDuplicate(upper: String): String = when {
        containsAny(upper, "SLUG", "PUBLIC_IDENTIFIER", "ORGANIZATIONS_SLUG") ->
            DUPLICATE_PUBLIC_IDENTIFIER
        containsAny(upper, "ORGANIZATION") -> ALREADY_EXISTS_ORG
        else -> CONFLICT
    }

    fun isUnsafeToShow(raw: String?): Boolean {
        val text = raw.orEmpty()
        if (text.isBlank()) return true
        val lower = text.lowercase()
        return listOf(
            "sqlstate",
            "duplicate key",
            "constraint",
            "postgrest",
            "pgrst",
            "jwt",
            "bearer",
            "apikey",
            "api key",
            "service_role",
            "stacktrace",
            "hint:",
            "code:",
            "http://",
            "https://",
            "authorization"
        ).any { lower.contains(it) } || '\n' in text
    }

    fun sanitizedDiagnostic(error: Throwable): String =
        SensitiveDataSanitizer.sanitize(blobOf(error), maxLength = 400)

    private fun blobOf(error: Throwable): String = buildString {
        append(error.message.orEmpty())
        generateSequence(error.cause) { it.cause }.forEach { cause ->
            append(' ')
            append(cause.message.orEmpty())
        }
    }

    private fun looksLikeNetwork(blob: String): Boolean {
        val lower = blob.lowercase()
        return listOf("unable to resolve", "failed to connect", "timeout", "network", "unknownhost")
            .any { lower.contains(it) }
    }

    private fun containsAny(haystack: String, vararg needles: String): Boolean =
        needles.any { haystack.contains(it) }
}
