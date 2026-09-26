package com.comunidapp.app.data.files

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.repository.MockFileAssetRepository
import com.comunidapp.app.data.repository.MockFileUploadRepository
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileLocalMetadata
import com.comunidapp.app.domain.files.FileUiErrorMapper
import com.comunidapp.app.domain.files.FileUploadRequest
import com.comunidapp.app.domain.files.TusUploadSessionHint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class FileUploadCoordinatorVideoTest {

    @Test
    fun reasonableVideoUsesDirectFileUploadNotByteArray() = runTest {
        val usedBytes = AtomicBoolean(false)
        val usedFile = AtomicBoolean(false)
        val assets = MockFileAssetRepository()
        val uploads = MockFileUploadRepository(assets) { 1_000L }
        val coordinator = FileUploadCoordinator(
            uploadRepository = uploads,
            assetRepository = assets,
            objectUploader = object : FileObjectUploader {
                override suspend fun uploadBytes(
                    physicalBucket: String,
                    storagePath: String,
                    bytes: ByteArray,
                    mimeType: String,
                    onProgress: (Int) -> Unit
                ): AppResult<Unit> {
                    usedBytes.set(true)
                    return AppResult.Success(Unit)
                }

                override suspend fun uploadFile(
                    physicalBucket: String,
                    storagePath: String,
                    file: File,
                    mimeType: String,
                    sizeBytes: Long,
                    onProgress: (Int) -> Unit,
                    isCancelled: () -> Boolean,
                    resumeUrl: String?,
                    onSession: (TusUploadSessionHint) -> Unit
                ): AppResult<Unit> {
                    usedFile.set(true)
                    onProgress(100)
                    return AppResult.Success(Unit)
                }
            },
            metadataReader = object : FileLocalMetadataReader {
                override suspend fun read(uriString: String) = AppResult.Success(
                    FileLocalMetadata("clip.mp4", "video/mp4", 3L * 1024 * 1024, uriString)
                )
            },
            bytesReader = object : FileBytesReader {
                override suspend fun readBytes(uriString: String): AppResult<ByteArray> {
                    usedBytes.set(true)
                    return AppResult.Success(byteArrayOf(1, 2, 3))
                }

                override suspend fun materializeForUpload(uriString: String): AppResult<File> {
                    val file = File.createTempFile("leover_vid_", ".mp4")
                    file.writeBytes(ByteArray(64))
                    return AppResult.Success(file)
                }
            },
            imageIngest = com.comunidapp.app.domain.media.ImageIngest { uri, _ ->
                com.comunidapp.app.domain.media.ImageIngestResult(
                    uriString = uri,
                    mimeType = "video/mp4",
                    sizeBytes = 3L * 1024 * 1024,
                    normalized = false
                )
            }
        )
        val result = coordinator.startUpload(
            "content://clip",
            FileUploadRequest(
                purpose = FileAssetPurpose.POST_MEDIA,
                owner = FileAssetOwner.User("user-1"),
                resourceRef = com.comunidapp.app.domain.files.FileResourceRef(
                    com.comunidapp.app.domain.files.FileResourceType.POST,
                    "post-1"
                ),
                originalFilename = "clip.mp4",
                declaredMimeType = "video/mp4",
                sizeBytes = 3L * 1024 * 1024,
                requestedVisibility = FileAssetVisibility.PUBLIC
            ),
            "user-1"
        )
        assertTrue((result as? AppResult.Failure)?.error?.code, result is AppResult.Success)
        assertTrue(usedFile.get())
        assertFalse(usedBytes.get())
    }

    @Test
    fun reelVideoUsesDirectFileUploadAndNeverReadBytes() = runTest {
        val usedBytes = AtomicBoolean(false)
        val usedFile = AtomicBoolean(false)
        val assets = MockFileAssetRepository()
        val uploads = MockFileUploadRepository(assets) { 1_000L }
        val coordinator = FileUploadCoordinator(
            uploadRepository = uploads,
            assetRepository = assets,
            objectUploader = object : FileObjectUploader {
                override suspend fun uploadBytes(
                    physicalBucket: String,
                    storagePath: String,
                    bytes: ByteArray,
                    mimeType: String,
                    onProgress: (Int) -> Unit
                ): AppResult<Unit> {
                    usedBytes.set(true)
                    return AppResult.Success(Unit)
                }

                override suspend fun uploadFile(
                    physicalBucket: String,
                    storagePath: String,
                    file: File,
                    mimeType: String,
                    sizeBytes: Long,
                    onProgress: (Int) -> Unit,
                    isCancelled: () -> Boolean,
                    resumeUrl: String?,
                    onSession: (TusUploadSessionHint) -> Unit
                ): AppResult<Unit> {
                    usedFile.set(true)
                    onProgress(100)
                    return AppResult.Success(Unit)
                }
            },
            metadataReader = object : FileLocalMetadataReader {
                override suspend fun read(uriString: String) = AppResult.Success(
                    FileLocalMetadata("reel.mp4", "video/mp4", 4L * 1024 * 1024, uriString)
                )
            },
            bytesReader = object : FileBytesReader {
                override suspend fun readBytes(uriString: String): AppResult<ByteArray> {
                    usedBytes.set(true)
                    error("readBytes must not be called for reel video")
                }

                override suspend fun materializeForUpload(uriString: String): AppResult<File> {
                    val file = File.createTempFile("leover_reel_", ".mp4")
                    file.writeBytes(ByteArray(64))
                    return AppResult.Success(file)
                }
            },
            imageIngest = com.comunidapp.app.domain.media.ImageIngest { uri, _ ->
                com.comunidapp.app.domain.media.ImageIngestResult(
                    uriString = uri,
                    mimeType = "video/mp4",
                    sizeBytes = 4L * 1024 * 1024,
                    normalized = false
                )
            }
        )
        val result = coordinator.startUpload(
            "content://reel",
            FileUploadRequest(
                purpose = FileAssetPurpose.REEL_MEDIA,
                owner = FileAssetOwner.User("user-1"),
                resourceRef = com.comunidapp.app.domain.files.FileResourceRef(
                    com.comunidapp.app.domain.files.FileResourceType.REEL,
                    "reel-1"
                ),
                originalFilename = "reel.mp4",
                declaredMimeType = "video/mp4",
                sizeBytes = 4L * 1024 * 1024,
                requestedVisibility = FileAssetVisibility.PUBLIC
            ),
            "user-1"
        )
        assertTrue((result as? AppResult.Failure)?.error?.code, result is AppResult.Success)
        assertTrue(usedFile.get())
        assertFalse(usedBytes.get())
    }

    @Test
    fun payloadTooLargeDoesNotShowMediaUpload413() {
        val message = FileUiErrorMapper.message(
            code = "HTTP_413",
            technicalMessage = "Payload Too Large"
        )
        assertFalse(message.contains("MEDIA-UPLOAD-413"))
        assertTrue(message.contains("El video supera el tamaño máximo permitido."))
    }

    @Test
    fun verifiedAvcThenOctetStreamMetadataDoesNotEmitMime01ForPostOrReel() = runTest {
        listOf(FileAssetPurpose.POST_MEDIA, FileAssetPurpose.REEL_MEDIA).forEach { purpose ->
            val usedBytes = AtomicBoolean(false)
            val usedFile = AtomicBoolean(false)
            val assets = MockFileAssetRepository()
            val uploads = MockFileUploadRepository(assets) { 1_000L }
            val coordinator = FileUploadCoordinator(
                uploadRepository = uploads,
                assetRepository = assets,
                objectUploader = object : FileObjectUploader {
                    override suspend fun uploadBytes(
                        physicalBucket: String,
                        storagePath: String,
                        bytes: ByteArray,
                        mimeType: String,
                        onProgress: (Int) -> Unit
                    ): AppResult<Unit> {
                        usedBytes.set(true)
                        return AppResult.Success(Unit)
                    }

                    override suspend fun uploadFile(
                        physicalBucket: String,
                        storagePath: String,
                        file: File,
                        mimeType: String,
                        sizeBytes: Long,
                        onProgress: (Int) -> Unit,
                        isCancelled: () -> Boolean,
                        resumeUrl: String?,
                        onSession: (TusUploadSessionHint) -> Unit
                    ): AppResult<Unit> {
                        usedFile.set(true)
                        onProgress(100)
                        return AppResult.Success(Unit)
                    }
                },
                metadataReader = object : FileLocalMetadataReader {
                    override suspend fun read(uriString: String) = AppResult.Success(
                        FileLocalMetadata("clip", "application/octet-stream", 3L * 1024 * 1024, uriString)
                    )
                },
                bytesReader = object : FileBytesReader {
                    override suspend fun readBytes(uriString: String): AppResult<ByteArray> {
                        usedBytes.set(true)
                        error("readBytes must not run after VerifiedVideo")
                    }

                    override suspend fun materializeForUpload(uriString: String): AppResult<File> {
                        val file = File.createTempFile("leover_vid_", ".mp4")
                        file.writeBytes(ByteArray(64))
                        return AppResult.Success(file)
                    }
                },
                imageIngest = com.comunidapp.app.domain.media.ImageIngest { uri, _ ->
                    com.comunidapp.app.domain.media.ImageIngestResult(
                        uriString = uri,
                        mimeType = "application/octet-stream",
                        sizeBytes = 3L * 1024 * 1024,
                        normalized = false
                    )
                }
            )
            val container = com.comunidapp.app.domain.media.VerifiedVideoPipeline
                .normalizeContainerMime("video/avc")
            val result = coordinator.startUpload(
                "content://provider-null-mime",
                FileUploadRequest(
                    purpose = purpose,
                    owner = FileAssetOwner.User("user-1"),
                    resourceRef = com.comunidapp.app.domain.files.FileResourceRef(
                        if (purpose == FileAssetPurpose.REEL_MEDIA) {
                            com.comunidapp.app.domain.files.FileResourceType.REEL
                        } else {
                            com.comunidapp.app.domain.files.FileResourceType.POST
                        },
                        "media-1"
                    ),
                    originalFilename = "clip.mp4",
                    declaredMimeType = container,
                    sizeBytes = 3L * 1024 * 1024,
                    requestedVisibility = FileAssetVisibility.PUBLIC
                ),
                "user-1"
            )
            assertTrue((result as? AppResult.Failure)?.error?.code, result is AppResult.Success)
            assertTrue(usedFile.get())
            assertFalse(usedBytes.get())
            val later = com.comunidapp.app.domain.files.FileValidationRules.validateUploadRequest(
                FileUploadRequest(
                    purpose = purpose,
                    owner = FileAssetOwner.User("user-1"),
                    originalFilename = "clip.mp4",
                    declaredMimeType = container,
                    sizeBytes = 3L * 1024 * 1024,
                    requestedVisibility = FileAssetVisibility.PUBLIC
                )
            )
            assertTrue(later.isSuccess)
            assertFalse(later.exceptionOrNull()?.message.orEmpty().contains("MIME_NOT_ALLOWED"))
        }
    }
}
