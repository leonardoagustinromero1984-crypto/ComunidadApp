package com.comunidapp.app.domain.pets

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.files.FileDisplayResolver
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.repository.MockFileAssetRepository
import com.comunidapp.app.data.repository.MockFileDownloadRepository
import com.comunidapp.app.domain.files.FileAsset
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetStatus
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.authorization.FileAuthContext
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetPhotoResolverTest {

    private val sampleAssetId = "c3febf69-e1a5-45d8-98aa-e5f07e2ec085"

    @Test
    fun prefersAvatarAssetOverLegacyPhotoUrl() = runTest {
        val fixture = Fixture()
        fixture.seedReadyPetAvatar(sampleAssetId)
        val resolver = fixture.displayResolver()
        val pet = samplePet(
            photoUrl = "https://legacy.example/old.jpg",
            avatarFileAssetId = sampleAssetId
        )
        val url = PetPhotoResolver.displayUrl(pet, actorUserId = "user-1", resolver = resolver)
        assertTrue(url.orEmpty().startsWith("https://"))
        assertTrue(url != "https://legacy.example/old.jpg")
        assertTrue(url.orEmpty().contains("/signed/$sampleAssetId"))
        assertFalse(url.orEmpty().contains("users/"))
    }

    @Test
    fun legacyHttpsPhotoUrlIsUsedWhenNoAsset() = runTest {
        val resolver = Fixture().displayResolver()
        val pet = samplePet(photoUrl = "https://legacy.example/pet.jpg", avatarFileAssetId = null)
        val url = PetPhotoResolver.displayUrl(pet, actorUserId = "user-1", resolver = resolver)
        assertEquals("https://legacy.example/pet.jpg", url)
    }

    @Test
    fun blankPetHasNoDisplayUrl() = runTest {
        val resolver = Fixture().displayResolver()
        val url = PetPhotoResolver.displayUrl(
            samplePet(photoUrl = null, avatarFileAssetId = null),
            actorUserId = "user-1",
            resolver = resolver
        )
        assertNull(url)
    }

    @Test
    fun assetIdResolvesToSignedHttpsNotStoragePath() = runTest {
        val fixture = Fixture()
        fixture.seedReadyPetAvatar(sampleAssetId)
        val url = PetPhotoResolver.displayUrl(
            samplePet(avatarFileAssetId = sampleAssetId),
            actorUserId = "user-1",
            resolver = fixture.displayResolver()
        )
        assertTrue(url.orEmpty().startsWith("https://mock.leover.local/signed/"))
        assertFalse(url.orEmpty().contains("public-media"))
        val resolved = fixture.displayResolver().resolve(
            sampleAssetId,
            null,
            FileAuthContext(actorUserId = "user-1")
        )
        assertTrue(resolved is AppResult.Success)
    }

    @Test
    fun sanitizeObjectPathKeepsTailOnly() {
        val path = "users/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/pets/pet-1/asset-1/pet.jpg"
        assertEquals(".../pet-1/asset-1/pet.jpg", PetPhotoResolver.sanitizeObjectPath(path))
    }

    private fun samplePet(
        photoUrl: String? = null,
        avatarFileAssetId: String? = null
    ) = Pet(
        id = "pet-1",
        ownerId = "user-1",
        name = "Luna",
        photoUrl = photoUrl,
        species = PetSpecies.DOG,
        sex = PetSex.FEMALE,
        ageYears = 1,
        size = PetSize.MEDIUM,
        description = "Test",
        avatarFileAssetId = avatarFileAssetId
    )

    private class Fixture {
        val assets = MockFileAssetRepository()

        fun displayResolver() = FileDisplayResolver(
            assets,
            MockFileDownloadRepository(assets)
        )

        fun seedReadyPetAvatar(assetId: String) {
            val asset = FileAsset(
                id = assetId,
                owner = FileAssetOwner.User("user-1"),
                purpose = FileAssetPurpose.PET_AVATAR,
                visibility = FileAssetVisibility.PUBLIC,
                status = FileAssetStatus.READY,
                createdByUserId = "user-1",
                createdAtEpochMs = 1_000L,
                updatedAtEpochMs = 1_000L
            )
            val field = MockFileAssetRepository::class.java.getDeclaredField("assets")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val map = field.get(assets) as ConcurrentHashMap<String, FileAsset>
            map[assetId] = asset
        }
    }
}
