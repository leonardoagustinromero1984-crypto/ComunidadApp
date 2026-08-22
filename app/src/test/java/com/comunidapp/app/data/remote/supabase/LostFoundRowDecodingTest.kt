package com.comunidapp.app.data.remote.supabase

import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.PetSpecies
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class LostFoundRowDecodingTest {

    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun parseLostFound_mapsPublicCodeFromStagingShape() {
        val element = json.parseToJsonElement(
            """
            [
              {
                "id": "lost-1",
                "author_id": "user-1",
                "author_name": "Rescatista",
                "type": "LOST",
                "pet_name": "Toby",
                "species": "DOG",
                "photo_url": null,
                "location": "Villa Crespo",
                "description": "Se escapó ayer",
                "contact_info": "+5491100000000",
                "status": "ACTIVE",
                "public_code": "PUB-LOSTTEST000000000000000001",
                "latitude": null,
                "longitude": null,
                "created_at": "2026-08-11T01:00:00Z",
                "updated_at": "2026-08-11T01:00:00Z"
              }
            ]
            """.trimIndent()
        )

        val row = SupabaseRowDecoding.decodeRows<LostFoundRow>(element).single()
        val post = parseLostFound(row)

        assertEquals("PUB-LOSTTEST000000000000000001", post.publicCode)
        assertEquals(LostFoundType.LOST, post.type)
        assertEquals(LostFoundStatus.ACTIVE, post.status)
        assertEquals(PetSpecies.DOG, post.species)
        assertEquals("Villa Crespo", post.location)
    }
}
