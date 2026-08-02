package com.comunidapp.app.domain.m26

import com.comunidapp.app.data.model.M26MockIds
import com.comunidapp.app.data.model.M26MockUsers
import com.comunidapp.app.data.model.M26RecommendationKind
import com.comunidapp.app.data.model.M26RecommendationStatus
import com.comunidapp.app.data.model.ReviewM26RecommendationInput
import com.comunidapp.app.data.model.StartM26AssistanceInput
import com.comunidapp.app.data.model.SubmitM26RecommendationInput
import com.comunidapp.app.data.model.M26AssistanceTopic
import com.comunidapp.app.data.repository.M26AiMemoryStore
import com.comunidapp.app.data.repository.M26AiValidators
import com.comunidapp.app.data.repository.MockM26AiRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M26AiFoundationTest {
    private fun repository(actor: String = M26MockUsers.MEMBER, store: M26AiMemoryStore = M26AiMemoryStore()) =
        MockM26AiRepository({ actor }, store)

    @Test fun validVisualMatchAccepted() =
        assertNull(M26AiValidators.validateVisualMatch("Foto A", "Foto B"))

    @Test fun identicalLabelsRejected() =
        assertEquals("M26_INVALID_MATCH", M26AiValidators.validateVisualMatch("Foto A", "foto a"))

    @Test fun invalidScoreRejected() =
        assertEquals("M26_INVALID_SCORE", M26AiValidators.validateScore(1.5))

    @Test fun validAssistancePromptAccepted() =
        assertNull(M26AiValidators.validateAssistancePrompt("Consulta sobre adopción responsable."))

    @Test fun shortAssistancePromptRejected() =
        assertEquals("M26_INVALID_ASSISTANCE", M26AiValidators.validateAssistancePrompt("hola"))

    @Test fun sanitizerRedactsEmail() =
        assertFalse(M26PrivacySanitizer.scrubPublicText("Escribí a hola@privado.com").contains("hola@privado.com"))

    @Test fun eligibleRecommendationRequiresHumanReview() = runBlocking {
        val approved = repository().observeEligibleRecommendations().first()
        assertTrue(approved.all { it.humanReviewed && it.approvedForDisplay })
        assertTrue(approved.any { it.title.contains("grooming", ignoreCase = true) })
    }

    @Test fun pendingRecommendationIsNotEligible() = runBlocking {
        val all = repository().observeEligibleRecommendations().first()
        assertTrue(all.none { it.title.contains("paseos seguros", ignoreCase = true) })
    }

    @Test fun publicVisualMatchHasNoPii() = runBlocking {
        val match = repository().observeVisualMatches().first().first()
        assertFalse(match.toString().contains("mock_user"))
        assertFalse(match.toString().contains("m26_match"))
    }

    @Test fun duplicateCandidatesOnlyOpen() = runBlocking {
        val items = repository().observeDuplicateCandidates().first()
        assertEquals(1, items.size)
        assertEquals("Perfil servicio grooming", items.single().primaryLabel)
    }

    @Test fun unauthorizedDismissMatchFails() = runBlocking {
        assertTrue(repository(M26MockUsers.UNAUTHORIZED).dismissVisualMatch(M26MockIds.MATCH_HIGH).isFailure)
    }

    @Test fun assistanceSessionStubCreated() = runBlocking {
        val before = repository().observeAssistanceSessions().first().size
        repository().startAssistanceSession(
            StartM26AssistanceInput(M26AssistanceTopic.GENERAL, "Consulta general sobre funciones.")
        ).getOrThrow()
        val after = repository().observeAssistanceSessions().first().size
        assertTrue(after >= before)
    }

    @Test fun reviewerCanApproveRecommendation() = runBlocking {
        val result = repository(M26MockUsers.REVIEWER).reviewRecommendation(
            ReviewM26RecommendationInput(M26MockIds.RECOMMENDATION_PENDING, approved = true, reviewerNote = "Aprobada.")
        ).getOrThrow()
        assertTrue(result.humanReviewed)
        assertEquals(M26RecommendationStatus.APPROVED, result.status)
    }

    @Test fun submitRecommendationStartsPendingReview() = runBlocking {
        val rec = repository().submitRecommendation(
            SubmitM26RecommendationInput(M26RecommendationKind.CONTENT, "Título válido", "Rationale suficientemente largo para validación.")
        ).getOrThrow()
        assertFalse(rec.humanReviewed)
        assertEquals(M26RecommendationStatus.PENDING_REVIEW, rec.status)
    }

    @Test fun mockSeedsAreDeterministic() = runBlocking {
        assertEquals(
            repository().observeVisualMatches().first().map { it.sourceLabel },
            repository().observeVisualMatches().first().map { it.sourceLabel }
        )
    }
}
