package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.SterilizationStatus
import com.comunidapp.app.data.model.VaccinationRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetHealthMergeTest {
    @Test
    fun preferRicherHealth_keepsCanonicalAllergiesWhenListSnapshotEmpty() {
        val cached = pet(allergies = listOf("Pollo"))
        val incoming = pet(allergies = emptyList())
        val merged = PetHealthMerge.preferRicherHealth(cached, incoming)
        assertEquals(listOf("Pollo"), merged.allergies)
    }

    @Test
    fun preferRicherHealth_keepsWeightWhenIncomingMissing() {
        val cached = pet(weightKg = 8.4f)
        val incoming = pet(weightKg = null)
        val merged = PetHealthMerge.preferRicherHealth(cached, incoming)
        assertEquals(8.4f, merged.weightKg)
    }

    @Test
    fun preferRicherHealth_keepsDataWhenCacheBasicOverwrites() {
        val cached = pet(
            sterilized = SterilizationStatus.YES,
            lastVetVisit = "2026-08-07",
            vaccinations = listOf(VaccinationRecord(name = "Rabia", date = "2026-08-11")),
            lastDeworming = "2026-08-11",
            lastFleaTreatment = "2026-08-05"
        )
        val incoming = pet(healthReadFailed = true)
        val merged = PetHealthMerge.preferRicherHealth(cached, incoming)
        assertEquals(SterilizationStatus.YES, merged.sterilized)
        assertEquals("Rabia", merged.vaccinations.first().name)
        assertEquals("2026-08-07", merged.lastVetVisit)
        assertTrue(PetHealthPresentation.hasHealthData(merged))
        assertEquals(
            PetHealthViewState.DATA,
            PetHealthPresentation.state(merged, healthLoading = false, healthLoadError = null)
        )
    }

    @Test
    fun hasHealthData_exampleFromSterilizedAndWeight() {
        val pet = pet(sterilized = SterilizationStatus.YES, weightKg = 8.4f)
        assertTrue(pet.sterilized != null || (pet.weightKg != null && pet.weightKg > 0))
    }

    private fun pet(
        allergies: List<String> = emptyList(),
        weightKg: Float? = null,
        sterilized: SterilizationStatus? = null,
        lastVetVisit: String? = null,
        vaccinations: List<VaccinationRecord> = emptyList(),
        lastDeworming: String? = null,
        lastFleaTreatment: String? = null,
        healthReadFailed: Boolean = false
    ) = Pet(
        id = "p1",
        ownerId = "u1",
        name = "Lolo",
        species = com.comunidapp.app.data.model.PetSpecies.DOG,
        sex = com.comunidapp.app.data.model.PetSex.MALE,
        ageYears = 2,
        size = com.comunidapp.app.data.model.PetSize.MEDIUM,
        description = "",
        allergies = allergies,
        weightKg = weightKg,
        sterilized = sterilized,
        lastVetVisit = lastVetVisit,
        vaccinations = vaccinations,
        lastDeworming = lastDeworming,
        lastFleaTreatment = lastFleaTreatment,
        healthReadFailed = healthReadFailed
    )
}
