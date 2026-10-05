package com.comunidapp.app.domain.qa

import com.comunidapp.app.data.model.M14PassportHistory
import com.comunidapp.app.data.model.M14PassportStatus
import com.comunidapp.app.domain.adoption.AdopterProfile
import com.comunidapp.app.domain.adoption.AdopterProfileCompleteness
import com.comunidapp.app.domain.foster.FOSTER_APPLICANTS_EMPTY
import com.comunidapp.app.domain.location.LocationPermissionNext
import com.comunidapp.app.domain.location.LocationPermissionPolicy
import com.comunidapp.app.domain.lostfound.LostFoundWhenLabel
import com.comunidapp.app.domain.lostfound.PublicAlertVisibility
import com.comunidapp.app.domain.m17.CommunityHelpPresentation
import com.comunidapp.app.domain.notifications.NotificationCategory
import com.comunidapp.app.domain.notifications.NotificationPreferenceVisibility
import com.comunidapp.app.domain.onboarding.onb02.ContextualNavigation
import com.comunidapp.app.domain.onboarding.onb02.GuideStep
import com.comunidapp.app.domain.onboarding.onb02.GuideTarget
import com.comunidapp.app.domain.onboarding.onb02.InteractiveOnboardingGuide
import com.comunidapp.app.domain.onboarding.onb02.SecondaryScreenExit
import com.comunidapp.app.domain.organization.OrganizationListContext
import com.comunidapp.app.domain.organization.OrganizationPublicSearch
import com.comunidapp.app.domain.organization.OrganizationScope
import com.comunidapp.app.domain.pets.LostReportDraft
import com.comunidapp.app.domain.pets.LostReportDraftStore
import com.comunidapp.app.domain.pets.PetDisplayName
import com.comunidapp.app.domain.user.PersonSearchMatcher
import com.comunidapp.app.domain.user.ProfileVisibility
import com.comunidapp.app.domain.user.UserPrivacySettings
import com.comunidapp.app.domain.user.UserProfile
import com.comunidapp.app.domain.user.UserProfileMapper
import com.comunidapp.app.domain.vitacora.VitaCoraHistoryDuplicates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PhysicalQa17B12Test {

    @Test
    fun registerConfirmationBecomesValidAsSoonAsThePasswordsMatch() {
        val vm = source("app/src/main/java/com/comunidapp/app/viewmodel/LoginViewModel.kt")
        assertTrue(vm.contains("validatePasswordConfirmation"))
        assertTrue(vm.contains("clearPasswordMismatch"))
        val tests = source("app/src/test/java/com/comunidapp/app/viewmodel/AuthViewModelsTest.kt")
        assertTrue(tests.contains("register_reenables_submit_as_soon_as_confirmation_matches"))
    }

    @Test
    fun interactiveGuideBlocksForeignTapsRestoresAndDoesNotRestart() {
        assertFalse(InteractiveOnboardingGuide.shouldStart(onboardingAlreadyCompleted = true))
        assertTrue(InteractiveOnboardingGuide.shouldStart(onboardingAlreadyCompleted = false))
        val saved = InteractiveOnboardingGuide.start()
        assertEquals(saved, InteractiveOnboardingGuide.restore(saved))
        assertEquals(GuideStep.GENERAL_TUTORIAL, InteractiveOnboardingGuide.restore(null).step)
        var step = InteractiveOnboardingGuide.start()
        InteractiveOnboardingGuide.order.dropLast(1).forEach { current ->
            assertEquals(current, step.step)
            assertFalse(InteractiveOnboardingGuide.allows(step.step, GuideTarget.OTHER))
            val target = when (current) {
                GuideStep.CHOOSE_FUNCTION -> GuideTarget.FUNCTION_CHOICE
                GuideStep.USE_LEOVER_AS -> GuideTarget.USE_AS_CHOICE
                GuideStep.HIGHLIGHT_ADD_FUNCTION -> GuideTarget.ADD_FUNCTION
                else -> GuideTarget.CONTINUE
            }
            val ignored = InteractiveOnboardingGuide.advance(step, GuideTarget.OTHER)
            assertEquals(step, ignored)
            step = InteractiveOnboardingGuide.advance(step, target)
        }
        assertEquals(GuideStep.FEED, step.step)
        assertTrue(step.completed)
        assertEquals(step, InteractiveOnboardingGuide.advance(step, GuideTarget.CONTINUE))
        assertEquals(SecondaryScreenExit.POP_TO_ORIGIN, ContextualNavigation.exitFromAddFunction())
        val nav = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(nav.contains("ADD_FUNCTION_LATER"))
        assertTrue(nav.contains("popBackStack()"))
        assertTrue(nav.contains("AddFunctionSpotlight.request()"))
        val settings = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/SettingsScreen.kt")
        assertTrue(settings.contains("spotlightAddFunction"))
        assertTrue(settings.contains("Desde acá agregás funciones"))
    }

    @Test
    fun phoneAndLocationStayPrivateUntilTheSamePreferenceSaysSo() {
        val profile = UserProfile(
            id = "u1",
            name = "Ana",
            displayName = "Ana",
            username = null,
            email = "ana@example.com",
            city = "CABA",
            locationText = "CABA",
            phone = "+541100000000",
            privacy = UserPrivacySettings(
                profileVisibility = ProfileVisibility.PUBLIC,
                showLocation = false,
                showPhone = false
            )
        )
        val hidden = UserProfileMapper.toPublicUserProfile(profile)
        assertNull(hidden.city)
        assertNull(hidden.locationText)
        assertNull(hidden.phone)
        val shown = UserProfileMapper.toPublicUserProfile(
            profile.copy(
                privacy = profile.privacy.copy(showLocation = true, showPhone = true)
            )
        )
        assertEquals("CABA", shown.city)
        assertEquals("+541100000000", shown.phone)
        val edit = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/EditProfileScreen.kt")
        assertTrue(edit.contains("Mostrar ubicación/zona en mi perfil"))
        assertTrue(edit.contains("Mostrar mi teléfono en mi perfil"))
        val migration = source(
            "infra/supabase-canonical/supabase/migrations/20261004220000_1111_profile_location_phone_privacy.sql"
        )
        assertTrue(migration.contains("phone_public"))
        assertTrue(migration.contains("show_location"))
        assertFalse(migration.contains("apply to production"))
    }

    @Test
    fun manadaSearchMatchesNameOrAliasAndNotEmailOrCity() {
        assertEquals("Buscar por nombre o alias", PersonSearchMatcher.PLACEHOLDER)
        assertTrue(PersonSearchMatcher.matches("an", "Ana", "otra", email = "zzz@x.com", city = "Rosario"))
        assertTrue(PersonSearchMatcher.matches("qa01", "QA01 Owner", "qa01owner"))
        assertFalse(PersonSearchMatcher.matches("ana@", "Ana", "ana", email = "ana@example.com"))
        assertFalse(PersonSearchMatcher.matches("rosario", "Ana", "ana", city = "Rosario"))
        assertFalse(PersonSearchMatcher.matches("a", "Ana", "ana"))
    }

    @Test
    fun essentialNoticesStayOnAndFunctionalOnesFollowCapabilities() {
        assertFalse(NotificationPreferenceVisibility.EMAIL_CHANNEL_VISIBLE)
        NotificationPreferenceVisibility.essential.forEach { category ->
            assertTrue(NotificationPreferenceVisibility.isEssential(category))
            assertFalse(NotificationPreferenceVisibility.configurable(category, setOf("ADOPTIONS", "FOSTER")))
        }
        assertFalse(NotificationPreferenceVisibility.configurable(NotificationCategory.SYSTEM, setOf("PERSONAL")))
        assertFalse(NotificationPreferenceVisibility.configurable(NotificationCategory.OTHER, setOf("PERSONAL")))
        assertTrue(
            NotificationPreferenceVisibility.configurable(NotificationCategory.LOST_FOUND, setOf("PERSONAL"))
        )
        assertFalse(
            NotificationPreferenceVisibility.configurable(NotificationCategory.ADOPTION, setOf("PERSONAL"))
        )
        assertTrue(
            NotificationPreferenceVisibility.configurable(NotificationCategory.ADOPTION, setOf("ADOPTIONS"))
        )
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/NotificationPreferencesScreen.kt")
        assertTrue(screen.contains("Avisos esenciales"))
        assertTrue(screen.contains("EMAIL_CHANNEL_VISIBLE"))
    }

    @Test
    fun lostFoundCardKeepsTwoActionsAndHumanizesTheDate() {
        val card = source("app/src/main/java/com/comunidapp/app/ui/screens/lostfound/LostFoundScreen.kt")
        assertTrue(card.contains("Ver en mapa"))
        assertTrue(card.contains("Aportar información"))
        assertFalse(card.contains("Avistamiento rápido"))
        assertFalse(card.contains("Registrar avistamiento"))
        assertFalse(card.contains("\"Coincidencias\""))
        val label = LostFoundWhenLabel.of("2026-10-01T15:04:00Z")
        assertFalse(label.contains("T15"))
        assertFalse(label.contains("2026-10-01T"))
        assertEquals("Fecha no disponible", LostFoundWhenLabel.of(""))
    }

    @Test
    fun creatingAPetKeepsTheLostReportDraft() {
        LostReportDraftStore.clear()
        val draft = LostReportDraft(
            typeName = "LOST",
            petName = "Luna",
            speciesName = "DOG",
            location = "Belgrano",
            description = "Collar rojo",
            contactInfo = "+5411",
            knownPetIds = setOf("old"),
            imageUri = "content://photo",
            latitude = -34.6,
            longitude = -58.4
        )
        LostReportDraftStore.capture(draft)
        assertEquals("+5411", LostReportDraftStore.peek()?.contactInfo)
        assertEquals("content://photo", LostReportDraftStore.peek()?.imageUri)
        assertEquals(-34.6, LostReportDraftStore.peek()?.latitude)
        assertEquals(setOf("old"), LostReportDraftStore.peek()?.knownPetIds)
        val form = source("app/src/main/java/com/comunidapp/app/ui/screens/publish/PublishForms.kt")
        assertTrue(form.contains("LostReportDraftStore.capture"))
        assertFalse(form.contains("LocationPermissionOnboarding"))
    }

    @Test
    fun settingsOpensOnlyAfterLocationPermissionCannotBeRequested() {
        assertEquals(
            LocationPermissionNext.REQUEST_RUNTIME,
            LocationPermissionPolicy.next(false, false, false, true)
        )
        assertEquals(
            LocationPermissionNext.EXPLAIN_AND_REQUEST,
            LocationPermissionPolicy.next(false, true, true, true)
        )
        assertEquals(
            LocationPermissionNext.OPEN_APP_SETTINGS,
            LocationPermissionPolicy.next(false, false, true, true)
        )
        assertEquals(
            LocationPermissionNext.OPEN_LOCATION_SOURCE_SETTINGS,
            LocationPermissionPolicy.next(true, false, true, false)
        )
    }

    @Test
    fun anOlderActivePublicCaseStaysVisibleToANewerAccount() {
        assertTrue(PublicAlertVisibility.visible(true, 1L, Long.MAX_VALUE))
        assertFalse(PublicAlertVisibility.visible(false, Long.MAX_VALUE, 1L))
        val list = source("app/src/main/java/com/comunidapp/app/viewmodel/LostFoundViewModel.kt")
        assertTrue(list.contains("PublicAlertVisibility.visible"))
        assertFalse(list.contains("accountCreated"))
    }

    @Test
    fun foundCaseWithoutARealNameReadsEncontradoEverywhereItIsPresented() {
        assertEquals("Encontrado", PetDisplayName.of("FOUND_CASE", null))
        assertEquals("Encontrado", PetDisplayName.of("FOUND_CASE", "Sin nombre"))
        assertEquals("Luna", PetDisplayName.of("FOUND_CASE", "Luna"))
        assertNull(PetDisplayName.persistableName("Encontrado"))
        val profile = source("app/src/main/java/com/comunidapp/app/ui/components/v2/V2Foundation.kt")
        assertTrue(profile.contains("PetDisplayName.of"))
    }

    @Test
    fun fosterApplicantsOpenAnEmptyStateInsteadOfDoingNothing() {
        assertEquals("Todavía no hay postulantes", FOSTER_APPLICANTS_EMPTY)
        val nav = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(nav.contains("fosterChooseApplicant(requestId)"))
        val screen = source("app/src/main/java/com/comunidapp/app/ui/screens/foster/FosterCareRequestScreens.kt")
        assertTrue(screen.contains("Ver postulantes"))
        assertTrue(screen.contains("FOSTER_APPLICANTS_EMPTY"))
    }

    @Test
    fun adoptionProfileUsesHumanCopyAndDropsPublicUnknownSex() {
        assertEquals("Perfil de adopción guardado.", AdopterProfileCompleteness.saveFeedback(AdopterProfile()))
        val fields = source("app/src/main/java/com/comunidapp/app/ui/screens/adoptions/AdoptionProfileFields.kt")
        val species = fields.substringAfter("fun SpeciesPrefChips").substringBefore("fun SizePrefChips")
        assertFalse(species.contains("Cualquiera"))
        assertTrue(species.contains("Sin preferencia"))
        assertTrue(fields.contains("FlowRow"))
        val search = source("app/src/main/java/com/comunidapp/app/ui/screens/adoptions/AdoptionsScreen.kt")
        assertTrue(search.contains("PetSex.MALE, PetSex.FEMALE"))
        assertFalse(search.contains("PetSex.entries"))
        val profile = source("app/src/main/java/com/comunidapp/app/ui/screens/adoptions/AdoptionGeneralProfileScreen.kt")
        assertTrue(profile.contains("Contanos un poco sobre tu hogar"))
        assertFalse(profile.contains("Guardaste"))
    }

    @Test
    fun organizationContextKeepsOnlyThatOrganization() {
        val rows = listOf("org-a" to "A", "org-b" to "B")
        assertEquals(listOf("org-a" to "A"), OrganizationScope.keep("org-a", rows) { it.first })
        assertEquals(rows, OrganizationScope.keep(null, rows) { it.first })
        OrganizationListContext.open("org-a")
        assertEquals("org-a", OrganizationListContext.organizationId)
        OrganizationListContext.clear()
        assertNull(OrganizationListContext.organizationId)
        val nav = source("app/src/main/java/com/comunidapp/app/navigation/M16NavGraph.kt")
        assertTrue(nav.contains("OrganizationListContext.open(shelterId)"))
        assertTrue(nav.contains("NavRoutes.ADOPTION_SEARCH"))
        assertTrue(nav.contains("NavRoutes.M17_GOODS"))
        assertTrue(nav.contains("NavRoutes.M17_CAMPAIGNS"))
        val card = source("app/src/main/java/com/comunidapp/app/ui/screens/m16/M16ShelterScreens.kt")
            .substringAfter("fun M16PublicShelterDetailContent")
            .substringBefore("fun M16PublicLogo")
        assertTrue(card.contains("Ver mascotas en adopción"))
        assertTrue(card.contains("Donar cosas"))
        assertTrue(card.contains("Aportar dinero"))
        assertFalse(card.contains("Cupos libres"))
        assertFalse(card.contains("Horarios de atención"))
        assertEquals("Buscar por nombre", OrganizationPublicSearch.PLACEHOLDER)
        assertTrue(OrganizationPublicSearch.matchesName("QA Refugio A", "refugio a"))
        assertFalse(OrganizationPublicSearch.matchesName("QA Refugio A", "Avellaneda"))
        assertTrue(OrganizationPublicSearch.matchesZone("Belgrano, CABA", "Belgrano"))
        val filters = source("app/src/main/java/com/comunidapp/app/ui/screens/m16/M16ShelterScreens.kt")
            .substringAfter("fun M16ListFilterRow")
            .substringBefore("fun M16PublicShelterCard")
        assertFalse(filters.contains("Adopciones"))
        assertFalse(filters.contains("Tránsito"))
        assertFalse(filters.contains("Rescate"))
    }

    @Test
    fun volunteerCopySpeaksInPlacesNotApplicants() {
        assertEquals("Convocatoria", CommunityHelpPresentation.volunteerTitle("Sin postulantes"))
        assertEquals("6 lugares disponibles", CommunityHelpPresentation.slotsLine(0, 6))
        assertEquals("Cupo completo", CommunityHelpPresentation.slotsLine(2, 2))
        assertEquals("1 lugar disponible", CommunityHelpPresentation.slotsLine(5, 6))
    }

    @Test
    fun theSameLostPhotoIsOneHistoryRowAndTheCareMomentWins() {
        val photo = "https://cdn.example/lost.jpg"
        val social = history("social", "SOCIAL", "Publicación en VitaCora", photo)
        val care = history("care", "PHOTO", "Se guardó un recuerdo", photo)
        val other = history("note", "NOTE", "Nota", null)
        val collapsed = VitaCoraHistoryDuplicates.collapse(listOf(social, care, other))
        assertEquals(listOf("care", "note"), collapsed.map { it.id })
        val distinct = VitaCoraHistoryDuplicates.collapse(
            listOf(social, other.copy(mediaDisplayUrl = "https://cdn.example/other.jpg"))
        )
        assertEquals(listOf("social", "note"), distinct.map { it.id })
    }

    private fun history(id: String, event: String, reason: String, media: String?) = M14PassportHistory(
        id = id,
        passportId = "p",
        fromStatus = null,
        toStatus = M14PassportStatus.ACTIVE,
        actorUserId = null,
        reason = reason,
        createdAt = 1L,
        metadataEvent = event,
        mediaDisplayUrl = media
    )

    private fun source(path: String): String {
        val file = listOf(
            File(path),
            File(System.getProperty("user.dir"), path),
            File(System.getProperty("user.dir"), "../$path")
        ).firstOrNull { it.isFile } ?: error("SOURCE_NOT_FOUND:$path")
        return file.readText()
    }
}
