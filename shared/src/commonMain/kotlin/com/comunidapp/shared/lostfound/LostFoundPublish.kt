package com.comunidapp.shared.lostfound

import com.comunidapp.shared.poc.m08.model.FileRef
import com.comunidapp.shared.ui.ErrorSanitizer

/**
 * Resultado SAFE de publicación Lost/Found — sin JWT/SQL/payloads.
 */
sealed interface LostFoundPublishResult {
    data class Success(
        val id: LostFoundId,
        val publicCode: String?,
        /** true solo si media quedó asociada de forma real. */
        val mediaAttached: Boolean,
        /**
         * true si el usuario adjuntó FileRef pero el upload M05 no está disponible
         * o falló — la fila textual sí se creó (MEDIA_WRITE PARTIAL).
         */
        val mediaDeferred: Boolean = false
    ) : LostFoundPublishResult

    data class ValidationError(val message: String) : LostFoundPublishResult
    data class Unauthenticated(val message: String) : LostFoundPublishResult
    data class PermissionDenied(val message: String) : LostFoundPublishResult
    data class NetworkError(val message: String) : LostFoundPublishResult
    data class MediaError(val message: String) : LostFoundPublishResult
    data class BackendError(val message: String) : LostFoundPublishResult
}

enum class LostFoundMediaWriteMode {
    /** Upload M05 completo (no en KMP-8 shared). */
    REAL_REMOTE,
    /** Insert textual OK; foto opcional no sube / no se finge. */
    PARTIAL,
    /** Sin capacidad de media. */
    UNAVAILABLE
}

internal fun mapPublishThrowable(t: Throwable): LostFoundPublishResult {
    val raw = t.message.orEmpty().lowercase()
    return when {
        "401" in raw || "jwt" in raw || "not authenticated" in raw || "session" in raw ->
            LostFoundPublishResult.Unauthenticated("Tu sesión no está disponible.")
        "403" in raw || "permission" in raw || "rls" in raw || "policy" in raw || "forbidden" in raw ->
            LostFoundPublishResult.PermissionDenied("No tenés permiso para publicar.")
        "network" in raw || "timeout" in raw || "unable to resolve" in raw || "connection" in raw ->
            LostFoundPublishResult.NetworkError("Problema de conexión. Intentá nuevamente.")
        "media" in raw || "upload" in raw || "storage" in raw ->
            LostFoundPublishResult.MediaError("No pudimos adjuntar la foto. Intentá sin foto o más tarde.")
        else -> LostFoundPublishResult.BackendError(ErrorSanitizer.sanitize(t))
    }
}

/** Payload de publicación — media opcional (FileRef neutral). */
data class LostFoundPublishRequest(
    val draft: LostFoundDraft,
    val media: FileRef? = null
)
