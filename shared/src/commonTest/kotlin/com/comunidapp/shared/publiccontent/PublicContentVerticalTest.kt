package com.comunidapp.shared.publiccontent

import com.comunidapp.shared.deeplink.DeepLinkTarget
import com.comunidapp.shared.remote.FakePublicContentRemoteGateway
import com.comunidapp.shared.remote.PublicAdoptionDto
import com.comunidapp.shared.remote.PublicLostFoundDto
import com.comunidapp.shared.remote.PublicPetDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PublicContentVerticalTest {

    private fun remote(
        gw: FakePublicContentRemoteGateway = FakePublicContentRemoteGateway()
    ) = RemotePublicContentRepository(gw)

    @Test
    fun pet_success() = runTest {
        val gw = FakePublicContentRemoteGateway(
            pet = PublicPetDto(
                publicCode = "PUB-PET1",
                displayName = "Luna",
                species = "Perro",
                breedText = "Mestiza",
                sex = "Hembra",
                status = "ACTIVE",
                photoUrl = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
            )
        )
        val result = remote(gw).resolve(DeepLinkTarget.PetPublic("PUB-PET1"))
        val success = assertIs<PublicContentResult.Success>(result)
        val pet = assertIs<PublicContent.Pet>(success.content)
        assertEquals("Luna", pet.displayName)
        assertEquals("PUB-PET1", pet.publicCode)
        assertEquals(1, gw.petCalls)
    }

    @Test
    fun passport_uses_pet_rpc() = runTest {
        val gw = FakePublicContentRemoteGateway(
            pet = PublicPetDto(
                publicCode = "PUB-PASS1",
                displayName = "Michi",
                status = "ACTIVE"
            )
        )
        val result = remote(gw).resolve(DeepLinkTarget.Passport("PUB-PASS1"))
        assertIs<PublicContentResult.Success>(result)
        assertEquals(1, gw.petCalls)
    }

    @Test
    fun adoption_success() = runTest {
        val gw = FakePublicContentRemoteGateway(
            adoption = PublicAdoptionDto(
                publicCode = "PUB-AD1",
                name = "Rocky",
                status = "PUBLISHED",
                isActive = true,
                species = "Perro"
            )
        )
        val result = remote(gw).resolve(DeepLinkTarget.AdoptionPublic("PUB-AD1"))
        val success = assertIs<PublicContentResult.Success>(result)
        assertIs<PublicContent.Adoption>(success.content)
    }

    @Test
    fun lost_and_found_success() = runTest {
        val gw = FakePublicContentRemoteGateway(
            lost = PublicLostFoundDto(
                publicCode = "PUB-L1",
                caseType = "LOST",
                petName = "Luna",
                status = "ACTIVE",
                isActive = true
            ),
            found = PublicLostFoundDto(
                publicCode = "PUB-F1",
                caseType = "FOUND",
                species = "Gato",
                status = "ACTIVE",
                isActive = true
            )
        )
        val lost = assertIs<PublicContentResult.Success>(
            remote(gw).resolve(DeepLinkTarget.LostCase("PUB-L1"))
        )
        assertEquals(PublicLostFoundCaseType.LOST, (lost.content as PublicContent.LostFound).caseType)
        val found = assertIs<PublicContentResult.Success>(
            remote(gw).resolve(DeepLinkTarget.FoundCase("PUB-F1"))
        )
        assertEquals(PublicLostFoundCaseType.FOUND, (found.content as PublicContent.LostFound).caseType)
    }

    @Test
    fun not_found() = runTest {
        val result = remote().resolve(DeepLinkTarget.PetPublic("MISSING"))
        assertIs<PublicContentResult.NotFound>(result)
    }

    @Test
    fun not_public_error_maps_to_not_found() = runTest {
        val gw = FakePublicContentRemoteGateway(
            petError = IllegalStateException("NOT_PUBLIC")
        )
        assertIs<PublicContentResult.NotFound>(
            remote(gw).resolve(DeepLinkTarget.PetPublic("X"))
        )
    }

    @Test
    fun malformed_lost_case_type() = runTest {
        val gw = FakePublicContentRemoteGateway(
            lost = PublicLostFoundDto(
                publicCode = "PUB-BAD",
                caseType = "WEIRD",
                status = "ACTIVE"
            )
        )
        assertIs<PublicContentResult.NotFound>(
            remote(gw).resolve(DeepLinkTarget.LostCase("PUB-BAD"))
        )
    }

    @Test
    fun network_error() = runTest {
        val gw = FakePublicContentRemoteGateway(
            petError = IllegalStateException("NETWORK timeout")
        )
        assertIs<PublicContentResult.NetworkError>(
            remote(gw).resolve(DeepLinkTarget.PetPublic("X"))
        )
    }

    @Test
    fun unsupported_unavailable() = runTest {
        val result = remote().resolve(DeepLinkTarget.Unsupported("blank"))
        assertIs<PublicContentResult.Unavailable>(result)
    }

    @Test
    fun unconfigured() = runTest {
        val result = UnconfiguredPublicContentRepository()
            .resolve(DeepLinkTarget.PetPublic("PUB-1"))
        assertIs<PublicContentResult.Unconfigured>(result)
    }

    @Test
    fun storage_prefix_photo_null() = runTest {
        val gw = FakePublicContentRemoteGateway(
            pet = PublicPetDto(
                publicCode = "PUB-PET2",
                displayName = "Luna",
                status = "ACTIVE",
                photoUrl = "storage:bucket/path.jpg"
            )
        )
        val success = assertIs<PublicContentResult.Success>(
            remote(gw).resolve(DeepLinkTarget.PetPublic("PUB-PET2"))
        )
        assertNull((success.content as PublicContent.Pet).photo)
    }

    @Test
    fun no_pii_markers_in_safe_models() {
        val pet = PublicContent.Pet(
            publicCode = "PUB-1",
            displayName = "Luna",
            species = "Perro",
            breedText = null,
            sex = null,
            status = "ACTIVE",
            photo = null
        )
        val text = pet.toString().lowercase()
        assertFalse(text.contains("email"))
        assertFalse(text.contains("phone"))
        assertFalse(text.contains("ownerid") || text.contains("owner_id"))
        assertFalse(text.contains("authorid") || text.contains("author_id"))
        assertFalse(text.contains("latitude") || text.contains("longitude"))
        assertTrue(DeepLinkTarget.PetPublic("X").isPublicContentTarget())
        assertFalse(DeepLinkTarget.SafeHome.isPublicContentTarget())
    }
}
