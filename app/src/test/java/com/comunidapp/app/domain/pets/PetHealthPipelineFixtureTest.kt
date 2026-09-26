package com.comunidapp.app.domain.pets

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.canonical.CanonicalPetHealthParser
import com.comunidapp.app.data.remote.supabase.canonical.withCanonicalHealth
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers
import com.comunidapp.app.data.remote.supabase.m08.PetM08Row
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetHealthPipelineFixtureTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun stagingCanonGetPetHealthJsonBecomesDataState() {
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
              "weights": [],
              "declared_notes": "Chequeo anual"
            }
        """.trimIndent()
        val element: JsonElement = json.parseToJsonElement(payload)
        val dto = com.comunidapp.app.data.remote.supabase.canonical.CanonicalPetHealthParser.parse(element)
        val domain: Pet = with(PetM08Mappers) {
            PetM08Row(
                id = "0d9ef8fa-b5f9-4d01-99e7-fb90d64419cd",
                name = "Samu",
                species = "DOG",
                sex = "MALE",
                size = "MEDIUM"
            ).withCanonicalHealth(dto).toPet()
        }

        assertEquals("YES", dto.sterilizedStatus)
        assertTrue(domain.vaccinations.isNotEmpty())
        assertEquals("Drontal", domain.dewormingProduct)
        assertEquals("2026-08-13", domain.lastVetVisit)
        assertEquals(
            PetHealthViewState.DATA,
            PetHealthPresentation.state(domain, healthLoading = false, healthLoadError = null)
        )
        assertTrue(PetHealthPresentation.hasHealthData(domain))
        assertEquals("YES", domain.sterilized?.name)
        assertEquals("Chequeo anual", domain.healthNotes)
    }

    @Test
    fun postgrestWrappedCanonGetPetHealthJsonStillBecomesData() {
        val inner = """
            {
              "pet_id": "0d9ef8fa-b5f9-4d01-99e7-fb90d64419cd",
              "sterilized_status": "YES",
              "last_vet_visit": "2026-08-13",
              "vaccinations": [
                {"id": "v1", "vaccine_name": "Rabia", "administered_on": "2026-01-10"}
              ],
              "parasite_treatments": [
                {"id": "p1", "kind": "DEWORMING", "product_name": "Drontal", "treated_on": "2026-02-01"}
              ],
              "allergies": [],
              "medications": [],
              "conditions": [],
              "weights": [],
              "declared_notes": "Chequeo anual"
            }
        """.trimIndent()
        val wrapped = """{"canon_get_pet_health": $inner}"""
        val element: JsonElement = json.parseToJsonElement(wrapped)
        val dto = com.comunidapp.app.data.remote.supabase.canonical.CanonicalPetHealthParser.parse(element)
        val domain: Pet = with(PetM08Mappers) {
            PetM08Row(
                id = "0d9ef8fa-b5f9-4d01-99e7-fb90d64419cd",
                name = "Samu",
                species = "DOG",
                sex = "MALE",
                size = "MEDIUM"
            ).withCanonicalHealth(dto).toPet()
        }
        assertEquals(
            PetHealthViewState.DATA,
            PetHealthPresentation.state(domain, healthLoading = false, healthLoadError = null)
        )
        assertEquals("Drontal", domain.dewormingProduct)
        assertEquals("2026-08-13", domain.lastVetVisit)
    }

    @Test
    fun stagingSamuCanonGetPetHealthResultDataBecomesData() {
        val resultData = """{"pet_id": "78068b30-03f1-4d41-82bc-81318a143471", "weights": [], "allergies": [], "conditions": [], "medications": [], "vaccinations": [{"id": "ff141e70-833d-4579-9a4a-6337f787bd66", "source": "DECLARED", "created_at": "2026-08-26T17:11:28.79022+00:00", "vaccine_name": "Rabia", "administered_on": "2026-08-11"}], "declared_notes": null, "last_vet_visit": "2026-08-07", "care_instructions": null, "sterilized_status": "YES", "parasite_treatments": [{"id": "11035b0b-c4c6-402d-bca3-532e59d97a29", "kind": "DEWORMING", "source": "DECLARED", "created_at": "2026-08-26T17:11:29.111477+00:00", "treated_on": "2026-08-11", "product_name": "Piperazina"}, {"id": "b0526829-d918-495e-a75d-32d7bd78ab19", "kind": "ANTIPARASITIC", "source": "DECLARED", "created_at": "2026-08-26T17:11:29.515502+00:00", "treated_on": "2026-08-05", "product_name": "Permetrina (spray)"}]}"""
        val element: JsonElement = json.parseToJsonElement(resultData)
        val dto = CanonicalPetHealthParser.parse(element)
        val domain: Pet = with(PetM08Mappers) {
            PetM08Row(
                id = "78068b30-03f1-4d41-82bc-81318a143471",
                name = "Samu",
                species = "DOG",
                sex = "MALE",
                size = "MEDIUM"
            ).withCanonicalHealth(dto).toPet()
        }
        assertEquals("YES", dto.sterilizedStatus)
        assertEquals("Rabia", domain.vaccinations.firstOrNull()?.name)
        assertEquals("Piperazina", domain.dewormingProduct)
        assertEquals("2026-08-11", domain.lastDeworming)
        assertEquals("Permetrina (spray)", domain.fleaTreatmentProduct)
        assertEquals("2026-08-07", domain.lastVetVisit)
        assertEquals("YES", domain.sterilized?.name)
        assertEquals(
            PetHealthViewState.DATA,
            PetHealthPresentation.state(domain, healthLoading = false, healthLoadError = null)
        )
    }

    @Test
    fun stagingSamuQuotedJsonStringResultDataStillBecomesData() {
        val inner = """{"pet_id": "78068b30-03f1-4d41-82bc-81318a143471", "weights": [], "allergies": [], "conditions": [], "medications": [], "vaccinations": [{"id": "ff141e70-833d-4579-9a4a-6337f787bd66", "source": "DECLARED", "created_at": "2026-08-26T17:11:28.79022+00:00", "vaccine_name": "Rabia", "administered_on": "2026-08-11"}], "declared_notes": null, "last_vet_visit": "2026-08-07", "care_instructions": null, "sterilized_status": "YES", "parasite_treatments": [{"id": "11035b0b-c4c6-402d-bca3-532e59d97a29", "kind": "DEWORMING", "source": "DECLARED", "created_at": "2026-08-26T17:11:29.111477+00:00", "treated_on": "2026-08-11", "product_name": "Piperazina"}, {"id": "b0526829-d918-495e-a75d-32d7bd78ab19", "kind": "ANTIPARASITIC", "source": "DECLARED", "created_at": "2026-08-26T17:11:29.515502+00:00", "treated_on": "2026-08-05", "product_name": "Permetrina (spray)"}]}"""
        val quoted = kotlinx.serialization.json.JsonPrimitive(inner)
        val dto = CanonicalPetHealthParser.parse(quoted)
        val domain: Pet = with(PetM08Mappers) {
            PetM08Row(
                id = "78068b30-03f1-4d41-82bc-81318a143471",
                name = "Samu",
                species = "DOG",
                sex = "MALE",
                size = "MEDIUM"
            ).withCanonicalHealth(dto).toPet()
        }
        assertEquals(
            PetHealthViewState.DATA,
            PetHealthPresentation.state(domain, healthLoading = false, healthLoadError = null)
        )
        assertEquals("Rabia", domain.vaccinations.first().name)
    }

    @Test
    fun stagingSamuCareInstructionsEmptyArrayStillBecomesData() {
        val resultData = """{"pet_id": "78068b30-03f1-4d41-82bc-81318a143471", "weights": [], "allergies": [], "conditions": [], "medications": [], "vaccinations": [{"id": "ff141e70-833d-4579-9a4a-6337f787bd66", "source": "DECLARED", "created_at": "2026-08-26T17:11:28.79022+00:00", "vaccine_name": "Rabia", "administered_on": "2026-08-11"}], "declared_notes": null, "last_vet_visit": "2026-08-07", "care_instructions": [], "sterilized_status": "YES", "parasite_treatments": [{"id": "11035b0b-c4c6-402d-bca3-532e59d97a29", "kind": "DEWORMING", "source": "DECLARED", "created_at": "2026-08-26T17:11:29.111477+00:00", "treated_on": "2026-08-11", "product_name": "Piperazina"}, {"id": "b0526829-d918-495e-a75d-32d7bd78ab19", "kind": "ANTIPARASITIC", "source": "DECLARED", "created_at": "2026-08-26T17:11:29.515502+00:00", "treated_on": "2026-08-05", "product_name": "Permetrina (spray)"}]}"""
        val dto = CanonicalPetHealthParser.parse(json.parseToJsonElement(resultData))
        val domain: Pet = with(PetM08Mappers) {
            PetM08Row(
                id = "78068b30-03f1-4d41-82bc-81318a143471",
                name = "Samu",
                species = "DOG",
                sex = "MALE",
                size = "MEDIUM"
            ).withCanonicalHealth(dto).toPet()
        }
        assertEquals(
            PetHealthViewState.DATA,
            PetHealthPresentation.state(domain, healthLoading = false, healthLoadError = null)
        )
        assertEquals("YES", domain.sterilized?.name)
    }

    @Test
    fun healthReadFailedWithoutDataIsErrorNotEmpty() {
        val pet = Pet(
            id = "p1",
            ownerId = "u1",
            name = "Lolo",
            species = PetSpecies.DOG,
            sex = PetSex.MALE,
            ageYears = 1,
            size = PetSize.MEDIUM,
            description = "",
            healthReadFailed = true
        )
        assertEquals(
            PetHealthViewState.ERROR,
            PetHealthPresentation.state(pet, healthLoading = false, healthLoadError = null)
        )
    }
}
