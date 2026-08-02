package com.comunidapp.app.domain.m21

import com.comunidapp.app.data.model.M21MockUsers
import com.comunidapp.app.data.model.M21ReviewTargetType
import com.comunidapp.app.data.model.SubmitM21ReviewInput
import com.comunidapp.app.data.repository.M21ReputationMemoryStore
import com.comunidapp.app.data.repository.M21ReputationValidators
import com.comunidapp.app.data.repository.MockM21ReputationRepository
import com.comunidapp.app.data.repository.SupabaseM21ReputationRepository
import com.comunidapp.app.data.remote.supabase.m21.M21ReputationErrorMapper
import com.comunidapp.app.data.remote.supabase.m21.toM21PublicReview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class M21ReputationFoundationTest {

    private lateinit var store: M21ReputationMemoryStore
    private lateinit var repository: MockM21ReputationRepository

    @Before
    fun setup() {
        store = M21ReputationMemoryStore()
        repository = MockM21ReputationRepository(
            actorUserId = { M21MockUsers.ADMIN },
            store = store
        )
    }

    @Test
    fun invalidReviewRejected() {
        assertEquals("M21_INVALID_RATING", M21ReputationValidators.validateReviewContent("ok", 0))
        assertEquals("M21_INVALID_REVIEW", M21ReputationValidators.validateReviewContent("", 5))
    }

    @Test
    fun privacySanitizerRedactsEmail() {
        val scrubbed = M21PrivacySanitizer.scrubPublicText("Contacto test@example.com")
        assertFalse(scrubbed.contains("test@example.com"))
    }

    @Test
    fun summarySeeded() = runBlocking {
        val summary = repository.observeMySummary().first()
        assertTrue(summary.reputationScore > 0)
    }

    @Test
    fun submitReviewWorks() = runBlocking {
        val result = repository.submitReview(
            SubmitM21ReviewInput(
                targetType = M21ReviewTargetType.DONATION,
                targetId = "mock_donation_unique",
                targetDisplayLabel = "Donación demo",
                rating = 5,
                content = "Gran experiencia con la campaña."
            )
        ).getOrThrow()
        assertEquals(5, result.rating)
        assertTrue(result.isOwnReview)
    }

    @Test
    fun duplicateReviewRejected() = runBlocking {
        val input = SubmitM21ReviewInput(
            targetType = M21ReviewTargetType.USER,
            targetId = "mock_user_dup",
            targetDisplayLabel = "Usuario",
            rating = 4,
            content = "Primera reseña válida para duplicado."
        )
        repository.submitReview(input).getOrThrow()
        val second = repository.submitReview(input)
        assertTrue(second.isFailure)
        assertEquals("M21_DUPLICATE_REVIEW", second.exceptionOrNull()?.let { M21ReputationErrorMapper.codeOf(it) })
    }

    @Test
    fun publicReviewMapperOmitsReviewerUserId() {
        val json = buildJsonObject {
            put("id", "rev-1")
            put("target_type", "SERVICE")
            put("target_display_label", "Turno")
            put("reviewer_display_name", "Ana")
            put("rating", 5)
            put("content", "Excelente")
            put("status", "PUBLISHED")
            put("created_at", "2026-01-01T12:00:00Z")
            put("is_own_review", false)
        }
        val public = json.toM21PublicReview()
        assertFalse(public.toString().contains("reviewer_user_id"))
    }

    @Test
    fun remoteRepositoryRequiresAuthentication() = runBlocking {
        val repo = SupabaseM21ReputationRepository(actorUserId = { null })
        val result = repo.submitReview(
            SubmitM21ReviewInput(
                targetType = M21ReviewTargetType.SERVICE,
                targetId = "x",
                targetDisplayLabel = "X",
                rating = 5,
                content = "Hola"
            )
        )
        assertTrue(result.isFailure)
        assertEquals("NOT_AUTHENTICATED", result.exceptionOrNull()?.let { M21ReputationErrorMapper.codeOf(it) })
    }
}
