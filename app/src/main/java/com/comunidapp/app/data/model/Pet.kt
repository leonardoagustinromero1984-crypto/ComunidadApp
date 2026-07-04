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
    val ownerId: String,
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
    val reminders: List<PetReminder> = emptyList(),
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)
