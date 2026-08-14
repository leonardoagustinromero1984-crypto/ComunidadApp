package com.comunidapp.shared.pets

import com.comunidapp.app.domain.pets.PetId

/**
 * Salud de mascota (OWNER_WRITE via pet.manage_health).
 * Solo en detalle autenticado — nunca en PublicContent.
 */
data class PetVaccination(
    val name: String,
    val date: String,
    val nextDueDate: String? = null
)

data class PetHealthReminder(
    val id: String,
    val title: String,
    val date: String,
    val type: String
)

data class PetHealthSummary(
    val vaccinations: List<PetVaccination> = emptyList(),
    val reminders: List<PetHealthReminder> = emptyList(),
    val lastDeworming: String? = null,
    val dewormingProduct: String? = null,
    val lastFleaTreatment: String? = null,
    val fleaTreatmentProduct: String? = null,
    val sterilized: String? = null,
    val lastVetVisit: String? = null,
    val healthNotes: String? = null,
    val weightKg: Float? = null
)

data class PetHealthDraft(
    val vaccinations: List<PetVaccination> = emptyList(),
    val reminders: List<PetHealthReminder> = emptyList(),
    val lastDeworming: String? = null,
    val dewormingProduct: String? = null,
    val lastFleaTreatment: String? = null,
    val fleaTreatmentProduct: String? = null,
    val sterilized: String? = null,
    val lastVetVisit: String? = null,
    val healthNotes: String? = null,
    val weightKg: Float? = null
)

object PetHealthDraftValidator {
    fun validate(draft: PetHealthDraft): Result<Unit> {
        draft.vaccinations.forEachIndexed { index, v ->
            if (v.name.trim().isEmpty()) {
                return Result.failure(IllegalArgumentException("PET_VACCINE_NAME_REQUIRED"))
            }
            if (v.date.trim().isEmpty()) {
                return Result.failure(IllegalArgumentException("PET_VACCINE_DATE_REQUIRED_$index"))
            }
        }
        draft.reminders.forEachIndexed { index, r ->
            if (r.title.trim().isEmpty()) {
                return Result.failure(IllegalArgumentException("PET_REMINDER_TITLE_REQUIRED_$index"))
            }
            if (r.date.trim().isEmpty()) {
                return Result.failure(IllegalArgumentException("PET_REMINDER_DATE_REQUIRED_$index"))
            }
        }
        val w = draft.weightKg
        if (w != null && w < 0f) {
            return Result.failure(IllegalArgumentException("PET_WEIGHT_INVALID"))
        }
        return Result.success(Unit)
    }
}

sealed interface PetHealthWriteResult {
    data class Success(val petId: PetId) : PetHealthWriteResult
    data class ValidationError(val message: String) : PetHealthWriteResult
    data class Unauthenticated(val message: String) : PetHealthWriteResult
    data class Forbidden(val message: String) : PetHealthWriteResult
    data class BackendError(val message: String) : PetHealthWriteResult
}

fun PetHealthSummary.toDraft(): PetHealthDraft =
    PetHealthDraft(
        vaccinations = vaccinations,
        reminders = reminders,
        lastDeworming = lastDeworming,
        dewormingProduct = dewormingProduct,
        lastFleaTreatment = lastFleaTreatment,
        fleaTreatmentProduct = fleaTreatmentProduct,
        sterilized = sterilized,
        lastVetVisit = lastVetVisit,
        healthNotes = healthNotes,
        weightKg = weightKg
    )
