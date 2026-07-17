package com.comunidapp.app.data.files

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.repository.MockFileAssetRepository
import com.comunidapp.app.data.repository.MockFileUploadRepository
import com.comunidapp.app.domain.files.FileAssetLink
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetStatus
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileLocalMetadata
import com.comunidapp.app.domain.files.FileRelationType
import com.comunidapp.app.domain.files.FileResourceRef
import com.comunidapp.app.domain.files.FileResourceType
import com.comunidapp.app.domain.files.FileUploadPhase
import com.comunidapp.app.domain.files.FileUploadRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileUploadCoordinatorTest {
    private val assets = MockFileAssetRepository()
    private val uploads = MockFileUploadRepository(assets) { 1_000L }
    private val metadata = object : FileLocalMetadataReader {
        override suspend fun read(uriString: String) = AppResult.Success(
            FileLocalMetadata("photo.jpg", "image/jpeg", 100L, uriString)
        )
    }
    private val bytes = object : FileBytesReader {
        override suspend fun readBytes(uriString: String) =
            AppResult.Success(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `mock upload reaches ready and never uses legacy bucket`() = runTest {
        val coordinator = coordinator(MockFileObjectUploader())
        val result = coordinator.startUpload("content://photo", request(), "user-1")

        assertTrue(result is AppResult.Success)
        result as AppResult.Success
        assertNotEquals("leover", result.data.physicalBucket.lowercase())
        assertEquals(FileUploadPhase.Ready, coordinator.uiState.value.phase)
        assertEquals(100, coordinator.uiState.value.progressPercent)
        assertEquals(FileAssetStatus.READY, (assets.getAsset(result.data.assetId) as AppResult.Success).data.status)
    }

    @Test
    fun `double submit is rejected while first upload is active`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val coordinator = coordinator(blockingUploader(gate))
        val first = async { coordinator.startUpload("content://one", request(), "user-1") }
        runCurrent()

        val second = coordinator.startUpload("content://two", request(), "user-1")
        assertTrue(second is AppResult.Failure)
        assertEquals("DOUBLE_SUBMIT", (second as AppResult.Failure).error.code)

        gate.complete(Unit)
        runCurrent()
        assertTrue(first.await() is AppResult.Success)
    }

    @Test
    fun `cancel is idempotent and retry succeeds`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val coordinator = coordinator(blockingUploader(gate))
        val uploading = async {
            coordinator.startUpload("content://photo", request(), "user-1")
        }
        runCurrent()
        val sessionId = coordinator.uiState.value.sessionId!!

        assertTrue(coordinator.cancel(sessionId) is AppResult.Success)
        assertTrue(coordinator.cancel(sessionId) is AppResult.Success)
        gate.complete(Unit)
        runCurrent()
        assertTrue(uploading.await() is AppResult.Failure)
        assertEquals(FileUploadPhase.Cancelled, coordinator.uiState.value.phase)

        val retryCoordinator = coordinator(failOnceUploader())
        assertTrue(
            retryCoordinator.startUpload("content://photo", request(), "user-1") is AppResult.Failure
        )
        assertTrue(retryCoordinator.uiState.value.canRetry)
        assertTrue(retryCoordinator.retry() is AppResult.Success)
    }

    @Test
    fun `safe replace keeps old until new is ready then unlinks and deletes`() = runTest {
        val coordinator = coordinator(MockFileObjectUploader())
        val resource = FileResourceRef(FileResourceType.USER, "user-1")
        val old = coordinator.startUpload("content://old", request(), "user-1") as AppResult.Success
        assets.linkAsset(
            old.data.assetId,
            FileAssetLink(
                assetId = old.data.assetId,
                resource = resource,
                relationType = FileRelationType.PRIMARY,
                isPrimary = true,
                createdAtEpochMs = 1_000L
            )
        )

        val replaced = coordinator.safeReplace(
            oldAssetId = old.data.assetId,
            uriString = "content://new",
            request = request(),
            actorUserId = "user-1",
            resource = resource,
            relation = FileRelationType.PRIMARY,
            markOldDeleted = true
        )

        assertTrue(replaced is AppResult.Success)
        assertEquals(
            FileAssetStatus.DELETED,
            (assets.getAsset(old.data.assetId) as AppResult.Success).data.status
        )
        assertTrue(assets.linksForTests(old.data.assetId).isEmpty())
        assertTrue(assets.linksForTests((replaced as AppResult.Success).data.assetId).isNotEmpty())
    }

    @Test
    fun `unlink and delete are separate explicit operations`() = runTest {
        val coordinator = coordinator(MockFileObjectUploader())
        val resource = FileResourceRef(FileResourceType.USER, "user-1")
        val uploaded = coordinator.startUpload("content://photo", request(), "user-1") as AppResult.Success
        assets.linkAsset(
            uploaded.data.assetId,
            FileAssetLink(
                uploaded.data.assetId,
                resource,
                FileRelationType.PRIMARY,
                createdAtEpochMs = 1_000L
            )
        )

        assertTrue(coordinator.unlink(uploaded.data.assetId, resource, FileRelationType.PRIMARY) is AppResult.Success)
        assertEquals(FileAssetStatus.READY, (assets.getAsset(uploaded.data.assetId) as AppResult.Success).data.status)
        assertTrue(coordinator.requestDelete(uploaded.data.assetId) is AppResult.Success)
    }

    @Test
    fun `sensitive evidence and support can never be public`() = runTest {
        val coordinator = coordinator(MockFileObjectUploader())
        for (purpose in listOf(
            FileAssetPurpose.MODERATION_EVIDENCE,
            FileAssetPurpose.SUPPORT_ATTACHMENT
        )) {
            val result = coordinator.selectAndValidate(
                uri = "content://sensitive",
                purpose = purpose,
                owner = FileAssetOwner.Platform(),
                visibility = FileAssetVisibility.PUBLIC,
                resourceRef = FileResourceRef(
                    if (purpose == FileAssetPurpose.MODERATION_EVIDENCE) {
                        FileResourceType.MODERATION_CASE
                    } else {
                        FileResourceType.SUPPORT_TICKET
                    },
                    "resource-1"
                ),
                actorUserId = "staff-1"
            )
            assertTrue(result is AppResult.Failure)
        }
    }

    @Test
    fun `clear removes uri locks retry and active sessions`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val coordinator = coordinator(blockingUploader(gate))
        val upload = async { coordinator.startUpload("content://secret", request(), "user-1") }
        runCurrent()
        assertTrue(coordinator.activeSessionIdsForTests().isNotEmpty())

        coordinator.clearAllSensitiveState()
        assertTrue(coordinator.activeSessionIdsForTests().isEmpty())
        assertEquals(null, coordinator.uiState.value.previewUri)
        assertFalse(coordinator.uiState.value.submittingLocked)
        gate.complete(Unit)
        upload.cancel()
    }

    private fun coordinator(uploader: FileObjectUploader) = FileUploadCoordinator(
        uploadRepository = uploads,
        assetRepository = assets,
        objectUploader = uploader,
        metadataReader = metadata,
        bytesReader = bytes,
        clock = { 1_000L }
    )

    private fun request() = FileUploadRequest(
        purpose = FileAssetPurpose.USER_AVATAR,
        owner = FileAssetOwner.User("user-1"),
        originalFilename = "photo.jpg",
        declaredMimeType = "image/jpeg",
        sizeBytes = 100L,
        requestedVisibility = FileAssetVisibility.PUBLIC
    )

    private fun blockingUploader(gate: CompletableDeferred<Unit>) = object : FileObjectUploader {
        override suspend fun uploadBytes(
            physicalBucket: String,
            storagePath: String,
            bytes: ByteArray,
            mimeType: String,
            onProgress: (Int) -> Unit
        ): AppResult<Unit> {
            onProgress(25)
            gate.await()
            onProgress(100)
            return AppResult.Success(Unit)
        }
    }

    private fun failOnceUploader(): FileObjectUploader {
        var first = true
        return object : FileObjectUploader {
            override suspend fun uploadBytes(
                physicalBucket: String,
                storagePath: String,
                bytes: ByteArray,
                mimeType: String,
                onProgress: (Int) -> Unit
            ): AppResult<Unit> {
                if (first) {
                    first = false
                    return fileUploadFailure("NETWORK", com.comunidapp.app.core.result.AppErrorKind.NETWORK)
                }
                onProgress(100)
                return AppResult.Success(Unit)
            }
        }
    }
}
