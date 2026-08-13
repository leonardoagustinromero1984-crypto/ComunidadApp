package com.comunidapp.shared.media

import com.comunidapp.shared.auth.InMemorySecureSessionStorage
import com.comunidapp.shared.poc.m08.model.FileRef
import com.comunidapp.shared.remote.SharedRemoteRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class M05MediaUploadVerticalTest {

    private fun jpegBytes(size: Int = 64) = ByteArray(size) { 0x2A }

    private fun validContent(
        name: String = "foto.jpg",
        mime: String = "image/jpeg",
        size: Int = 64
    ) = FileContent(jpegBytes(size), name, mime, size.toLong())

    @Test
    fun file_ref_valid_shape() {
        val ref = FileRef("a.jpg", "image/jpeg", 10, "content://x")
        assertEquals("a.jpg", ref.name)
        assertTrue(ref.isImage)
    }

    @Test
    fun file_empty_rejected() {
        val result = M05LostFoundMediaRules.validate(
            FileContent(ByteArray(0), "a.jpg", "image/jpeg", 0)
        )
        assertTrue(result.isFailure)
        assertEquals("MEDIA_FILE_EMPTY", result.exceptionOrNull()?.message)
    }

    @Test
    fun mime_valid() {
        assertTrue(M05LostFoundMediaRules.validate(validContent(mime = "image/png")).isSuccess)
        assertTrue(M05LostFoundMediaRules.validate(validContent(mime = "image/webp")).isSuccess)
    }

    @Test
    fun mime_invalid() {
        val result = M05LostFoundMediaRules.validate(validContent(mime = "image/heic"))
        assertTrue(result.isFailure)
        assertEquals("MEDIA_MIME_REJECTED", result.exceptionOrNull()?.message)
    }

    @Test
    fun size_exceeded() {
        val result = M05LostFoundMediaRules.validate(
            FileContent(
                bytes = ByteArray(8),
                name = "big.jpg",
                mimeType = "image/jpeg",
                sizeBytes = M05LostFoundMediaRules.MAX_BYTES + 1
            )
        )
        assertTrue(result.isFailure)
        assertEquals("MEDIA_FILE_TOO_LARGE", result.exceptionOrNull()?.message)
    }

    @Test
    fun filename_sanitize() {
        val ok = M05LostFoundMediaRules.sanitizeFilename("Mi Foto (1).JPG")
        assertTrue(ok.isSuccess)
        assertEquals("Mi_Foto__1_.JPG", ok.getOrNull())
        assertTrue(M05LostFoundMediaRules.sanitizeFilename("../evil.jpg").isFailure)
        assertTrue(M05LostFoundMediaRules.sanitizeFilename("x.exe").isFailure)
    }

    @Test
    fun fake_m05_upload_success() = runTest {
        val gw = FakeM05MediaUploadGateway(succeedWithAssetId = "asset-1")
        val result = gw.uploadLostFoundMedia(
            M05MediaUploadRequest("case-1", "user-1", FileRef("a.jpg", "image/jpeg", 10, "id"))
        )
        assertEquals("asset-1", result.getOrNull())
        assertEquals(1, gw.calls)
    }

    @Test
    fun fake_m05_upload_fail() = runTest {
        val gw = FakeM05MediaUploadGateway(
            succeedWithAssetId = null,
            error = IllegalStateException("MEDIA_UPLOAD_FAILED")
        )
        assertTrue(
            gw.uploadLostFoundMedia(
                M05MediaUploadRequest("c", "u", FileRef("a.jpg", "image/jpeg", 10, "id"))
            ).isFailure
        )
    }

    @Test
    fun media_error_sanitized_no_token_leak() {
        val msg = mapMediaThrowable(IllegalStateException("403 RLS JWT eyJhbGciOi denied"))
        assertFalse(msg.contains("eyJ"))
        assertFalse(msg.contains("RLS"))
        assertTrue(msg.contains("permiso", ignoreCase = true))
    }

    @Test
    fun purpose_entity_constants() {
        assertEquals("LOST_FOUND_MEDIA", M05LostFoundMediaRules.PURPOSE)
        assertEquals("LOST_FOUND_CASE", M05LostFoundMediaRules.RESOURCE_TYPE)
        assertEquals(8_388_608L, M05LostFoundMediaRules.MAX_BYTES)
    }

    @Test
    fun unconfigured_runtime_still_single_modes() {
        val runtime = SharedRemoteRuntime.create(null, InMemorySecureSessionStorage())
        assertEquals(
            com.comunidapp.shared.lostfound.LostFoundDataMode.REAL_REMOTE,
            runtime.lostFoundRepository.dataMode
        )
    }

    @Test
    fun common_main_media_rules_no_platform_types() {
        // Smoke: reglas viven en commonMain y no requieren platform APIs.
        assertTrue(M05LostFoundMediaRules.ALLOWED_MIME.contains("image/jpeg"))
    }
}
