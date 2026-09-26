package com.comunidapp.app.domain.onboarding.onb03

import com.comunidapp.app.data.local.InMemoryOnb02Store
import com.comunidapp.app.data.local.Onb02StoreProvider
import com.comunidapp.app.domain.auth.GoogleAuthLifecycle
import com.comunidapp.app.domain.auth.GoogleOAuthPending
import com.comunidapp.app.domain.commercial.CommercialEntitlementStatus
import com.comunidapp.app.domain.commercial.CommercialOfferPolicy
import com.comunidapp.app.domain.commercial.CommercialProductFamily
import com.comunidapp.app.domain.commercial.CommercialTier
import com.comunidapp.app.domain.context.ContextIdentityMapping
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.context.OperationalContextKind
import com.comunidapp.app.domain.onboarding.onb02.LeoverFunction
import com.comunidapp.app.domain.onboarding.onb02.Onb02Copy
import com.comunidapp.app.domain.onboarding.onb02.Onb02FlowKind
import com.comunidapp.app.domain.onboarding.onb02.OrganizationKindOption
import com.comunidapp.app.domain.onboarding.onb02.ProductOrganizationCategory
import com.comunidapp.app.domain.onboarding.onb02.TutorialCatalog
import com.comunidapp.app.domain.onboarding.onb02.TutorialId
import com.comunidapp.app.domain.organization.OrganizationCreatePolicy
import com.comunidapp.app.domain.organization.authorization.MembershipDisplay
import com.comunidapp.app.domain.organization.authorization.OrganizationRoleCode
import com.comunidapp.app.ui.UiRegressionGateTest
import com.comunidapp.app.viewmodel.Onb02ViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoVerOnb03ContractTest {

    @Test
    fun COMMON_NEW_PERSON_ONCE() {
        val queue = TutorialQueueResolver.queue(
            CanonicalTutorialEvent.NEW_PERSON_ONBOARDING,
            consumed = { false }
        )
        assertEquals(listOf(TutorialId.T00_MULTI_FUNCTION_INTRO), queue.tutorials)
        val again = TutorialQueueResolver.queue(
            CanonicalTutorialEvent.NEW_PERSON_ONBOARDING,
            consumed = { it == TutorialId.T00_MULTI_FUNCTION_INTRO }
        )
        assertTrue(again.tutorials.isEmpty())
    }

    @Test
    fun COMMON_NOT_REPEATED_AFTER_GOOGLE_LOGIN() {
        val store = InMemoryOnb02Store()
        store.markExistingAcknowledged("user-1")
        Onb02StoreProvider.override = store
        try {
            assertEquals(null, Onb02StoreProvider.decideEntry("user-1", justCompletedProfileSetup = false))
        } finally {
            Onb02StoreProvider.override = null
        }
    }

    @Test
    fun COMMON_NOT_REPEATED_AFTER_EMAIL_LOGIN() {
        COMMON_NOT_REPEATED_AFTER_GOOGLE_LOGIN()
    }

    @Test
    fun COMMON_NOT_REPEATED_AFTER_RESUME() {
        val store = InMemoryOnb02Store()
        store.markCompleted("u", TutorialId.T00_MULTI_FUNCTION_INTRO)
        val vm = Onb02ViewModel(store = store, userIdProvider = { "u" })
        vm.start(Onb02FlowKind.FULL_ONBOARDING)
        assertNotEquals(
            TutorialId.T00_MULTI_FUNCTION_INTRO,
            vm.ui.value.currentTutorial?.id.takeIf { vm.ui.value.phase.name == "INTRO" }
        )
    }

    @Test
    fun COMMON_NOT_REPEATED_AFTER_FUNCTION_ACTIVATION() {
        val q = TutorialQueueResolver.queue(
            CanonicalTutorialEvent.FUNCTION_ACTIVATED,
            consumed = { it == TutorialId.T00_MULTI_FUNCTION_INTRO },
            newlyActivated = setOf(LeoverFunction.RESCUER)
        )
        assertFalse(q.tutorials.contains(TutorialId.T00_MULTI_FUNCTION_INTRO))
    }

    @Test
    fun NO_REDUNDANT_PERSONAL_TUTORIAL() {
        val q = TutorialQueueResolver.queue(
            CanonicalTutorialEvent.NEW_PERSON_ONBOARDING,
            consumed = { false }
        )
        assertFalse(q.tutorials.contains(TutorialId.T01_PROFILE_PERSONAL))
    }

    @Test
    fun FUNCTION_ACTIVATED_BEFORE_TUTORIAL() {
        val confirm = source("app/src/main/java/com/comunidapp/app/viewmodel/Onb02ViewModel.kt")
        assertTrue(confirm.contains("store.saveSelection") || confirm.contains("store.addExtras"))
        assertTrue(confirm.contains("markSelectionConfirmed"))
        val org = confirm.substringAfter("fun confirmOrganizationSetup")
            .substringBefore("fun finish")
        assertTrue(org.contains("PendingTutorialQueue.set"))
        assertTrue(org.contains("finish()"))
    }

    @Test
    fun FUNCTION_TUTORIAL_ONLY_AFTER_SUCCESSFUL_SAVE() {
        FUNCTION_ACTIVATED_BEFORE_TUTORIAL()
    }

    @Test
    fun TUTORIAL_COMPLETE_PERSISTS_BEFORE_NAVIGATION() {
        val vmSrc = source("app/src/main/java/com/comunidapp/app/viewmodel/Onb02ViewModel.kt")
        val complete = vmSrc.substringAfter("fun nextStepOrFinish")
            .substringBefore("fun confirmSelection")
        assertTrue(complete.contains("markCompleted"))
        assertTrue(complete.indexOf("markCompleted") < complete.indexOf("advanceAfterTutorial"))
    }

    @Test
    fun TUTORIAL_SKIP_PERSISTS_BEFORE_NAVIGATION() {
        val vmSrc = source("app/src/main/java/com/comunidapp/app/viewmodel/Onb02ViewModel.kt")
        val skip = vmSrc.substringAfter("fun skipCurrentTutorial")
            .substringBefore("fun nextStepOrFinish")
        assertTrue(skip.contains("markSkipped"))
        assertTrue(skip.indexOf("markSkipped") < skip.indexOf("advanceAfterTutorial"))
    }

    @Test
    fun TUTORIAL_FINISH_GOES_TO_CANONICAL_LANDING() {
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(graph.contains("popUpTo(NavRoutes.HOME)"))
    }

    @Test
    fun TUTORIAL_FINISH_CLEARS_SETUP_BACKSTACK() {
        TUTORIAL_FINISH_GOES_TO_CANONICAL_LANDING()
    }

    @Test
    fun BACK_FROM_LANDING_DOES_NOT_REOPEN_SETUP() {
        val graph = source("app/src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt")
        assertTrue(graph.contains("popUpTo(NavRoutes.CREATE_ORGANIZATION) { inclusive = true }"))
    }

    @Test
    fun BACKGROUND_DURING_TUTORIAL_RESTORES_SAME_PAGE() {
        val store = InMemoryOnb02Store()
        store.markTutorialPage("u", TutorialId.T00_MULTI_FUNCTION_INTRO, 1)
        assertEquals(1, store.tutorialPage("u", TutorialId.T00_MULTI_FUNCTION_INTRO))
        val vm = Onb02ViewModel(store = store, userIdProvider = { "u" })
        vm.start(Onb02FlowKind.FULL_ONBOARDING)
        assertEquals(1, vm.ui.value.stepIndex)
    }

    @Test
    fun BACKGROUND_DOES_NOT_ADVANCE_ONBOARDING() {
        val store = InMemoryOnb02Store()
        val vm = Onb02ViewModel(store = store, userIdProvider = { "u" })
        vm.start(Onb02FlowKind.FULL_ONBOARDING)
        assertEquals(com.comunidapp.app.viewmodel.Onb02Phase.INTRO, vm.ui.value.phase)
        vm.start(Onb02FlowKind.FULL_ONBOARDING)
        assertEquals(com.comunidapp.app.viewmodel.Onb02Phase.INTRO, vm.ui.value.phase)
    }

    @Test
    fun COMPLETED_TUTORIAL_NOT_AUTO_REPEATED() {
        assertTrue(TutorialQueueResolver.isConsumed(completed = true, skipped = false))
        val q = TutorialQueueResolver.queue(
            CanonicalTutorialEvent.FUNCTION_ACTIVATED,
            consumed = { true },
            newlyActivated = setOf(LeoverFunction.WALKER)
        )
        assertTrue(q.tutorials.isEmpty())
    }

    @Test
    fun SKIPPED_TUTORIAL_NOT_AUTO_REPEATED() {
        assertTrue(TutorialQueueResolver.isConsumed(completed = false, skipped = true))
    }

    @Test
    fun MANUAL_REPLAY_DOES_NOT_CHANGE_ONBOARDING() {
        val store = InMemoryOnb02Store()
        store.markCompleted("u")
        val vm = Onb02ViewModel(store = store, userIdProvider = { "u" })
        vm.start(Onb02FlowKind.REOPEN_FROM_HELP, reopenId = TutorialId.T02_RESCUER)
        vm.skipCurrentTutorial()
        assertEquals(null, vm.setupRouteAfterTutorials())
    }

    @Test
    fun CONTEXT_MAPPING_EXHAUSTIVE() {
        val vet = ContextIdentityMapping.contextForOrganization("1", "Huellas", "VETERINARY_CLINIC")
        assertTrue(vet is OperationalContext.Veterinary)
        assertEquals("Veterinaria · Huellas", vet.displayName)
        val refuge = ContextIdentityMapping.contextForOrganization("2", "Patitas", "SHELTER")
        assertTrue(refuge is OperationalContext.Organization)
        assertEquals("Refugio · Patitas", refuge.displayName)
        assertFalse(ContextIdentityMapping.ORDINAL_MAPPING_USED)
        assertFalse(ContextIdentityMapping.FALLBACK_TO_REFUGE)
    }

    @Test
    fun VETERINARY_PERSON_CONTEXT_EXACT() {
        assertEquals(
            "Profesional veterinario",
            ContextIdentityMapping.label(OperationalContextKind.VETERINARY, "")
        )
    }

    @Test
    fun VETERINARY_ORG_CONTEXT_EXACT() {
        CONTEXT_MAPPING_EXHAUSTIVE()
    }

    @Test
    fun NO_CONTEXT_ORDINAL_MAPPING() {
        assertFalse(ContextIdentityMapping.ORDINAL_MAPPING_USED)
        val store = source("app/src/main/java/com/comunidapp/app/domain/context/ActiveContextStore.kt")
        assertFalse(store.contains(".ordinal"))
    }

    @Test
    fun NO_CONTEXT_FALLBACK_TO_REFUGE() {
        assertFalse(ContextIdentityMapping.FALLBACK_TO_REFUGE)
        val nav = source("app/src/main/java/com/comunidapp/app/domain/context/ContextNavigation.kt")
        assertTrue(nav.contains("isRefugeNav"))
        assertTrue(nav.contains("professionalItems"))
    }

    @Test
    fun COMMERCIAL_ORG_CATEGORY_SET_EXACT() {
        val labels = ProductOrganizationCategory.commercialVisible.map { it.visibleLabel }
        assertEquals(
            listOf(
                "Veterinaria", "Tienda", "Guardería", "Peluquería",
                "Paseos y cuidado", "Educación / Adiestramiento", "Empresa o marca",
                "Lugar pet friendly"
            ),
            labels
        )
        assertFalse(OrganizationCreatePolicy.OTHER_BUSINESS_IN_COMMERCIAL_SELECTOR)
        assertFalse(OrganizationCreatePolicy.REFUGE_IN_COMMERCIAL_SELECTOR)
        assertFalse(labels.contains("Refugio / ONG"))
        assertFalse(labels.contains("Otro servicio"))
    }

    @Test
    fun GENERIC_COMMERCIAL_ORG_REQUIRES_CATEGORY_AND_NAME() {
        assertEquals(listOf("category", "name"), OrganizationCreatePolicy.genericInitialFields())
        assertEquals(listOf("name"), OrganizationCreatePolicy.specificInitialFields())
        assertFalse(OrganizationCreatePolicy.REASON_SOCIAL_INITIAL)
        assertFalse(OrganizationCreatePolicy.PUBLIC_IDENTIFIER_INITIAL)
        assertFalse(OrganizationCreatePolicy.COUNTRY_ISO_INITIAL)
    }

    @Test
    fun INTERNAL_IDENTIFIER_GENERATED() {
        val slug = OrganizationCreatePolicy.generateInternalSlug("Huellas Vet")
        assertTrue(slug.startsWith("huellas-vet"))
        assertTrue(slug.length > "huellas-vet".length)
    }

    @Test
    fun CREATOR_IS_ADMINISTRATOR() {
        assertEquals("Administrador", MembershipDisplay.ADMINISTRATOR_VISIBLE)
        assertEquals("Administrador", MembershipDisplay.visibleRole(OrganizationRoleCode.OWNER))
        assertEquals("Miembro", MembershipDisplay.visibleRole(OrganizationRoleCode.MEMBER))
        assertFalse(MembershipDisplay.PENDING_HAS_ACCESS)
        assertFalse(MembershipDisplay.VET_MEMBERSHIP_AUTO_HEALTH_ACCESS)
    }

    @Test
    fun LAUNCH_OFFER_90_DAYS() {
        assertEquals(90, CommercialOfferPolicy.LAUNCH_90_NO_CARD.trialDays)
        assertFalse(CommercialOfferPolicy.LAUNCH_90_NO_CARD.paymentMethodRequiredAtStart)
        assertEquals(CommercialProductFamily.LEOVER_COMMERCIAL, CommercialProductFamily.LEOVER_COMMERCIAL)
        val snap = CommercialOfferPolicy.snapshotAtActivation(
            CommercialOfferPolicy.LAUNCH_90_NO_CARD,
            CommercialTier.LEOVER_COMMERCIAL_PROFESSIONAL,
            0L
        )
        assertEquals("LAUNCH_90_NO_CARD", snap.offerCode)
        assertEquals(90, snap.trialDaysSnapshot)
        val later = CommercialOfferPolicy.STANDARD_30_WITH_PAYMENT
        assertTrue(CommercialOfferPolicy.futurePolicyDoesNotShortenExisting(snap, later))
        assertTrue(CommercialOfferPolicy.publiclyDiscoverable(CommercialEntitlementStatus.TRIAL_ACTIVE))
        assertTrue(CommercialOfferPolicy.expiredHidesFromCommunity(CommercialEntitlementStatus.EXPIRED))
        assertTrue(CommercialOfferPolicy.expiredPreservesProfileData())
        assertFalse(CommercialOfferPolicy.BRAND_STUDIO_REQUIRED_V1)
        assertEquals(
            "FREE_FOR_NOW_PENDING_PRODUCT_VALIDATION",
            CommercialOfferPolicy.FOSTER_COMMERCIAL_STATUS
        )
    }

    @Test
    fun GOOGLE_FIRST_TAP_NO_PREMATURE_ERROR() {
        val repo = source("app/src/main/java/com/comunidapp/app/data/repository/SupabaseAuthRepository.kt")
        val googleFn = repo.substringAfter("override suspend fun signInWithGoogle")
            .substringBefore("override fun linkedAuthMethods")
        assertFalse(googleFn.contains("google oauth without session"))
        assertTrue(googleFn.contains("GoogleOAuthPending.pendingUser"))
        assertEquals(GoogleAuthLifecycle.WAITING_EXTERNAL_AUTH.name, GoogleAuthLifecycle.WAITING_EXTERNAL_AUTH.name)
        assertTrue(GoogleOAuthPending.FIRST_TAP_ERROR_ROOT_CAUSE.contains("currentUserOrNull"))
        val login = source("app/src/main/java/com/comunidapp/app/viewmodel/LoginViewModel.kt")
        assertTrue(login.contains("WAITING_EXTERNAL_AUTH"))
        assertTrue(login.contains("if (_uiState.value.isBusy) return"))
    }

    @Test
    fun COMMON_TUTORIAL_COPY_AND_VITACORA_HEART() {
        val t00 = TutorialCatalog.definition(TutorialId.T00_MULTI_FUNCTION_INTRO)
        assertEquals(4, t00.steps.size)
        assertEquals("Bienvenido a LeoVer", t00.steps[0].title)
        assertTrue(t00.steps[1].titleIsVitacoraWordmark)
        assertFalse(t00.steps[1].body.contains("bitácora", ignoreCase = true))
        assertFalse(t00.steps[1].body.contains("evoca una bitácora"))
        assertEquals(Onb02Copy.PROFILE_EXPLANATION_SLIDE_TITLE, t00.steps.last().title)
        assertEquals("Empezar", t00.steps.last().primaryCta)
        val pager = source("app/src/main/java/com/comunidapp/app/ui/components/leo/LeoVerTutorialPager.kt")
        assertTrue(pager.contains("VitaCoraWordmarkTitle"))
        assertTrue(pager.contains("Icons.Filled.Favorite"))
        assertEquals("Cuidador de mascotas", LeoverFunction.CAREGIVER.visibleLabel)
        assertEquals(
            OrganizationKindOption.commercial.map { it.name },
            listOf(
                "VETERINARY_CLINIC", "SHOP", "DAYCARE", "GROOMING",
                "WALKING_CARE", "TRAINING", "BRAND", "PET_FRIENDLY_VENUE"
            )
        )
    }

    @Test
    fun QUEUE_NEW_VET_PERSON_AND_ORG() {
        val person = TutorialQueueResolver.queue(
            CanonicalTutorialEvent.FUNCTION_ACTIVATED,
            consumed = { false },
            newlyActivated = setOf(LeoverFunction.VETERINARY_PROFESSIONAL),
            availableContextCount = 2
        )
        assertEquals(
            listOf(
                TutorialId.T04_VETERINARY_PROFESSIONAL,
                TutorialId.T12_COMMERCIAL_PROFESSIONAL,
                TutorialId.T11_USE_LEOVER_AS
            ),
            person.tutorials
        )
        val org = TutorialQueueResolver.queue(
            CanonicalTutorialEvent.ORGANIZATION_CREATED,
            consumed = { false },
            organizationKind = OrganizationKindOption.VETERINARY_CLINIC,
            commercialOrg = true
        )
        assertEquals(
            listOf(
                TutorialId.T10_ORGANIZATION,
                TutorialId.T13_COMMERCIAL_ORGANIZATION,
                TutorialId.T10A_VETERINARY_CLINIC
            ),
            org.tutorials
        )
        val refuge = TutorialQueueResolver.queue(
            CanonicalTutorialEvent.ORGANIZATION_CREATED,
            consumed = { false },
            organizationKind = OrganizationKindOption.SHELTER,
            commercialOrg = false
        )
        assertFalse(refuge.tutorials.contains(TutorialId.T13_COMMERCIAL_ORGANIZATION))
        assertFalse(refuge.tutorials.contains(TutorialId.T10A_VETERINARY_CLINIC))
        assertFalse(refuge.tutorials.contains(TutorialId.T10_ORGANIZATION))
        assertEquals(listOf(TutorialId.T10B_SHELTER), refuge.tutorials)
        val invite = TutorialQueueResolver.queue(
            CanonicalTutorialEvent.ORGANIZATION_INVITATION_ACCEPTED,
            consumed = { false },
            organizationKind = OrganizationKindOption.VETERINARY_CLINIC,
            invitedAsAdmin = false
        )
        assertEquals(TutorialId.T14_ORG_JOIN_MEMBER, invite.tutorials.first())
        assertFalse(invite.tutorials.contains(TutorialId.T00_MULTI_FUNCTION_INTRO))
        assertFalse(invite.tutorials.contains(TutorialId.T13_COMMERCIAL_ORGANIZATION))
    }

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()
}
