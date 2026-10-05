package com.comunidapp.app.domain.m13

import com.comunidapp.app.data.model.PetSpecies

/** A sighting keeps the species of the linked case. It does not assume a dog. */
object ContributeSpecies {
    fun fromCase(caseSpecies: PetSpecies?): PetSpecies = caseSpecies ?: PetSpecies.OTHER
}
