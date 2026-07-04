package com.comunidapp.app.data.model

object PetHealthCatalog {

    private val commonVaccines = listOf(
        "Rabia",
        "Refuerzo anual",
        "Consulta / plan veterinario",
        "Otra (especificar en notas)"
    )

    private val dogVaccines = listOf(
        "Rabia",
        "Séxtuple (DHPP + Lepto)",
        "Octuple",
        "Bordetella (tos de las perreras)",
        "Leptospirosis",
        "Giardia",
        "Refuerzo anual",
        "Otra"
    )

    private val catVaccines = listOf(
        "Rabia",
        "Triple felina (RCP)",
        "Cuádruple felina",
        "Leucemia felina (FeLV)",
        "Refuerzo anual",
        "Otra"
    )

    private val horseVaccines = listOf(
        "Tétanos",
        "Influenza equina",
        "Encefalitis equina",
        "Rinoneumonitis (EHV)",
        "Rabia equina",
        "Refuerzo anual",
        "Otra"
    )

    private val farmVaccines = listOf(
        "Brucelosis",
        "Carbunco",
        "Clostridiales",
        "Aftosa (campo oficial)",
        "Leptospirosis",
        "Refuerzo anual",
        "Otra"
    )

    private val smallPetVaccines = listOf(
        "Mixomatosis (conejo)",
        "Enfermedad hemorrágica (conejo)",
        "Consulta veterinaria",
        "Otra"
    )

    private val birdVaccines = listOf(
        "Newcastle",
        "Pox aviar",
        "Consulta veterinaria",
        "Otra"
    )

    fun vaccinesForSpecies(species: PetSpecies): List<String> = when (species) {
        PetSpecies.DOG -> dogVaccines
        PetSpecies.CAT -> catVaccines
        PetSpecies.HORSE, PetSpecies.DONKEY -> horseVaccines
        PetSpecies.COW, PetSpecies.SHEEP, PetSpecies.GOAT, PetSpecies.PIG -> farmVaccines
        PetSpecies.RABBIT -> smallPetVaccines
        PetSpecies.BIRD, PetSpecies.CHICKEN, PetSpecies.DUCK -> birdVaccines
        PetSpecies.HAMSTER, PetSpecies.GUINEA_PIG, PetSpecies.FISH, PetSpecies.REPTILE -> commonVaccines
        PetSpecies.OTHER -> commonVaccines
    }.distinct()

    val dewormingProducts = listOf(
        "Ivermectina",
        "Albendazol",
        "Febendazol",
        "Praziquantel",
        "Moxidectina",
        "Piperazina",
        "Spot-on desparasitante",
        "Otro producto"
    )

    val fleaAndTickProducts = listOf(
        "Fipronil (pipeta)",
        "Fluralaner (comprimido)",
        "Amitraz (collar)",
        "Permetrina (spray)",
        "Selamectina",
        "Collar antipulgas",
        "Otro producto"
    )
}
