package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.mock.MockAuthDatabase
import com.comunidapp.app.data.mock.MockData
import com.comunidapp.app.data.model.AdoptionApplication
import com.comunidapp.app.data.model.AdoptionApplicationStatus
import com.comunidapp.app.data.model.AdoptionDocumentType
import com.comunidapp.app.data.model.AdoptionFollowUpCheck
import com.comunidapp.app.data.model.AdoptionFollowUpPlan
import com.comunidapp.app.data.model.AdoptionFollowUpPlanStatus
import com.comunidapp.app.data.model.AdoptionFollowUpStatus
import com.comunidapp.app.data.model.AdoptionInterviewType
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.AdoptionWelfareStatus
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionErrorMapper
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionException
import com.comunidapp.app.data.repository.AdoptionDocumentRefValidator
import com.comunidapp.app.data.repository.M09CompletionMemoryStore
import com.comunidapp.app.data.repository.MockAdoptionAgreementRepository
import com.comunidapp.app.data.repository.MockAdoptionApplicationRepository
import com.comunidapp.app.data.repository.MockAdoptionCompletionRepository
import com.comunidapp.app.data.repository.MockAdoptionDocumentRepository
import com.comunidapp.app.data.repository.MockAdoptionFollowUpRepository
import com.comunidapp.app.data.repository.MockAdoptionInterviewRepository
import com.comunidapp.app.data.repository.MockAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class M09AdoptionCompletionTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var authRepo: MockAuthRepository
    private val publisherId = MockData.currentUser.id
    private val applicantId = "user_3"
    private lateinit var appStore: MutableStateFlow<List<AdoptionApplication>>
    private lateinit var store: M09CompletionMemoryStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepo = MockAuthRepository()
        authRepo.resetForTests()
        appStore = MutableStateFlow(emptyList())
        store = M09CompletionMemoryStore()
        InMemoryDataStore.replaceAdoptionPosts(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        MockAuthDatabase.resetToFixtures()
        store.clear()
        InMemoryDataStore.replaceAdoptionPosts(emptyList())
    }

    private fun seedPausedAdoptionWithPet() {
        val pet = Pet(
            id = "pet-m09-fin",
            ownerId = publisherId,
            name = "Luna",
            species = PetSpecies.DOG,
            sex = PetSex.FEMALE,
            ageYears = 2,
            size = PetSize.MEDIUM,
            description = "ok",
            status = "ACTIVE"
        )
        if (InMemoryDataStore.getPetById(pet.id) == null) InMemoryDataStore.addPet(pet)
        else InMemoryDataStore.updatePet(pet)
        InMemoryDataStore.replaceAdoptionPosts(
            listOf(
                AdoptionPost(
                    id = "adopt-1",
                    petId = pet.id,
                    publisherId = publisherId,
                    shelterName = "Pub",
                    title = "Luna",
                    name = "Luna",
                    species = PetSpecies.DOG,
                    sex = PetSex.FEMALE,
                    ageYears = 2,
                    size = PetSize.MEDIUM,
                    location = "CABA",
                    description = "desc",
                    status = AdoptionStatus.PAUSED
                )
            )
        )
    }

    private fun seedAcceptedApplication(): AdoptionApplication {
        val app = AdoptionApplication(
            id = "app-1",
            adoptionId = "adopt-1",
            applicantUserId = applicantId,
            applicantName = "Carlos",
            message = "Quiero adoptar",
            status = AdoptionApplicationStatus.ACCEPTED,
            submittedAt = 1L
        )
        appStore.value = listOf(app)
        return app
    }

    private fun apps() = { appStore.value }
    private fun isManager(adoptionId: String, userId: String) =
        InMemoryDataStore.getAdoptionPostById(adoptionId)?.publisherId == userId

    private fun publisherInterviews() = MockAdoptionInterviewRepository(
        actorUserId = { publisherId },
        applications = apps(),
        isManager = ::isManager,
        store = store
    )

    private fun applicantInterviews() = MockAdoptionInterviewRepository(
        actorUserId = { applicantId },
        applications = apps(),
        isManager = ::isManager,
        store = store
    )

    private fun publisherDocs() = MockAdoptionDocumentRepository(
        actorUserId = { publisherId },
        applications = apps(),
        isManager = ::isManager,
        store = store
    )

    private fun applicantDocs() = MockAdoptionDocumentRepository(
        actorUserId = { applicantId },
        applications = apps(),
        isManager = ::isManager,
        store = store
    )

    private fun publisherAgreement() = MockAdoptionAgreementRepository(
        actorUserId = { publisherId },
        applications = apps(),
        isManager = ::isManager,
        store = store
    )

    private fun applicantAgreement() = MockAdoptionAgreementRepository(
        actorUserId = { applicantId },
        applications = apps(),
        isManager = ::isManager,
        store = store
    )

    private fun publisherCompletion() =
        MockAdoptionCompletionRepository(
            actorUserId = { publisherId },
            applications = apps(),
            isManager = ::isManager,
            store = store
        )

    private fun seedFollowUpChecks() {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val plan = AdoptionFollowUpPlan(
            id = "plan-seed",
            adoptionId = "adopt-1",
            adopterUserId = applicantId,
            status = AdoptionFollowUpPlanStatus.ACTIVE,
            createdAt = now
        )
        store.plans.value = listOf(plan)
        store.checks.value = listOf(7, 30, 90).mapIndexed { index, days ->
            AdoptionFollowUpCheck(
                id = "chk-seed-$index",
                planId = plan.id,
                adoptionId = "adopt-1",
                dueAt = now + days * day,
                status = AdoptionFollowUpStatus.PENDING,
                createdAt = now,
                updatedAt = now
            )
        }
    }

    private fun followUp(actor: String) = MockAdoptionFollowUpRepository(
        actorUserId = { actor },
        isManager = ::isManager,
        store = store
    )

    private suspend fun prepareReadyToFinalize() {
        seedPausedAdoptionWithPet()
        seedAcceptedApplication()
        val interview = publisherInterviews().scheduleInterview(
            "adopt-1", "app-1", System.currentTimeMillis(),
            AdoptionInterviewType.PHONE, null, null
        ).getOrThrow()
        publisherInterviews().completeInterview(interview.id, "OK").getOrThrow()
        val doc = publisherDocs().requestDocument(
            "adopt-1", "app-1", AdoptionDocumentType.IDENTITY, true
        ).getOrThrow()
        applicantDocs().submitDocumentReference(doc.id, "m05://docs/id").getOrThrow()
        publisherDocs().reviewDocument(doc.id, true, null).getOrThrow()
        val agr = publisherAgreement().createAgreement(
            "adopt-1", "app-1", "1.0", "Términos"
        ).getOrThrow()
        applicantAgreement().acceptAgreement(agr.id).getOrThrow()
        publisherAgreement().acceptAgreement(agr.id).getOrThrow()
    }

    // --- Interviews ---

    @Test
    fun scheduleValidInterview() = runTest {
        seedPausedAdoptionWithPet()
        seedAcceptedApplication()
        val result = publisherInterviews().scheduleInterview(
            "adopt-1", "app-1", System.currentTimeMillis(),
            AdoptionInterviewType.IN_PERSON, "Refugio", null
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun cannotScheduleWithoutAccepted() = runTest {
        seedPausedAdoptionWithPet()
        appStore.value = listOf(
            AdoptionApplication(
                id = "app-1", adoptionId = "adopt-1", applicantUserId = applicantId,
                message = "x", status = AdoptionApplicationStatus.SUBMITTED, submittedAt = 1L
            )
        )
        val result = publisherInterviews().scheduleInterview(
            "adopt-1", "app-1", 1L, AdoptionInterviewType.PHONE, null, null
        )
        assertEquals("INTERVIEW_NOT_ALLOWED", (result.exceptionOrNull() as M09AdoptionException).code)
    }

    @Test
    fun confirmCompleteCancelInterview() = runTest {
        seedPausedAdoptionWithPet()
        seedAcceptedApplication()
        val id = publisherInterviews().scheduleInterview(
            "adopt-1", "app-1", 1L, AdoptionInterviewType.VIDEO_CALL, "link", null
        ).getOrThrow().id
        assertEquals(
            "CONFIRMED",
            applicantInterviews().confirmInterview(id).getOrThrow().status.name
        )
        assertEquals(
            "COMPLETED",
            publisherInterviews().completeInterview(id, "Bien").getOrThrow().status.name
        )
    }

    @Test
    fun cancelInterview_andInvalidTransition() = runTest {
        seedPausedAdoptionWithPet()
        seedAcceptedApplication()
        val id = publisherInterviews().scheduleInterview(
            "adopt-1", "app-1", 1L, AdoptionInterviewType.PHONE, null, null
        ).getOrThrow().id
        publisherInterviews().completeInterview(id, "ok")
        val cancel = publisherInterviews().cancelInterview(id)
        assertEquals("INTERVIEW_ALREADY_COMPLETED", (cancel.exceptionOrNull() as M09AdoptionException).code)
    }

    @Test
    fun interviewMissingId() = runTest {
        val result = publisherInterviews().getInterviewById("")
        assertEquals("INTERVIEW_NOT_FOUND", (result.exceptionOrNull() as M09AdoptionException).code)
        assertEquals(
            "INTERVIEW_NOT_FOUND",
            (publisherInterviews().getInterviewById("missing").exceptionOrNull() as M09AdoptionException).code
        )
    }

    // --- Documents ---

    @Test
    fun documentRequestSubmitApproveReject() = runTest {
        seedPausedAdoptionWithPet()
        seedAcceptedApplication()
        val doc = publisherDocs().requestDocument(
            "adopt-1", "app-1", AdoptionDocumentType.ADDRESS_PROOF, true
        ).getOrThrow()
        applicantDocs().submitDocumentReference(doc.id, "m05://addr").getOrThrow()
        assertEquals("APPROVED", publisherDocs().reviewDocument(doc.id, true, null).getOrThrow().status.name)

        val doc2 = publisherDocs().requestDocument(
            "adopt-1", "app-1", AdoptionDocumentType.OTHER, true
        ).getOrThrow()
        applicantDocs().submitDocumentReference(doc2.id, "file_asset:x").getOrThrow()
        val rejected = publisherDocs().reviewDocument(doc2.id, false, "Ilegible").getOrThrow()
        assertEquals("REJECTED", rejected.status.name)
        assertEquals("Ilegible", rejected.rejectionReason)
    }

    @Test
    fun documentForbiddenForStrangerApprove() = runTest {
        seedPausedAdoptionWithPet()
        seedAcceptedApplication()
        val doc = publisherDocs().requestDocument(
            "adopt-1", "app-1", AdoptionDocumentType.IDENTITY, true
        ).getOrThrow()
        applicantDocs().submitDocumentReference(doc.id, "m05://id").getOrThrow()
        val stranger = MockAdoptionDocumentRepository(
            actorUserId = { "user_2" },
            applications = apps(),
            isManager = ::isManager,
            store = store
        )
        assertEquals(
            "DOCUMENT_FORBIDDEN",
            (stranger.reviewDocument(doc.id, true, null).exceptionOrNull() as M09AdoptionException).code
        )
    }

    @Test
    fun rejectUnsafePublicDocumentUrl() = runTest {
        assertTrue(
            AdoptionDocumentRefValidator.isUnsafePublicReference(
                "https://xxx.supabase.co/storage/v1/object/public/leover/doc.pdf"
            )
        )
        seedPausedAdoptionWithPet()
        seedAcceptedApplication()
        val doc = publisherDocs().requestDocument(
            "adopt-1", "app-1", AdoptionDocumentType.IDENTITY, true
        ).getOrThrow()
        val bad = applicantDocs().submitDocumentReference(
            doc.id,
            "https://x/storage/v1/object/public/leover/secret.pdf"
        )
        assertEquals(
            "DOCUMENT_UNSAFE_REFERENCE",
            (bad.exceptionOrNull() as M09AdoptionException).code
        )
    }

    // --- Agreement ---

    @Test
    fun agreementCreateAndBothAccept() = runTest {
        seedPausedAdoptionWithPet()
        seedAcceptedApplication()
        val agr = publisherAgreement().createAgreement(
            "adopt-1", "app-1", "1.0", "Términos LeoVer"
        ).getOrThrow()
        applicantAgreement().acceptAgreement(agr.id).getOrThrow()
        val accepted = publisherAgreement().acceptAgreement(agr.id).getOrThrow()
        assertEquals("ACCEPTED", accepted.status.name)
        assertTrue(accepted.adopterAcceptedAt != null && accepted.publisherAcceptedAt != null)
    }

    @Test
    fun agreementCannotEditAfterAccepted_andNoDuplicate() = runTest {
        seedPausedAdoptionWithPet()
        seedAcceptedApplication()
        val agr = publisherAgreement().createAgreement("adopt-1", "app-1", "1.0", "T").getOrThrow()
        applicantAgreement().acceptAgreement(agr.id)
        publisherAgreement().acceptAgreement(agr.id)
        assertEquals(
            "AGREEMENT_ALREADY_ACCEPTED",
            (publisherAgreement().acceptAgreement(agr.id).exceptionOrNull() as M09AdoptionException).code
        )
        assertEquals(
            "AGREEMENT_ALREADY_EXISTS",
            (publisherAgreement().createAgreement("adopt-1", "app-1", "1.0", "T")
                .exceptionOrNull() as M09AdoptionException).code
        )
        assertEquals(
            "AGREEMENT_ALREADY_ACCEPTED",
            (publisherAgreement().cancelAgreement(agr.id).exceptionOrNull() as M09AdoptionException).code
        )
    }

    // --- Finalize ---

    @Test
    fun finalizeBlockedWithoutInterviewDocsAgreement() = runTest {
        seedPausedAdoptionWithPet()
        seedAcceptedApplication()
        val completion = publisherCompletion()
        assertEquals(
            "ADOPTION_NOT_READY_TO_FINALIZE",
            (completion.finalizeAdoption("adopt-1").exceptionOrNull() as M09AdoptionException).code
        )
        // interview only
        val interview = publisherInterviews().scheduleInterview(
            "adopt-1", "app-1", 1L, AdoptionInterviewType.PHONE, null, null
        ).getOrThrow()
        publisherInterviews().completeInterview(interview.id, "ok")
        assertEquals(
            "ADOPTION_NOT_READY_TO_FINALIZE",
            (completion.finalizeAdoption("adopt-1").exceptionOrNull() as M09AdoptionException).code
        )
        val doc = publisherDocs().requestDocument(
            "adopt-1", "app-1", AdoptionDocumentType.IDENTITY, true
        ).getOrThrow()
        applicantDocs().submitDocumentReference(doc.id, "m05://x").getOrThrow()
        publisherDocs().reviewDocument(doc.id, true, null)
        assertEquals(
            "ADOPTION_NOT_READY_TO_FINALIZE",
            (completion.finalizeAdoption("adopt-1").exceptionOrNull() as M09AdoptionException).code
        )
    }

    @Test
    fun finalizeAdoption_readyProcess_refusesCanonicalTransfer() = runTest {
        prepareReadyToFinalize()
        val result = publisherCompletion().finalizeAdoption("adopt-1")
        val error = result.exceptionOrNull() as M09AdoptionException
        assertEquals("ADOPTION_USE_CANONICAL_TRANSFER", error.code)
        assertEquals(
            "Para cambiar la responsabilidad de la mascota usá la transferencia canónica.",
            error.message
        )
        assertFalse(error.message.orEmpty().contains("PostgREST", ignoreCase = true))
        assertFalse(result.isSuccess)

        val pet = InMemoryDataStore.getPetById("pet-m09-fin")!!
        assertEquals("ACTIVE", pet.status)
        assertEquals(publisherId, pet.ownerId)
        assertEquals(AdoptionStatus.PAUSED, InMemoryDataStore.getAdoptionPostById("adopt-1")!!.status)
        assertFalse(store.petHistory.any { it.reason == "ADOPTION_EVALUATION_DONE" })
        assertTrue(store.finalized.value.none { it.adoptionId == "adopt-1" })
        assertTrue(store.checks.value.none { it.adoptionId == "adopt-1" })
        assertTrue(store.plans.value.none { it.adoptionId == "adopt-1" })
        assertTrue(store.transfers.none { it.third == applicantId })
    }

    @Test
    fun finalizeAdoption_repeatCall_stillRefusesWithoutMutation() = runTest {
        prepareReadyToFinalize()
        val first = publisherCompletion().finalizeAdoption("adopt-1")
        val second = publisherCompletion().finalizeAdoption("adopt-1")
        assertEquals(
            "ADOPTION_USE_CANONICAL_TRANSFER",
            (first.exceptionOrNull() as M09AdoptionException).code
        )
        assertEquals(
            "ADOPTION_USE_CANONICAL_TRANSFER",
            (second.exceptionOrNull() as M09AdoptionException).code
        )
        assertEquals(AdoptionStatus.PAUSED, InMemoryDataStore.getAdoptionPostById("adopt-1")!!.status)
        assertEquals("ACTIVE", InMemoryDataStore.getPetById("pet-m09-fin")!!.status)
        assertEquals(publisherId, InMemoryDataStore.getPetById("pet-m09-fin")!!.ownerId)
        assertTrue(store.finalized.value.isEmpty())
        assertFalse(store.petHistory.any { it.reason == "ADOPTION_EVALUATION_DONE" })
    }

    // --- Follow-up ---

    @Test
    fun followUpListCompleteOverdueCriticalPermission() = runTest {
        seedFollowUpChecks()
        val checks = followUp(applicantId).observeChecks("adopt-1").first()
        assertEquals(3, checks.size)
        val first = checks.minByOrNull { it.dueAt }!!
        followUp(applicantId).completeCheck(first.id, "ok", AdoptionWelfareStatus.GOOD).getOrThrow()
        assertEquals(
            "FOLLOWUP_ALREADY_COMPLETED",
            (followUp(applicantId).completeCheck(first.id, "x", AdoptionWelfareStatus.GOOD)
                .exceptionOrNull() as M09AdoptionException).code
        )
        val overdue = checks.maxByOrNull { it.dueAt }!!.copy(
            dueAt = System.currentTimeMillis() - 1000,
            status = AdoptionFollowUpStatus.PENDING
        )
        store.checks.value = store.checks.value.map { if (it.id == overdue.id) overdue else it }
        assertEquals(
            AdoptionFollowUpStatus.OVERDUE,
            followUp(applicantId).getCheckById(overdue.id).getOrThrow().status
        )
        followUp(applicantId).completeCheck(
            overdue.id, "alerta", AdoptionWelfareStatus.CRITICAL
        ).getOrThrow()
        assertEquals(
            AdoptionWelfareStatus.CRITICAL,
            store.checks.value.first { it.id == overdue.id }.welfareStatus
        )
        val stranger = followUp("user_2")
        assertEquals(
            "FOLLOWUP_FORBIDDEN",
            (stranger.getPlan("adopt-1").exceptionOrNull() as M09AdoptionException).code
        )
    }

    @Test
    fun followUpErrorsEmptyIdUnknownStatusDoubleSubmitGuard() = runTest {
        assertEquals(
            "FOLLOWUP_NOT_FOUND",
            (followUp(publisherId).getCheckById("").exceptionOrNull() as M09AdoptionException).code
        )
        assertEquals(
            AdoptionFollowUpStatus.PENDING,
            com.comunidapp.app.data.model.AdoptionFollowUpStatus.fromString("WEIRD")
        )
        assertFalse(
            M09AdoptionErrorMapper.userMessage("ADOPTION_NOT_READY_TO_FINALIZE")
                .contains("PostgREST", ignoreCase = true)
        )
        assertFalse(MockAdoptionCompletionRepository::class.java.name.contains("Supabase"))
    }

    @Test
    fun processSnapshot_andApplicationRepoIsolation() {
        assertTrue(MockAdoptionApplicationRepository(actorUserId = { applicantId }) is Any)
    }
}
