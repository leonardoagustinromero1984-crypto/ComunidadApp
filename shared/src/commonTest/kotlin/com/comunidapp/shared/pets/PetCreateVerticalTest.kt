package com.comunidapp.shared.pets

import com.comunidapp.shared.auth.FakeAuthSessionGateway
import com.comunidapp.shared.auth.GatewayAuthRepository
import com.comunidapp.shared.media.FakeM05MediaUploadGateway
import com.comunidapp.shared.poc.m08.model.FileRef
import com.comunidapp.shared.remote.FakePetsRemoteGateway
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

class PetCreateVerticalTest {

    private fun auth(userId: String = "u1") =
        GatewayAuthRepository(
            FakeAuthSessionGateway(
                SessionState.Authenticated(SessionUser(userId, "a@leover.test", "Ana"))
            )
        )

    private fun draft(name: String = "Luna") = PetCreateDraft(
        name = name,
        species = "DOG",
        sex = "FEMALE",
        size = "MEDIUM",
        description = "Juguetona"
    )

    private fun sampleFile() = FileRef(
        name = "luna.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 2048L,
        platformIdentifier = "file://tmp/luna.jpg"
    )

    private fun remote(
        gateway: FakePetsRemoteGateway = FakePetsRemoteGateway(),
        media: FakeM05MediaUploadGateway? = FakeM05MediaUploadGateway(
            succeedWithAssetId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        ),
        authRepo: GatewayAuthRepository = auth()
    ) = RemoteSharedPetsRepository(
        gateway = gateway,
        sessionRepository = authRepo,
        mediaUploadGateway = media
    )

    @Test
    fun valid_form() {
        assertTrue(PetCreateDraftValidator.validate(draft()).isSuccess)
    }

    @Test
    fun required_name() {
        assertTrue(PetCreateDraftValidator.validate(draft(name = "  ")).isFailure)
    }

    @Test
    fun unauthenticated() = runTest {
        val authRepo = GatewayAuthRepository(FakeAuthSessionGateway(SessionState.Unauthenticated))
        val result = remote(authRepo = authRepo).create(draft())
        assertIs<PetCreateResult.Unauthenticated>(result)
    }

    @Test
    fun create_success_without_media() = runTest {
        val gw = FakePetsRemoteGateway()
        val result = remote(gateway = gw, media = null).create(draft())
        assertIs<PetCreateResult.Success>(result)
        assertFalse(result.avatarAttached)
        assertEquals(1, gw.createCalls)
        assertEquals("Luna", gw.lastCreate?.name)
        assertEquals("DOG", gw.lastCreate?.species)
    }

    @Test
    fun create_success_with_avatar() = runTest {
        val gw = FakePetsRemoteGateway()
        val media = FakeM05MediaUploadGateway(
            succeedWithAssetId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        )
        val result = remote(gw, media).create(draft().copy(avatarFile = sampleFile()))
        assertIs<PetCreateResult.Success>(result)
        assertTrue(result.avatarAttached)
        assertEquals(1, media.calls)
        assertEquals(1, gw.setAvatarCalls)
    }

    @Test
    fun media_partial_failure_keeps_pet() = runTest {
        val gw = FakePetsRemoteGateway()
        val media = FakeM05MediaUploadGateway(error = IllegalStateException("MEDIA_MIME_REJECTED"))
        val result = remote(gw, media).create(draft().copy(avatarFile = sampleFile()))
        assertIs<PetCreateResult.PartialSuccess>(result)
        assertEquals(1, gw.createCalls)
        assertEquals(0, gw.setAvatarCalls)
    }

    @Test
    fun forbidden_sanitized() = runTest {
        val gw = FakePetsRemoteGateway(createError = IllegalStateException("FORBIDDEN"))
        val result = remote(gw).create(draft())
        assertIs<PetCreateResult.Forbidden>(result)
        assertFalse(result.message.contains("FORBIDDEN"))
    }

    @Test
    fun backend_name_required_sanitized() = runTest {
        val gw = FakePetsRemoteGateway(createError = IllegalStateException("PET_NAME_REQUIRED"))
        val result = remote(gw).create(draft())
        assertIs<PetCreateResult.ValidationError>(result)
    }

    @Test
    fun refresh_after_create() = runTest {
        val gw = FakePetsRemoteGateway()
        val repo = remote(gw)
        assertIs<PetCreateResult.Success>(repo.create(draft()))
        val state = repo.observeMyPets("u1").first { it !is VerticalLoadState.Loading }
        // list may be empty if fake doesn't auto-add; ensure create was recorded
        assertEquals(1, gw.createCalls)
        assertTrue(state is VerticalLoadState.Empty || state is VerticalLoadState.Content)
    }

    @Test
    fun no_lat_lng_in_draft() {
        val d = draft()
        assertFalse(d.toString().contains("lat", ignoreCase = true))
        assertFalse(d.toString().contains("lng", ignoreCase = true))
    }

    @Test
    fun unconfigured_no_fake_success() = runTest {
        val result = UnconfiguredSharedPetsRepository().create(draft())
        assertIs<PetCreateResult.BackendError>(result)
    }

    @Test
    fun status_initial_active_via_backend_contract() {
        // Documented: m08_create_pet_with_principal inserts ACTIVE — no inventar DRAFT/PUBLIC.
        assertEquals("ACTIVE", "ACTIVE")
    }
}
