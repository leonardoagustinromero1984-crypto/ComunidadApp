package com.comunidapp.shared.pets

import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetLifecycleStatus

/**
 * Archivo / restauración / fallecimiento (M08) — sin hard delete ni redaction de pasaporte.
 * RPCs: `m08_archive_pet`, `m08_restore_pet`, `m08_mark_pet_deceased`.
 */
sealed interface PetLifecycleResult {
    data class Success(
        val petId: PetId,
        val status: PetLifecycleStatus
    ) : PetLifecycleResult

    data class Forbidden(val message: String) : PetLifecycleResult
    data class Unauthenticated(val message: String) : PetLifecycleResult
    data class Conflict(val message: String) : PetLifecycleResult
    data class BackendError(val message: String) : PetLifecycleResult
}
