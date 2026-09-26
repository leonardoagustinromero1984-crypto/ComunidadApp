package com.comunidapp.app.data.local

import android.content.Context
import android.net.Uri
import com.comunidapp.app.LeoverApplication
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.SterilizationStatus
import com.comunidapp.app.viewmodel.PetFormUiState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local pet form draft. Survives background and activity recreation.
 * Never creates a pet in DB. Cleared only on successful save or explicit discard.
 */
object PetFormDraftStore {
    private const val PREFS = "leover_pet_form_draft"
    private const val KEY = "draft_json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun read(userId: String, editPetId: String?): PetFormDraft? {
        val raw = prefs()?.getString(KEY, null) ?: return null
        val draft = runCatching { json.decodeFromString(PetFormDraft.serializer(), raw) }.getOrNull()
            ?: return null
        if (draft.userId != userId) return null
        val expectedEdit = editPetId?.takeIf { it.isNotBlank() }
        if (draft.editPetId != expectedEdit) return null
        return draft
    }

    fun write(draft: PetFormDraft) {
        prefs()?.edit()?.putString(KEY, json.encodeToString(PetFormDraft.serializer(), draft))?.apply()
    }

    fun clear() {
        prefs()?.edit()?.remove(KEY)?.apply()
    }

    private fun prefs() = runCatching {
        LeoverApplication.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }.getOrNull()
}

@Serializable
data class PetFormDraft(
    val userId: String,
    val editPetId: String? = null,
    val name: String = "",
    val species: String = PetSpecies.DOG.name,
    val sex: String = PetSex.UNKNOWN.name,
    val ageYears: Int = 1,
    val ageYearsInput: String = "1",
    val ageMonths: Int = 0,
    val ageMonthsInput: String = "0",
    val size: String = PetSize.MEDIUM.name,
    val description: String = "",
    val sterilized: String? = null,
    val microchipId: String = "",
    val lastVetVisit: String = "",
    val vaccinations: List<DraftVaccination> = emptyList(),
    val pendingVaccineName: String = "",
    val pendingVaccineDate: String = "",
    val pendingVaccineNextDate: String = "",
    val dewormingProduct: String = "",
    val lastDeworming: String = "",
    val nextDeworming: String = "",
    val fleaTreatmentProduct: String = "",
    val lastFleaTreatment: String = "",
    val nextFleaTreatment: String = "",
    val healthNotes: String = "",
    val allergyName: String = "",
    val medicationName: String = "",
    val conditionName: String = "",
    val pendingImageUri: String? = null,
    val breed: String = ""
)

@Serializable
data class DraftVaccination(
    val name: String,
    val date: String,
    val nextDueDate: String? = null
)

fun PetFormUiState.toDraft(userId: String, editPetId: String?): PetFormDraft = PetFormDraft(
    userId = userId,
    editPetId = editPetId?.takeIf { it.isNotBlank() },
    name = name,
    species = speciesCode.ifBlank { species.name },
    sex = sex.name,
    ageYears = ageYears,
    ageYearsInput = ageYearsInput,
    ageMonths = ageMonths,
    ageMonthsInput = ageMonthsInput,
    size = size.name,
    description = description,
    sterilized = sterilized?.name,
    microchipId = microchipId,
    lastVetVisit = lastVetVisit,
    vaccinations = vaccinations.map { DraftVaccination(it.name, it.date, it.nextDueDate) },
    pendingVaccineName = pendingVaccineName,
    pendingVaccineDate = pendingVaccineDate,
    pendingVaccineNextDate = pendingVaccineNextDate,
    dewormingProduct = dewormingProduct,
    lastDeworming = lastDeworming,
    nextDeworming = nextDeworming,
    fleaTreatmentProduct = fleaTreatmentProduct,
    lastFleaTreatment = lastFleaTreatment,
    nextFleaTreatment = nextFleaTreatment,
    healthNotes = healthNotes,
    allergyName = allergyName,
    medicationName = medicationName,
    conditionName = conditionName,
    pendingImageUri = pendingImageUri?.toString(),
    breed = breed
)

fun PetFormDraft.isRecoverableForEdit(fetched: PetFormUiState): Boolean {
    if (editPetId?.takeIf { it.isNotBlank() } != fetched.petId.takeIf { it.isNotBlank() }) return false
    if (fetched.name.isNotBlank() && name.isBlank()) return false
    if (fetched.name.isNotBlank() && name == fetched.name) {
        return (species != fetched.speciesCode && species != fetched.species.name) ||
            sex != fetched.sex.name ||
            breed != fetched.breed ||
            description != fetched.description ||
            pendingImageUri != null ||
            sterilized != fetched.sterilized?.name ||
            lastVetVisit != fetched.lastVetVisit ||
            vaccinations != fetched.vaccinations.map { DraftVaccination(it.name, it.date, it.nextDueDate) }
    }
    return name.isNotBlank()
}

fun PetFormDraft.applyTo(state: PetFormUiState): PetFormUiState = state.copy(
    name = name.takeIf { it.isNotBlank() } ?: state.name,
    species = com.comunidapp.app.domain.pets.PetSpeciesCatalog.toPetSpecies(species),
    speciesCode = species.ifBlank { state.speciesCode },
    sex = runCatching { PetSex.valueOf(sex) }.getOrDefault(state.sex),
    ageYears = ageYears,
    ageYearsInput = ageYearsInput,
    ageMonths = ageMonths,
    ageMonthsInput = ageMonthsInput,
    size = runCatching { PetSize.valueOf(size) }.getOrDefault(state.size),
    description = description,
    sterilized = sterilized?.let { runCatching { SterilizationStatus.valueOf(it) }.getOrNull() },
    microchipId = microchipId,
    lastVetVisit = lastVetVisit,
    vaccinations = vaccinations.map {
        com.comunidapp.app.data.model.VaccinationRecord(it.name, it.date, it.nextDueDate)
    },
    pendingVaccineName = pendingVaccineName,
    pendingVaccineDate = pendingVaccineDate,
    pendingVaccineNextDate = pendingVaccineNextDate,
    dewormingProduct = dewormingProduct,
    lastDeworming = lastDeworming,
    nextDeworming = nextDeworming,
    fleaTreatmentProduct = fleaTreatmentProduct,
    lastFleaTreatment = lastFleaTreatment,
    nextFleaTreatment = nextFleaTreatment,
    healthNotes = healthNotes,
    allergyName = allergyName,
    medicationName = medicationName,
    conditionName = conditionName,
    pendingImageUri = pendingImageUri?.let { runCatching { Uri.parse(it) }.getOrNull() },
    breed = breed
)
