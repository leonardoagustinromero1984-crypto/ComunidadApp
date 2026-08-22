package com.comunidapp.app.data.remote.supabase.canonical

import com.comunidapp.app.data.remote.supabase.VaccinationRecordDto
import com.comunidapp.app.data.remote.supabase.m08.PetM08Row
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CanonicalNamedHealthItem(
    val id: String? = null,
    val name: String? = null,
    @SerialName("vaccine_name") val vaccineName: String? = null,
    val instructions: String? = null,
    val source: String? = null,
    val status: String? = null,
    val kind: String? = null,
    @SerialName("product_name") val productName: String? = null,
    @SerialName("treated_on") val treatedOn: String? = null,
    @SerialName("administered_on") val administeredOn: String? = null,
    val kilograms: Double? = null,
    @SerialName("measured_on") val measuredOn: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CanonicalCareInstructionsDto(
    val feeding: String? = null,
    val medication: String? = null,
    val specials: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class CanonicalPetHealthDto(
    @SerialName("pet_id") val petId: String? = null,
    val allergies: List<CanonicalNamedHealthItem> = emptyList(),
    val medications: List<CanonicalNamedHealthItem> = emptyList(),
    val vaccinations: List<CanonicalNamedHealthItem> = emptyList(),
    @SerialName("parasite_treatments") val parasiteTreatments: List<CanonicalNamedHealthItem> = emptyList(),
    val conditions: List<CanonicalNamedHealthItem> = emptyList(),
    val weights: List<CanonicalNamedHealthItem> = emptyList(),
    @SerialName("care_instructions") val careInstructions: CanonicalCareInstructionsDto? = null,
    @SerialName("declared_notes") val declaredNotes: String? = null
)

fun PetM08Row.withCanonicalHealth(health: CanonicalPetHealthDto): PetM08Row {
    val latestWeight = health.weights.firstOrNull()?.kilograms?.toFloat()
    val deworming = health.parasiteTreatments.firstOrNull {
        it.kind.equals("DEWORMING", ignoreCase = true)
    }
    val flea = health.parasiteTreatments.firstOrNull {
        it.kind.equals("ANTIPARASITIC", ignoreCase = true)
    }
    return copy(
        allergies = health.allergies.mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) },
        medications = health.medications.mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) },
        conditions = health.conditions.mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) },
        vaccinations = health.vaccinations.mapNotNull { item ->
            val name = item.vaccineName?.trim().orEmpty()
            if (name.isEmpty()) null else VaccinationRecordDto(name = name, date = item.administeredOn.orEmpty())
        },
        weightKg = latestWeight ?: weightKg,
        lastDeworming = deworming?.treatedOn ?: lastDeworming,
        dewormingProduct = deworming?.productName ?: dewormingProduct,
        lastFleaTreatment = flea?.treatedOn ?: lastFleaTreatment,
        fleaTreatmentProduct = flea?.productName ?: fleaTreatmentProduct,
        healthNotes = health.careInstructions?.specials ?: health.declaredNotes ?: healthNotes
    )
}
