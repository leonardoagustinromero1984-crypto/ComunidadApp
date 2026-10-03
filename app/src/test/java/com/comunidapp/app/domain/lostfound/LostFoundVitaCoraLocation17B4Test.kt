package com.comunidapp.app.domain.lostfound

import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.M14PassportHistory
import com.comunidapp.app.data.model.M14PassportStatus
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.domain.location.LocationFixAcceptance
import com.comunidapp.app.domain.location.LocationPermissionNext
import com.comunidapp.app.domain.location.LocationPermissionPolicy
import com.comunidapp.app.domain.location.MapCameraPolicy
import com.comunidapp.app.domain.map.LeoVerGeoPoint
import com.comunidapp.app.domain.map.LeoVerMapCameraState
import com.comunidapp.app.domain.pets.PetCreatePermissionFeedback
import com.comunidapp.app.domain.pets.PetDisplayName
import com.comunidapp.app.domain.social.FeedAfterPublish
import com.comunidapp.app.domain.social.OwnPostActions
import com.comunidapp.app.domain.social.SocialPostMedia
import com.comunidapp.app.domain.user.ManualSessionEpoch
import com.comunidapp.app.domain.user.SessionBoundState
import com.comunidapp.app.domain.vitacora.VitaCoraHistoryDestinationKind
import com.comunidapp.app.domain.vitacora.VitaCoraHistoryNavigation
import com.comunidapp.app.domain.vitacora.VitaCoraHistoryPresentation
import com.comunidapp.app.domain.vitacora.VitaCoraSocialMomentCodec
import com.comunidapp.app.ui.UiRegressionGateTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LostFoundVitaCoraLocation17B4Test {

    @Test
    fun requestablePermission_usesRuntime_notSettings() {
        assertEquals(
            LocationPermissionNext.REQUEST_RUNTIME,
            LocationPermissionPolicy.next(
                granted = false,
                shouldShowRationale = false,
                hasRequestedBefore = false,
                locationServicesEnabled = true
            )
        )
    }

    @Test
    fun deniedButRequestable_explainsAndRequestsAgain() {
        assertEquals(
            LocationPermissionNext.EXPLAIN_AND_REQUEST,
            LocationPermissionPolicy.next(
                granted = false,
                shouldShowRationale = true,
                hasRequestedBefore = true,
                locationServicesEnabled = true
            )
        )
    }

    @Test
    fun blockedPermission_opensAppSettings() {
        assertEquals(
            LocationPermissionNext.OPEN_APP_SETTINGS,
            LocationPermissionPolicy.next(
                granted = false,
                shouldShowRationale = false,
                hasRequestedBefore = true,
                locationServicesEnabled = true
            )
        )
    }

    @Test
    fun returnFromSettings_revalidatesGrantedPermission() {
        val before = LocationPermissionPolicy.next(
            granted = false,
            shouldShowRationale = false,
            hasRequestedBefore = true,
            locationServicesEnabled = true
        )
        val after = LocationPermissionPolicy.next(
            granted = true,
            shouldShowRationale = false,
            hasRequestedBefore = true,
            locationServicesEnabled = true
        )
        assertEquals(LocationPermissionNext.OPEN_APP_SETTINGS, before)
        assertEquals(LocationPermissionNext.ALREADY_GRANTED, after)
    }

    @Test
    fun locationServicesOff_opensLocationSettings_notAppSettings() {
        assertEquals(
            LocationPermissionNext.OPEN_LOCATION_SOURCE_SETTINGS,
            LocationPermissionPolicy.next(
                granted = true,
                shouldShowRationale = false,
                hasRequestedBefore = true,
                locationServicesEnabled = false
            )
        )
    }

    @Test
    fun withoutValidCoordinate_doesNotPresentFalseLocation() {
        val fallback = LeoVerMapCameraState.ARGENTINA_FALLBACK
        assertFalse(MapCameraPolicy.shouldMoveTo(fallback))
        assertFalse(MapCameraPolicy.presentsConfirmedPosition(fallback, confirmed = true))
        assertFalse(MapCameraPolicy.presentsConfirmedPosition(null, confirmed = false))
        assertNull(
            LocationFixAcceptance.confirmedOrNull(
                latitude = fallback.latitude,
                longitude = fallback.longitude,
                ageMs = 0L,
                accuracyMeters = 10f,
                fromLastKnown = false
            )
        )
    }

    @Test
    fun staleLastKnown_isNotConfirmed() {
        val point = LeoVerGeoPoint(-31.42, -64.18)
        assertNull(
            LocationFixAcceptance.confirmedOrNull(
                latitude = point.latitude,
                longitude = point.longitude,
                ageMs = LocationFixAcceptance.LAST_KNOWN_MAX_AGE_MS + 1,
                accuracyMeters = 20f,
                fromLastKnown = true
            )
        )
        assertNull(
            LocationFixAcceptance.confirmedOrNull(
                latitude = point.latitude,
                longitude = point.longitude,
                ageMs = null,
                accuracyMeters = 20f,
                fromLastKnown = true
            )
        )
    }

    @Test
    fun validCoordinate_updatesMap() {
        val point = LeoVerGeoPoint(-31.4201, -64.1888)
        assertTrue(MapCameraPolicy.shouldMoveTo(point))
        assertEquals(
            point,
            LocationFixAcceptance.confirmedOrNull(
                latitude = point.latitude,
                longitude = point.longitude,
                ageMs = 1_000L,
                accuracyMeters = 30f,
                fromLastKnown = true
            )
        )
        assertTrue(MapCameraPolicy.presentsConfirmedPosition(point, confirmed = true))
    }

    @Test
    fun publishLost_returnsToFeed_visibleWithoutManualRefresh() {
        val existing = feed("old", PostType.GENERAL, null)
        val created = feed("lost-1", PostType.LOST_FOUND, "LOST")
        val visible = FeedAfterPublish.merge(listOf(existing), created)
        assertEquals(listOf("lost-1", "old"), visible.map { it.id })
        assertEquals("PERDIDO", LostFoundAlertLabel.forKind(visible.first().alertKind))
    }

    @Test
    fun publishFound_returnsToFeed_visibleWithoutManualRefresh() {
        val created = feed("found-1", PostType.LOST_FOUND, "FOUND")
        val visible = FeedAfterPublish.merge(emptyList(), created)
        assertEquals("found-1", visible.single().id)
        assertEquals("ENCONTRADO", LostFoundAlertLabel.forKind(visible.single().alertKind))
    }

    @Test
    fun refreshDoesNotDuplicatePublishedPost() {
        val created = feed("same", PostType.LOST_FOUND, "LOST")
        val refreshed = listOf(created, feed("older", PostType.GENERAL, null))
        val merged = FeedAfterPublish.merge(refreshed, created)
        assertEquals(listOf("same", "older"), merged.map { it.id })
    }

    @Test
    fun lateResponseFromAnotherSession_doesNotEnterFeed() {
        val epoch = ManualSessionEpoch()
        val state = SessionBoundState(listOf(feed("kept", PostType.GENERAL, null)), epoch)
        val token = epoch.current()
        epoch.invalidate()
        val wrote = state.tryUpdate(token) { current ->
            FeedAfterPublish.merge(current, feed("foreign", PostType.LOST_FOUND, "FOUND"))
        }
        assertFalse(wrote)
        assertEquals(listOf("kept"), state.value.map { it.id })
    }

    @Test
    fun lostShowsPerdido_foundShowsEncontrado_neverCombined() {
        assertEquals("PERDIDO", LostFoundAlertLabel.forKind("LOST"))
        assertEquals("ENCONTRADO", LostFoundAlertLabel.forKind("FOUND"))
        assertNull(LostFoundAlertLabel.forKind(null))
        assertNull(LostFoundAlertLabel.forKind("LOST_FOUND"))
        val card = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoSocialPostCard.kt")
        val legacy = source("app/src/main/java/com/comunidapp/app/ui/components/FeedPostCard.kt")
        assertFalse(card.contains("PERDIDO / ENCONTRADO"))
        assertFalse(legacy.contains("Perdido/Encontrado"))
    }

    @Test
    fun foundAgeUx_isYears_andStorageStaysMonths() {
        assertEquals(24, EstimatedAgeYears.yearsToStoredMonths(2))
        assertEquals(0, EstimatedAgeYears.yearsToStoredMonths(0))
        assertEquals("Edad estimada: 2 años", EstimatedAgeYears.displayLabel(24))
        assertEquals("Edad estimada: 1 año", EstimatedAgeYears.displayLabel(12))
        assertEquals("Edad estimada: menos de 1 año", EstimatedAgeYears.displayLabel(7))
        assertNull(EstimatedAgeYears.yearsToStoredMonths(null))
        val form = source("app/src/main/java/com/comunidapp/app/ui/screens/publish/PublishForms.kt")
        assertTrue(form.contains("Edad estimada (años, opcional)"))
        assertFalse(form.contains("Edad estimada (meses, opcional)"))
        val publish = source("app/src/main/java/com/comunidapp/app/viewmodel/PublishViewModel.kt")
        assertTrue(publish.contains("estimatedAgeMonths"))
    }

    @Test
    fun foundWithoutName_displaysEncontrado_withoutPersistingIt() {
        assertEquals("Encontrado", PetDisplayName.of("FOUND_CASE", null))
        assertEquals("Encontrado", PetDisplayName.of("FOUND_CASE", "Sin nombre"))
        assertEquals("Firulais", PetDisplayName.of("FOUND_CASE", "Firulais"))
        assertEquals("Sin nombre", PetDisplayName.of("STANDARD", ""))
        assertEquals("Luna", PetDisplayName.of("STANDARD", "Luna"))
        assertNull(PetDisplayName.persistableName("Encontrado"))
        assertNull(PetDisplayName.persistableName("  "))
        assertEquals("Firulais", PetDisplayName.persistableName("Firulais"))
        assertEquals(
            "Encontrado",
            PetDisplayName.alertSubject(LostFoundType.FOUND, null, "Perro")
        )
    }

    @Test
    fun unnamedFound_lostActionIsPerdido_namedPetKeepsPerdiA() {
        assertEquals("Perdido", PetDisplayName.lostActionLabel("FOUND_CASE", null))
        assertEquals("Perdido", PetDisplayName.lostActionLabel("FOUND_CASE", "Sin nombre"))
        assertEquals("Perdí a Firulais", PetDisplayName.lostActionLabel("STANDARD", "Firulais"))
        assertEquals("Perdí a Firulais", PetDisplayName.lostActionLabel("FOUND_CASE", "Firulais"))
        assertFalse(PetDisplayName.lostActionLabel("FOUND_CASE", null).contains("Sin nombre"))
        assertFalse(PetDisplayName.lostActionLabel("FOUND_CASE", null).contains("Encontrado"))
    }

    @Test
    fun ownAuthor_hidesReportAndBlock_keepsSave() {
        assertFalse(OwnPostActions.showReport("user-a", "user-a"))
        assertFalse(OwnPostActions.showBlock("user-a", "user-a"))
        assertTrue(OwnPostActions.showReport("user-a", "user-b"))
        assertTrue(OwnPostActions.showBlock("user-a", "user-b"))
        assertTrue(OwnPostActions.showSave())
        val home = source("app/src/main/java/com/comunidapp/app/ui/screens/home/HomeScreen.kt")
        assertTrue(home.contains("OwnPostActions.showReport"))
        assertTrue(home.contains("OwnPostActions.showBlock"))
        assertTrue(home.contains("onSaveClick"))
    }

    @Test
    fun vitacoraLostFound_withTarget_opensDetail_withoutTarget_doesNot() {
        val composition = SocialPostMedia.encode(
            postType = "LOST_FOUND",
            alertKind = "LOST",
            lostFoundCaseId = "case-9"
        )
        val targeted = VitaCoraHistoryNavigation.destination(
            metadataEvent = "SOCIAL",
            reason = "Publicación en VitaCora",
            compositionJson = composition,
            socialContentId = "post-9",
            lostFoundCaseId = null
        )
        assertEquals(VitaCoraHistoryDestinationKind.LOST_FOUND_CASE, targeted?.kind)
        assertEquals("case-9", targeted?.entityId)
        val photo = VitaCoraHistoryNavigation.destination(
            metadataEvent = "PHOTO",
            reason = "Foto del hallazgo",
            compositionJson = null,
            socialContentId = null,
            lostFoundCaseId = "case-photo"
        )
        assertEquals("case-photo", photo?.entityId)
        assertNull(
            VitaCoraHistoryNavigation.destination(
                metadataEvent = "BIRTHDAY",
                reason = "Cumpleaños",
                compositionJson = null,
                socialContentId = null,
                lostFoundCaseId = null
            )
        )
        assertNull(
            VitaCoraHistoryNavigation.destination(
                metadataEvent = "LOST",
                reason = "LOST",
                compositionJson = null,
                socialContentId = null,
                lostFoundCaseId = null
            )
        )
    }

    @Test
    fun memoryWithMedia_showsPreview_andSavedHistoryStaysHuman() {
        val memory = history(
            kind = "PHOTO",
            reason = "Foto del hallazgo",
            media = "https://example.invalid/hallazgo.jpg"
        )
        assertEquals("Se guardó un recuerdo", VitaCoraHistoryPresentation.titleFor(memory))
        assertEquals("Foto del hallazgo", VitaCoraHistoryPresentation.detailFor(memory))
        assertTrue(VitaCoraHistoryPresentation.isPlayableImage(memory))
        val note = history(kind = "MEMORY", reason = "MEMORY", body = "Lo encontré en la plaza")
        assertEquals("Lo encontré en la plaza", VitaCoraHistoryPresentation.detailFor(note))
        val saved = history(
            kind = "SOCIAL",
            reason = "Publicación en VitaCora",
            media = "https://example.invalid/saved.jpg",
            contentKind = "POST"
        )
        assertEquals("Se guardó una publicación", VitaCoraHistoryPresentation.titleFor(saved))
        assertTrue(VitaCoraHistoryPresentation.isPlayableImage(saved))
        val home = source("app/src/main/java/com/comunidapp/app/viewmodel/HomeViewModel.kt")
        assertTrue(home.contains("toggleSavePost"))
        val body = VitaCoraSocialMomentCodec.encode(
            contentId = "post-1",
            compositionJson = SocialPostMedia.encode(alertKind = "FOUND", lostFoundCaseId = "c1"),
            mediaAssetId = "asset-1"
        )
        assertTrue(body.contains("c1"))
    }

    @Test
    fun authorizedCreate_doesNotSurfacePermissionSnackbar() {
        assertTrue(
            PetCreatePermissionFeedback.suppressPermissionErrorAfterAuthorizedCreate(
                createSucceeded = true,
                isEditMode = false
            )
        )
        assertFalse(
            PetCreatePermissionFeedback.suppressPermissionErrorAfterAuthorizedCreate(
                createSucceeded = true,
                isEditMode = true
            )
        )
        assertFalse(
            PetCreatePermissionFeedback.suppressPermissionErrorAfterAuthorizedCreate(
                createSucceeded = false,
                isEditMode = false
            )
        )
    }

    @Test
    fun locationCopy_keepsFirstClarification_andDropsExtendedTreatmentOnScreen() {
        val onboarding = source(
            "app/src/main/java/com/comunidapp/app/ui/screens/location/LocationPermissionOnboarding.kt"
        )
        assertTrue(onboarding.contains("LocationConsentContracts.TITLE"))
        assertTrue(onboarding.contains("LocationConsentContracts.BODY"))
        assertTrue(onboarding.contains("LocationConsentContracts.CTA_ALLOW"))
        assertFalse(onboarding.contains("LocationConsentContracts.TREATMENT"))
        assertFalse(onboarding.contains("LocationConsentContracts.NO_BACKGROUND"))
        assertTrue(onboarding.contains("LocationPermissionPolicy"))
        assertTrue(onboarding.contains("ON_RESUME"))
    }

    private fun feed(id: String, type: PostType, alertKind: String?) = FeedPost(
        id = id,
        authorId = "author",
        authorName = "Ana",
        type = type,
        title = id,
        content = "aviso",
        alertKind = alertKind
    )

    private fun history(
        kind: String,
        reason: String,
        media: String? = null,
        body: String? = null,
        contentKind: String? = null
    ) = M14PassportHistory(
        id = kind,
        passportId = "pet",
        fromStatus = null,
        toStatus = M14PassportStatus.ACTIVE,
        actorUserId = null,
        reason = reason,
        createdAt = 1L,
        metadataEvent = kind,
        mediaDisplayUrl = media,
        sourceContentKind = contentKind,
        mediaDisplayUrls = listOfNotNull(media),
        bodyPreview = body
    )

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
