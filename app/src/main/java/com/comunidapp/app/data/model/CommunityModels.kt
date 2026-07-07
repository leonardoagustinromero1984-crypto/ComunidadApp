package com.comunidapp.app.data.model

enum class CommunityCategory {
    SHELTER,
    VET,
    TRAINER,
    WALKER,
    SHOP,
    DONATION
}

data class CommunityListing(
    val id: String,
    val category: CommunityCategory,
    val name: String,
    val photoUrl: String? = null,
    val location: String,
    val description: String,
    val contactInfo: String? = null,
    val tags: List<String> = emptyList()
)

data class FosterHomeListing(
    val id: String,
    val hostName: String,
    val photoUrl: String? = null,
    val location: String,
    val capacity: Int,
    val acceptedSpecies: List<PetSpecies>,
    val notes: String,
    val available: Boolean,
    val contactInfo: String
)

data class AdoptionEvent(
    val id: String,
    val title: String,
    val photoUrl: String? = null,
    val location: String,
    val date: String,
    val organizerName: String,
    val description: String,
    val contactInfo: String
)
