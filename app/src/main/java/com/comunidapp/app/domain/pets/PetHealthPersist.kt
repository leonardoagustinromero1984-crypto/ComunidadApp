package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.remote.supabase.m08.UpdatePetHealthParams

/**
 * True when the pet form carries at least one health field worth persisting.
 * Used to skip health RPCs on create/update when health is fully empty.
 */
object PetHealthPersist {

    fun hasData(pet: Pet): Boolean = hasSignals(
        sterilized = pet.sterilized?.name,
        lastVetVisit = pet.lastVetVisit,
        healthNotes = pet.healthNotes,
        weightKg = pet.weightKg,
        vaccinations = pet.vaccinations.any { it.name.isNotBlank() || it.date.isNotBlank() },
        lastDeworming = pet.lastDeworming,
        dewormingProduct = pet.dewormingProduct,
        lastFleaTreatment = pet.lastFleaTreatment,
        fleaTreatmentProduct = pet.fleaTreatmentProduct,
        allergies = pet.allergies.any { it.isNotBlank() },
        medications = pet.medications.any { it.isNotBlank() },
        conditions = pet.conditions.any { it.isNotBlank() }
    )

    fun hasData(params: UpdatePetHealthParams): Boolean = hasSignals(
        sterilized = params.sterilized,
        lastVetVisit = params.lastVetVisit,
        healthNotes = params.healthNotes,
        weightKg = params.weightKg,
        vaccinations = params.vaccinations.any { it.name.isNotBlank() || it.date.isNotBlank() },
        lastDeworming = params.lastDeworming,
        dewormingProduct = params.dewormingProduct,
        lastFleaTreatment = params.lastFleaTreatment,
        fleaTreatmentProduct = params.fleaTreatmentProduct,
        allergies = params.allergies.any { it.isNotBlank() },
        medications = params.medications.any { it.isNotBlank() },
        conditions = params.conditions.any { it.isNotBlank() }
    )

    private fun hasSignals(
        sterilized: String?,
        lastVetVisit: String?,
        healthNotes: String?,
        weightKg: Float?,
        vaccinations: Boolean,
        lastDeworming: String?,
        dewormingProduct: String?,
        lastFleaTreatment: String?,
        fleaTreatmentProduct: String?,
        allergies: Boolean,
        medications: Boolean,
        conditions: Boolean
    ): Boolean =
        !sterilized.isNullOrBlank() ||
            !lastVetVisit.isNullOrBlank() ||
            !healthNotes.isNullOrBlank() ||
            (weightKg != null && weightKg > 0f) ||
            vaccinations ||
            !lastDeworming.isNullOrBlank() ||
            !dewormingProduct.isNullOrBlank() ||
            !lastFleaTreatment.isNullOrBlank() ||
            !fleaTreatmentProduct.isNullOrBlank() ||
            allergies ||
            medications ||
            conditions
}
