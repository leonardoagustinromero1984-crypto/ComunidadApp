package com.comunidapp.app.data.remote.supabase.canonical

import com.comunidapp.app.data.remote.supabase.SupabaseRowDecoding
import com.comunidapp.app.data.remote.supabase.VaccinationRecordDto
import com.comunidapp.app.data.remote.supabase.m08.PetM08Row
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
    @SerialName("sterilized_status") val sterilizedStatus: String? = null,
    @SerialName("last_vet_visit") val lastVetVisit: String? = null,
    val allergies: List<CanonicalNamedHealthItem> = emptyList(),
    val medications: List<CanonicalNamedHealthItem> = emptyList(),
    val vaccinations: List<CanonicalNamedHealthItem> = emptyList(),
    @SerialName("parasite_treatments") val parasiteTreatments: List<CanonicalNamedHealthItem> = emptyList(),
    val conditions: List<CanonicalNamedHealthItem> = emptyList(),
    val weights: List<CanonicalNamedHealthItem> = emptyList(),
    @SerialName("care_instructions") val careInstructions: CanonicalCareInstructionsDto? = null,
    @SerialName("declared_notes") val declaredNotes: String? = null
)

/** Runtime parser for canon_get_pet_health (bare object, array, or function wrapper). */
object CanonicalPetHealthParser {
    fun parse(element: JsonElement): CanonicalPetHealthDto {
        val unwrapped = SupabaseRowDecoding.unwrapComposite(element)
        return runCatching { SupabaseRowDecoding.decodeRow<CanonicalPetHealthDto>(unwrapped) }
            .recoverCatching { SupabaseRowDecoding.decodeRows<CanonicalPetHealthDto>(unwrapped).first() }
            .getOrThrow()
    }
}

fun CanonicalNamedHealthItem.displayName(): String =
    vaccineName?.trim()?.takeIf { it.isNotEmpty() }
        ?: name?.trim()?.takeIf { it.isNotEmpty() }
        ?: productName?.trim().orEmpty()

fun PetM08Row.withCanonicalHealth(health: CanonicalPetHealthDto): PetM08Row {
    val latestWeight = health.weights.firstOrNull()?.kilograms?.toFloat()
    val deworming = health.parasiteTreatments.firstOrNull {
        it.kind.equals("DEWORMING", ignoreCase = true)
    } ?: health.parasiteTreatments.firstOrNull()
    val flea = health.parasiteTreatments.firstOrNull {
        it.kind.equals("ANTIPARASITIC", ignoreCase = true) ||
            it.kind.equals("FLEA", ignoreCase = true)
    }
    return copy(
        sterilized = health.sterilizedStatus?.takeIf { it.isNotBlank() } ?: sterilized,
        lastVetVisit = health.lastVetVisit?.takeIf { it.isNotBlank() } ?: lastVetVisit,
        allergies = health.allergies.mapNotNull { it.displayName().takeIf(String::isNotEmpty) },
        medications = health.medications.mapNotNull { it.displayName().takeIf(String::isNotEmpty) },
        conditions = health.conditions.mapNotNull { it.displayName().takeIf(String::isNotEmpty) },
        vaccinations = health.vaccinations.mapNotNull { item ->
            val name = item.displayName()
            if (name.isEmpty()) null else VaccinationRecordDto(name = name, date = item.administeredOn.orEmpty())
        },
        weightKg = latestWeight ?: weightKg,
        lastDeworming = deworming?.treatedOn ?: lastDeworming,
        dewormingProduct = deworming?.productName ?: dewormingProduct,
        lastFleaTreatment = flea?.treatedOn ?: lastFleaTreatment,
        fleaTreatmentProduct = flea?.productName ?: fleaTreatmentProduct,
        healthNotes = health.careInstructions?.specials ?: health.declaredNotes ?: healthNotes,
        healthReadFailed = false
    )
}
