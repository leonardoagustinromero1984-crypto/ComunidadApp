package com.comunidapp.app.domain.canonical

import com.comunidapp.app.data.model.M14PassportStatus
import com.comunidapp.app.data.model.M14PetPassport
import com.comunidapp.app.data.model.M14Visibility
import com.comunidapp.app.data.model.Pet

object CanonicalVitaCoraProjection {
    fun fromPet(pet: Pet?): M14PetPassport? {
        if (pet == null || pet.id.isBlank()) return null
        val now = pet.updatedAt ?: pet.createdAt ?: 0L
        return M14PetPassport(
            id = pet.id,
            petId = pet.id,
            passportNumber = pet.publicVitacoraNumber?.toString()?.takeIf { it.isNotBlank() }
                ?: pet.publicCode?.takeIf { it.isNotBlank() }
                ?: pet.id.take(8),
            publicCode = pet.publicCode,
            status = M14PassportStatus.ACTIVE,
            displayName = com.comunidapp.app.domain.pets.PetDisplayName.of(pet.originKind, pet.name),
            species = pet.species,
            breedText = pet.breed,
            sex = pet.sex,
            primaryColor = pet.color,
            distinctiveMarks = pet.healthNotes,
            microchipNumber = null,
            visibility = M14Visibility.RESPONSIBLES,
            createdBy = pet.createdByUserId.orEmpty(),
            createdAt = pet.createdAt ?: now,
            updatedAt = now
        )
    }
}
