package com.comunidapp.app.data.remote.supabase.canonical

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetHealthRpcPayloadTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val samu = """{"pet_id":"78068b30-03f1-4d41-82bc-81318a143471","weights":[],"allergies":[],"conditions":[],"medications":[],"vaccinations":[{"id":"ff141e70-833d-4579-9a4a-6337f787bd66","source":"DECLARED","created_at":"2026-08-26T17:11:28.79022+00:00","vaccine_name":"Rabia","administered_on":"2026-08-11"}],"declared_notes":null,"last_vet_visit":"2026-08-07","care_instructions":null,"sterilized_status":"YES","parasite_treatments":[{"id":"11035b0b-c4c6-402d-bca3-532e59d97a29","kind":"DEWORMING","source":"DECLARED","created_at":"2026-08-26T17:11:29.111477+00:00","treated_on":"2026-08-11","product_name":"Piperazina"},{"id":"b0526829-d918-495e-a75d-32d7bd78ab19","kind":"ANTIPARASITIC","source":"DECLARED","created_at":"2026-08-26T17:11:29.515502+00:00","treated_on":"2026-08-05","product_name":"Permetrina (spray)"}]}"""

    @Test
    fun emptyDecodeAsDoesNotWinOverRichRawData() {
        val empty = JsonObject(emptyMap())
        val dto = PetHealthRpcPayload.resolve(empty, samu)
        assertEquals("YES", dto.sterilizedStatus)
        assertEquals("2026-08-07", dto.lastVetVisit)
        assertEquals("Rabia", dto.vaccinations.first().displayName())
        assertEquals("Piperazina", dto.parasiteTreatments.first().productName)
        assertTrue(PetHealthRpcPayload.hasSignals(dto))
    }

    @Test
    fun nestedDataWrapperIsUnwrapped() {
        val wrapped = json.parseToJsonElement("""{"data":$samu}""")
        val dto = PetHealthRpcPayload.resolve(wrapped, null)
        assertEquals("YES", dto.sterilizedStatus)
        assertEquals(1, dto.vaccinations.size)
    }
}
