package com.comunidapp.shared.adoption

import com.comunidapp.shared.auth.FakeAuthSessionGateway
import com.comunidapp.shared.auth.GatewayAuthRepository
import com.comunidapp.shared.remote.FakeAdoptionApplicationRemoteGateway
import com.comunidapp.shared.remote.RemoteAdoptionApplicationRow
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.session.SessionUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AdoptionShelterReviewVerticalTest {

    private fun auth() =
        GatewayAuthRepository(
            FakeAuthSessionGateway(
                SessionState.Authenticated(SessionUser("owner-1", "o@leover.test", "Owner"))
            )
        )

    private fun row(
        id: String = "app-1",
        status: String = "SUBMITTED",
        applicantName: String? = "Candidato",
        phone: String? = "+54911XXXX"
    ) = RemoteAdoptionApplicationRow(
        id = id,
        adoptionId = "adopt-1",
        applicantUserId = "secret-uid",
        applicantName = applicantName,
        message = "Quiero adoptar con cuidado",
        housingType = "casa",
        hasOtherPets = false,
        previousExperience = "Sí",
        contactPhone = phone,
        status = status,
        adoptionTitle = "Nube",
        petName = "Nube",
        submittedAt = "2026-08-13T12:00:00Z"
    )

    private fun remote(
        gw: FakeAdoptionApplicationRemoteGateway = FakeAdoptionApplicationRemoteGateway(
            received = listOf(row())
        ),
        authRepo: GatewayAuthRepository = auth()
    ) = RemoteAdoptionApplicationRepository(gateway = gw, sessionRepository = authRepo)

    @Test
    fun owner_list_success() = runTest {
        val list = remote().listReceived().getOrThrow()
        assertEquals(1, list.size)
        assertEquals("Candidato", list.first().applicantDisplayName)
        assertFalse(list.first().toString().contains("secret-uid"))
    }

    @Test
    fun unauthenticated_list_fails() = runTest {
        val authRepo = GatewayAuthRepository(FakeAuthSessionGateway(SessionState.Unauthenticated))
        assertTrue(remote(authRepo = authRepo).listReceived().isFailure)
    }

    @Test
    fun non_owner_forbidden() = runTest {
        val gw = FakeAdoptionApplicationRemoteGateway(
            receivedError = IllegalStateException("APPLICATION_FORBIDDEN")
        )
        assertTrue(remote(gw).listReceived().isFailure)
    }

    @Test
    fun detail_success_private_fields() = runTest {
        val detail = remote().getForReview(AdoptionApplicationId("app-1")).getOrThrow()
        assertEquals("Candidato", detail.applicantDisplayName)
        assertEquals("+54911XXXX", detail.contactPhone)
        assertEquals("casa", detail.housingType)
        assertFalse(detail.toString().contains("secret-uid"))
    }

    @Test
    fun mark_under_review_success() = runTest {
        val result = remote().markUnderReview(AdoptionApplicationId("app-1"))
        assertIs<AdoptionApplicationResult.Success>(result)
    }

    @Test
    fun accept_success() = runTest {
        val gw = FakeAdoptionApplicationRemoteGateway(
            received = listOf(row(status = "UNDER_REVIEW"))
        )
        assertIs<AdoptionApplicationResult.Success>(
            remote(gw).accept(AdoptionApplicationId("app-1"))
        )
    }

    @Test
    fun reject_success() = runTest {
        assertIs<AdoptionApplicationResult.Success>(
            remote().reject(AdoptionApplicationId("app-1"), "No hay espacio")
        )
    }

    @Test
    fun invalid_transition_sanitized() = runTest {
        val gw = FakeAdoptionApplicationRemoteGateway(
            reviewError = IllegalStateException("APPLICATION_INVALID_TRANSITION")
        )
        val result = remote(gw).accept(AdoptionApplicationId("app-1"))
        assertIs<AdoptionApplicationResult.ValidationError>(result)
        assertFalse(result.message.contains("APPLICATION_INVALID", ignoreCase = true))
    }

    @Test
    fun network_sanitized() = runTest {
        val gw = FakeAdoptionApplicationRemoteGateway(
            reviewError = IllegalStateException("network timeout")
        )
        val result = remote(gw).markUnderReview(AdoptionApplicationId("app-1"))
        assertIs<AdoptionApplicationResult.BackendError>(result)
        assertTrue(result.message.contains("conexión", ignoreCase = true))
    }

    @Test
    fun public_models_no_review_pii() {
        assertTrue(AdoptionSummary::class != AdoptionApplicationReviewDetail::class)
        val summary = AdoptionApplicationSummary(
            id = AdoptionApplicationId("a1"),
            adoptionId = AdoptionId("ad1"),
            status = AdoptionApplicationStatus.SUBMITTED,
            adoptionTitle = "T",
            petName = "P",
            submittedAtLabel = "—",
            messagePreview = "hola"
        )
        assertFalse(summary.toString().contains("phone", ignoreCase = true))
        assertFalse(summary.toString().contains("applicant", ignoreCase = true))
    }

    @Test
    fun transition_helpers() {
        assertTrue(AdoptionApplicationStatus.canMarkUnderReview(AdoptionApplicationStatus.SUBMITTED))
        assertFalse(AdoptionApplicationStatus.canMarkUnderReview(AdoptionApplicationStatus.ACCEPTED))
        assertTrue(AdoptionApplicationStatus.canAccept(AdoptionApplicationStatus.UNDER_REVIEW))
        assertFalse(AdoptionApplicationStatus.canReject(AdoptionApplicationStatus.WITHDRAWN))
    }

    @Test
    fun unconfigured_no_fake_success() = runTest {
        val result = UnconfiguredAdoptionApplicationRepository()
            .accept(AdoptionApplicationId("app-1"))
        assertIs<AdoptionApplicationResult.BackendError>(result)
    }
}
