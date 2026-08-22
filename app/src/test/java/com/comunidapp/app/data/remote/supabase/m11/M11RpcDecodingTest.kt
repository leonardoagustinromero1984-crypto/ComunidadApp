package com.comunidapp.app.data.remote.supabase.m11

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class M11RpcDecodingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodeRow_acceptsBareShelterObject() {
        val element = json.parseToJsonElement(
            """
            {
              "id": "shelter-1",
              "organization_id": "org-1",
              "display_name": "Refugio Norte",
              "status": "ACTIVE",
              "total_capacity": 12,
              "accepted_species": ["DOG", "CAT"]
            }
            """.trimIndent()
        )
        val row = M11RpcDecoding.decodeRow<ShelterProfileRow>(element)
        assertEquals("shelter-1", row.id)
        assertEquals("org-1", row.organizationId)
        assertEquals("Refugio Norte", row.displayName)
    }

    @Test
    fun decodeRow_acceptsOneElementArray() {
        val element = json.parseToJsonElement(
            """
            [{
              "id": "shelter-2",
              "organization_id": "org-2",
              "display_name": "Huellas",
              "status": "DRAFT",
              "total_capacity": 8
            }]
            """.trimIndent()
        )
        val row = M11RpcDecoding.decodeRow<ShelterProfileRow>(element)
        assertEquals("shelter-2", row.id)
        assertEquals("Huellas", row.displayName)
    }
}
