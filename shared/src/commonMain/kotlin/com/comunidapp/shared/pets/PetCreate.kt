package com.comunidapp.shared.pets

import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.shared.poc.m08.model.FileRef

/**
 * Borrador create — campos de `m08_create_pet_with_principal` (sin org en form mínimo KMP).
 * Avatar opcional vía M05 PET_AVATAR + `m08_set_pet_avatar_asset` post-create.
 */
data class PetCreateDraft(
    val name: String,
    val species: String = "UNKNOWN",
    val sex: String = "UNKNOWN",
    val size: String = "UNKNOWN",
    val description: String = "",
    val avatarFile: FileRef? = null
)

object PetCreateDraftValidator {
    fun validate(draft: PetCreateDraft): Result<Unit> {
        if (draft.name.trim().isEmpty()) {
            return Result.failure(IllegalArgumentException("PET_NAME_REQUIRED"))
        }
        if (draft.name.trim().length > 80) {
            return Result.failure(IllegalArgumentException("PET_NAME_TOO_LONG"))
        }
        return Result.success(Unit)
    }
}

sealed interface PetCreateResult {
    data class Success(val id: PetId, val avatarAttached: Boolean) : PetCreateResult
    /** Mascota creada; avatar falló — no se finge foto. */
    data class PartialSuccess(val id: PetId, val mediaMessage: String) : PetCreateResult
    data class ValidationError(val message: String) : PetCreateResult
    data class Unauthenticated(val message: String) : PetCreateResult
    data class Forbidden(val message: String) : PetCreateResult
    data class Conflict(val message: String) : PetCreateResult
    data class BackendError(val message: String) : PetCreateResult
}
