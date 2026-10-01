package com.comunidapp.app.domain.qa

import com.comunidapp.app.data.local.InMemoryOnb02Store
import com.comunidapp.app.data.local.Onb02Completion
import com.comunidapp.app.data.local.Onb02StoreProvider
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.repository.M14PublicQrPayloadService
import com.comunidapp.app.data.repository.M14Validators
import com.comunidapp.app.domain.adoption.AdoptionGeneralProfileCodec
import com.comunidapp.app.domain.canonical.CanonicalProviderWrite
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.location.SharedLocationCapture
import com.comunidapp.app.domain.lostfound.LostFoundCreatePayload
import com.comunidapp.app.domain.map.LeoVerGeoPoint
import com.comunidapp.app.domain.map.LeoVerMapCameraState
import com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind
import com.comunidapp.app.domain.onboarding.onb02.OnboardingEntryIdentity
import com.comunidapp.app.domain.pets.LostPetCasePrefillMapper
import com.comunidapp.app.domain.schedule.AppointmentSlotPolicy
import com.comunidapp.app.domain.user.ProfileAvatarMemory
import com.comunidapp.app.domain.validation.ProviderPublishRequirements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PhysicalQa17AContractTest {

    @Test
    fun googleFirstEntryUsesResolvedPersonAndStartsTutorialBeforeFeed() {
        assertEquals("person-1", OnboardingEntryIdentity.resolve("person-1", null))
        assertEquals("auth-1", OnboardingEntryIdentity.resolve(" ", "auth-1"))
        assertNull(OnboardingEntryIdentity.resolve(null, " "))
        assertTrue(OnboardingEntryIdentity.holdsNavigationUntilTutorial(Onb02Completion.NOT_STARTED))
        assertTrue(OnboardingEntryIdentity.holdsNavigationUntilTutorial(Onb02Completion.FULL_PENDING))
        assertFalse(OnboardingEntryIdentity.holdsNavigationUntilTutorial(Onb02Completion.COMPLETED))

        val store = InMemoryOnb02Store()
        Onb02StoreProvider.override = store
        try {
            val first = Onb02StoreProvider.decideEntry(
                userId = "google-new",
                justCompletedProfileSetup = true,
                personOnboardingComplete = true,
                remoteTutorialFlowCompleted = false
            )
            assertEquals(Onb02FlowKind.FULL_ONBOARDING, first)
            val again = Onb02StoreProvider.decideEntry(
                userId = "google-new",
                justCompletedProfileSetup = false,
                personOnboardingComplete = true,
                remoteTutorialFlowCompleted = false
            )
            assertEquals(Onb02FlowKind.FULL_ONBOARDING, again)
            val reinstallFinished = Onb02StoreProvider.decideEntry(
                userId = "google-returning",
                justCompletedProfileSetup = false,
                personOnboardingComplete = true,
                remoteTutorialFlowCompleted = true
            )
            assertNull(reinstallFinished)
        } finally {
            Onb02StoreProvider.override = null
        }
    }

    @Test
    fun profilePhotoKeepsLocalDisplayForTheSavedAsset() {
        ProfileAvatarMemory.clear()
        val asset = "11111111-1111-4111-8111-111111111111"
        ProfileAvatarMemory.remember("user-1", asset, "content://crop/avatar")
        val saved = User(id = "user-1", name = "Ana", email = "ana@example.com", avatarPath = asset)
        assertEquals("content://crop/avatar", ProfileAvatarMemory.localDisplayFor(saved))
        assertNull(ProfileAvatarMemory.localDisplayFor(saved.copy(avatarPath = "other-asset")))
        assertNull(ProfileAvatarMemory.localDisplayFor(saved.copy(id = "user-2")))
        ProfileAvatarMemory.clear()
        assertNull(ProfileAvatarMemory.localDisplayFor(saved))
    }

    @Test
    fun lostAndFoundCreateDoNotSendAHumanLabelAsLocalityId() {
        assertNull(LostFoundCreatePayload.locationNodeIdOrNull("Palermo, CABA"))
        assertNull(LostFoundCreatePayload.locationNodeIdOrNull("Zona cercana"))
        assertEquals("loc-caba", LostFoundCreatePayload.locationNodeIdOrNull("loc-caba"))
        val photo = "22222222-2222-4222-8222-222222222222"
        assertTrue(LostFoundCreatePayload.isMediaAssetId(photo))
        assertFalse(LostFoundCreatePayload.isMediaAssetId("content://photo"))
        val repo = File("src/main/java/com/comunidapp/app/data/repository/CanonicalConsumerRepositories.kt").readText()
        assertTrue(repo.contains("LostFoundCreatePayload"))
        assertTrue(repo.contains("p_location_label"))
        assertFalse(repo.contains("put(\"p_locality_id\", humanLocation)"))
    }

    @Test
    fun lostPetSelectionPrefillsExistingPhoto() {
        val pet = Pet(
            id = "pet-luna",
            name = "Luna",
            species = PetSpecies.DOG,
            sex = PetSex.FEMALE,
            ageYears = 3,
            size = PetSize.SMALL,
            description = "Juguetona",
            locationText = "Palermo",
            avatarFileAssetId = "33333333-3333-4333-8333-333333333333"
        )
        val prefill = LostPetCasePrefillMapper.from(pet)
        assertEquals("Luna", prefill.name)
        assertEquals(PetSpecies.DOG, prefill.species)
        assertTrue(prefill.hasExistingPhoto)
        assertTrue(prefill.description.contains("Luna"))
        assertEquals("Palermo", prefill.location)
    }

    @Test
    fun sharedLocationDoesNotTreatTheCountryFallbackAsAFix() {
        assertFalse(SharedLocationCapture.shouldRequestPermission(alreadyGranted = true))
        assertTrue(SharedLocationCapture.shouldRequestPermission(alreadyGranted = false))
        val fallback = LeoVerMapCameraState.ARGENTINA_FALLBACK
        assertTrue(SharedLocationCapture.isFallback(fallback.latitude, fallback.longitude))
        assertNull(SharedLocationCapture.realFixOrNull(fallback))
        val real = LeoVerGeoPoint(-34.55, -58.45)
        assertEquals(real, SharedLocationCapture.realFixOrNull(real))
    }

    @Test
    fun canonicalHexPublicCodeBuildsThePetUrl() {
        assertFalse(M14Validators.publicCodeLooksLikePii("012345678901"))
        assertFalse(M14Validators.publicCodeLooksLikePii("PUB-ABCDEF012345"))
        assertTrue(M14Validators.publicCodeLooksLikePii("+54 11 4444-5555"))
        val url = M14PublicQrPayloadService.buildPublicHttpsUrl("0123456789AB").getOrThrow()
        assertTrue(url.startsWith("https://leover.com.ar/mascota/"))
        assertTrue(url.contains("0123456789AB"))
    }

    @Test
    fun adoptionProfileFieldsRoundTrip() {
        val decoded = AdoptionGeneralProfileCodec.decode(
            """{"housing_type":"Casa","motivation":"Quiero adoptar","notes":"Patio"}"""
        )
        assertEquals("Casa", decoded.housing)
        assertEquals("Quiero adoptar", decoded.motivation)
        assertEquals("Patio", decoded.notes)
        assertEquals(AdoptionGeneralProfileCodec.Fields(), AdoptionGeneralProfileCodec.decode("{}"))
        assertEquals(AdoptionGeneralProfileCodec.Fields(), AdoptionGeneralProfileCodec.decode(null))
    }

    @Test
    fun walkerAndOrganizationCanPublishAVisuallyCompleteForm() {
        assertEquals(
            com.comunidapp.app.data.model.ServiceCategory.WALKER,
            CanonicalProviderWrite.categoryFromContext(
                OperationalContext.Provider("walker-1", "Paseador", "WALKING")
            )
        )
        assertEquals(
            com.comunidapp.app.data.model.ServiceCategory.WALKER,
            CanonicalProviderWrite.categoryFromContext(
                OperationalContext.Organization("org-1", "Paseos Norte", "WALKING_CARE")
            )
        )
        assertEquals(
            "WALKING",
            CanonicalProviderWrite.storageCategory(com.comunidapp.app.data.model.ServiceCategory.WALKER)
        )
        val hours = ProviderPublishRequirements.hoursMatchingVisibleDefault(emptyList())
        val ready = ProviderPublishRequirements.summary(
            name = "Paseos Norte",
            location = ProviderPublishRequirements.locationMatchingPin("", -34.55, -58.45),
            phone = "1144556677",
            hours = hours,
            acceptsBookings = true,
            slotIntervalMinutes = AppointmentSlotPolicy.DEFAULT_INTERVAL_MINUTES
        )
        assertTrue(ready.isEmpty)
        val blankName = ProviderPublishRequirements.summary(
            name = "",
            location = "Palermo",
            phone = "1144556677",
            hours = hours,
            acceptsBookings = false,
            slotIntervalMinutes = null
        )
        assertFalse(blankName.isEmpty)
        assertEquals("", ProviderPublishRequirements.locationMatchingPin("", -34.6037, -58.3816))
    }
}
