package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetStatus
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileRelationType
import com.comunidapp.app.domain.files.FileResourceRef
import com.comunidapp.app.domain.files.FileResourceType
import com.comunidapp.app.domain.files.FileSignedTtlClass
import com.comunidapp.app.domain.files.FileUploadRequest
import com.comunidapp.app.domain.files.FileUploadSessionState
import com.comunidapp.app.domain.files.FileAccessRequest
import com.comunidapp.app.domain.files.FileAssetLink
import com.comunidapp.app.domain.files.authorization.FileAccessDecision
import com.comunidapp.app.domain.files.authorization.FileAuthContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FileRepositoryMocksTest {

    private lateinit var assets: MockFileAssetRepository
    private lateinit var uploads: MockFileUploadRepository
    private lateinit var downloads: MockFileDownloadRepository
    private lateinit var access: MockFileAccessRepository
    private lateinit var retention: MockFileRetentionRepository

    @Before
    fun setUp() {
        assets = MockFileAssetRepository()
        uploads = MockFileUploadRepository(assets) { 1_000L }
        downloads = MockFileDownloadRepository(assets)
        access = MockFileAccessRepository(assets)
        retention = MockFileRetentionRepository(assets)
    }

    @Test
    fun upload_session_completes_without_content_uri() = runBlocking {
        val session = uploads.createUploadSession(
            FileUploadRequest(
                purpose = FileAssetPurpose.USER_AVATAR,
                owner = FileAssetOwner.User("u1"),
                originalFilename = "avatar.jpg",
                declaredMimeType = "image/jpeg",
                sizeBytes = 2048,
                requestedVisibility = FileAssetVisibility.PUBLIC
            ),
            createdByUserId = "u1",
            nowEpochMs = 1_000L
        )
        assertTrue(session is AppResult.Success)
        val s = (session as AppResult.Success).data
        assertFalse(s.id.contains("content://"))
        uploads.startUpload(s.id)
        uploads.updateProgress(s.id, 50)
        val done = uploads.completeUpload(s.id, 2_000L)
        assertTrue(done is AppResult.Success)
        assertEquals(FileUploadSessionState.COMPLETED, (done as AppResult.Success).data.state)
        val asset = (assets.getAsset(s.assetId) as AppResult.Success).data
        assertEquals(FileAssetStatus.READY, asset.status)
    }

    @Test
    fun cancel_idempotent_and_legacy_leover_upload_denied() = runBlocking {
        val denied = uploads.createUploadSession(
            FileUploadRequest(
                purpose = FileAssetPurpose.OTHER,
                owner = FileAssetOwner.User("u1"),
                originalFilename = "x.jpg",
                declaredMimeType = "image/jpeg",
                sizeBytes = 10,
                requestedVisibility = FileAssetVisibility.OWNER_ONLY
            ),
            createdByUserId = "u1",
            nowEpochMs = 1L
        )
        assertTrue(denied is AppResult.Failure)

        val ok = uploads.createUploadSession(
            FileUploadRequest(
                purpose = FileAssetPurpose.POST_MEDIA,
                owner = FileAssetOwner.User("u1"),
                resourceRef = FileResourceRef(FileResourceType.POST, "post-1"),
                originalFilename = "p.jpg",
                declaredMimeType = "image/jpeg",
                sizeBytes = 100,
                requestedVisibility = FileAssetVisibility.PUBLIC
            ),
            createdByUserId = "u1",
            nowEpochMs = 1L
        ) as AppResult.Success
        uploads.startUpload(ok.data.id)
        val c1 = uploads.cancelUpload(ok.data.id, 2L) as AppResult.Success
        val c2 = uploads.cancelUpload(ok.data.id, 3L) as AppResult.Success
        assertEquals(FileUploadSessionState.CANCELLED, c1.data.state)
        assertEquals(FileUploadSessionState.CANCELLED, c2.data.state)
    }

    @Test
    fun unlink_keeps_asset_legal_hold_blocks_physical() = runBlocking {
        val created = assets.createDraftAsset(
            FileAssetPurpose.USER_AVATAR,
            FileAssetOwner.User("u1"),
            FileAssetVisibility.PUBLIC,
            "u1",
            1L
        ) as AppResult.Success
        val link = FileAssetLink(
            assetId = created.data.id,
            resource = FileResourceRef(FileResourceType.USER, "u1"),
            relationType = FileRelationType.PRIMARY,
            isPrimary = true,
            createdAtEpochMs = 1L
        )
        assets.linkAsset(created.data.id, link)
        assets.unlinkAsset(created.data.id, link.resource, FileRelationType.PRIMARY)
        assertTrue(assets.getAsset(created.data.id) is AppResult.Success)

        val held = retention.requestLegalHold(created.data.id) as AppResult.Success
        assertTrue(held.data.legalHold)
        val can = retention.canPhysicallyDelete(created.data.id, 10L) as AppResult.Success
        assertFalse(can.data)
    }

    @Test
    fun signed_url_temporary_not_on_asset() = runBlocking {
        val up = uploads.createUploadSession(
            FileUploadRequest(
                purpose = FileAssetPurpose.USER_AVATAR,
                owner = FileAssetOwner.User("u1"),
                originalFilename = "a.jpg",
                declaredMimeType = "image/jpeg",
                sizeBytes = 50,
                requestedVisibility = FileAssetVisibility.PUBLIC
            ),
            "u1",
            1L
        ) as AppResult.Success
        uploads.startUpload(up.data.id)
        uploads.completeUpload(up.data.id, 2L)
        val signed = downloads.requestSignedUrl(
            FileAccessRequest(
                assetId = up.data.assetId,
                actorUserId = "u1",
                purpose = FileAssetPurpose.USER_AVATAR,
                ttlClass = FileSignedTtlClass.PUBLIC_RESOLUTION
            ),
            FileAuthContext(actorUserId = "u1"),
            nowEpochMs = 3L
        ) as AppResult.Success
        assertTrue(signed.data.temporaryUrl.startsWith("https://"))
        val asset = (assets.getAsset(up.data.assetId) as AppResult.Success).data
        // asset model has no signed url field — temporary only in response
        assertEquals(FileAssetStatus.READY, asset.status)
    }

    @Test
    fun access_denies_unknown_lookup() = runBlocking {
        val created = assets.createDraftAsset(
            FileAssetPurpose.USER_AVATAR,
            FileAssetOwner.User("u1"),
            FileAssetVisibility.OWNER_ONLY,
            "u1",
            1L
        ) as AppResult.Success
        val d = access.canRead(
            FileAuthContext(actorUserId = "u1", permissionLookupFailed = true),
            created.data.id
        ) as AppResult.Success
        assertEquals(FileAccessDecision.DENIED_UNKNOWN, d.data)
    }
}
