package com.comunidapp.shared.pets

import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.shared.poc.m08.model.FileRef

/**
 * Borrador edit — campos de `m08_update_pet_profile` (sin health/archive/delete).
 * Avatar opcional vía M05 PET_AVATAR + `m08_set_pet_avatar_asset`.
 */
data class PetEditDraft(
    val name: String,
    val species: String = "UNKNOWN",
    val breed: String? = null,
    val sex: String = "UNKNOWN",
    val size: String = "UNKNOWN",
    val description: String = "",
    val ageYears: Int = 0,
    val ageMonths: Int = 0,
    val color: String? = null,
    val avatarFile: FileRef? = null
)

object PetEditDraftValidator {
    fun validate(draft: PetEditDraft): Result<Unit> {
        if (draft.name.trim().isEmpty()) {
            return Result.failure(IllegalArgumentException("PET_NAME_REQUIRED"))
        }
        if (draft.name.trim().length > 80) {
            return Result.failure(IllegalArgumentException("PET_NAME_TOO_LONG"))
        }
        if (draft.ageYears < 0 || draft.ageMonths < 0 || draft.ageMonths > 11) {
            return Result.failure(IllegalArgumentException("PET_AGE_INVALID"))
        }
        return Result.success(Unit)
    }
}

sealed interface PetEditResult {
    data class Success(val id: PetId, val avatarAttached: Boolean) : PetEditResult
    /** Perfil actualizado; avatar falló — no se finge foto. */
    data class PartialSuccess(val id: PetId, val mediaMessage: String) : PetEditResult
    data class ValidationError(val message: String) : PetEditResult
    data class Unauthenticated(val message: String) : PetEditResult
    data class Forbidden(val message: String) : PetEditResult
    data class BackendError(val message: String) : PetEditResult
}
