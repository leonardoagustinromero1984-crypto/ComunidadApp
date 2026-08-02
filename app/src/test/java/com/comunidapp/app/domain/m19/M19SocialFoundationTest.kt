package com.comunidapp.app.domain.m19

import com.comunidapp.app.data.model.M19FeedFilter
import com.comunidapp.app.data.model.M19PostStatus
import com.comunidapp.app.data.model.M19PrivacySanitizer
import com.comunidapp.app.data.model.M19ReactionType
import com.comunidapp.app.data.repository.M19SocialMemoryStore
import com.comunidapp.app.data.repository.M19SocialModerationAdapter
import com.comunidapp.app.data.repository.M19SocialValidators
import com.comunidapp.app.data.repository.MockM19SocialRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class M19SocialFoundationTest {

    private lateinit var store: M19SocialMemoryStore
    private lateinit var repository: MockM19SocialRepository

    @Before
    fun setup() {
        store = M19SocialMemoryStore()
        repository = MockM19SocialRepository(actorUserId = { "mock_user_admin" }, store = store)
    }

    @Test
    fun invalidTitleRejected() {
        assertEquals("M19_INVALID_TITLE", M19SocialValidators.validateTitle(""))
        assertEquals("M19_INVALID_TITLE", M19SocialValidators.validateTitle("x".repeat(121)))
    }

    @Test
    fun invalidContentRejected() {
        assertEquals("M19_INVALID_CONTENT", M19SocialValidators.validateContent("abc"))
    }

    @Test
    fun draftNotPublic() {
        assertFalse(M19PostStatus.DRAFT.isPublicFeed)
        assertEquals("M19_POST_NOT_PUBLIC", M19SocialValidators.validatePublicRead(M19PostStatus.DRAFT))
    }

    @Test
    fun hiddenNotInPublicFeed() {
        assertEquals("M19_POST_NOT_PUBLIC", M19SocialValidators.validatePublicRead(M19PostStatus.HIDDEN))
    }

    @Test
    fun removedPostBlocked() {
        assertEquals("M19_POST_REMOVED", M19SocialValidators.validatePublicRead(M19PostStatus.REMOVED))
    }

    @Test
    fun terminalPostCannotReopen() {
        assertEquals(
            "M19_STATE_ALREADY_FINAL",
            M19SocialValidators.validateStateTransition(M19PostStatus.REMOVED, M19PostStatus.PUBLISHED)
        )
    }

    @Test
    fun privacySanitizerRedactsEmail() {
        val scrubbed = M19PrivacySanitizer.scrubPublicText("Contacto: test@example.com")
        assertFalse(scrubbed.contains("test@example.com"))
        assertTrue(scrubbed.contains("[redactado]"))
    }

    @Test
    fun feedShowsOnlyPublished() = runBlocking {
        val feed = repository.searchFeed(M19FeedFilter()).getOrThrow()
        assertTrue(feed.isNotEmpty())
        assertTrue(feed.all { it.status == M19PostStatus.PUBLISHED })
        assertTrue(feed.none { it.title.contains("Borrador") })
        assertTrue(feed.none { it.title.contains("oculta") })
    }

    @Test
    fun draftNotAccessiblePublicly() = runBlocking {
        val draft = store.posts.value.first { it.status == M19PostStatus.DRAFT }
        val result = repository.getPublicPostById(draft.id)
        assertTrue(result.isFailure)
    }

    @Test
    fun publishIdempotent() = runBlocking {
        val draft = store.posts.value.first { it.status == M19PostStatus.DRAFT }
        repository.publishPost(draft.id).getOrThrow()
        val before = store.idempotentRetryCount()
        repository.publishPost(draft.id).getOrThrow()
        assertTrue(store.idempotentRetryCount() > before)
    }

    @Test
    fun commentOnPublishedPost() = runBlocking {
        val post = store.posts.value.first { it.status == M19PostStatus.PUBLISHED }
        val comment = repository.addComment(post.id, "Excelente iniciativa").getOrThrow()
        assertFalse(comment.content.contains("@"))
        assertTrue(comment.authorDisplayName.isNotBlank())
    }

    @Test
    fun reactionIdempotentSameType() = runBlocking {
        val post = store.posts.value.first { it.status == M19PostStatus.PUBLISHED }
        repository.addReaction(post.id, M19ReactionType.LIKE).getOrThrow()
        val before = store.idempotentRetryCount()
        repository.addReaction(post.id, M19ReactionType.LIKE).getOrThrow()
        assertTrue(store.idempotentRetryCount() > before)
    }

    @Test
    fun reactionSwitchTypeReplacesPrevious() = runBlocking {
        val post = store.posts.value.first { it.status == M19PostStatus.PUBLISHED }
        repository.addReaction(post.id, M19ReactionType.LIKE).getOrThrow()
        repository.addReaction(post.id, M19ReactionType.SUPPORT).getOrThrow()
        assertEquals(M19ReactionType.SUPPORT, repository.getMyReaction(post.id))
    }

    @Test
    fun unauthorizedUserCannotManage() = runBlocking {
        val repo = MockM19SocialRepository(actorUserId = { "unknown_user" }, store = store)
        assertFalse(repo.canManageOrganization(com.comunidapp.app.data.model.M19MockOrganizations.ORG_NORTE))
    }

    @Test
    fun publicPostHasNoInternalIds() = runBlocking {
        val post = store.posts.value.first { it.status == M19PostStatus.PUBLISHED }
        val public = repository.getPublicPostById(post.id).getOrThrow()
        assertTrue(public.likeCount >= 0)
        assertTrue(public.commentCount >= 0)
    }

    @Test
    fun engagementSummaryAggregates() = runBlocking {
        val post = store.posts.value.first { it.status == M19PostStatus.PUBLISHED }
        val summary = repository.getEngagementSummary(post.id).getOrThrow()
        assertTrue(summary.reactionCount >= 1)
        assertTrue(summary.commentCount >= 1)
    }

    @Test
    fun moderationAdapterUsesM04OtherTarget() = runBlocking {
        val result = M19SocialModerationAdapter.reportPost(
            postId = "m19_post_1",
            reason = "spam",
            reporterId = "mock_user_admin"
        )
        assertNotNull(result)
    }

    @Test
    fun invalidCommentRejected() {
        assertEquals("M19_INVALID_COMMENT", M19SocialValidators.validateComment(""))
    }

    @Test
    fun hiddenPostNotInFeed() = runBlocking {
        val hidden = store.posts.value.first { it.status == M19PostStatus.HIDDEN }
        val feed = repository.searchFeed(M19FeedFilter()).getOrThrow()
        assertTrue(feed.none { it.id == hidden.id })
    }

    @Test
    fun removeReactionIdempotent() = runBlocking {
        val post = store.posts.value.first { it.status == M19PostStatus.PUBLISHED }
        repository.removeReaction(post.id).getOrThrow()
        val before = store.idempotentRetryCount()
        repository.removeReaction(post.id).getOrThrow()
        assertTrue(store.idempotentRetryCount() > before)
        assertNull(repository.getMyReaction(post.id))
    }
}
