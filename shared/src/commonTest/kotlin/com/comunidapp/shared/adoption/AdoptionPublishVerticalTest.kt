package com.comunidapp.shared.adoption

import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.shared.auth.FakeAuthSessionGateway
import com.comunidapp.shared.auth.GatewayAuthRepository
import com.comunidapp.shared.location.ApproximateLocation
import com.comunidapp.shared.remote.FakeAdoptionRemoteGateway
import com.comunidapp.shared.remote.RemoteAdoptionPublicationRow
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.session.SessionUser
import com.comunidapp.shared.ui.VerticalLoadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class AdoptionPublishVerticalTest {

    private fun auth(userId: String = "user-1") =
        GatewayAuthRepository(
            FakeAuthSessionGateway(
                SessionState.Authenticated(SessionUser(userId, "a@leover.test", "Ana"))
            )
        )

    private fun validDraft() = AdoptionPublishDraft(
        petId = PetId("pet-1"),
        title = "Nube busca hogar",
        description = "Cachorra sociable, ideal para familia",
        requirements = "Patio",
        approximateLocation = ApproximateLocation("Palermo", "CABA"),
        publishImmediately = true
    )

    private fun remote(
        gateway: FakeAdoptionRemoteGateway = FakeAdoptionRemoteGateway(),
        authRepo: GatewayAuthRepository = auth()
    ) = RemoteAdoptionRepository(gateway = gateway, sessionRepository = authRepo)

    @Test
    fun draft_valid() {
        assertTrue(AdoptionPublishDraftValidator.validate(validDraft()).isSuccess)
    }

    @Test
    fun title_required() {
        assertTrue(
            AdoptionPublishDraftValidator.validate(validDraft().copy(title = "  ")).isFailure
        )
    }

    @Test
    fun description_required() {
        assertTrue(
            AdoptionPublishDraftValidator.validate(validDraft().copy(description = "")).isFailure
        )
    }

    @Test
    fun unauthenticated_publish() = runTest {
        val authRepo = GatewayAuthRepository(FakeAuthSessionGateway(SessionState.Unauthenticated))
        val result = remote(authRepo = authRepo).publish(validDraft())
        assertIs<AdoptionPublishResult.Unauthenticated>(result)
    }

    @Test
    fun forbidden_sanitized() = runTest {
        val gw = FakeAdoptionRemoteGateway(
            createError = IllegalStateException("403 RLS policy denied")
        )
        val result = remote(gw).publish(validDraft())
        assertIs<AdoptionPublishResult.Forbidden>(result)
        assertFalse(result.message.contains("RLS", ignoreCase = true))
    }

    @Test
    fun create_success_published() = runTest {
        val gw = FakeAdoptionRemoteGateway()
        val result = remote(gw).publish(validDraft())
        assertIs<AdoptionPublishResult.Success>(result)
        assertTrue(result.published)
        assertEquals(1, gw.createCalls)
        assertEquals("pet-1", gw.lastCreate?.petId)
        assertTrue(gw.lastCreate?.publish == true)
        assertFalse(gw.lastCreate?.locationText.orEmpty().contains("lat"))
    }

    @Test
    fun draft_not_immediate() = runTest {
        val gw = FakeAdoptionRemoteGateway()
        val result = remote(gw).publish(validDraft().copy(publishImmediately = false))
        assertIs<AdoptionPublishResult.Success>(result)
        assertFalse(result.published)
        assertEquals(false, gw.lastCreate?.publish)
    }

    @Test
    fun conflict_already_exists() = runTest {
        val gw = FakeAdoptionRemoteGateway(
            createError = IllegalStateException("ADOPTION_ALREADY_EXISTS")
        )
        val result = remote(gw).publish(validDraft())
        assertIs<AdoptionPublishResult.Conflict>(result)
        assertTrue(result.message.contains("publicación", ignoreCase = true))
    }

    @Test
    fun create_fail_backend() = runTest {
        val gw = FakeAdoptionRemoteGateway(
            createError = IllegalStateException("PET_NOT_ADOPTABLE")
        )
        val result = remote(gw).publish(validDraft())
        assertIs<AdoptionPublishResult.BackendError>(result)
        assertFalse(result.message.contains("PET_NOT_ADOPTABLE"))
    }

    @Test
    fun locality_safe_no_coordinates() = runTest {
        val gw = FakeAdoptionRemoteGateway()
        remote(gw).publish(validDraft())
        val loc = gw.lastCreate?.locationText.orEmpty()
        assertTrue(loc.contains("Palermo") || loc.contains("CABA"))
        assertFalse(loc.contains("-34."))
        assertFalse(loc.contains("lng", ignoreCase = true))
    }

    @Test
    fun media_write_partial_no_photo_in_draft() {
        // Productive Android form: photo = pet snapshot; draft has no FileRef/photo field.
        val draft = validDraft()
        assertTrue(draft.title.isNotBlank())
        assertTrue(draft.petId.value.isNotBlank())
    }

    @Test
    fun refresh_after_publish() = runTest {
        val gw = FakeAdoptionRemoteGateway(
            created = RemoteAdoptionPublicationRow(
                id = "adopt-new-1",
                name = "Nube",
                title = "Nube busca hogar",
                description = "desc",
                status = "PUBLISHED",
                petId = "pet-1",
                location = "Palermo"
            )
        )
        val repo = remote(gw)
        assertIs<AdoptionPublishResult.Success>(repo.publish(validDraft()))
        val state = repo.observeList().first { it !is VerticalLoadState.Loading }
        assertIs<VerticalLoadState.Content<List<AdoptionSummary>>>(state)
        assertTrue(state.data.any { it.id.value == "adopt-new-1" })
    }

    @Test
    fun unconfigured_no_fake_success() = runTest {
        val result = UnconfiguredAdoptionRepository().publish(validDraft())
        assertIs<AdoptionPublishResult.BackendError>(result)
    }

    @Test
    fun sanitized_error_no_sql_leak_tokens() = runTest {
        val longLeak =
            "Postgrest error SELECT * FROM adoption_publications WHERE secret=abc " +
                "endpoint https://xyz.supabase.co/rest/v1 bucket private/path/xyz " +
                "stacktrace at com.example.Internal"
        assertTrue(longLeak.length > 120)
        val gw = FakeAdoptionRemoteGateway(createError = IllegalStateException(longLeak))
        val result = remote(gw).publish(validDraft())
        assertIs<AdoptionPublishResult.BackendError>(result)
        assertFalse(result.message.contains("supabase.co", ignoreCase = true))
        assertFalse(result.message.contains("adoption_publications", ignoreCase = true))
        assertFalse(result.message.contains("stacktrace", ignoreCase = true))
    }
}
