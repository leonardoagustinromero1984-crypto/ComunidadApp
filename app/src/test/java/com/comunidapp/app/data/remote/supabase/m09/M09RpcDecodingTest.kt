package com.comunidapp.app.data.remote.supabase.m09

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M09RpcDecodingTest {

    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun decodeRow_acceptsBareCompositeObject_withPublicCode() {
        val element = json.parseToJsonElement(
            """
            {
              "id": "adoption-1",
              "publisher_id": "user-1",
              "publisher_name": "Rescatista",
              "pet_id": "pet-1",
              "name": "Luna",
              "title": "Luna busca hogar",
              "species": "DOG",
              "sex": "FEMALE",
              "age_years": 2,
              "age_months": 0,
              "size": "MEDIUM",
              "location": "Palermo",
              "location_text": "Palermo",
              "description": "Muy dulce",
              "requirements": "",
              "status": "PUBLISHED",
              "public_code": "PUB-ADOPTIONTEST0000000000000001",
              "published_at": "2026-08-11T01:00:00Z",
              "created_at": "2026-08-11T01:00:00Z",
              "updated_at": "2026-08-11T01:00:00Z"
            }
            """.trimIndent()
        )

        val row = M09RpcDecoding.decodeRow<AdoptionPublicationRow>(element)

        assertEquals("adoption-1", row.id)
        assertEquals("PUBLISHED", row.status)
        assertEquals("PUB-ADOPTIONTEST0000000000000001", row.publicCode)
    }

    @Test
    fun decodeRows_acceptsSetofArray_withPublicCode() {
        val element = json.parseToJsonElement(
            """
            [
              {
                "id": "adoption-2",
                "publisher_id": "user-1",
                "publisher_name": "Rescatista",
                "name": "Milo",
                "species": "CAT",
                "sex": "MALE",
                "age_years": 1,
                "age_months": 3,
                "size": "SMALL",
                "location": "Belgrano",
                "description": "Tranquilo",
                "status": "PUBLISHED",
                "public_code": "PUB-ADOPTIONTEST0000000000000002"
              }
            ]
            """.trimIndent()
        )

        val rows = M09RpcDecoding.decodeRows<AdoptionPublicationRow>(element)

        assertEquals(1, rows.size)
        assertEquals("PUB-ADOPTIONTEST0000000000000002", rows.single().publicCode)
    }

    @Test
    fun codeOf_mapsJsonDecodeErrorToSerialization() {
        val code = M09AdoptionErrorMapper.codeOf(
            IllegalStateException(
                "Unexpected JSON token at offset 0: Expected start of the array '[', but had '{' instead"
            )
        )
        assertEquals("SERIALIZATION", code)
        assertTrue(M09AdoptionErrorMapper.userMessage(code).contains("interpretar"))
    }
}
