package com.comunidapp.app.domain.qa

import androidx.lifecycle.SavedStateHandle
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.m17.canonicalCampaignFallback
import com.comunidapp.app.data.repository.AdoptionRepository
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.CreateM13SightingInput
import com.comunidapp.app.data.repository.LostFoundRepository
import com.comunidapp.app.data.repository.M13MemoryStore
import com.comunidapp.app.data.repository.MockAdoptionRepository
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockLostFoundRepository
import com.comunidapp.app.data.repository.MockM13SightingRepository
import com.comunidapp.app.data.repository.MockPlatformRepository
import com.comunidapp.app.data.repository.MockUserRepository
import com.comunidapp.app.data.repository.UserRepository
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.domain.lostfound.IncidentMoment
import com.comunidapp.app.domain.lostfound.PublicLostFoundFeed
import com.comunidapp.app.domain.onboarding.onb02.GuideDestination
import com.comunidapp.app.domain.onboarding.onb02.GuideStep
import com.comunidapp.app.domain.onboarding.onb02.InteractiveOnboardingGuide
import com.comunidapp.app.domain.onboarding.onb02.TeachAddFunctionExit
import com.comunidapp.app.domain.onboarding.onb02.TeachAddFunctionResult
import com.comunidapp.app.domain.organization.CanonicalHelpRow
import com.comunidapp.app.domain.organization.CanonicalPublicHelp
import com.comunidapp.app.domain.pets.LostReportDraft
import com.comunidapp.app.domain.pets.LostReportDraftCodec
import com.comunidapp.app.domain.user.ProfilePrivacySave
import com.comunidapp.app.domain.user.UpdateMyProfileCommand
import com.comunidapp.app.domain.user.UserPrivacySettings
import com.comunidapp.app.domain.user.UserProfileMapper
import com.comunidapp.app.viewmodel.AdoptionsViewModel
import com.comunidapp.app.viewmodel.AdoptionListUiState
import com.comunidapp.app.viewmodel.EditProfileViewModel
import com.comunidapp.app.viewmodel.LostFoundViewModel
import com.comunidapp.app.viewmodel.LostReportFormViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostQa17B12SecondAuditTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun backDuringTeachReturnsToSettingsAndDoesNotOpenTheFeed() {
        val exit = TeachAddFunctionExit.resolve(GuideStep.TEACH_ADD_FUNCTION, finishedRoute = null)
        assertEquals(TeachAddFunctionResult.RETURN_TO_SETTINGS, exit)
        val restored = TeachAddFunctionExit.snapshotAfterBack()
        assertEquals(GuideStep.HIGHLIGHT_ADD_FUNCTION, restored.step)
        assertFalse(restored.completed)
        assertNotEquals(GuideDestination.FEED, InteractiveOnboardingGuide.destination(restored.step))
        assertEquals(
            GuideDestination.ADD_FUNCTION_SELECTOR,
            InteractiveOnboardingGuide.destination(GuideStep.TEACH_ADD_FUNCTION)
        )
    }

    @Test
    fun completingTeachFinishesOnTheFeed() {
        val exit = TeachAddFunctionExit.resolve(GuideStep.TEACH_ADD_FUNCTION, finishedRoute = "home")
        assertEquals(TeachAddFunctionResult.FINISH_ON_FEED, exit)
        val done = InteractiveOnboardingGuide.advance(
            com.comunidapp.app.domain.onboarding.onb02.GuideSnapshot(GuideStep.TEACH_ADD_FUNCTION, completed = false),
            com.comunidapp.app.domain.onboarding.onb02.GuideTarget.CONTINUE
        )
        assertEquals(GuideStep.FEED, done.step)
        assertTrue(done.completed)
    }

    @Test
    fun openingShelterSearchRefreshesBeforeFiltering() = runTest(dispatcher) {
        val repo = RefreshingAdoptions()
        val viewModel = AdoptionsViewModel(repo)
        viewModel.setOrganization("org-a")
        viewModel.refresh()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state is AdoptionListUiState.Content)
        val posts = (state as AdoptionListUiState.Content).posts
        assertEquals(listOf("adopt-a"), posts.map { it.id })
        assertTrue(repo.refreshes >= 1)
        assertTrue(repo.startedEmpty)
    }

    @Test
    fun canonicalHelpKeepsTheOrganizationOnMoneyGoodsAndVolunteering() {
        val rows = listOf(
            CanonicalHelpRow("norte", "org-a"),
            CanonicalHelpRow("sur", "org-b"),
            CanonicalHelpRow("general", null)
        )
        assertEquals(listOf("norte"), CanonicalPublicHelp.visible("org-a", rows).map { it.id })
        assertEquals(listOf("sur"), CanonicalPublicHelp.visible("org-b", rows).map { it.id })
        assertEquals(listOf("norte", "sur", "general"), CanonicalPublicHelp.visible(null, rows).map { it.id })
        val fallback = canonicalCampaignFallback(
            buildJsonObject { put("p_organization_id", "org-a") },
            listOf(
                buildJsonObject {
                    put("id", "camp-a")
                    put("organization_id", "org-a")
                    put("title", "QA17B12 Aporte Norte")
                },
                buildJsonObject {
                    put("id", "camp-b")
                    put("organization_id", "org-b")
                    put("title", "QA17B12 Aporte Sur")
                }
            )
        )
        assertEquals(listOf("camp-a"), fallback.map { it["id"].toString().trim('"') })
    }

    @Test
    fun lostDraftRestoresTheSelectedPetAndTheIncidentMoment() {
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
            longitude = -58.4,
            foundSexName = "FEMALE",
            foundSizeName = "SMALL",
            estimatedAgeYears = "4",
            boundPetId = "pet-new",
            occurredAtEpochMs = IncidentMoment.combine("2026-03-02", "15:45", 0L),
            notes = "Se soltó la correa"
        )
        val restored = LostReportFormViewModel(
            SavedStateHandle(mapOf(LostReportFormViewModel.KEY to LostReportDraftCodec.encode(draft)))
        ).read()
        assertEquals("pet-new", restored?.boundPetId)
        assertEquals(draft.occurredAtEpochMs, restored?.occurredAtEpochMs)
        assertEquals("Se soltó la correa", restored?.notes)
        assertEquals("+5411", restored?.contactInfo)
        val fresh = LostReportFormViewModel(SavedStateHandle())
        assertNull(fresh.read())
        val stored = IncidentMoment.withNote("Collar rojo", draft.occurredAtEpochMs!!)
        assertTrue(stored.contains("Hecho: 2026-03-02 15:45"))
    }

    @Test
    fun privacyFailureDoesNotReportSaveSuccess() = runTest(dispatcher) {
        val user = MockData.currentUser.copy(avatarPath = null, profileImageUrl = null)
        val auth = object : AuthRepository by MockAuthRepository() {
            override fun getCurrentUser() = user
        }
        val users = object : UserRepository by MockUserRepository() {
            override suspend fun getUser(userId: String) = user
            override suspend fun updateMyProfile(userId: String, command: UpdateMyProfileCommand) =
                Result.success(UserProfileMapper.toUserProfile(user))
            override suspend fun updatePrivacySettings(
                userId: String,
                settings: UserPrivacySettings
            ): Result<Unit> = Result.failure(IllegalStateException(ProfilePrivacySave.UNAVAILABLE))
        }
        val viewModel = EditProfileViewModel(auth, users)
        advanceUntilIdle()
        viewModel.saveProfile()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.saveSuccess)
        assertEquals(ProfilePrivacySave.UNAVAILABLE, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun publicFeedKeepsClaimedAndInCareFromTheRealStatusMapping() = runTest(dispatcher) {
        val olderThanAccount = 1_000L
        val accountCreated = 50_000L
        val statuses = listOf("OPEN", "CLAIMED", "IN_CARE", "RESOLVED", "HIDDEN", "CANCELLED")
        val posts = statuses.map { raw ->
            lost(raw, LostFoundStatus.fromString(raw), olderThanAccount)
        }
        val visible = posts.filter {
            PublicLostFoundFeed.include(it.status, it.createdAt, accountCreated)
        }.map { it.id }
        assertEquals(listOf("OPEN", "CLAIMED", "IN_CARE"), visible)
        val viewModel = LostFoundViewModel(
            object : LostFoundRepository by MockLostFoundRepository() {
                override fun observeLostFoundPosts(): StateFlow<List<LostFoundPost>> =
                    MutableStateFlow(posts)
            },
            MockPlatformRepository(),
            MockAuthRepository()
        )
        advanceUntilIdle()
        assertEquals(listOf("OPEN", "CLAIMED", "IN_CARE"), viewModel.posts.value.map { it.id })
    }

    @Test
    fun aSightingPersistsTheChosenMoment() = runTest(dispatcher) {
        val repo = MockM13SightingRepository(actorUserId = { "qa02" }, store = M13MemoryStore())
        val moment = IncidentMoment.combine("2026-04-11", "09:30", 0L)
        val created = repo.createSighting(
            CreateM13SightingInput(
                lostFoundCaseId = "case-1",
                species = PetSpecies.CAT,
                primaryColor = "negro",
                observedAt = moment,
                zoneText = "Belgrano",
                description = "Lo vi en la esquina"
            )
        ).getOrThrow()
        assertEquals(moment, created.observedAt)
        assertEquals(PetSpecies.CAT, created.species)
    }

    @Test
    fun fixtureTargetsCanonicalTablesAndDoesNotPickAnArbitraryPet() {
        val sql = repoText("infra/supabase-canonical/qa/seed_17b12_staging.sql")
        val runner = repoText("scripts/qa/seed-17b12-staging.ps1")
        assertTrue(sql.contains("tobqbddfcyitwgbkthhy"))
        assertTrue(sql.contains("QA17B12_ABORT_NOT_STAGING"))
        assertTrue(runner.contains("tobqbddfcyitwgbkthhy"))
        assertTrue(runner.contains("if (-not \$Apply)"))
        listOf(
            "public.donation_campaigns",
            "public.volunteer_opportunities",
            "public.in_kind_needs",
            "public.lost_found_sightings",
            "observed_at",
            "public.adoption_publications",
            "public.lost_found_alerts",
            "qa01owner",
            "QA17B12 Mascota Norte",
            "QA17B12 Mascota Sur"
        ).forEach { token -> assertTrue(sql.contains(token)) }
        assertFalse(sql.contains("insert into public.community_events"))
        assertFalse(sql.contains("insert into public.in_kind_offers"))
        listOf(
            "m17_donation_campaigns",
            "m17_in_kind_needs",
            "m17_volunteer_opportunities",
            "order by created_at limit 1"
        ).forEach { token -> assertFalse(sql.contains(token)) }
    }

    private fun lost(id: String, status: LostFoundStatus, createdAt: Long) = LostFoundPost(
        id = id,
        authorId = "qa01",
        authorName = "QA",
        type = LostFoundType.LOST,
        species = PetSpecies.DOG,
        location = "Belgrano",
        description = "QA17B12",
        contactInfo = "",
        status = status,
        date = "2026-01-01",
        createdAt = createdAt
    )

    private fun repoText(relative: String): String {
        val file = listOf(File(relative), File("..", relative)).firstOrNull { it.isFile }
            ?: error("No se encontró $relative")
        return file.readText()
    }
}

private class RefreshingAdoptions : AdoptionRepository by MockAdoptionRepository() {
    var refreshes = 0
    var startedEmpty = false
    private val published = MutableStateFlow<List<AdoptionPost>>(emptyList())

    override suspend fun refreshPublished(): Result<Unit> {
        if (refreshes == 0) startedEmpty = published.value.isEmpty()
        refreshes += 1
        published.value = listOf(
            adoption("adopt-a", "org-a"),
            adoption("adopt-b", "org-b")
        )
        return Result.success(Unit)
    }

    override fun observePublishedAdoptions(): Flow<List<AdoptionPost>> = published

    private fun adoption(id: String, organizationId: String) = AdoptionPost(
        id = id,
        publisherOrganizationId = organizationId,
        shelterName = organizationId,
        name = id,
        species = PetSpecies.DOG,
        sex = PetSex.UNKNOWN,
        ageYears = 2,
        size = PetSize.MEDIUM,
        location = "Belgrano",
        description = "QA17B12",
        status = AdoptionStatus.PUBLISHED
    )
}
