package com.comunidapp.app.domain.lostfound

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.files.SightingMediaUpload
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileLogicalBucket
import com.comunidapp.app.domain.files.FileResourceType
import com.comunidapp.app.domain.files.FileUploadRequest
import com.comunidapp.app.domain.files.FileUploadSession
import com.comunidapp.app.domain.files.FileUploadSessionState
import com.comunidapp.app.domain.files.PreparedFileUpload
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SightingPhotoPublisherTest {

    @Test
    fun blank_photo_is_absent() = runTest {
        val publisher = SightingPhotoPublisher { _, _, _ ->
            error("upload must not run")
        }
        val resolved = publisher.resolve("  ", "actor", "case-1")
        assertEquals(null, (resolved as AppResult.Success).data)
    }

    @Test
    fun canonical_refs_pass_through() = runTest {
        val publisher = SightingPhotoPublisher { _, _, _ -> error("upload must not run") }
        val m05 = publisher.resolve(" m05://qa/foto ", "actor", "case-1") as AppResult.Success
        val asset = publisher.resolve("file_asset:abc", "actor", "case-1") as AppResult.Success
        assertEquals("m05://qa/foto", m05.data)
        assertEquals("file_asset:abc", asset.data)
    }

    @Test
    fun content_uri_is_uploaded_and_returned_as_canonical_ref() = runTest {
        var seen: String? = null
        val publisher = SightingPhotoPublisher { localUri, actor, caseId ->
            seen = "$localUri|$actor|$caseId"
            AppResult.Success("file_asset:uploaded")
        }
        val resolved = publisher.resolve(
            "content://media/external/images/1",
            "actor-1",
            "case-1"
        ) as AppResult.Success
        assertEquals("content://media/external/images/1|actor-1|case-1", seen)
        assertEquals("file_asset:uploaded", resolved.data)
        assertTrue(!resolved.data!!.startsWith("content://"))
    }

    @Test
    fun upload_that_returns_content_uri_is_rejected() = runTest {
        val publisher = SightingPhotoPublisher { _, _, _ ->
            AppResult.Success("content://still-local")
        }
        val resolved = publisher.resolve("content://media/1", "actor", "case-1")
        assertTrue(resolved is AppResult.Failure)
    }

    @Test
    fun upload_failure_is_returned() = runTest {
        val publisher = SightingPhotoPublisher { _, _, _ ->
            AppResult.Failure(
                AppError(AppErrorKind.NETWORK, "Sin conexión", "NETWORK", code = "NETWORK")
            )
        }
        val resolved = publisher.resolve("file:///tmp/foto.jpg", "actor", "case-1")
        assertTrue(resolved is AppResult.Failure)
    }

    @Test
    fun invalid_uri_does_not_upload() = runTest {
        val publisher = SightingPhotoPublisher { _, _, _ -> error("upload must not run") }
        val resolved = publisher.resolve("https://example.invalid/foto.jpg", "actor", "case-1")
        assertTrue(resolved is AppResult.Failure)
    }

    @Test
    fun uploader_receives_local_content_and_prefixes_asset_id() = runTest {
        var uri: String? = null
        var request: FileUploadRequest? = null
        val upload = SightingMediaUpload { localUri, incoming, _ ->
            uri = localUri
            request = incoming
            AppResult.Success(
                PreparedFileUpload(
                    session = FileUploadSession(
                        id = "session",
                        assetId = "asset-1",
                        versionId = "version-1",
                        state = FileUploadSessionState.COMPLETED,
                        createdAtEpochMs = 1L
                    ),
                    physicalBucket = "public",
                    storagePath = "lost_found/case-1/asset-1/sighting.jpg",
                    assetId = "asset-1",
                    versionId = "version-1",
                    logicalBucket = FileLogicalBucket.PUBLIC_MEDIA
                )
            )
        }
        val ref = upload.upload("content://media/picker/9", "actor-1", "case-1") as AppResult.Success
        val seenRequest = checkNotNull(request)
        assertEquals("content://media/picker/9", uri)
        assertEquals(FileAssetPurpose.LOST_FOUND_MEDIA, seenRequest.purpose)
        assertEquals(FileResourceType.LOST_FOUND_CASE, seenRequest.resourceRef?.type)
        assertEquals("case-1", seenRequest.resourceRef?.resourceId)
        assertEquals("file_asset:asset-1", ref.data)
        assertNull(SightingPhotoPublisher.canonicalOrNull("content://media/picker/9"))
    }
}
