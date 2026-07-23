package com.comunidapp.app.data.model

data class AdoptionPost(
    val id: String,
    val petId: String? = null,
    val publisherId: String? = null,
    val publisherOrganizationId: String? = null,
    val shelterId: String? = null,
    val shelterName: String,
    val title: String = "",
    val name: String,
    val photoUrl: String? = null,
    val species: PetSpecies,
    val sex: PetSex,
    val ageYears: Int,
    val ageMonths: Int = 0,
    val size: PetSize,
    val location: String,
    val description: String,
    val requirements: String = "",
    val status: AdoptionStatus = AdoptionStatus.PUBLISHED,
    val publishedAt: Long? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
) {
    val displayTitle: String
        get() = title.ifBlank { name }
}
