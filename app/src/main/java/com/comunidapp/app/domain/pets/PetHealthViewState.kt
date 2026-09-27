package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.Pet

enum class PetHealthViewState {
    LOADING,
    DATA,
    ERROR,
    EMPTY
}

object PetHealthPresentation {

    fun state(
        pet: Pet?,
        healthLoading: Boolean,
        healthLoadError: String?
    ): PetHealthViewState {
        if (healthLoading) return PetHealthViewState.LOADING
        if (pet != null && hasHealthData(pet)) return PetHealthViewState.DATA
        if (!healthLoadError.isNullOrBlank() || pet?.healthReadFailed == true) {
            return PetHealthViewState.ERROR
        }
        return PetHealthViewState.EMPTY
    }

    fun hasHealthData(pet: Pet): Boolean =
        pet.sterilized != null ||
            (pet.weightKg != null && pet.weightKg > 0) ||
            !pet.lastVetVisit.isNullOrBlank() ||
            !pet.healthNotes.isNullOrBlank() ||
            pet.allergies.any { it.isNotBlank() } ||
            pet.medications.any { it.isNotBlank() } ||
            pet.conditions.any { it.isNotBlank() } ||
            pet.vaccinations.any { it.date.isNotBlank() || it.name.isNotBlank() } ||
            !pet.lastDeworming.isNullOrBlank() ||
            !pet.dewormingProduct.isNullOrBlank() ||
            !pet.lastFleaTreatment.isNullOrBlank() ||
            !pet.fleaTreatmentProduct.isNullOrBlank()
}
