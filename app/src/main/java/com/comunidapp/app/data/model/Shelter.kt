package com.comunidapp.app.data.model

data class ShelterNeed(
    val item: String,
    val quantity: String
)

data class Shelter(
    val id: String,
    val ownerId: String = "",
    val name: String,
    val photoUrl: String? = null,
    val location: String,
    val description: String,
    val contactPhone: String? = null,
    val contactEmail: String? = null,
    val adoptionPetIds: List<String> = emptyList(),
    val needs: List<ShelterNeed> = emptyList()
)
