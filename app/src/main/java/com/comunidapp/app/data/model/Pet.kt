package com.comunidapp.app.data.model

data class VaccinationRecord(
    val name: String,
    val date: String,
    val nextDueDate: String? = null
)

data class PetReminder(
    val id: String,
    val title: String,
    val date: String,
    val type: String
)

data class Pet(
    val id: String,
    /** Null when principal is organization-only; never empty string. */
    val ownerId: String? = null,
    val name: String,
    val photoUrl: String? = null,
    val species: PetSpecies,
    val sex: PetSex,
    val ageYears: Int,
    val ageMonths: Int = 0,
    val size: PetSize,
    val description: String,
    val vaccinations: List<VaccinationRecord> = emptyList(),
    val lastDeworming: String? = null,
    val dewormingProduct: String? = null,
    val lastFleaTreatment: String? = null,
    val fleaTreatmentProduct: String? = null,
    val sterilized: SterilizationStatus? = null,
    val microchipId: String? = null,
    val lastVetVisit: String? = null,
    val healthNotes: String? = null,
    val weightKg: Float? = null,
    val color: String? = null,
    val breed: String? = null,
    val personality: String? = null,
    val locationText: String? = null,
    val reminders: List<PetReminder> = emptyList(),
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val status: String = "ACTIVE",
    val deceasedAt: Long? = null,
    val archivedAt: Long? = null,
    val avatarFileAssetId: String? = null,
    val microchipNormalized: String? = null
)
