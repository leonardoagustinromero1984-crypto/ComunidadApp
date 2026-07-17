package com.comunidapp.app.data.files

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.repository.MockFileAssetRepository
import com.comunidapp.app.data.repository.MockFileDownloadRepository
import com.comunidapp.app.data.repository.MockFileUploadRepository
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileLocalMetadata
import com.comunidapp.app.domain.files.FileResourceRef
import com.comunidapp.app.domain.files.FileResourceType
import com.comunidapp.app.domain.files.FileUploadRequest
import com.comunidapp.app.domain.files.authorization.FileAuthContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileDisplayResolverTest {

    @Test
    fun `signed urls expire in memory and are never stored in asset`() = runTest {
        val fixture = Fixture()
        val uploaded = fixture.upload(publicRequest(), "user-1")
        var now = 1_000L
        val resolver = FileDisplayResolver(
            fixture.assets,
            MockFileDownloadRepository(fixture.assets)
        ) { now }

        val first = resolver.resolve(
            uploaded.assetId,
            null,
            FileAuthContext(actorUserId = "user-1")
        ) as AppResult.Success
        assertTrue(first.data.displayValue.startsWith("https://"))
        assertFalse(fixture.assets.getAsset(uploaded.assetId).toString().contains(first.data.displayValue))

        now = first.data.expiresAtEpochMs!! + 1
        val second = resolver.resolve(
            uploaded.assetId,
            null,
            FileAuthContext(actorUserId = "user-1")
        ) as AppResult.Success
        assertNotEquals(first.data.displayValue, second.data.displayValue)
        resolver.clearTemporaryState()
        assertEquals(0, resolver.cachedCountForTests())
    }

    @Test
    fun `deep link without sensitive permission is denied`() = runTest {
        val fixture = Fixture("evidence.pdf", "application/pdf")
        val uploaded = fixture.upload(
            FileUploadRequest(
                purpose = FileAssetPurpose.MODERATION_EVIDENCE,
                owner = FileAssetOwner.Platform(),
                resourceRef = FileResourceRef(FileResourceType.MODERATION_CASE, "case-1"),
                originalFilename = "evidence.pdf",
                declaredMimeType = "application/pdf",
                sizeBytes = 100L,
                requestedVisibility = FileAssetVisibility.AUTHORIZED_STAFF
            ),
            "staff-1"
        )
        val resolver = FileDisplayResolver(
            fixture.assets,
            MockFileDownloadRepository(fixture.assets)
        )

        val result = resolver.resolve(
            uploaded.assetId,
            null,
            FileAuthContext(actorUserId = "staff-1"),
            deepLinkSensitive = true
        )
        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun `wrong organization is denied`() = runTest {
        val fixture = Fixture()
        val uploaded = fixture.upload(
            FileUploadRequest(
                purpose = FileAssetPurpose.ORGANIZATION_LOGO,
                owner = FileAssetOwner.Organization("org-a"),
                resourceRef = FileResourceRef(FileResourceType.ORGANIZATION, "org-a"),
                originalFilename = "logo.jpg",
                declaredMimeType = "image/jpeg",
                sizeBytes = 100L,
                requestedVisibility = FileAssetVisibility.PUBLIC
            ),
            "editor-1"
        )
        val resolver = FileDisplayResolver(
            fixture.assets,
            MockFileDownloadRepository(fixture.assets)
        )

        val result = resolver.resolve(
            uploaded.assetId,
            null,
            FileAuthContext(actorUserId = "editor-1", organizationId = "org-b")
        )
        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun `legacy fallback is read only and rejects local permanent references`() = runTest {
        val fixture = Fixture()
        val resolver = FileDisplayResolver(
            fixture.assets,
            MockFileDownloadRepository(fixture.assets)
        )
        val legacy = resolver.resolve(
            null,
            "https://legacy.example/avatar.jpg",
            FileAuthContext(actorUserId = null)
        ) as AppResult.Success
        assertTrue(legacy.data.legacyReadOnly)
        assertTrue(
            resolver.resolve(
                null,
                "content://private",
                FileAuthContext(actorUserId = "user-1")
            ) is AppResult.Failure
        )
    }

    private class Fixture(
        private val filename: String = "photo.jpg",
        private val mime: String = "image/jpeg"
    ) {
        val assets = MockFileAssetRepository()
        private val uploads = MockFileUploadRepository(assets) { 1_000L }
        private val coordinator = FileUploadCoordinator(
            uploads,
            assets,
            MockFileObjectUploader(),
            object : FileLocalMetadataReader {
                override suspend fun read(uriString: String) = AppResult.Success(
                    FileLocalMetadata(filename, mime, 100L, uriString)
                )
            },
            object : FileBytesReader {
                override suspend fun readBytes(uriString: String) =
                    AppResult.Success(byteArrayOf(1))
            },
            { 1_000L }
        )

        suspend fun upload(request: FileUploadRequest, actor: String) =
            (coordinator.startUpload("content://file", request, actor) as AppResult.Success).data
    }

    private fun publicRequest() = FileUploadRequest(
        purpose = FileAssetPurpose.USER_AVATAR,
        owner = FileAssetOwner.User("user-1"),
        originalFilename = "photo.jpg",
        declaredMimeType = "image/jpeg",
        sizeBytes = 100L,
        requestedVisibility = FileAssetVisibility.PUBLIC
    )
}
