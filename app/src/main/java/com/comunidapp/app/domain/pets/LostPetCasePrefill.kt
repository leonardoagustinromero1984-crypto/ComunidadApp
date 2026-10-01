package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSpecies

data class LostPetCasePrefill(
    val petId: String,
    val name: String,
    val species: PetSpecies,
    val avatarAssetId: String?,
    val description: String,
    val location: String?
) {
    val hasExistingPhoto: Boolean get() = !avatarAssetId.isNullOrBlank()
}

object LostPetCasePrefillMapper {
    fun from(pet: Pet): LostPetCasePrefill {
        val description = buildString {
            append("Se perdió ${pet.name}.")
            append(" Sexo: ${sexLabel(pet.sex)}.")
            pet.breed?.takeIf { it.isNotBlank() }?.let { append(" Raza: $it.") }
            pet.color?.takeIf { it.isNotBlank() }?.let { append(" Color: $it.") }
            pet.description.takeIf { it.isNotBlank() }?.let { append(" $it") }
        }.trim()
        return LostPetCasePrefill(
            petId = pet.id,
            name = pet.name,
            species = pet.species,
            avatarAssetId = pet.avatarFileAssetId?.trim()?.takeIf { it.isNotEmpty() },
            description = description,
            location = pet.locationText?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    private fun sexLabel(sex: PetSex): String = when (sex) {
        PetSex.MALE -> "Macho"
        PetSex.FEMALE -> "Hembra"
        PetSex.UNKNOWN -> "Desconocido"
    }
}
