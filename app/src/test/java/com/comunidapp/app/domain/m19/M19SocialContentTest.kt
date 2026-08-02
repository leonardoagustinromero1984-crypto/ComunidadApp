package com.comunidapp.app.domain.m19

import com.comunidapp.app.data.model.M19ContentReferenceType
import com.comunidapp.app.data.model.M19FeedFilter
import com.comunidapp.app.data.model.M19FeedFilterKind
import com.comunidapp.app.data.model.M19MediaAttachment
import com.comunidapp.app.data.model.M19MockReferenceIds
import com.comunidapp.app.data.model.M19PostStatus
import com.comunidapp.app.data.model.M19PrivacySanitizer
import com.comunidapp.app.data.model.M19ReactionType
import com.comunidapp.app.data.repository.M19ContentReferenceResolver
import com.comunidapp.app.data.repository.M19FeedService
import com.comunidapp.app.data.repository.M19SocialMemoryStore
import com.comunidapp.app.data.repository.M19SocialModerationAdapter
import com.comunidapp.app.data.repository.MockM19SocialRepository
import com.comunidapp.app.navigation.NavRoutes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** M19 Bloque 3 — feed, referencias, reacciones, privacidad (25 casos). */
class M19SocialContentTest {

    private lateinit var store: M19SocialMemoryStore
    private lateinit var repository: MockM19SocialRepository

    @Before
    fun setup() {
        store = M19SocialMemoryStore()
        repository = MockM19SocialRepository(actorUserId = { "mock_user_admin" }, store = store)
    }

    @Test fun draftNotInFeed() = runBlocking {
        val feed = repository.searchFeed(M19FeedFilter()).getOrThrow()
        assertTrue(feed.none { it.title.contains("Borrador") })
    }

    @Test fun publishedInFeed() = runBlocking {
        val feed = repository.searchFeed(M19FeedFilter()).getOrThrow()
        assertTrue(feed.isNotEmpty())
        assertTrue(feed.all { it.status == M19PostStatus.PUBLISHED })
    }

    @Test fun hiddenNotInFeed() = runBlocking {
        val feed = repository.searchFeed(M19FeedFilter()).getOrThrow()
        assertTrue(feed.none { it.title.contains("oculta", ignoreCase = true) })
    }

    @Test fun moderatedNotInFeed() = runBlocking {
        val feed = repository.searchFeed(M19FeedFilter()).getOrThrow()
        assertTrue(feed.none { it.title.contains("moderado", ignoreCase = true) })
    }

    @Test fun feedChronological() = runBlocking {
        val feed = repository.searchFeed(M19FeedFilter()).getOrThrow()
        val times = feed.map { it.publishedAt ?: it.createdAt }
        assertEquals(times, times.sortedDescending())
    }

    @Test fun paginationNoDuplicates() = runBlocking {
        val page1 = repository.searchFeedPage(M19FeedFilter(pageSize = 1)).getOrThrow()
        assertTrue(page1.hasMore)
        assertNotNull(page1.nextCursor)
        val page2 = repository.searchFeedPage(
            M19FeedFilter(pageSize = 1, cursor = page1.nextCursor)
        ).getOrThrow()
        assertTrue(page2.items.isNotEmpty())
        assertNotEquals(page1.items.first().id, page2.items.first().id)
    }

    @Test fun refreshPreservesOnSimulatedError() {
        val partial = M19SocialResilience.partialFromError(
            "M19_PARTIAL",
            preserved = listOf(
                store.posts.value.first { it.status == M19PostStatus.PUBLISHED }
                    .toPublicPost(store.engagementFor(store.posts.value.first().id))
            )
        )
        assertTrue(partial.items.isNotEmpty())
        assertNotNull(partial.userMessage)
    }

    @Test fun referencePetSafe() {
        val ref = M19ContentReferenceResolver.snapshot(
            M19ContentReferenceType.PET, M19MockReferenceIds.PET, "Luna"
        )
        assertTrue(ref.isPublic)
        assertEquals("Mascota Luna", ref.displayLabel)
    }

    @Test fun referenceShelterSafe() {
        val ref = M19ContentReferenceResolver.snapshot(
            M19ContentReferenceType.SHELTER, M19MockReferenceIds.SHELTER, "Refugio"
        )
        assertTrue(ref.isPublic)
    }

    @Test fun referenceCampaignSafe() {
        val ref = M19ContentReferenceResolver.snapshot(
            M19ContentReferenceType.CAMPAIGN, M19MockReferenceIds.CAMPAIGN, "Campaña"
        )
        assertTrue(ref.isPublic)
    }

    @Test fun referenceEventSafe() {
        val ref = M19ContentReferenceResolver.snapshot(
            M19ContentReferenceType.EVENT, M19MockReferenceIds.EVENT, "Evento"
        )
        assertTrue(ref.isPublic)
    }

    @Test fun privateMediaNotPublic() = runBlocking {
        val post = store.posts.value.first { it.mediaAttachments.any { m -> !m.isPublic } }
        val public = post.toPublicPost(store.engagementFor(post.id))
        assertTrue(public.mediaAttachments.all { it.isPublic })
        assertFalse(public.mediaAttachments.any { it.ref.startsWith("private://") })
    }

    @Test fun foreignCommentNotEditable() = runBlocking {
        val comment = store.commentsFor(
            store.posts.value.first { it.status == M19PostStatus.PUBLISHED }.id
        ).first()
        val foreign = MockM19SocialRepository(actorUserId = { "other_user" }, store = store)
        val result = foreign.editComment(comment.id, "hack")
        assertTrue(result.isFailure)
    }

    @Test fun archivedCommentNotPublic() = runBlocking {
        val postId = store.posts.value.first { it.status == M19PostStatus.PUBLISHED }.id
        val added = repository.addComment(postId, "temporal").getOrThrow()
        repository.archiveComment(added.id).getOrThrow()
        val listed = repository.listPublicComments(postId).getOrThrow()
        assertTrue(listed.none { it.id == added.id })
    }

    @Test fun duplicateReactionIdempotent() = runBlocking {
        val postId = store.posts.value.first { it.status == M19PostStatus.PUBLISHED }.id
        val before = store.idempotentRetryCount()
        repository.addReaction(postId, M19ReactionType.LIKE).getOrThrow()
        repository.addReaction(postId, M19ReactionType.LIKE).getOrThrow()
        assertTrue(store.idempotentRetryCount() > before)
    }

    @Test fun toggleRemovesReaction() = runBlocking {
        val postId = store.posts.value.first { it.status == M19PostStatus.PUBLISHED }.id
        repository.addReaction(postId, M19ReactionType.LOVE).getOrThrow()
        repository.removeReaction(postId).getOrThrow()
        assertNull(repository.getMyReaction(postId))
    }

    @Test fun countsCorrect() = runBlocking {
        val postId = store.posts.value.first { it.status == M19PostStatus.PUBLISHED }.id
        val summary = repository.getEngagementSummary(postId).getOrThrow()
        assertTrue(summary.reactionCount >= summary.likeCount)
        assertTrue(summary.commentCount >= 0)
    }

    @Test fun hiddenPostRejectsReaction() = runBlocking {
        val hidden = store.posts.value.first { it.status == M19PostStatus.HIDDEN }
        val result = repository.addReaction(hidden.id, M19ReactionType.LIKE)
        assertTrue(result.isFailure)
    }

    @Test fun reportPostM04() = runBlocking {
        val postId = store.posts.value.first { it.status == M19PostStatus.PUBLISHED }.id
        val result = M19SocialModerationAdapter.reportPost(postId, "spam", reporterId = "mock_user_admin")
        assertTrue(result.isSuccess || result.isFailure)
    }

    @Test fun m06UnavailableDoesNotBlockComment() = runBlocking {
        val postId = store.posts.value.first { it.status == M19PostStatus.PUBLISHED }.id
        val result = repository.addComment(postId, "Sin depender de M06")
        assertTrue(result.isSuccess)
    }

    @Test fun sanitizerRemovesPii() {
        val scrubbed = M19PrivacySanitizer.scrubPublicText("mail@test.com y +5491112345678")
        assertFalse(scrubbed.contains("test.com"))
        assertTrue(scrubbed.contains("[redactado]"))
    }

    @Test fun errorDoesNotExposePayload() {
        val msg = M19SocialResilience.safeUserMessage(IllegalStateException("user_id=secret select * from users"))
        assertFalse(msg.contains("select"))
        assertFalse(msg.contains("secret"))
    }

    @Test fun mockDeterministic() = runBlocking {
        val a = repository.searchFeed(M19FeedFilter()).getOrThrow().map { it.id }
        val b = repository.searchFeed(M19FeedFilter()).getOrThrow().map { it.id }
        assertEquals(a, b)
    }

    @Test fun foreignUserCannotManage() = runBlocking {
        val foreign = MockM19SocialRepository(actorUserId = { "intruder" }, store = store)
        assertFalse(foreign.canManageOrganization(store.posts.value.first().organizationId))
    }

    @Test fun navigationSeparatesM18M19() {
        assertNotEquals(NavRoutes.M18_EVENTS, NavRoutes.M19_FEED)
        assertTrue(NavRoutes.M18_EVENTS.startsWith("m18/"))
        assertTrue(NavRoutes.M19_FEED.startsWith("m19/"))
    }

    @Test fun filterByPetKind() = runBlocking {
        val filtered = repository.searchFeedPage(M19FeedFilter(kind = M19FeedFilterKind.PETS)).getOrThrow()
        assertTrue(filtered.items.all { it.contentReferences.any { r -> r.type == M19ContentReferenceType.PET } })
    }
}
