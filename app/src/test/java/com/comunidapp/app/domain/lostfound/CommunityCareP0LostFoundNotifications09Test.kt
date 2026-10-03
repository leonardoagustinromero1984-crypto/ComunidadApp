package com.comunidapp.app.domain.lostfound

import com.comunidapp.app.data.model.AppNotification
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.NotificationType
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.repository.CanonicalNotificationInbox
import com.comunidapp.app.data.repository.LostFoundMatchCandidate
import com.comunidapp.app.data.repository.LostFoundRepository
import com.comunidapp.app.data.repository.MockAuthRepository
import com.comunidapp.app.data.repository.MockPlatformRepository
import com.comunidapp.app.domain.alerts.AlertMapTypeFilter
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.ui.UiRegressionGateTest
import com.comunidapp.app.viewmodel.AlertMapViewModel
import com.comunidapp.app.viewmodel.LostFoundViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Community Care P0: canonical inbox list, lost/found initial load,
 * and automatic match candidates with a null assertion actor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommunityCareP0LostFoundNotifications09Test {

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
    fun canonicalStagingDoesNotBindTheVisibleInboxToTheM06Mock() {
        val provider = source("app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt")
        val inbox = provider.substringAfter("val notificationInboxRepository")
            .substringBefore("val notificationPreferenceRepository")
        assertTrue(inbox.contains("useLegacyRemoteModules -> SupabaseNotificationInboxRepository()"))
        assertTrue(inbox.contains("useSupabase -> CanonicalNotificationInboxRepository()"))
        assertTrue(inbox.contains("else -> m06Stage2ContractMocks.inbox"))
        assertFalse(inbox.contains("useSupabase -> m06Stage2ContractMocks.inbox"))

        val platform = provider.substringAfter("val platformRepository")
            .substringBefore("val m06Stage2ContractMocks")
        assertTrue(platform.contains("useSupabase -> CanonicalPlatformRepository()"))

        val canonical = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalPlatformRepository.kt")
        val observe = canonical.substringAfter("override fun observeNotifications")
            .substringBefore("override suspend fun markNotificationRead")
        assertTrue(observe.contains("CanonicalNotificationInbox.fetchVisible()"))
        assertFalse(observe.contains("InMemoryDataStore"))
        assertFalse(observe.contains("m06Stage2ContractMocks"))
        assertFalse(observe.contains("fallback.observeNotifications"))

        val list = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalNotificationInbox.kt")
        assertTrue(list.contains("CanonicalBackend.RPC_LIST_MY_NOTIFICATIONS"))
        assertFalse(list.contains("m06_get_inbox"))
        assertFalse(list.contains("m06Stage2ContractMocks"))
        assertFalse(list.contains("grant select"))
        assertFalse(list.contains("service_role"))

        val screen = source("app/src/main/java/com/comunidapp/app/viewmodel/NotificationsViewModel.kt")
        assertTrue(screen.contains("platformRepository.observeNotifications(user.id)"))
    }

    @Test
    fun canonListMyNotificationsMapsOntoTheVisibleInbox() {
        val payload = Json.parseToJsonElement(
            """
            [{
              "id": "11111111-1111-1111-1111-111111111111",
              "user_id": "22222222-2222-2222-2222-222222222222",
              "category": "LOST_FOUND",
              "state": "UNREAD",
              "title": "Posible coincidencia",
              "body": "Encontraron un animal que podría coincidir con tu mascota.",
              "deep_link_type": "LOST_FOUND_CASE",
              "deep_link_resource_type": "LOST_FOUND_CASE",
              "deep_link_resource_id": "33333333-3333-3333-3333-333333333333",
              "related_type": "LOST_FOUND_CASE",
              "related_id": "33333333-3333-3333-3333-333333333333",
              "created_at": "2026-09-27T12:00:00Z",
              "read_at": null
            }]
            """.trimIndent()
        )
        val visible: List<AppNotification> = CanonicalNotificationInbox.mapVisible(payload)
        val item = visible.single()
        assertEquals("11111111-1111-1111-1111-111111111111", item.id)
        assertEquals("22222222-2222-2222-2222-222222222222", item.userId)
        assertEquals("Posible coincidencia", item.title)
        assertEquals("Encontraron un animal que podría coincidir con tu mascota.", item.body)
        assertEquals("33333333-3333-3333-3333-333333333333", item.relatedId)
        assertEquals("LOST_FOUND_CASE", item.relatedType)
        assertEquals(NotificationType.SIGHTING, item.type)
        assertNull(item.readAt)
        assertTrue(item.isUnread)
        assertEquals(
            "canon_list_my_notifications",
            CanonicalBackend.RPC_LIST_MY_NOTIFICATIONS
        )
    }

    @Test
    fun markReadStaysUncoveredWhenNoCanonicalContractExists() {
        val backend = source("app/src/main/java/com/comunidapp/app/domain/canonical/CanonicalBackend.kt")
        assertFalse(backend.contains("canon_mark_notification"))
        val canonical = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalPlatformRepository.kt")
        assertTrue(canonical.contains("MARK_READ_UNAVAILABLE"))
        assertFalse(canonical.contains("fallback.markNotificationRead"))
        assertFalse(canonical.contains("fallback.markAllNotificationsRead"))
        val inbox = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalNotificationInbox.kt")
        assertTrue(inbox.contains("CANONICAL_MARK_READ_UNAVAILABLE"))
        assertFalse(inbox.contains("m06_mark_notification_read"))
    }

    @Test
    fun openingLostFoundLoadsTheCanonicalListWithoutGps() = runTest(dispatcher) {
        val post = alert(id = "alert-1")
        val repository = RecordingLostFoundRepository(loaded = listOf(post))
        val map = AlertMapViewModel(lostFoundRepository = repository)
        advanceUntilIdle()
        val shown = mutableListOf<List<com.comunidapp.app.viewmodel.AlertMapItem>>()
        val job = launch { map.alerts.collect { shown.add(it) } }
        advanceUntilIdle()
        assertTrue(shown.any { items -> items.any { it.post.id == "alert-1" } })
        assertEquals(1, repository.refreshCount)
        assertEquals("alert-1", map.alerts.value.single().post.id)
        assertNull(map.alerts.value.single().post.latitude)
        assertNull(map.alerts.value.single().post.longitude)
        assertFalse(map.uiState.value.isLoading)
        assertNull(map.uiState.value.loadError)

        map.setTypeFilter(AlertMapTypeFilter.FOUND)
        advanceUntilIdle()
        assertEquals(1, repository.refreshCount)
        assertTrue(map.alerts.value.isEmpty())

        map.retry()
        advanceUntilIdle()
        assertEquals(2, repository.refreshCount)
        job.cancel()

        val beforeList = repository.refreshCount
        val list = LostFoundViewModel(
            lostFoundRepository = repository,
            platformRepository = MockPlatformRepository(),
            authRepository = MockAuthRepository()
        )
        advanceUntilIdle()
        assertEquals(beforeList + 1, repository.refreshCount)
        list.onTypeFilterChange(LostFoundType.LOST)
        advanceUntilIdle()
        assertEquals(beforeList + 1, repository.refreshCount)

        val mapSource = source("app/src/main/java/com/comunidapp/app/viewmodel/AlertMapViewModel.kt")
        assertTrue(mapSource.contains("lostFoundRepository.refreshAlerts()"))
        assertFalse(mapSource.contains("observeLostFoundPosts().value"))
        val listSource = source("app/src/main/java/com/comunidapp/app/viewmodel/LostFoundViewModel.kt")
        assertTrue(listSource.contains("lostFoundRepository.refreshAlerts()"))
        val canonical = source("app/src/main/java/com/comunidapp/app/data/repository/CanonicalConsumerRepositories.kt")
        val refresh = canonical.substringAfter("override suspend fun refreshAlerts")
            .substringBefore("override suspend fun markLostFoundInCare")
        assertTrue(refresh.contains("refresh()"))
        assertTrue(canonical.contains("CanonicalBackend.RPC_LIST_LOST_FOUND"))
        val selector = source("app/src/main/java/com/comunidapp/app/domain/pets/LostPetSelector.kt")
        assertTrue(selector.contains("FOUND_CASE"))
        assertTrue(selector.contains("return false"))
    }

    @Test
    fun automaticCandidateIsVisibleAndDoesNotInventAnAssertionActor() {
        val automatic = LostFoundMatchCandidate(
            id = "cand-auto",
            lostAlertId = "lost-1",
            assertedBy = null,
            status = "PENDING",
            score = 0.72,
            matchReason = "BASIC_GEO_TIME"
        )
        val oldUi = listOf(automatic).filter { it.status.equals("PENDING", true) && it.assertedBy != null }
        assertTrue(oldUi.isEmpty())

        val forCustodian = LostFoundMatchCandidatePolicy.present(
            candidates = listOf(automatic),
            viewerIsCustodian = true
        ).single()
        assertEquals("cand-auto", forCustodian.candidate.id)
        assertNull(forCustodian.candidate.assertedBy)
        assertTrue(forCustodian.automatic)
        assertEquals("Coincidencia automática", forCustodian.headline)
        assertTrue(forCustodian.showCustodianActions)

        val forOwner = LostFoundMatchCandidatePolicy.present(
            candidates = listOf(automatic),
            viewerIsCustodian = false
        ).single()
        assertNull(forOwner.candidate.assertedBy)
        assertFalse(forOwner.showCustodianActions)
        assertEquals(automatic.assertedBy, forOwner.candidate.assertedBy)
    }

    @Test
    fun custodianActionsStayPermissionAndStateDependent() {
        val pendingAsserted = LostFoundMatchCandidate(
            id = "cand-person",
            lostAlertId = "lost-1",
            assertedBy = "owner-1",
            status = "PENDING"
        )
        val rejectedAutomatic = LostFoundMatchCandidate(
            id = "cand-rejected",
            lostAlertId = "lost-2",
            assertedBy = null,
            status = "REJECTED_BY_OWNER"
        )
        val presented = LostFoundMatchCandidatePolicy.present(
            candidates = listOf(pendingAsserted, rejectedAutomatic),
            viewerIsCustodian = true
        )
        assertEquals(listOf("cand-person"), presented.map { it.candidate.id })
        assertEquals("owner-1", presented.single().candidate.assertedBy)
        assertFalse(presented.single().automatic)
        assertTrue(presented.single().showCustodianActions)

        val hiddenFromOwner = LostFoundMatchCandidatePolicy.present(
            candidates = listOf(pendingAsserted),
            viewerIsCustodian = false
        ).single()
        assertFalse(hiddenFromOwner.showCustodianActions)
        assertEquals("owner-1", hiddenFromOwner.candidate.assertedBy)

        assertFalse(
            LostFoundMatchCandidatePolicy.canLoad(
                isFound = true,
                viewerId = "lost-owner",
                authorId = "finder",
                claimedBy = null,
                isCustodian = false
            )
        )
        assertTrue(
            LostFoundMatchCandidatePolicy.canLoad(
                isFound = true,
                viewerId = "finder",
                authorId = "finder",
                claimedBy = null,
                isCustodian = true
            )
        )

        val detail = source("app/src/main/java/com/comunidapp/app/ui/screens/lostfound/AlertMapScreen.kt")
        assertFalse(detail.contains("assertedBy != null"))
        assertTrue(detail.contains("LostFoundMatchCandidatePolicy.present"))
        assertTrue(detail.contains("showCustodianActions"))
        assertTrue(detail.contains("assertFoundMightBeMine"))
        assertTrue(detail.contains("confirmFoundOwnerMatch"))
        val notifications = source("app/src/main/java/com/comunidapp/app/ui/screens/profile/NotificationsScreen.kt")
        assertTrue(notifications.contains("LOST_FOUND_CASE"))
        assertTrue(notifications.contains("onOpenLostFound"))
    }

    private fun alert(id: String) = LostFoundPost(
        id = id,
        authorId = "owner-1",
        authorName = "",
        type = LostFoundType.LOST,
        petName = "Luna",
        species = PetSpecies.DOG,
        location = "Palermo",
        description = "Perdida",
        contactInfo = "",
        status = LostFoundStatus.ACTIVE,
        date = "2026-09-27T12:00:00Z",
        createdAt = 1_758_000_000_000L
    )

    private fun source(path: String): String = UiRegressionGateTest.sourceFile(path).readText()

    private class RecordingLostFoundRepository(
        private val loaded: List<LostFoundPost>
    ) : LostFoundRepository {
        private val posts = MutableStateFlow<List<LostFoundPost>>(emptyList())
        var refreshCount: Int = 0

        override fun observeLostFoundPosts(): StateFlow<List<LostFoundPost>> = posts.asStateFlow()

        override fun getFilteredLostFound(
            type: LostFoundType?,
            species: PetSpecies?,
            location: String?,
            status: LostFoundStatus?
        ): List<LostFoundPost> = posts.value

        override suspend fun addLostFoundPost(post: LostFoundPost): Result<String> = Result.success(post.id)

        override suspend fun updateLostFoundPost(post: LostFoundPost): Result<Unit> = Result.success(Unit)

        override suspend fun updateStatus(id: String, status: LostFoundStatus): Result<Unit> = Result.success(Unit)

        override suspend fun refreshAlerts(): Result<Unit> {
            refreshCount += 1
            posts.value = loaded
            return Result.success(Unit)
        }
    }
}
