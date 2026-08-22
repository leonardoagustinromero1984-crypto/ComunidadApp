package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.PetSpecies

/**
 * Maps UI species to public.species.code. Unknown codes become OTHER
 * so create does not fail FK while Admin catalogs catch up.
 */
object PetSpeciesCatalog {
    val knownCodes: Set<String> = PetSpecies.entries.map { it.name }.toSet()

    fun toRpcCode(raw: String?): String {
        val code = raw?.trim()?.uppercase().orEmpty()
        if (code.isEmpty()) return PetSpecies.OTHER.name
        if (code in knownCodes) return code
        return when (code) {
            "PERRO", "DOGGO", "CANINE" -> PetSpecies.DOG.name
            "GATO", "FELINE" -> PetSpecies.CAT.name
            else -> PetSpecies.OTHER.name
        }
    }

    fun toPetSpecies(raw: String?): PetSpecies =
        PetSpecies.fromString(toRpcCode(raw))
}
