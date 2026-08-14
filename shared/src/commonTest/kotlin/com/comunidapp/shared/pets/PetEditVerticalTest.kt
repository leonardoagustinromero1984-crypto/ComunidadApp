package com.comunidapp.shared.pets

import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.shared.auth.FakeAuthSessionGateway
import com.comunidapp.shared.auth.GatewayAuthRepository
import com.comunidapp.shared.media.FakeM05MediaUploadGateway
import com.comunidapp.shared.poc.m08.model.FileRef
import com.comunidapp.shared.remote.FakePetsRemoteGateway
import com.comunidapp.shared.remote.RemotePetRow
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

class PetEditVerticalTest {

    private fun auth(userId: String = "u1") =
        GatewayAuthRepository(
            FakeAuthSessionGateway(
                SessionState.Authenticated(SessionUser(userId, "a@leover.test", "Ana"))
            )
        )

    private fun draft(name: String = "Luna") = PetEditDraft(
        name = name,
        species = "DOG",
        breed = "Mestiza",
        sex = "FEMALE",
        size = "MEDIUM",
        description = "Juguetona",
        ageYears = 2,
        ageMonths = 3,
        color = "Negro"
    )

    private fun sampleFile() = FileRef(
        name = "luna.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 2048L,
        platformIdentifier = "file://tmp/luna.jpg"
    )

    private fun remote(
        gateway: FakePetsRemoteGateway = FakePetsRemoteGateway(
            detail = RemotePetRow(
                id = "pet-1",
                name = "Luna",
                species = "DOG",
                sex = "FEMALE",
                size = "MEDIUM",
                description = "Old",
                ageYears = 1,
                ageMonths = 0,
                color = "Marrón"
            )
        ),
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
    fun success() = runTest {
        val gw = FakePetsRemoteGateway(
            detail = RemotePetRow(id = "pet-1", name = "Luna", species = "DOG")
        )
        val result = remote(gw).update(PetId("pet-1"), draft())
        assertIs<PetEditResult.Success>(result)
        assertEquals(1, gw.updateCalls)
        assertEquals("Luna", gw.lastUpdate?.name)
        assertEquals(2, gw.lastUpdate?.ageYears)
        assertEquals("Negro", gw.lastUpdate?.color)
    }

    @Test
    fun forbidden() = runTest {
        val gw = FakePetsRemoteGateway(
            detail = RemotePetRow(id = "pet-1", name = "Luna"),
            updateError = IllegalStateException("FORBIDDEN")
        )
        val result = remote(gw).update(PetId("pet-1"), draft())
        assertIs<PetEditResult.Forbidden>(result)
        assertFalse(result.message.contains("FORBIDDEN"))
    }

    @Test
    fun name_required() = runTest {
        assertTrue(PetEditDraftValidator.validate(draft(name = "  ")).isFailure)
        val result = remote().update(PetId("pet-1"), draft(name = " "))
        assertIs<PetEditResult.ValidationError>(result)
    }

    @Test
    fun avatar_partial() = runTest {
        val gw = FakePetsRemoteGateway(
            detail = RemotePetRow(id = "pet-1", name = "Luna")
        )
        val media = FakeM05MediaUploadGateway(error = IllegalStateException("MEDIA_MIME_REJECTED"))
        val result = remote(gw, media).update(
            PetId("pet-1"),
            draft().copy(avatarFile = sampleFile())
        )
        assertIs<PetEditResult.PartialSuccess>(result)
        assertEquals(1, gw.updateCalls)
        assertEquals(0, gw.setAvatarCalls)
    }

    @Test
    fun unauthenticated() = runTest {
        val authRepo = GatewayAuthRepository(FakeAuthSessionGateway(SessionState.Unauthenticated))
        val result = remote(authRepo = authRepo).update(PetId("pet-1"), draft())
        assertIs<PetEditResult.Unauthenticated>(result)
    }

    @Test
    fun refresh_after_update() = runTest {
        val gw = FakePetsRemoteGateway(
            detail = RemotePetRow(
                id = "pet-1",
                name = "Luna",
                species = "DOG",
                description = "Old"
            )
        )
        val repo = remote(gw)
        assertIs<PetEditResult.Success>(repo.update(PetId("pet-1"), draft(name = "Nueva")))
        val state = repo.observePetDetail(PetId("pet-1")).first { it !is VerticalLoadState.Loading }
        val content = assertIs<VerticalLoadState.Content<PetDetailView>>(state)
        assertEquals("Nueva", content.data.displayName)
    }

    @Test
    fun unconfigured_no_fake_success() = runTest {
        val result = UnconfiguredSharedPetsRepository().update(PetId("pet-1"), draft())
        assertIs<PetEditResult.BackendError>(result)
    }
}
