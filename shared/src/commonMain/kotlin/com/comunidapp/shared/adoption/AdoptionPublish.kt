package com.comunidapp.shared.adoption

import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.shared.location.ApproximateLocation

/**
 * Borrador de publicación M09 — campos exactos de `m09_create_adoption_publication`.
 * Foto = snapshot de `pets.photo_url` en backend (sin M05 write separado en form productivo).
 */
data class AdoptionPublishDraft(
    val petId: PetId,
    val title: String,
    val description: String,
    val requirements: String = "",
    val approximateLocation: ApproximateLocation = ApproximateLocation("Zona no especificada"),
    val publishImmediately: Boolean = true
)

object AdoptionPublishDraftValidator {
    fun validate(draft: AdoptionPublishDraft): Result<Unit> {
        if (draft.petId.value.isBlank()) {
            return Result.failure(IllegalArgumentException("ADOPTION_PET_REQUIRED"))
        }
        if (draft.title.trim().isEmpty()) {
            return Result.failure(IllegalArgumentException("ADOPTION_TITLE_REQUIRED"))
        }
        if (draft.description.trim().isEmpty()) {
            return Result.failure(IllegalArgumentException("ADOPTION_DESCRIPTION_REQUIRED"))
        }
        return Result.success(Unit)
    }
}

sealed interface AdoptionPublishResult {
    data class Success(val id: AdoptionId, val published: Boolean) : AdoptionPublishResult
    data class ValidationError(val message: String) : AdoptionPublishResult
    data class Unauthenticated(val message: String) : AdoptionPublishResult
    data class Forbidden(val message: String) : AdoptionPublishResult
    data class Conflict(val message: String) : AdoptionPublishResult
    data class BackendError(val message: String) : AdoptionPublishResult
}
