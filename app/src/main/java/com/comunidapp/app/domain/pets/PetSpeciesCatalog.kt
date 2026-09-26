package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.PetSpecies

/**
 * Maps UI species to public.species.code.
 * Unknown catalog codes pass through so Superadmin can add species without a new APK.
 */
object PetSpeciesCatalog {
    val knownCodes: Set<String> = PetSpecies.entries.map { it.name }.toSet()

    private val canonicalNames = mapOf(
        "DOG" to "Perro",
        "CAT" to "Gato",
        "RABBIT" to "Conejo",
        "FERRET" to "Hurón",
        "GUINEA_PIG" to "Cobayo",
        "HAMSTER" to "Hámster",
        "HEDGEHOG" to "Erizo",
        "HORSE" to "Caballo",
        "DONKEY" to "Burro",
        "PIG" to "Cerdo",
        "BIRD" to "Ave",
        "REPTILE" to "Reptil",
        "FISH" to "Pez",
        "AMPHIBIAN" to "Anfibio",
        "INVERTEBRATE" to "Invertebrado"
    )

    fun toRpcCode(raw: String?): String {
        val code = raw?.trim()?.uppercase()?.replace(' ', '_').orEmpty()
        if (code.isEmpty()) return PetSpecies.OTHER.name
        return when (code) {
            "PERRO", "DOGGO", "CANINE" -> PetSpecies.DOG.name
            "GATO", "FELINE" -> PetSpecies.CAT.name
            else -> code
        }
    }

    fun toPetSpecies(raw: String?): PetSpecies =
        PetSpecies.entries.find { it.name == toRpcCode(raw) } ?: PetSpecies.OTHER

    fun displayLabel(
        code: String?,
        speciesName: String? = null,
        species: PetSpecies? = null
    ): String {
        val trimmedName = speciesName?.trim().orEmpty()
        if (trimmedName.isNotEmpty()) return trimmedName
        val normalized = toRpcCode(code)
        canonicalNames[normalized]?.let { return it }
        if (species != null && (code.isNullOrBlank() || normalized in knownCodes)) {
            return displayLabel(species)
        }
        return normalized.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
            .ifBlank { displayLabel(species ?: PetSpecies.OTHER) }
    }

    fun displayLabel(species: PetSpecies): String = when (species) {
        PetSpecies.DOG -> "Perro"
        PetSpecies.CAT -> "Gato"
        PetSpecies.HORSE -> "Caballo"
        PetSpecies.COW -> "Vaca"
        PetSpecies.SHEEP -> "Oveja"
        PetSpecies.GOAT -> "Cabra"
        PetSpecies.PIG -> "Cerdo"
        PetSpecies.RABBIT -> "Conejo"
        PetSpecies.HAMSTER -> "Hámster"
        PetSpecies.GUINEA_PIG -> "Cobayo"
        PetSpecies.BIRD -> "Ave"
        PetSpecies.FISH -> "Pez"
        PetSpecies.REPTILE -> "Reptil"
        PetSpecies.CHICKEN -> "Gallina"
        PetSpecies.DUCK -> "Pato"
        PetSpecies.DONKEY -> "Burro"
        PetSpecies.OTHER -> "Otra especie"
    }
}

fun com.comunidapp.app.data.model.Pet.resolvedSpeciesCode(): String =
    PetSpeciesCatalog.toRpcCode(speciesCode ?: species.name)
