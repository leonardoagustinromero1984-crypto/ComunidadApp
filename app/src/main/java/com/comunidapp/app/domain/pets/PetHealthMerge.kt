package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.Pet

/**
 * Preserves canonical health fields when list-cache snapshots overwrite enriched pets.
 */
object PetHealthMerge {
    fun preferRicherHealth(cached: Pet?, incoming: Pet): Pet {
        if (cached == null || cached.id != incoming.id) return incoming
        return incoming.copy(
            vaccinations = incoming.vaccinations.ifEmpty { cached.vaccinations },
            allergies = incoming.allergies.ifEmpty { cached.allergies },
            medications = incoming.medications.ifEmpty { cached.medications },
            conditions = incoming.conditions.ifEmpty { cached.conditions },
            reminders = incoming.reminders.ifEmpty { cached.reminders },
            sterilized = incoming.sterilized ?: cached.sterilized,
            weightKg = incoming.weightKg ?: cached.weightKg,
            lastVetVisit = incoming.lastVetVisit?.takeIf { it.isNotBlank() } ?: cached.lastVetVisit,
            healthNotes = incoming.healthNotes?.takeIf { it.isNotBlank() } ?: cached.healthNotes,
            lastDeworming = incoming.lastDeworming?.takeIf { it.isNotBlank() } ?: cached.lastDeworming,
            dewormingProduct = incoming.dewormingProduct?.takeIf { it.isNotBlank() } ?: cached.dewormingProduct,
            lastFleaTreatment = incoming.lastFleaTreatment?.takeIf { it.isNotBlank() }
                ?: cached.lastFleaTreatment,
            fleaTreatmentProduct = incoming.fleaTreatmentProduct?.takeIf { it.isNotBlank() }
                ?: cached.fleaTreatmentProduct,
            publicCode = incoming.publicCode?.takeIf { it.isNotBlank() } ?: cached.publicCode,
            publicVitacoraNumber = incoming.publicVitacoraNumber ?: cached.publicVitacoraNumber
        )
    }
}
