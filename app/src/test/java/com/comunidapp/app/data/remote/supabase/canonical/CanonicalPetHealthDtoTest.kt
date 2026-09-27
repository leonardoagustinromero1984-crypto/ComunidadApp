package com.comunidapp.app.data.remote.supabase.canonical

import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.m08.PetM08Row
import com.comunidapp.app.data.model.SterilizationStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalPetHealthDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesSterilizedVaccinationAndParasiteTreatment() {
        val payload = """
            {
              "pet_id": "0d9ef8fa-b5f9-4d01-99e7-fb90d64419cd",
              "sterilized_status": "YES",
              "last_vet_visit": "2026-08-13",
              "vaccinations": [
                {"id": "v1", "vaccine_name": "Rabia", "administered_on": "2026-01-10"}
              ],
              "parasite_treatments": [
                {"id": "p1", "kind": "DEWORMING", "product_name": "Drontal", "treated_on": "2026-02-01"},
                {"id": "p2", "kind": "ANTIPARASITIC", "product_name": "Frontline", "treated_on": "2026-03-01"}
              ],
              "allergies": [],
              "medications": [],
              "conditions": [],
              "weights": []
            }
        """.trimIndent()
        val element: JsonElement = json.parseToJsonElement(payload)
        val dto = M08RpcDecoding.decodeRow<CanonicalPetHealthDto>(element)
        val merged = PetM08Row(
            id = "0d9ef8fa-b5f9-4d01-99e7-fb90d64419cd",
            name = "Samu",
            species = "DOG",
            sex = "MALE",
            size = "MEDIUM"
        ).withCanonicalHealth(dto)
        assertEquals("YES", merged.sterilized)
        assertEquals("2026-08-13", merged.lastVetVisit)
        assertEquals(1, merged.vaccinations?.size)
        assertEquals("Drontal", merged.dewormingProduct)
        assertEquals("Frontline", merged.fleaTreatmentProduct)
        assertEquals(SterilizationStatus.YES, SterilizationStatus.fromString(merged.sterilized))
        assertFalse(merged.healthReadFailed)
    }

    @Test
    fun hasHealthDataWhenSterilizedPresent() {
        val dto = CanonicalPetHealthDto(sterilizedStatus = "YES", lastVetVisit = "2026-08-13")
        val merged = PetM08Row(
            id = "pet",
            name = "Samu",
            species = "DOG",
            sex = "UNKNOWN",
            size = "MEDIUM"
        ).withCanonicalHealth(dto)
        assertTrue(!merged.sterilized.isNullOrBlank())
    }
}
