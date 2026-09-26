package com.comunidapp.app.data.remote.supabase.m08

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M08RpcDecodingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodeRow_acceptsBarePetObject() {
        val element = json.parseToJsonElement(
            """
            {
              "id": "pet-1",
              "owner_id": "user-1",
              "name": "Luna",
              "species": "DOG",
              "sex": "FEMALE",
              "age_years": 2,
              "age_months": 3,
              "size": "MEDIUM",
              "description": "Dulce",
              "status": "ACTIVE"
            }
            """.trimIndent()
        )
        val row = M08RpcDecoding.decodeRow<PetM08Row>(element)
        assertEquals("pet-1", row.id)
        assertEquals("Luna", row.name)
        assertEquals(2, row.ageYears)
        assertEquals(3, row.ageMonths)
    }

    @Test
    fun decodeRow_acceptsOneElementArray() {
        val element = json.parseToJsonElement(
            """
            [{
              "id": "pet-2",
              "name": "Milo",
              "species": "CAT",
              "sex": "MALE",
              "age_years": 1,
              "age_months": 0,
              "size": "SMALL",
              "description": "Tranquilo",
              "status": "ACTIVE"
            }]
            """.trimIndent()
        )
        val row = M08RpcDecoding.decodeRow<PetM08Row>(element)
        assertEquals("pet-2", row.id)
        assertEquals("Milo", row.name)
    }

    @Test
    fun decodeRow_unwrapsFunctionNameWrapper() {
        val element = json.parseToJsonElement(
            """
            {
              "m08_create_pet_with_principal": {
                "id": "pet-3",
                "name": "Nina",
                "species": "DOG",
                "sex": "FEMALE",
                "age_years": 0,
                "age_months": 8,
                "size": "SMALL",
                "description": "Cachorra",
                "status": "ACTIVE",
                "vaccinations": [],
                "reminders": [],
                "weight_kg": null
              }
            }
            """.trimIndent()
        )
        val row = M08RpcDecoding.decodeRow<PetM08Row>(element)
        assertEquals("pet-3", row.id)
        assertEquals("Nina", row.name)
        assertTrue(row.vaccinations.isNullOrEmpty())
    }

    @Test
    fun decodeRow_parsesJsonbStringsAndNumericWeight() {
        val element = json.parseToJsonElement(
            """
            {
              "id": "pet-4",
              "name": "Toby",
              "species": "DOG",
              "sex": "MALE",
              "age_years": 4,
              "age_months": 2,
              "size": "MEDIUM",
              "description": "Juguetón",
              "status": "ACTIVE",
              "vaccinations": "[{\"name\":\"Rabia\",\"date\":\"2026-08-06\",\"next_due_date\":\"2027-08-06\"}]",
              "reminders": "[{\"id\":\"next_deworming\",\"title\":\"Próxima desparasitación\",\"date\":\"2026-12-06\",\"type\":\"NEXT_DEWORMING\"}]",
              "weight_kg": "12.50",
              "created_at": "2026-07-21T19:59:30.305436+00:00"
            }
            """.trimIndent()
        )
        val row = M08RpcDecoding.decodeRow<PetM08Row>(element)
        assertEquals("Toby", row.name)
        assertEquals(12.50f, row.weightKg ?: 0f, 0.01f)
        assertEquals(1, row.vaccinations?.size)
        assertEquals("2027-08-06", row.vaccinations?.first()?.nextDueDate)
        assertEquals("2026-12-06", row.reminders?.first()?.date)
    }

    @Test
    fun decodeRow_acceptsRealisticRemotePetShape() {
        val element = json.parseToJsonElement(
            """
            {
              "id": "31d53f2c-756e-4bd2-b7fb-24829b81bf40",
              "owner_id": "31d53f2c-756e-4bd2-b7fb-24829b81bf40",
              "name": "Lola",
              "photo_url": null,
              "species": "DOG",
              "sex": "FEMALE",
              "age_years": 3,
              "age_months": 0,
              "size": "MEDIUM",
              "description": "Mestiza",
              "vaccinations": [],
              "last_deworming": "2026-08-06",
              "deworming_product": null,
              "last_flea_treatment": "2026-08-06",
              "flea_treatment_product": null,
              "sterilized": "YES",
              "microchip_id": null,
              "last_vet_visit": null,
              "health_notes": null,
              "weight_kg": null,
              "color": null,
              "breed": null,
              "personality": null,
              "location_text": null,
              "reminders": [],
              "created_at": "2026-07-21T19:59:30.305436+00:00",
              "updated_at": "2026-08-14T12:00:00+00:00",
              "status": "ACTIVE",
              "deceased_at": null,
              "archived_at": null,
              "avatar_file_asset_id": null
            }
            """.trimIndent()
        )
        val row = M08RpcDecoding.decodeRow<PetM08Row>(element)
        assertEquals("Lola", row.name)
        assertEquals("YES", row.sterilized)
        assertEquals("2026-08-06", row.lastDeworming)
        assertNull(row.weightKg)
        assertTrue(row.vaccinations.isNullOrEmpty())
    }

    @Test
    fun decodeRows_acceptsAccessiblePetList() {
        val element = json.parseToJsonElement(
            """
            [{
              "id": "pet-5",
              "name": "Mora",
              "species": "CAT",
              "sex": "FEMALE",
              "age_years": 1,
              "age_months": 2,
              "size": "SMALL",
              "description": "",
              "status": "ACTIVE",
              "relation_code": "PRINCIPAL",
              "capabilities": "[\"UPDATE\",\"MANAGE_HEALTH\"]",
              "can_update": true,
              "can_manage_health": true,
              "can_manage_media": true
            }]
            """.trimIndent()
        )
        val rows = M08RpcDecoding.decodeRows<AccessiblePetM08Row>(element)
        assertEquals(1, rows.size)
        assertEquals("Mora", rows.first().name)
        assertTrue(rows.first().canUpdate)
        assertEquals(2, rows.first().capabilities.size)
    }

    @Test
    fun decodeRow_mapsAvatarFileAssetId() {
        val element = json.parseToJsonElement(
            """
            {
              "id": "pet-avatar",
              "name": "Lola",
              "species": "DOG",
              "sex": "FEMALE",
              "age_years": 1,
              "age_months": 0,
              "size": "MEDIUM",
              "description": "",
              "status": "ACTIVE",
              "photo_url": null,
              "avatar_file_asset_id": "c3febf69-e1a5-45d8-98aa-e5f07e2ec085"
            }
            """.trimIndent()
        )
        val row = M08RpcDecoding.decodeRow<PetM08Row>(element)
        assertEquals("c3febf69-e1a5-45d8-98aa-e5f07e2ec085", row.avatarFileAssetId)
        assertEquals(null, row.photoUrl)
    }

    @kotlinx.serialization.Serializable
    private data class VitacoraSearchRow(
        @kotlinx.serialization.SerialName("target_kind") val targetKind: String,
        @kotlinx.serialization.SerialName("target_id") val targetId: String,
        @kotlinx.serialization.SerialName("display_name") val displayName: String,
        val subtitle: String? = null,
        val verified: Boolean = false
    )

    @Test
    fun decodeRows_acceptsVitacoraAccessTargetJsonbArray() {
        val element = json.parseToJsonElement(
            """
            [{
              "target_kind": "ORGANIZATION",
              "target_id": "c9b96602-18cf-40d8-a932-0bc533a32863",
              "display_name": "vet prueba",
              "subtitle": "VETERINARY_CLINIC",
              "verified": false
            }]
            """.trimIndent()
        )
        val rows = M08RpcDecoding.decodeRows<VitacoraSearchRow>(element)
        assertEquals(1, rows.size)
        assertEquals("vet prueba", rows.first().displayName)
        assertEquals("ORGANIZATION", rows.first().targetKind)
    }
}
