package com.comunidapp.shared.lostfound

/**
 * Resultado de gestión owner (resolver / editar contenido / reemplazar foto) — sin hard delete.
 * RESOLVED: RLS permite editar description/location/photo; documentado en UI.
 */
sealed interface LostFoundManageResult {
    data object Success : LostFoundManageResult
    /** Contenido guardado; foto falló tras upload o al asociar — no se afirma foto nueva. */
    data class PartialSuccess(val message: String) : LostFoundManageResult
    data class Forbidden(val message: String) : LostFoundManageResult
    data class Unauthenticated(val message: String) : LostFoundManageResult
    data class Conflict(val message: String) : LostFoundManageResult
    data class BackendError(val message: String) : LostFoundManageResult
}

/**
 * Borrador owner edit — description + location aproximada + foto opcional.
 * No type / author / publicCode / lat-lng / photo remove.
 */
data class LostFoundEditDraft(
    val description: String,
    val location: String,
    val newPhoto: com.comunidapp.shared.poc.m08.model.FileRef? = null
)

object LostFoundEditDraftValidator {
    fun validate(draft: LostFoundEditDraft): Result<Unit> {
        if (draft.description.trim().length < 8) {
            return Result.failure(IllegalArgumentException("LOST_FOUND_DRAFT_DESCRIPTION_SHORT"))
        }
        if (draft.location.trim().isEmpty()) {
            return Result.failure(IllegalArgumentException("LOST_FOUND_DRAFT_LOCATION_BLANK"))
        }
        return Result.success(Unit)
    }
}
