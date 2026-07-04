package com.comunidapp.app.data.model

data class LostFoundPost(
    val id: String,
    val authorId: String,
    val authorName: String,
    val type: LostFoundType,
    val petName: String? = null,
    val species: PetSpecies,
    val photoUrl: String? = null,
    val location: String,
    val description: String,
    val contactInfo: String,
    val date: String
)
