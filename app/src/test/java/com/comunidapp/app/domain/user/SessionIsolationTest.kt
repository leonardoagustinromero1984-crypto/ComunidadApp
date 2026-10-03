package com.comunidapp.app.domain.user

import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.m08.AcceptPetTransferParams
import com.comunidapp.app.data.remote.supabase.m08.AccessiblePetM08Row
import com.comunidapp.app.data.remote.supabase.m08.ArchivePetParams
import com.comunidapp.app.data.remote.supabase.m08.AssignPetResponsibilityParams
import com.comunidapp.app.data.remote.supabase.m08.CancelPetTransferParams
import com.comunidapp.app.data.remote.supabase.m08.CreatePetWithPrincipalParams
import com.comunidapp.app.data.remote.supabase.m08.DetectPetDuplicateParams
import com.comunidapp.app.data.remote.supabase.m08.GrantPetAuthorizationParams
import com.comunidapp.app.data.remote.supabase.m08.InitiatePetTransferParams
import com.comunidapp.app.data.remote.supabase.m08.MarkPetDeceasedParams
import com.comunidapp.app.data.remote.supabase.m08.PetAccessContextRow
import com.comunidapp.app.data.remote.supabase.m08.PetAuthorizationM08Row
import com.comunidapp.app.data.remote.supabase.m08.PetDuplicateCandidateRow
import com.comunidapp.app.data.remote.supabase.m08.PetM08RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m08.PetM08Row
import com.comunidapp.app.data.remote.supabase.m08.PetResponsibilityM08Row
import com.comunidapp.app.data.remote.supabase.m08.PetTransferM08Row
import com.comunidapp.app.data.remote.supabase.m08.ProfilePetRow
import com.comunidapp.app.data.remote.supabase.m08.RejectPetTransferParams
import com.comunidapp.app.data.remote.supabase.m08.RestorePetParams
import com.comunidapp.app.data.remote.supabase.m08.RevokePetAuthorizationParams
import com.comunidapp.app.data.remote.supabase.m08.RevokePetResponsibilityParams
import com.comunidapp.app.data.remote.supabase.m08.SetPetAvatarAssetParams
import com.comunidapp.app.data.remote.supabase.m08.UpdatePetHealthParams
import com.comunidapp.app.data.remote.supabase.m08.UpdatePetProfileParams
import com.comunidapp.app.data.repository.LegacyPetRepositoryAdapter
import com.comunidapp.app.data.repository.M16MemoryStore
import com.comunidapp.app.data.repository.M17ExtendedMemoryStore
import com.comunidapp.app.data.repository.M17MemoryStore
import com.comunidapp.app.data.repository.M18EventMemoryStore
import com.comunidapp.app.data.repository.M19SocialMemoryStore
import com.comunidapp.app.data.repository.M20MessagingMemoryStore
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.pets.PetManagementContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionIsolationTest {

    @Test
    fun latePetResponseFromAccountADoesNotAppearForAccountB() = runTest {
        val epoch = ManualSessionEpoch()
        val remote = ScriptedPetRemote()
        var uid: String? = "user-a"
        val adapter = LegacyPetRepositoryAdapter(
            remote = remote,
            authUidProvider = { uid },
            scope = this,
            epoch = epoch,
            startPolling = false,
            nowEpochMs = { testScheduler.currentTime }
        )

        remote.enqueue(listOf(accessiblePet("pet-a", "Luna de A")))
        adapter.refreshAccessiblePets()
        assertEquals(listOf("pet-a"), adapter.observePets().value.map { it.id })
        assertEquals("user-a", adapter.observePets().value.single().accessSubjectUserId)

        advanceTimeBy(1_600)
        val late = remote.enqueueSuspended()
        val pending = async { adapter.refreshAccessiblePets() }
        advanceUntilIdle()
        assertEquals(2, remote.calls)

        uid = "user-b"
        adapter.clearAccountCache()
        assertTrue(adapter.observePets().value.isEmpty())
        assertTrue(adapter.getPetsByOwner("user-b").isEmpty())

        late.complete(listOf(accessiblePet("pet-a", "Luna de A")))
        pending.await()

        assertTrue(adapter.observePets().value.none { it.id == "pet-a" || it.name == "Luna de A" })
        assertTrue(adapter.getPetsByOwner("user-b").isEmpty())

        remote.enqueue(listOf(accessiblePet("pet-shared", "Compartida", ownerId = "user-a")))
        adapter.refreshAccessiblePets()
        val visible = adapter.observePets().value
        assertEquals(listOf("pet-shared"), visible.map { it.id })
        assertEquals("user-b", visible.single().accessSubjectUserId)
        assertTrue(
            PetManagementContext.visibleIn(visible.single(), OperationalContext.Personal, "user-b")
        )
        assertFalse(
            PetManagementContext.visibleIn(
                visible.single().copy(id = "pet-a", accessSubjectUserId = "user-a", ownerId = "user-a"),
                OperationalContext.Personal,
                "user-b"
            )
        )
    }

    @Test
    fun clearInvalidatesPollAndDropsThePreviousResponse() = runTest {
        val epoch = ManualSessionEpoch()
        val remote = ScriptedPetRemote()
        var uid: String? = "user-a"
        val first = remote.enqueueSuspended()
        val poll = Job()
        val adapter = LegacyPetRepositoryAdapter(
            remote = remote,
            authUidProvider = { uid },
            scope = CoroutineScope(coroutineContext + poll),
            epoch = epoch,
            startPolling = true,
            nowEpochMs = { testScheduler.currentTime }
        )
        runCurrent()
        assertEquals(1, remote.calls)
        val generation = epoch.current()

        uid = "user-b"
        adapter.clearAccountCache()
        assertTrue(epoch.current() > generation)
        assertTrue(adapter.observePets().value.isEmpty())

        val restarted = remote.enqueueSuspended()
        first.complete(listOf(accessiblePet("pet-a", "Luna de A")))
        runCurrent()

        assertTrue(adapter.observePets().value.none { it.id == "pet-a" })
        restarted.cancel()
        poll.cancel()
    }

    @Test
    fun oldGenerationDoesNotMutateSessionBoundState() {
        val epoch = ManualSessionEpoch()
        val likes = SessionBoundState(emptySet<String>(), epoch)
        val posts = SessionBoundState(emptyList<String>(), epoch)
        val tokenA = epoch.current()
        assertTrue(likes.tryWrite(tokenA, setOf("saved-by-a")))
        assertTrue(posts.tryWrite(tokenA, listOf("author-view-a")))

        epoch.rotate {
            likes.reset(emptySet())
            posts.reset(emptyList())
        }

        assertFalse(likes.tryWrite(tokenA, setOf("saved-by-a")))
        assertFalse(posts.tryUpdate(tokenA) { it + "author-view-a" })
        assertTrue(likes.value.isEmpty())
        assertTrue(posts.value.isEmpty())

        val tokenB = epoch.current()
        assertTrue(likes.tryWrite(tokenB, setOf("saved-by-b")))
        assertEquals(setOf("saved-by-b"), likes.value)
    }

    @Test
    fun feedClearDoesNotRelaunchAndPersonalizedStateStaysOnCurrentGeneration() {
        val feed = File("src/main/java/com/comunidapp/app/data/repository/CanonicalConsumerRepositories.kt").readText()
        val feedClass = feed.substringAfter("class CanonicalFeedRepository")
        val clear = feedClass.substringAfter("override fun clearAccountCache()").substringBefore("override suspend fun refreshPosts")
        assertTrue(clear.contains("epoch.rotate"))
        assertTrue(clear.contains("liked.reset"))
        assertFalse(clear.contains("refresh("))
        assertTrue(feed.contains("val token = epoch.current()"))
        assertTrue(feed.contains("posts.tryUpdate(token)"))

        val pets = File("src/main/java/com/comunidapp/app/data/repository/LegacyPetRepositoryAdapter.kt").readText()
        val petClear = pets.substringAfter("override fun clearAccountCache()").substringBefore("private fun startPoll")
        assertFalse(petClear.contains("refreshCache"))
        assertTrue(petClear.contains("epoch.rotate"))
        assertTrue(petClear.contains("pollJob?.cancel()"))
    }

    @Test
    fun mockStoresDropPreviousActorAfterAccountSwitch() {
        val actor = "user-a-isolation"
        val shelters = M16MemoryStore().also { it.seedDefaults(actor) }
        val campaigns = M17MemoryStore().also { it.seedDefaults(actor) }
        val events = M18EventMemoryStore().also { it.seedDefaults(actor) }
        val social = M19SocialMemoryStore().also { it.seedDefaults(actor) }
        val messages = M20MessagingMemoryStore().also { it.seedDefaults(actor) }
        val extended = M17ExtendedMemoryStore().also { it.seedDefaults() }

        assertTrue(shelters.organizationManagers.value.values.any { actor in it })
        assertTrue(campaigns.campaigns.value.any { it.createdBy == actor })
        assertTrue(events.registrations.value.any { it.userId == actor })
        assertTrue(social.posts.value.any { it.authorUserId == actor || it.createdBy == actor })
        assertTrue(messages.conversations.value.any { actor in it.participantUserIds })

        shelters.clearSessionResidue()
        campaigns.clearSessionResidue()
        events.clearSessionResidue()
        social.clearSessionResidue()
        messages.clearSessionResidue()
        extended.clearSessionResidue()

        assertFalse(shelters.organizationManagers.value.values.any { actor in it })
        assertFalse(campaigns.campaigns.value.any { it.createdBy == actor })
        assertFalse(campaigns.organizationManagers.value.values.any { actor in it })
        assertFalse(events.registrations.value.any { it.userId == actor })
        assertFalse(events.organizationManagers.value.values.any { actor in it })
        assertFalse(social.posts.value.any { it.authorUserId == actor || it.createdBy == actor })
        assertFalse(messages.conversations.value.any { actor in it.participantUserIds })
        assertFalse(extended.applications.value.any { it.userId == actor })
        assertEquals(
            SessionGeneration.NEUTRAL_MOCK_ACTOR,
            shelters.organizationManagers.value.values.first().single()
        )
    }

    @Test
    fun inMemoryStoreDropsPersonalizedFeedStateAndKeepsDemoCatalog() = runTest {
        val before = InMemoryDataStore.feedPosts.value.size
        InMemoryDataStore.toggleLike("post-session-a", "user-a")
        InMemoryDataStore.addComment("post-session-a", "user-a", "A", "nota de A")
        InMemoryDataStore.clearUserScopedSession()
        assertTrue(InMemoryDataStore.observeLikedPosts("user-a").first().isEmpty())
        assertTrue(InMemoryDataStore.observeComments("post-session-a").first().isEmpty())
        assertEquals(before, InMemoryDataStore.feedPosts.value.size)
    }
}

private fun accessiblePet(
    id: String,
    name: String,
    ownerId: String = "user-a"
) = AccessiblePetM08Row(
    id = id,
    ownerId = ownerId,
    name = name,
    species = PetSpecies.DOG.name,
    sex = PetSex.UNKNOWN.name,
    size = PetSize.MEDIUM.name,
    createdByUserId = ownerId,
    managementContextKind = "PERSON",
    managementContextId = ownerId
)

private class ScriptedPetRemote : UnusedPetRemote() {
    var calls: Int = 0
    private val steps = ArrayDeque<suspend () -> List<AccessiblePetM08Row>>()

    fun enqueue(rows: List<AccessiblePetM08Row>) {
        steps.addLast { rows }
    }

    fun enqueueSuspended(): CompletableDeferred<List<AccessiblePetM08Row>> {
        val gate = CompletableDeferred<List<AccessiblePetM08Row>>()
        steps.addLast { gate.await() }
        return gate
    }

    override suspend fun listAccessiblePets(status: String?): List<AccessiblePetM08Row> {
        calls += 1
        val next = steps.removeFirstOrNull() ?: return emptyList()
        return next()
    }
}

private open class UnusedPetRemote : PetM08RemoteDataSource {
    override suspend fun listAccessiblePets(status: String?): List<AccessiblePetM08Row> = emptyList()
    override suspend fun getPetById(petId: String): PetM08Row? = null
    override suspend fun createPetWithPrincipal(params: CreatePetWithPrincipalParams): PetM08Row = error("unused")
    override suspend fun updatePetProfile(params: UpdatePetProfileParams): PetM08Row = error("unused")
    override suspend fun updatePetHealth(params: UpdatePetHealthParams): PetM08Row = error("unused")
    override suspend fun getPetAccessContext(petId: String): PetAccessContextRow = error("unused")
    override suspend fun archivePet(params: ArchivePetParams): PetM08Row = error("unused")
    override suspend fun restorePet(params: RestorePetParams): PetM08Row = error("unused")
    override suspend fun markPetDeceased(params: MarkPetDeceasedParams): PetM08Row = error("unused")
    override suspend fun setPetAvatarAsset(params: SetPetAvatarAssetParams): PetM08Row = error("unused")
    override suspend fun detectDuplicates(params: DetectPetDuplicateParams): List<PetDuplicateCandidateRow> = emptyList()
    override suspend fun assignResponsibility(params: AssignPetResponsibilityParams): PetResponsibilityM08Row = error("unused")
    override suspend fun revokeResponsibility(params: RevokePetResponsibilityParams): PetResponsibilityM08Row = error("unused")
    override suspend fun listResponsibilities(petId: String): List<PetResponsibilityM08Row> = emptyList()
    override suspend fun grantAuthorization(params: GrantPetAuthorizationParams): PetAuthorizationM08Row = error("unused")
    override suspend fun revokeAuthorization(params: RevokePetAuthorizationParams): PetAuthorizationM08Row = error("unused")
    override suspend fun listAuthorizations(petId: String): List<PetAuthorizationM08Row> = emptyList()
    override suspend fun initiateTransfer(params: InitiatePetTransferParams): PetTransferM08Row = error("unused")
    override suspend fun acceptTransfer(params: AcceptPetTransferParams): PetTransferM08Row = error("unused")
    override suspend fun rejectTransfer(params: RejectPetTransferParams): PetTransferM08Row = error("unused")
    override suspend fun cancelTransfer(params: CancelPetTransferParams): PetTransferM08Row = error("unused")
    override suspend fun listTransfers(petId: String): List<PetTransferM08Row> = emptyList()
    override suspend fun listStatusHistory(petId: String) = emptyList<com.comunidapp.app.data.remote.supabase.m08.PetStatusHistoryM08Row>()
    override suspend fun listPetsForPersonProfile(personUserId: String): List<ProfilePetRow> = emptyList()
}
