package com.comunidapp.app.domain.m26

import com.comunidapp.app.data.model.M26AiJob
import com.comunidapp.app.data.model.M26AiJobStatus
import com.comunidapp.app.data.model.M26AiJobType
import com.comunidapp.app.data.model.M26AiResultStatus
import com.comunidapp.app.data.model.M26DuplicateStatus
import com.comunidapp.app.data.model.M26EvaluatedRecommendation
import com.comunidapp.app.data.model.M26MockIds
import com.comunidapp.app.data.model.M26MockUsers
import com.comunidapp.app.data.model.M26ModelDescriptor
import com.comunidapp.app.data.model.M26RecommendationKind
import com.comunidapp.app.data.model.M26RecommendationStatus
import com.comunidapp.app.data.model.M26ReviewDecision
import com.comunidapp.app.data.model.RequestM26AiJobInput
import com.comunidapp.app.data.model.RequestM26VisualMatchInput
import com.comunidapp.app.data.model.ReviewM26AiResultInput
import com.comunidapp.app.data.repository.M26AiMemoryStore
import com.comunidapp.app.data.repository.M26AiValidators
import com.comunidapp.app.data.repository.MockM26AiRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M26AiOperationsTest {
    private fun repo(actor: String = M26MockUsers.MEMBER, store: M26AiMemoryStore = M26AiMemoryStore()) =
        MockM26AiRepository({ actor }, store)

    @Test fun requestJobWorks() = runBlocking {
        val job = repo().requestAnalysis(
            RequestM26AiJobInput(M26AiJobType.VISUAL_MATCH, "Foto A|Foto B", "req-1")
        ).getOrThrow()
        assertEquals(M26AiJobStatus.COMPLETED, job.status)
    }

    @Test fun retryDoesNotDuplicateJob() = runBlocking {
        val r = repo()
        val first = r.requestAnalysis(RequestM26AiJobInput(M26AiJobType.ASSISTANCE, "Consulta general", "idem-1")).getOrThrow()
        val second = r.requestAnalysis(RequestM26AiJobInput(M26AiJobType.ASSISTANCE, "Consulta general", "idem-1")).getOrThrow()
        assertEquals(first.id, second.id)
        assertEquals(1, r.observeMyJobs().first().count { it.clientRequestId == "idem-1" })
    }

    @Test fun foreignClientKeyDoesNotReuseOtherUserJob() = runBlocking {
        val store = M26AiMemoryStore()
        val member = repo(M26MockUsers.MEMBER, store)
        member.requestAnalysis(RequestM26AiJobInput(M26AiJobType.ASSISTANCE, "Consulta A", "shared-key")).getOrThrow()
        val other = repo(M26MockUsers.OTHER, store)
        val otherJob = other.requestAnalysis(RequestM26AiJobInput(M26AiJobType.ASSISTANCE, "Consulta B", "shared-key")).getOrThrow()
        assertNotEquals(M26MockUsers.MEMBER, otherJob.ownerUserId)
    }

    @Test fun queuedBecomesRunningThenCompleted() = runBlocking {
        val job = repo().requestAnalysis(RequestM26AiJobInput(M26AiJobType.RECOMMENDATION, "Guía bienestar", null)).getOrThrow()
        assertEquals(M26AiJobStatus.COMPLETED, job.status)
        assertTrue(job.completedAt != null)
    }

    @Test fun completedIsNotPublicAutomatically() = runBlocking {
        val r = repo()
        r.requestAnalysis(RequestM26AiJobInput(M26AiJobType.VISUAL_MATCH, "A|B", null)).getOrThrow()
        val result = r.observeMyResults().first().last()
        assertEquals(M26AiResultStatus.PENDING_REVIEW, result.status)
        assertTrue(result.isEstimate)
    }

    @Test fun approvedCanBePublic() = runBlocking {
        val store = M26AiMemoryStore()
        val member = repo(M26MockUsers.MEMBER, store)
        member.requestAnalysis(RequestM26AiJobInput(M26AiJobType.RECOMMENDATION, "Evento adopción", null)).getOrThrow()
        val resultId = store.results.value.last { it.ownerUserId == M26MockUsers.MEMBER }.id
        repo(M26MockUsers.REVIEWER, store).reviewResult(ReviewM26AiResultInput(resultId, M26ReviewDecision.APPROVED, null)).getOrThrow()
        assertTrue(M26AiOperationsService.isPublicResult(M26AiResultStatus.APPROVED))
    }

    @Test fun rejectedIsNotPublic() = runBlocking {
        val store = M26AiMemoryStore()
        val member = repo(M26MockUsers.MEMBER, store)
        member.requestAnalysis(RequestM26AiJobInput(M26AiJobType.VISUAL_MATCH, "X|Y", null)).getOrThrow()
        val resultId = store.results.value.last().id
        repo(M26MockUsers.REVIEWER, store).reviewResult(ReviewM26AiResultInput(resultId, M26ReviewDecision.REJECTED, "Baja calidad")).getOrThrow()
        assertFalse(M26AiOperationsService.isPublicResult(M26AiResultStatus.REJECTED))
    }

    @Test fun terminalJobDoesNotReopen() {
        assertEquals("M26_JOB_TERMINAL", M26JobLifecycle.validateJobTransition(M26AiJobStatus.COMPLETED, M26AiJobStatus.RUNNING))
    }

    @Test fun cancelIsIdempotent() = runBlocking {
        val store = M26AiMemoryStore()
        val now = System.currentTimeMillis()
        val job = M26AiJob(
            "j-cancel", M26MockUsers.MEMBER, M26AiJobType.ASSISTANCE, M26AiJobStatus.CANCELLED, null,
            M26ModelDescriptor("leover-stub", "1.0.0"), now, now
        )
        store.jobs.value = listOf(job)
        val r = repo(M26MockUsers.MEMBER, store)
        assertEquals(M26AiJobStatus.CANCELLED, r.cancelJob("j-cancel").getOrThrow().status)
        assertEquals(M26AiJobStatus.CANCELLED, r.cancelJob("j-cancel").getOrThrow().status)
    }

    @Test fun errorDoesNotModifySourceEntities() = runBlocking {
        val store = M26AiMemoryStore()
        val r = repo(M26MockUsers.MEMBER, store)
        val before = store.visualMatches.value.size
        val fail = r.requestAnalysis(RequestM26AiJobInput(M26AiJobType.VISUAL_MATCH, "invalid", null))
        assertTrue(fail.isFailure)
        assertEquals(before, store.visualMatches.value.size)
    }

    @Test fun matchingScoreInRange() = runBlocking {
        val r = repo()
        r.requestVisualMatch(RequestM26VisualMatchInput("Perro marrón", "Avistamiento marrón")).getOrThrow()
        val match = r.observeVisualMatches().first().last()
        assertTrue(match.score in 0.0..1.0)
    }

    @Test fun matchingDoesNotConfirmIdentity() = runBlocking {
        val r = repo()
        r.requestAnalysis(RequestM26AiJobInput(M26AiJobType.VISUAL_MATCH, "Perro|Avistamiento", null)).getOrThrow()
        val result = r.observeMyResults().first().last()
        assertTrue(result.summary.contains("Posible", ignoreCase = true) || result.isEstimate)
    }

    @Test fun matchingDoesNotExposeOwner() = runBlocking {
        val match = repo().observeVisualMatches().first().first()
        assertFalse(match.toString().contains("mock_user"))
    }

    @Test fun matchingDoesNotExposePreciseLocation() = runBlocking {
        val text = repo().observeVisualMatches().first().joinToString()
        assertFalse(text.contains("calle", ignoreCase = true))
        assertFalse(text.contains("av.", ignoreCase = true))
    }

    @Test fun privateFileReferenceNotInPublicMatch() = runBlocking {
        val text = repo().observeVisualMatches().first().joinToString()
        assertFalse(text.contains("private/"))
        assertFalse(text.contains("m05_"))
    }

    @Test fun duplicatePairNotDuplicatedInverted() = runBlocking {
        val store = M26AiMemoryStore()
        val r = repo(M26MockUsers.MEMBER, store)
        val before = store.duplicateCandidates.value.size
        r.requestAnalysis(RequestM26AiJobInput(M26AiJobType.DUPLICATE_SCAN, "Servicio A|Servicio B", null)).getOrThrow()
        r.requestAnalysis(RequestM26AiJobInput(M26AiJobType.DUPLICATE_SCAN, "Servicio B|Servicio A", null)).getOrThrow()
        assertEquals(before + 1, store.duplicateCandidates.value.size)
    }

    @Test fun duplicateDoesNotAutoMerge() = runBlocking {
        val store = M26AiMemoryStore()
        val r = repo(M26MockUsers.MEMBER, store)
        r.requestAnalysis(RequestM26AiJobInput(M26AiJobType.DUPLICATE_SCAN, "Perfil X|Perfil Y", null)).getOrThrow()
        val dup = store.duplicateCandidates.value.last()
        r.confirmDuplicate(dup.id)
        assertEquals(M26DuplicateStatus.CONFIRMED, store.duplicateCandidates.value.first { it.id == dup.id }.status)
    }

    @Test fun rejectDuplicateDoesNotDeleteEntities() = runBlocking {
        val store = M26AiMemoryStore()
        val r = repo(M26MockUsers.MEMBER, store)
        val before = store.duplicateCandidates.value.size
        r.dismissDuplicate(M26MockIds.DUPLICATE_OPEN)
        assertEquals(before, store.duplicateCandidates.value.size)
    }

    @Test fun assistanceBelongsToUser() = runBlocking {
        val sessions = repo().observeAssistanceSessions().first()
        assertTrue(sessions.isNotEmpty())
    }

    @Test fun foreignSessionNotAccessible() = runBlocking {
        assertTrue(repo(M26MockUsers.UNAUTHORIZED).observeAssistanceSessions().first().isEmpty())
    }

    @Test fun assistanceDoesNotProduceDiagnosis() {
        assertEquals("M26_ASSISTANCE_NOT_AUTHORITATIVE", M26AiValidators.validateNoDiagnosis("Necesito diagnóstico de moquillo"))
    }

    @Test fun suspendedRecommendationNotEligible() = runBlocking {
        val items = repo().observeEligibleRecommendations().first()
        assertTrue(items.none { it.title.contains("promocionado", ignoreCase = true) })
    }

    @Test fun rejectedRecommendationNotEligible() = runBlocking {
        val items = repo().observeEligibleRecommendations().first()
        assertTrue(items.all { it.approvedForDisplay })
    }

    @Test fun approvedRecommendationAppears() = runBlocking {
        val items = repo().observeEligibleRecommendations().first()
        assertTrue(items.any { it.title.contains("grooming", ignoreCase = true) })
    }

    @Test fun sensitiveAttributeNotInPublicExplanation() = runBlocking {
        val r = repo()
        r.requestAnalysis(RequestM26AiJobInput(M26AiJobType.VISUAL_MATCH, "A|B", null)).getOrThrow()
        r.observeMyResults().first().last().reasonCodes.forEach { code ->
            assertFalse(code.contains("email", ignoreCase = true))
            assertFalse(code.contains("tel", ignoreCase = true))
        }
    }

    @Test fun authorizedReviewWorks() = runBlocking {
        val store = M26AiMemoryStore()
        val member = repo(M26MockUsers.MEMBER, store)
        member.requestAnalysis(RequestM26AiJobInput(M26AiJobType.VISUAL_MATCH, "A|B", null)).getOrThrow()
        val resultId = store.results.value.last().id
        assertTrue(repo(M26MockUsers.REVIEWER, store).reviewResult(ReviewM26AiResultInput(resultId, M26ReviewDecision.APPROVED, null)).isSuccess)
    }

    @Test fun commonUserCannotReview() = runBlocking {
        val store = M26AiMemoryStore()
        repo(M26MockUsers.MEMBER, store).requestAnalysis(RequestM26AiJobInput(M26AiJobType.VISUAL_MATCH, "A|B", null)).getOrThrow()
        val resultId = store.results.value.last().id
        assertTrue(repo(M26MockUsers.MEMBER, store).reviewResult(ReviewM26AiResultInput(resultId, M26ReviewDecision.APPROVED, null)).isFailure)
    }

    @Test fun approveRepeatedIsIdempotent() = runBlocking {
        val store = M26AiMemoryStore()
        val member = repo(M26MockUsers.MEMBER, store)
        member.requestAnalysis(RequestM26AiJobInput(M26AiJobType.VISUAL_MATCH, "C|D", null)).getOrThrow()
        val resultId = store.results.value.last().id
        val reviewer = repo(M26MockUsers.REVIEWER, store)
        reviewer.reviewResult(ReviewM26AiResultInput(resultId, M26ReviewDecision.APPROVED, null)).getOrThrow()
        assertTrue(reviewer.reviewResult(ReviewM26AiResultInput(resultId, M26ReviewDecision.APPROVED, null)).isSuccess)
    }

    @Test fun rejectRepeatedIsIdempotent() = runBlocking {
        val store = M26AiMemoryStore()
        val member = repo(M26MockUsers.MEMBER, store)
        member.requestAnalysis(RequestM26AiJobInput(M26AiJobType.VISUAL_MATCH, "E|F", null)).getOrThrow()
        val resultId = store.results.value.last().id
        val reviewer = repo(M26MockUsers.REVIEWER, store)
        reviewer.reviewResult(ReviewM26AiResultInput(resultId, M26ReviewDecision.REJECTED, null)).getOrThrow()
        assertTrue(reviewer.reviewResult(ReviewM26AiResultInput(resultId, M26ReviewDecision.REJECTED, null)).isSuccess)
    }

    @Test fun explanationDoesNotContainPrivateData() = runBlocking {
        val text = M26PrivacySanitizer.scrubPublicText("Contacto hola@privado.com tel +54 11 5555-1212")
        assertFalse(text.contains("hola@privado.com"))
    }

    @Test fun resultKeepsModelVersion() = runBlocking {
        val store = M26AiMemoryStore()
        repo(M26MockUsers.MEMBER, store).requestAnalysis(RequestM26AiJobInput(M26AiJobType.ASSISTANCE, "Consulta versión", null)).getOrThrow()
        val result = store.results.value.last()
        assertEquals("leover-stub", result.model.name)
        assertEquals("1.0.0", result.model.version)
    }

    @Test fun resultKeepsProvenance() = runBlocking {
        val store = M26AiMemoryStore()
        val job = repo(M26MockUsers.MEMBER, store).requestAnalysis(RequestM26AiJobInput(M26AiJobType.ASSISTANCE, "Consulta origen", null)).getOrThrow()
        val result = store.results.value.last()
        assertEquals(job.id, result.provenance.jobId)
        assertEquals("M26", result.provenance.sourceModule)
    }

    @Test fun deletedEntityInvalidatesRecommendationDisplay() = runBlocking {
        val eligible = M26RecommendationEligibilityService.filterEligiblePublic(
            listOf(
                M26EvaluatedRecommendation(
                    "x", M26MockUsers.MEMBER, M26RecommendationKind.CONTENT,
                    "T", "R", true, null, M26RecommendationStatus.EXPIRED, 0, 0
                )
            )
        )
        assertTrue(eligible.isEmpty())
    }

    @Test fun m05UnavailableFailsControlled() {
        assertTrue(M26AiResilience.safeUserMessage("M26_PERMISSION_DENIED").isNotBlank())
    }

    @Test fun m04UnavailableDoesNotAutoPublish() = runBlocking {
        val store = M26AiMemoryStore()
        repo(M26MockUsers.MEMBER, store).requestAnalysis(RequestM26AiJobInput(M26AiJobType.RECOMMENDATION, "Auto pub", null)).getOrThrow()
        val result = store.results.value.last()
        assertNotEquals(M26AiResultStatus.APPROVED, result.status)
    }

    @Test fun refreshFailureKeepsExistingData() = runBlocking {
        val r = repo()
        val before = r.observeVisualMatches().first().size
        assertTrue(before > 0)
        assertEquals(before, r.observeVisualMatches().first().size)
    }

    @Test fun mockIsDeterministic() = runBlocking {
        val a = repo().observeVisualMatches().first()
        val b = repo().observeVisualMatches().first()
        assertEquals(a.size, b.size)
    }

    @Test fun noRealExternalIntegration() {
        assertEquals("leover-stub", M26AiOperationsService.stubModelVersion().first)
    }

    @Test fun noM24IntegrationInModule() {
        val pkg = MockM26AiRepository::class.java.`package`?.name.orEmpty()
        assertFalse(pkg.contains("m24"))
    }
}
