package com.comunidapp.app.data.remote.supabase.m08

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toPet
import com.comunidapp.app.data.remote.supabase.m08.PetM08Mappers.toUpdateProfileParams
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PetM08MappersTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun updateProfileParams_doesNotSerializePhotoOrAvatarFields() {
        val pet = Pet(
            id = "pet-1",
            ownerId = "user-1",
            name = "Luna",
            photoUrl = "https://should-not-be-sent.example/pet.jpg",
            species = PetSpecies.DOG,
            sex = PetSex.FEMALE,
            ageYears = 2,
            size = PetSize.MEDIUM,
            description = "Amigable",
            avatarFileAssetId = "c3febf69-e1a5-45d8-98aa-e5f07e2ec085"
        )
        val encoded = json.encodeToString(
            UpdatePetProfileParams.serializer(),
            pet.toUpdateProfileParams()
        )
        assertFalse(encoded.contains("photo_url"))
        assertFalse(encoded.contains("photoUrl"))
        assertFalse(encoded.contains("avatar_file_asset_id"))
        assertFalse(encoded.contains("avatarFileAssetId"))
        assertFalse(encoded.contains("c3febf69"))
        assertFalse(encoded.contains("should-not-be-sent"))
    }

    @Test
    fun toPet_mapsAvatarFileAssetIdAndLeavesLegacyPhotoNull() {
        val row = PetM08Row(
            id = "pet-avatar",
            name = "Lola",
            species = "DOG",
            sex = "FEMALE",
            ageYears = 1,
            ageMonths = 0,
            size = "MEDIUM",
            description = "",
            photoUrl = null,
            avatarFileAssetId = "c3febf69-e1a5-45d8-98aa-e5f07e2ec085"
        )
        val pet = row.toPet()
        assertEquals("c3febf69-e1a5-45d8-98aa-e5f07e2ec085", pet.avatarFileAssetId)
        assertNull(pet.photoUrl)
    }
}
