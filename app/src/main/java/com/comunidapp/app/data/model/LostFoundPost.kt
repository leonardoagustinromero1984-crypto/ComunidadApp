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
    val status: LostFoundStatus = LostFoundStatus.ACTIVE,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val date: String,
    val createdAt: Long? = null
)
