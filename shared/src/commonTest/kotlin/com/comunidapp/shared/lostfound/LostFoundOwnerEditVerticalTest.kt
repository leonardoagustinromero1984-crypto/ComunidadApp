package com.comunidapp.shared.lostfound

import com.comunidapp.shared.auth.FakeAuthSessionGateway
import com.comunidapp.shared.auth.GatewayAuthRepository
import com.comunidapp.shared.media.FakeMediaResolver
import com.comunidapp.shared.poc.m08.model.FileRef
import com.comunidapp.shared.remote.FakeLostFoundMediaUploadGateway
import com.comunidapp.shared.remote.FakeLostFoundRemoteGateway
import com.comunidapp.shared.remote.FakeLostFoundWriteGateway
import com.comunidapp.shared.remote.RemoteLostFoundRow
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.session.SessionUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LostFoundOwnerEditVerticalTest {

    private fun auth(userId: String = "owner-1") =
        GatewayAuthRepository(
            FakeAuthSessionGateway(
                SessionState.Authenticated(SessionUser(userId, "a@leover.test", "Ana"))
            )
        )

    private fun activeRow(
        id: String = "lf-1",
        authorId: String = "owner-1",
        status: String = "ACTIVE",
        type: String = "LOST"
    ) = RemoteLostFoundRow(
        id = id,
        authorId = authorId,
        authorName = "Ana",
        type = type,
        petName = "Luna",
        species = "DOG",
        location = "Palermo",
        description = "Se perdió cerca de la plaza",
        status = status,
        publicCode = "PUB-LF1",
        photoUrl = "old-asset"
    )

    private fun sampleFile() = FileRef(
        name = "luna.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 2048L,
        platformIdentifier = "file://tmp/luna.jpg"
    )

    private fun remote(
        row: RemoteLostFoundRow = activeRow(),
        write: FakeLostFoundWriteGateway = FakeLostFoundWriteGateway(),
        media: FakeLostFoundMediaUploadGateway = FakeLostFoundMediaUploadGateway(
            succeedWithAssetId = "new-asset-id"
        ),
        mediaResolver: FakeMediaResolver? = FakeMediaResolver(),
        authRepo: GatewayAuthRepository = auth()
    ): Triple<RemoteLostFoundRepository, FakeLostFoundWriteGateway, FakeLostFoundMediaUploadGateway> {
        val read = FakeLostFoundRemoteGateway(detail = row, list = listOf(row))
        val repo = RemoteLostFoundRepository(
            gateway = read,
            writeGateway = write,
            sessionRepository = authRepo,
            mediaUploadGateway = media,
            mediaResolver = mediaResolver
        )
        return Triple(repo, write, media)
    }

    @Test
    fun update_owner_content_success() = runTest {
        val (repo, write, _) = remote()
        val result = repo.updateOwnerContent(
            LostFoundId("lf-1"),
            description = "Nueva descripción más larga",
            location = "Recoleta"
        )
        assertIs<LostFoundManageResult.Success>(result)
        assertEquals(1, write.ownerFieldUpdates.size)
        assertEquals("Nueva descripción más larga", write.ownerFieldUpdates.first().second)
        assertEquals("Recoleta", write.ownerFieldUpdates.first().third)
        // type immutable — write gateway never receives type field
        assertTrue(write.inserted.isEmpty())
        assertTrue(write.statusUpdates.isEmpty() || write.statusUpdates.none { it.second == "LOST" })
    }

    @Test
    fun resolved_allows_edit() = runTest {
        val (repo, write, _) = remote(row = activeRow(status = "RESOLVED"))
        val result = repo.updateOwnerContent(
            LostFoundId("lf-1"),
            description = "Actualizado tras resolver caso",
            location = "Palermo"
        )
        assertIs<LostFoundManageResult.Success>(result)
        assertEquals(1, write.ownerFieldUpdates.size)
    }

    @Test
    fun forbidden_non_owner() = runTest {
        val (repo, write, media) = remote(row = activeRow(authorId = "other"))
        assertIs<LostFoundManageResult.Forbidden>(
            repo.updateOwnerContent(LostFoundId("lf-1"), "desc larga ok", "Zona")
        )
        assertIs<LostFoundManageResult.Forbidden>(
            repo.replacePhoto(LostFoundId("lf-1"), sampleFile())
        )
        assertTrue(write.ownerFieldUpdates.isEmpty())
        assertEquals(0, media.calls)
        assertTrue(write.photoUpdates.isEmpty())
    }

    @Test
    fun type_immutable_gateway_never_gets_type() = runTest {
        val write = FakeLostFoundWriteGateway()
        val (repo, _, _) = remote(write = write)
        repo.updateOwnerContent(LostFoundId("lf-1"), "Descripción válida larga", "CABA")
        // updateOwnerFields only description/location — no type column in update row
        assertEquals(1, write.ownerFieldUpdates.size)
        assertTrue(write.inserted.isEmpty())
    }

    @Test
    fun photo_replace_success_clears_cache() = runTest {
        val resolver = FakeMediaResolver()
        val write = FakeLostFoundWriteGateway()
        val media = FakeLostFoundMediaUploadGateway(succeedWithAssetId = "asset-new")
        val (repo, _, _) = remote(write = write, media = media, mediaResolver = resolver)
        val result = repo.replacePhoto(LostFoundId("lf-1"), sampleFile())
        assertIs<LostFoundManageResult.Success>(result)
        assertEquals(1, media.calls)
        assertEquals(listOf("lf-1" to "asset-new"), write.photoUpdates)
        assertEquals(1, resolver.clearCount)
    }

    @Test
    fun photo_upload_fail() = runTest {
        val write = FakeLostFoundWriteGateway()
        val media = FakeLostFoundMediaUploadGateway(
            succeedWithAssetId = null,
            error = IllegalStateException("MEDIA_UPLOAD_FAILED")
        )
        val (repo, _, _) = remote(write = write, media = media)
        val result = repo.replacePhoto(LostFoundId("lf-1"), sampleFile())
        assertIs<LostFoundManageResult.BackendError>(result)
        assertTrue(write.photoUpdates.isEmpty())
    }

    @Test
    fun photo_association_fail_partial() = runTest {
        val write = FakeLostFoundWriteGateway(
            photoUpdateError = IllegalStateException("RLS denied")
        )
        val media = FakeLostFoundMediaUploadGateway(succeedWithAssetId = "uploaded-but-orphan")
        val (repo, _, _) = remote(write = write, media = media)
        val result = repo.replacePhoto(LostFoundId("lf-1"), sampleFile())
        assertIs<LostFoundManageResult.PartialSuccess>(result)
        assertEquals(1, media.calls)
        // no claim of new photo success
    }

    @Test
    fun unconfigured_no_fake_success() = runTest {
        assertIs<LostFoundManageResult.BackendError>(
            UnconfiguredLostFoundRepository().replacePhoto(LostFoundId("x"), sampleFile())
        )
    }
}
