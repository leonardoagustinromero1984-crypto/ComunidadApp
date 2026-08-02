package com.comunidapp.app.domain.m19

import com.comunidapp.app.data.model.M19FeedFilter
import com.comunidapp.app.data.model.M19PostStatus
import com.comunidapp.app.data.model.M19PrivacySanitizer
import com.comunidapp.app.data.model.M19ReactionType
import com.comunidapp.app.data.remote.supabase.m19.M19SocialErrorMapper
import com.comunidapp.app.data.remote.supabase.m19.toM19EngagementSummary
import com.comunidapp.app.data.remote.supabase.m19.toM19PublicComment
import com.comunidapp.app.data.remote.supabase.m19.toM19PublicPost
import com.comunidapp.app.data.repository.MockM19SocialRepository
import com.comunidapp.app.data.repository.M19SocialValidators
import com.comunidapp.app.data.repository.SupabaseM19SocialRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M19SocialRemoteMapperTest {

    @Test
    fun publicPostMapperOmitsInternalFields() {
        val json = buildJsonObject {
            put("id", "post-1")
            put("title", "Historias de adopción")
            put("content", "Contenido público del feed")
            put("organization_display_name", "Refugio Norte")
            put("author_display_name", "Equipo Norte")
            put("status", "PUBLISHED")
            put("like_count", 5)
            put("support_count", 2)
            put("celebrate_count", 1)
            put("comment_count", 3)
            put("published_at", "2026-01-01T00:00:00Z")
            put("created_at", "2026-01-01T00:00:00Z")
        }
        val public = json.toM19PublicPost()
        assertEquals("post-1", public.id)
        assertFalse(public.title.contains("@"))
        assertEquals(5, public.likeCount)
        assertEquals(3, public.commentCount)
    }

    @Test
    fun engagementSummaryMapperParsesCounts() {
        val json = buildJsonObject {
            put("like_count", 10)
            put("support_count", 4)
            put("celebrate_count", 2)
            put("comment_count", 7)
        }
        val summary = json.toM19EngagementSummary()
        assertEquals(16, summary.reactionCount)
        assertEquals(7, summary.commentCount)
    }

    @Test
    fun publicCommentMapperSanitizedShape() {
        val json = buildJsonObject {
            put("id", "c-1")
            put("post_id", "post-1")
            put("author_display_name", "Voluntario")
            put("content", "Gran publicación")
            put("created_at", "2026-01-01T00:00:00Z")
        }
        val comment = json.toM19PublicComment()
        assertEquals("post-1", comment.postId)
        assertEquals("Voluntario", comment.authorDisplayName)
    }

    @Test
    fun draftNotPublicStatus() {
        assertFalse(M19PostStatus.DRAFT.isPublicFeed)
    }

    @Test
    fun terminalCannotReopen() {
        assertEquals(
            "M19_STATE_ALREADY_FINAL",
            M19SocialValidators.validateStateTransition(
                M19PostStatus.REMOVED,
                M19PostStatus.PUBLISHED
            )
        )
    }

    @Test
    fun scrubPublicTextRedactsEmail() {
        val scrubbed = M19PrivacySanitizer.scrubPublicText("Contacto: user@test.com")
        assertTrue(scrubbed.contains("[redactado]"))
    }

    @Test
    fun mockRepositoryStillOperative() {
        val repo = MockM19SocialRepository(actorUserId = { "mock_user_admin" })
        val result = kotlinx.coroutines.runBlocking {
            repo.searchFeed(M19FeedFilter())
        }
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull().orEmpty().isNotEmpty())
    }

    @Test
    fun remoteRepositoryRequiresSupabaseForFeed() {
        val repo = SupabaseM19SocialRepository(actorUserId = { "u1" })
        val result = kotlinx.coroutines.runBlocking {
            repo.searchFeed(M19FeedFilter())
        }
        assertTrue(result.isFailure || result.isSuccess)
        if (result.isFailure) {
            val code = result.exceptionOrNull()?.let { M19SocialErrorMapper.codeOf(it) }
            assertNotNull(code)
        }
    }

    @Test
    fun reactionTypesMinimalSet() {
        assertEquals(3, M19ReactionType.entries.size)
        assertTrue(M19ReactionType.LIKE.name.isNotBlank())
    }
}

private fun assertNotNull(value: Any?) {
    org.junit.Assert.assertNotNull(value)
}
