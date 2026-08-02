package com.comunidapp.app.domain.m21

import com.comunidapp.app.data.model.M21MockUsers
import com.comunidapp.app.data.model.M21ReviewTargetType
import com.comunidapp.app.data.model.SubmitM21ReviewInput
import com.comunidapp.app.data.repository.M21ReputationMemoryStore
import com.comunidapp.app.data.repository.M21ReputationValidators
import com.comunidapp.app.data.repository.MockM21ReputationRepository
import com.comunidapp.app.data.remote.supabase.m21.M21ReputationErrorMapper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
}
