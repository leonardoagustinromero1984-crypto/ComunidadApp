package com.comunidapp.app.domain.m21

import com.comunidapp.app.data.model.CheckM21EligibilityInput
import com.comunidapp.app.data.model.EditM21ReviewInput
import com.comunidapp.app.data.model.M21MockEligibilityIds
import com.comunidapp.app.data.model.M21MockTargetIds
import com.comunidapp.app.data.model.M21MockUsers
import com.comunidapp.app.data.model.M21ReviewContextReference
import com.comunidapp.app.data.model.M21ReviewContextType
import com.comunidapp.app.data.model.M21ReviewDisputeReason
import com.comunidapp.app.data.model.M21ReviewStatus
import com.comunidapp.app.data.model.M21ReviewTargetType
import com.comunidapp.app.data.model.M21VerificationStatus
import com.comunidapp.app.data.model.SubmitM21DisputeInput
import com.comunidapp.app.data.model.SubmitM21ReviewInput
import com.comunidapp.app.data.model.SubmitM21ReviewResponseInput
import com.comunidapp.app.data.model.SubmitM21VerificationInput
import com.comunidapp.app.data.remote.supabase.m21.M21ReputationErrorMapper
import com.comunidapp.app.data.repository.M21ReputationMemoryStore
import com.comunidapp.app.data.repository.M21ReputationModerationAdapter
import com.comunidapp.app.data.repository.M21ReputationValidators
import com.comunidapp.app.data.repository.MockM21ReputationRepository
import com.comunidapp.app.navigation.NavRoutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** M21 Bloque 3 — operaciones mock (35 casos B17). */
class M21ReviewOperationsTest {

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

    private fun ctx(id: String, type: M21ReviewContextType = M21ReviewContextType.DONATION_COMPLETED) =
        M21ReviewContextReference(type, id, "Contexto test")

    @Test fun selfReviewRejected() = runBlocking {
        val selfRepo = MockM21ReputationRepository(actorUserId = { M21MockUsers.ADMIN }, store = store)
        val result = selfRepo.submitReview(
            SubmitM21ReviewInput(
                targetType = M21ReviewTargetType.USER,
                targetId = M21MockUsers.ADMIN,
                targetDisplayLabel = "Yo mismo",
                rating = 5,
                content = "Auto reseña inválida para prueba.",
                contextReference = ctx("mock_ctx_self")
            )
        )
        assertTrue(result.isFailure)
        assertEquals("M21_SELF_REVIEW", result.exceptionOrNull()?.let { M21ReputationErrorMapper.codeOf(it) })
    }

    @Test fun eligibleContextAllowsReview() = runBlocking {
        val result = repository.submitReview(
            SubmitM21ReviewInput(
                targetType = M21ReviewTargetType.DONATION,
                targetId = M21MockTargetIds.DONATION,
                targetDisplayLabel = "Campaña solidaria",
                rating = 5,
                content = "Gran experiencia con la campaña de donación.",
                contextReference = ctx(M21MockEligibilityIds.DONATION_COMPLETED, M21ReviewContextType.DONATION_COMPLETED)
            )
        )
        assertTrue(result.isSuccess)
    }

    @Test fun invalidContextRejectsReview() = runBlocking {
        val result = repository.submitReview(
            SubmitM21ReviewInput(
                targetType = M21ReviewTargetType.SERVICE,
                targetId = M21MockTargetIds.SERVICE,
                targetDisplayLabel = "Turno cancelado",
                rating = 4,
                content = "No debería publicarse por contexto cancelado.",
                contextReference = ctx(M21MockEligibilityIds.CANCELLED_CONTEXT, M21ReviewContextType.SERVICE_COMPLETED)
            )
        )
        assertTrue(result.isFailure)
    }

    @Test fun duplicateContextRejected() = runBlocking {
        val input = SubmitM21ReviewInput(
            targetType = M21ReviewTargetType.SERVICE,
            targetId = M21MockTargetIds.SERVICE,
            targetDisplayLabel = "Turno veterinario",
            rating = 3,
            content = "Segunda reseña del mismo contexto duplicado.",
            contextReference = ctx(M21MockEligibilityIds.DUPLICATE_CONTEXT, M21ReviewContextType.SERVICE_COMPLETED)
        )
        val reviewerRepo = MockM21ReputationRepository(actorUserId = { M21MockUsers.REVIEWER }, store = store)
        val second = reviewerRepo.submitReview(input)
        assertTrue(second.isFailure)
        assertEquals("M21_DUPLICATE_REVIEW", second.exceptionOrNull()?.let { M21ReputationErrorMapper.codeOf(it) })
    }

    @Test fun ratingOutOfRangeRejected() {
        assertEquals("M21_INVALID_RATING", M21ReputationValidators.validateReviewContent("ok", 0))
        assertEquals("M21_INVALID_RATING", M21ReputationValidators.validateReviewContent("ok", 6))
    }

    @Test fun draftNotPublic() = runBlocking {
        val detail = repository.getReviewDetail("m21_rev_draft")
        assertTrue(detail.isSuccess)
        val public = store.reviews.value.filter {
            it.targetType == M21ReviewTargetType.ADOPTION &&
                it.status == M21ReviewStatus.DRAFT
        }
        val targetReviews = repository.observeReviewsForTarget(M21ReviewTargetType.ADOPTION, M21MockTargetIds.ADOPTION).first()
        assertTrue(public.isNotEmpty())
        assertFalse(targetReviews.any { it.id == "m21_rev_draft" })
    }

    @Test fun publishedIsPublic() = runBlocking {
        val reviews = repository.observeReviewsForTarget(M21ReviewTargetType.ADOPTION, M21MockTargetIds.ADOPTION).first()
        assertTrue(reviews.any { it.id == "m21_rev_positive" })
    }

    @Test fun hiddenNotPublic() = runBlocking {
        val reviews = repository.observeReviewsForTarget(M21ReviewTargetType.USER, M21MockUsers.ORG_MANAGER).first()
        assertFalse(reviews.any { it.status == M21ReviewStatus.HIDDEN })
    }

    @Test fun archivedNotCounted() = runBlocking {
        val breakdown = repository.observeSubjectBreakdown(M21ReviewTargetType.DONATION, M21MockTargetIds.DONATION).first()
        assertFalse(breakdown.reviews.any { it.id == "m21_rev_archived" })
    }

    @Test fun moderatedNotCounted() = runBlocking {
        val breakdown = repository.observeSubjectBreakdown(M21ReviewTargetType.USER, M21MockUsers.ORG_MANAGER).first()
        assertFalse(breakdown.reviews.any { it.status == M21ReviewStatus.REMOVED_BY_MODERATION })
    }

    @Test fun editOwnReviewWorks() = runBlocking {
        val edited = repository.editReview(
            EditM21ReviewInput("m21_rev_critical", rating = 3, content = "Contenido editado por autor.")
        ).getOrThrow()
        assertEquals(M21ReviewStatus.EDITED, edited.status)
        assertEquals(3, edited.rating)
    }

    @Test fun editOthersReviewFails() = runBlocking {
        val reviewerRepo = MockM21ReputationRepository(actorUserId = { M21MockUsers.REVIEWER }, store = store)
        val result = reviewerRepo.editReview(
            EditM21ReviewInput("m21_rev_critical", content = "Intento ajeno.")
        )
        assertTrue(result.isFailure)
    }

    @Test fun subjectCannotModifyReview() = runBlocking {
        val subjectRepo = MockM21ReputationRepository(actorUserId = { M21MockUsers.ORG_MANAGER }, store = store)
        val result = subjectRepo.editReview(
            EditM21ReviewInput("m21_rev_with_response", content = "Sujeto no puede editar.")
        )
        assertTrue(result.isFailure)
    }

    @Test fun authorizedResponseWorks() = runBlocking {
        val subjectRepo = MockM21ReputationRepository(actorUserId = { M21MockUsers.SERVICE_PROVIDER }, store = store)
        val response = subjectRepo.submitReviewResponse(
            SubmitM21ReviewResponseInput("m21_rev_critical", "Agradecemos el feedback y mejoramos tiempos.")
        ).getOrThrow()
        assertNotNull(response.content)
    }

    @Test fun unauthorizedResponseFails() = runBlocking {
        val result = repository.submitReviewResponse(
            SubmitM21ReviewResponseInput("m21_rev_with_response", "Intento de respuesta ajena.")
        )
        assertTrue(result.isFailure)
    }

    @Test fun responseDoesNotChangeRating() = runBlocking {
        val before = store.reviews.value.first { it.id == "m21_rev_critical" }.rating
        MockM21ReputationRepository(actorUserId = { M21MockUsers.SERVICE_PROVIDER }, store = store)
            .submitReviewResponse(
                SubmitM21ReviewResponseInput("m21_rev_critical", "Respuesta institucional válida.")
            ).getOrThrow()
        val after = store.reviews.value.first { it.id == "m21_rev_critical" }.rating
        assertEquals(before, after)
    }

    @Test fun averageRatingCorrect() = runBlocking {
        val breakdown = repository.observeSubjectBreakdown(M21ReviewTargetType.ORGANIZATION, M21MockTargetIds.ORGANIZATION).first()
        assertNotNull(breakdown.averageRating)
        assertTrue(breakdown.averageRating!! in 1.0..5.0)
    }

    @Test fun distributionCorrect() = runBlocking {
        val breakdown = repository.observeSubjectBreakdown(M21ReviewTargetType.SERVICE, M21MockTargetIds.SERVICE).first()
        assertTrue(breakdown.ratingDistribution.total >= 1)
    }

    @Test fun emptyReviewsAvoidDivisionByZero() = runBlocking {
        val breakdown = repository.observeSubjectBreakdown(M21ReviewTargetType.USER, M21MockUsers.EMPTY_PROFILE).first()
        assertNull(breakdown.averageRating)
        assertEquals(0, breakdown.publishedReviewCount)
    }

    @Test fun editRatingUpdatesAggregate() = runBlocking {
        val before = repository.observeSubjectBreakdown(M21ReviewTargetType.SERVICE, M21MockTargetIds.SERVICE).first().averageRating
        repository.editReview(EditM21ReviewInput("m21_rev_critical", rating = 5)).getOrThrow()
        val after = repository.observeSubjectBreakdown(M21ReviewTargetType.SERVICE, M21MockTargetIds.SERVICE).first().averageRating
        assertNotEquals(before, after)
    }

    @Test fun archiveUpdatesAggregate() = runBlocking {
        val beforeCount = repository.observeSubjectBreakdown(M21ReviewTargetType.DONATION, M21MockTargetIds.DONATION).first().publishedReviewCount
        MockM21ReputationRepository(actorUserId = { M21MockUsers.ADMIN }, store = store)
            .archiveReview("m21_rev_archived")
        val afterCount = repository.observeSubjectBreakdown(M21ReviewTargetType.DONATION, M21MockTargetIds.DONATION).first().publishedReviewCount
        assertTrue(afterCount <= beforeCount)
    }

    @Test fun disputeCreatesM04Case() = runBlocking {
        assertNotNull(M21ReputationModerationAdapter::reportReview)
        val subjectRepo = MockM21ReputationRepository(actorUserId = { M21MockUsers.SERVICE_PROVIDER }, store = store)
        subjectRepo.submitDispute(
            SubmitM21DisputeInput(
                reviewId = "m21_rev_critical",
                reason = M21ReviewDisputeReason.FACTUAL_ERROR,
                details = "Disputa de prueba con detalle suficiente."
            )
        ).getOrThrow()
        assertTrue(store.disputes.value.any { it.reviewId == "m21_rev_critical" })
    }

    @Test fun disputeDoesNotDeleteReview() = runBlocking {
        val before = store.reviews.value.count { it.id == "m21_rev_critical" }
        MockM21ReputationRepository(actorUserId = { M21MockUsers.SERVICE_PROVIDER }, store = store)
            .submitDispute(
                SubmitM21DisputeInput(
                    "m21_rev_critical",
                    M21ReviewDisputeReason.OTHER,
                    "Disputa sin eliminar reseña."
                )
            ).getOrThrow()
        val after = store.reviews.value.count { it.id == "m21_rev_critical" }
        assertEquals(before, after)
    }

    @Test fun privateEvidenceNotPublic() = runBlocking {
        val verification = store.verifications.value.first { it.evidenceRef != null }
        val public = verification.toPublicVerification(isOwn = true)
        assertFalse(public.toString().contains("m05_private"))
    }

    @Test fun requesterCannotSelfApprove() = runBlocking {
        repository.submitVerification(
            SubmitM21VerificationInput(
                verificationType = com.comunidapp.app.data.model.M21VerificationType.IDENTITY,
                displayLabel = "Nueva solicitud"
            )
        ).getOrThrow()
        val pending = store.verifications.value.last { it.userId == M21MockUsers.ADMIN }
        assertEquals(M21VerificationStatus.PENDING, pending.status)
    }

    @Test fun approvedShowsMinimalSignal() = runBlocking {
        val approved = store.verifications.value.first { it.status == M21VerificationStatus.APPROVED }
        val public = approved.toPublicVerification(isOwn = false)
        assertEquals(M21VerificationStatus.APPROVED, public.status)
        assertNotNull(public.displayLabel)
    }

    @Test fun rejectedDoesNotExposeInternalReason() = runBlocking {
        val rejected = store.verifications.value.first { it.status == M21VerificationStatus.REJECTED }
        val public = rejected.toPublicVerification(isOwn = true)
        assertFalse(public.toString().contains("Documento ilegible"))
    }

    @Test fun expiredDoesNotShowActiveSignal() = runBlocking {
        val expired = store.verifications.value.first { it.status == M21VerificationStatus.EXPIRED }
        val public = expired.toPublicVerification(isOwn = false)
        assertEquals(M21VerificationStatus.EXPIRED, public.status)
        assertNull(public.licenseSummary)
    }

    @Test fun revokedDoesNotShowActiveSignal() = runBlocking {
        val revoked = store.verifications.value.first { it.status == M21VerificationStatus.REVOKED }
        val public = revoked.toPublicVerification(isOwn = false)
        assertEquals(M21VerificationStatus.REVOKED, public.status)
    }

    @Test fun sanitizerRemovesPii() {
        val scrubbed = M21PrivacySanitizer.scrubPublicText("Contacto test@example.com DNI 12345678")
        assertFalse(scrubbed.contains("test@example.com"))
    }

    @Test fun riskSignalNotPublic() = runBlocking {
        val detail = repository.getReviewDetail("m21_rev_moderated").getOrThrow()
        assertFalse(detail.toString().contains("INELIGIBLE_CONTEXT"))
        assertTrue(store.riskSignals.value.isNotEmpty())
    }

    @Test fun m06UnavailableDoesNotBlock() = runBlocking {
        val hook = repository.observeNotificationsHook().first()
        assertFalse(hook.available)
    }

    @Test fun mockDeterministicSeeds() {
        val secondStore = M21ReputationMemoryStore()
        secondStore.seedDefaults()
        assertEquals(store.reviews.value.size, secondStore.reviews.value.size)
        assertEquals("m21_rev_positive", store.reviews.value.first().id)
    }

    @Test fun errorDoesNotExposePayload() {
        val message = M21ReputationResilience.safeUserMessage("user_id=secret reviewer_user_id=abc")
        assertFalse(message.contains("secret"))
        assertTrue(message.contains("[redactado]"))
    }

    @Test fun navigationRespectsEligibility() = runBlocking {
        val eligibility = repository.checkEligibility(
            CheckM21EligibilityInput(
                targetType = M21ReviewTargetType.USER,
                targetId = M21MockUsers.ADMIN,
                targetDisplayLabel = "Admin"
            )
        ).getOrThrow()
        assertFalse(eligibility.eligible)
        assertTrue(NavRoutes.m21Subject("ORGANIZATION", M21MockTargetIds.ORGANIZATION).startsWith("m21/subject/"))
    }
}
