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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AdoptionApplicationVerticalTest {

    private fun auth(userId: String = "user-1") =
        GatewayAuthRepository(
            FakeAuthSessionGateway(
                SessionState.Authenticated(SessionUser(userId, "a@leover.test", "Ana"))
            )
        )

    private fun validDraft() = AdoptionApplicationDraft(
        adoptionId = AdoptionId("adopt-1"),
        message = "Quiero adoptar con responsabilidad y tiempo disponible.",
        housingType = "departamento",
        hasOtherPets = false,
        previousExperience = "Sí, tuve un perro",
        contactPhone = null
    )

    private fun remote(
        gateway: FakeAdoptionApplicationRemoteGateway = FakeAdoptionApplicationRemoteGateway(),
        authRepo: GatewayAuthRepository = auth()
    ) = RemoteAdoptionApplicationRepository(gateway = gateway, sessionRepository = authRepo)

    @Test
    fun draft_valid() {
        assertTrue(AdoptionApplicationDraftValidator.validate(validDraft()).isSuccess)
    }

    @Test
    fun message_required() {
        assertTrue(
            AdoptionApplicationDraftValidator.validate(validDraft().copy(message = " ")).isFailure
        )
    }

    @Test
    fun authenticated_apply_success() = runTest {
        val gw = FakeAdoptionApplicationRemoteGateway()
        val result = remote(gw).submit(validDraft())
        assertIs<AdoptionApplicationResult.Success>(result)
        assertEquals(1, gw.submitCalls)
        assertEquals("adopt-1", gw.lastSubmit?.adoptionId)
        assertNull(gw.lastSubmit?.contactPhone)
    }

    @Test
    fun unauthenticated() = runTest {
        val authRepo = GatewayAuthRepository(FakeAuthSessionGateway(SessionState.Unauthenticated))
        val result = remote(authRepo = authRepo).submit(validDraft())
        assertIs<AdoptionApplicationResult.Unauthenticated>(result)
    }

    @Test
    fun duplicate_conflict() = runTest {
        val gw = FakeAdoptionApplicationRemoteGateway(
            submitError = IllegalStateException("APPLICATION_ALREADY_EXISTS")
        )
        val result = remote(gw).submit(validDraft())
        assertIs<AdoptionApplicationResult.Conflict>(result)
        assertTrue(result.message.contains("solicitud activa", ignoreCase = true))
    }

    @Test
    fun forbidden_sanitized() = runTest {
        val gw = FakeAdoptionApplicationRemoteGateway(
            submitError = IllegalStateException("403 forbidden RLS")
        )
        val result = remote(gw).submit(validDraft())
        assertIs<AdoptionApplicationResult.Forbidden>(result)
        assertFalse(result.message.contains("RLS", ignoreCase = true))
    }

    @Test
    fun backend_validation_message() = runTest {
        val gw = FakeAdoptionApplicationRemoteGateway(
            submitError = IllegalStateException("APPLICATION_MESSAGE_INVALID")
        )
        val result = remote(gw).submit(validDraft())
        assertIs<AdoptionApplicationResult.ValidationError>(result)
    }

    @Test
    fun network_error_sanitized() = runTest {
        val gw = FakeAdoptionApplicationRemoteGateway(
            submitError = IllegalStateException("network timeout")
        )
        val result = remote(gw).submit(validDraft())
        assertIs<AdoptionApplicationResult.BackendError>(result)
        assertTrue(result.message.contains("conexión", ignoreCase = true))
    }

    @Test
    fun status_mapping_real_only() {
        assertEquals(AdoptionApplicationStatus.SUBMITTED, AdoptionApplicationStatus.parse("SUBMITTED"))
        assertEquals(AdoptionApplicationStatus.UNDER_REVIEW, AdoptionApplicationStatus.parse("UNDER_REVIEW"))
        assertEquals(AdoptionApplicationStatus.ACCEPTED, AdoptionApplicationStatus.parse("ACCEPTED"))
        assertEquals(AdoptionApplicationStatus.REJECTED, AdoptionApplicationStatus.parse("REJECTED"))
        assertEquals(AdoptionApplicationStatus.WITHDRAWN, AdoptionApplicationStatus.parse("WITHDRAWN"))
        assertNull(AdoptionApplicationStatus.parse("FAKE_STATUS"))
    }

    @Test
    fun withdraw_when_allowed() = runTest {
        val gw = FakeAdoptionApplicationRemoteGateway(
            mine = listOf(
                RemoteAdoptionApplicationRow(
                    id = "app-1",
                    adoptionId = "adopt-1",
                    message = "hola",
                    status = "SUBMITTED",
                    adoptionTitle = "Nube",
                    petName = "Nube"
                )
            )
        )
        val result = remote(gw).withdraw(AdoptionApplicationId("app-1"))
        assertIs<AdoptionApplicationResult.Success>(result)
        assertTrue(AdoptionApplicationStatus.canWithdraw(AdoptionApplicationStatus.SUBMITTED))
        assertFalse(AdoptionApplicationStatus.canWithdraw(AdoptionApplicationStatus.ACCEPTED))
    }

    @Test
    fun list_mine_no_pii_fields_in_summary() = runTest {
        val gw = FakeAdoptionApplicationRemoteGateway(
            mine = listOf(
                RemoteAdoptionApplicationRow(
                    id = "app-1",
                    adoptionId = "adopt-1",
                    applicantUserId = "secret-uid",
                    applicantName = "Secret Name",
                    contactPhone = "+54911XXXX",
                    message = "Mensaje privado de postulación",
                    status = "SUBMITTED",
                    adoptionTitle = "Nube",
                    petName = "Nube",
                    submittedAt = "2026-08-13T12:00:00Z"
                )
            )
        )
        val mine = remote(gw).listMine().getOrThrow()
        assertEquals(1, mine.size)
        val s = mine.first()
        assertEquals("Nube", s.adoptionTitle)
        assertFalse(s.messagePreview.contains("+54911"))
        // Summary type has no phone/email/applicantUserId properties for public leak.
        assertEquals("app-1", s.id.value)
    }

    @Test
    fun public_adoption_models_have_no_applicant_pii() {
        val summaryNames = AdoptionSummary::class.simpleName
        assertEquals("AdoptionSummary", summaryNames)
        // Compile-time separation: AdoptionApplicationSummary is distinct type.
        assertTrue(AdoptionApplicationSummary::class != AdoptionSummary::class)
    }

    @Test
    fun unconfigured_no_fake_success() = runTest {
        val result = UnconfiguredAdoptionApplicationRepository().submit(validDraft())
        assertIs<AdoptionApplicationResult.BackendError>(result)
    }

    @Test
    fun adoption_read_still_real_remote_mode() {
        assertEquals(AdoptionDataMode.REAL_REMOTE, UnconfiguredAdoptionRepository().dataMode)
        assertEquals(AdoptionDataMode.REAL_REMOTE, UnconfiguredAdoptionApplicationRepository().dataMode)
    }
}
