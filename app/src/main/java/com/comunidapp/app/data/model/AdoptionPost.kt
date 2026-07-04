package com.comunidapp.app.data.model

data class AdoptionPost(
    val id: String,
    val shelterId: String? = null,
    val shelterName: String,
    val name: String,
    val photoUrl: String? = null,
    val species: PetSpecies,
    val sex: PetSex,
    val ageYears: Int,
    val ageMonths: Int = 0,
    val size: PetSize,
    val location: String,
    val description: String,
    val status: AdoptionStatus = AdoptionStatus.AVAILABLE
)
