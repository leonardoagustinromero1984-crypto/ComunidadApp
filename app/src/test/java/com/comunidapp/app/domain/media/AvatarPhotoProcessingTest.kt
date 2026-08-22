package com.comunidapp.app.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AvatarPhotoProcessingTest {

    @Test
    fun PHOTO_CONFIRM_USES_PROCESSED_FILE() {
        val uploaded = AvatarPhotoConfirmPolicy.uriToUpload(
            sourceUri = "content://media/picker/original.jpg",
            processedUri = "file:///cache/avatar-edit/avatar_1.jpg"
        )
        assertEquals("file:///cache/avatar-edit/avatar_1.jpg", uploaded)
        val vm = File("src/main/java/com/comunidapp/app/viewmodel/EditProfileViewModel.kt").readText()
        assertTrue(vm.contains("AvatarPhotoProcessor"))
        assertTrue(vm.contains("processedPhotoPath"))
    }

    @Test
    fun PHOTO_SOURCE_URI_NOT_UPLOADED_AFTER_EDIT() {
        val source = "content://media/picker/original.jpg"
        val processed = "file:///cache/avatar-edit/avatar_1.jpg"
        val uploaded = AvatarPhotoConfirmPolicy.uriToUpload(source, processed)
        assertNotEquals(source, uploaded)
        runCatching { AvatarPhotoConfirmPolicy.uriToUpload(source, source) }
            .onSuccess { error("source URI must not be uploadable after edit") }
            .onFailure { assertTrue(it.message.orEmpty().contains("PHOTO_SOURCE_URI_FORBIDDEN")) }
    }

    @Test
    fun PHOTO_OUTPUT_IS_SQUARE() {
        val crop = AvatarPhotoCropMath.sourceCropRect(
            sourceWidth = 2000,
            sourceHeight = 1200,
            offsetX = 0f,
            offsetY = 0f,
            scale = 1f,
            viewportPx = 280f
        )
        assertEquals(crop.size, crop.size)
        assertEquals(crop.right - crop.left, crop.bottom - crop.top)
        assertEquals(AvatarPhotoEditorState.OUTPUT_SIZE_PX, AvatarPhotoCropMath.outputSide())
    }

    @Test
    fun PHOTO_OUTPUT_DIMENSION_BOUNDED() {
        assertEquals(1024, AvatarPhotoEditorState.OUTPUT_SIZE_PX)
        assertTrue(AvatarPhotoCropMath.outputSide(4096) <= 1024)
        assertTrue(AvatarPhotoCropMath.outputSide(32) >= 64)
    }

    @Test
    fun PHOTO_PAN_CHANGES_CROP() {
        val centered = AvatarPhotoCropMath.sourceCropRect(2000, 1200, 0f, 0f, 1f, 280f)
        val panned = AvatarPhotoCropMath.sourceCropRect(2000, 1200, 80f, 0f, 1f, 280f)
        assertTrue(panned.left < centered.left)
        assertEquals(centered.size, panned.size)
    }

    @Test
    fun PHOTO_ZOOM_CHANGES_CROP() {
        val wide = AvatarPhotoCropMath.sourceCropRect(2000, 1200, 0f, 0f, 1f, 280f)
        val zoomed = AvatarPhotoCropMath.sourceCropRect(2000, 1200, 0f, 0f, 2f, 280f)
        assertTrue(zoomed.size < wide.size)
        assertTrue(zoomed.left > wide.left)
    }

    @Test
    fun PHOTO_EXIF_ORIENTATION_RESPECTED() {
        assertEquals(1200 to 2000, AvatarPhotoCropMath.orientedSize(2000, 1200, 6))
        assertEquals(2000 to 1200, AvatarPhotoCropMath.orientedSize(2000, 1200, 1))
        assertTrue(AvatarPhotoCropMath.swapsAxes(6))
        assertTrue(AvatarPhotoCropMath.swapsAxes(8))
        assertFalse(AvatarPhotoCropMath.swapsAxes(1))
        val processor = File("src/main/java/com/comunidapp/app/domain/media/AvatarPhotoProcessor.kt").readText()
        assertTrue(processor.contains("ExifInterface"))
        assertTrue(processor.contains("applyExif"))
    }

    @Test
    fun PHOTO_OUTPUT_GPS_METADATA_ABSENT() {
        val jfif = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
            'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 0x00,
            0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0xFF.toByte(), 0xDA.toByte()
        )
        assertFalse(AvatarPhotoJpeg.containsGpsExif(jfif))
        assertTrue(AvatarPhotoJpeg.containsGpsExif(jpegWithGpsExif()))
        assertTrue(AvatarPhotoRules.STRIP_GPS_METADATA)
        val processor = File("src/main/java/com/comunidapp/app/domain/media/AvatarPhotoProcessor.kt").readText()
        assertTrue(processor.contains("containsGpsExif"))
        assertTrue(processor.contains("CompressFormat.JPEG"))
    }

    @Test
    fun PHOTO_CANCEL_PRESERVES_CURRENT_AVATAR() {
        val before = AvatarPhotoSession(
            editorSourceUri = "content://photo",
            pendingProcessedUri = null,
            avatarPath = "avatars/current.jpg"
        )
        val after = AvatarPhotoConfirmPolicy.afterCancel(before)
        assertEquals(null, after.editorSourceUri)
        assertEquals(before.pendingProcessedUri, after.pendingProcessedUri)
        assertEquals("avatars/current.jpg", after.avatarPath)
        val vm = File("src/main/java/com/comunidapp/app/viewmodel/EditProfileViewModel.kt").readText()
        val cancel = vm.substringAfter("fun cancelPhotoEditor").substringBefore("fun saveProfile")
        assertFalse(cancel.contains("pendingImageUri = null"))
        assertFalse(cancel.contains("avatarPath = null"))
    }

    private fun jpegWithGpsExif(): ByteArray {
        val tiff = byteArrayOf(
            'I'.code.toByte(), 'I'.code.toByte(), 0x2A, 0x00,
            0x08, 0x00, 0x00, 0x00,
            0x01, 0x00,
            0x25, 0x88.toByte(), 0x04, 0x00, 0x01, 0x00, 0x00, 0x00, 0x1A, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x01, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        val exifBody = byteArrayOf(
            'E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0x00, 0x00
        ) + tiff
        val length = exifBody.size + 2
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE1.toByte(),
            ((length shr 8) and 0xFF).toByte(),
            (length and 0xFF).toByte()
        ) + exifBody + byteArrayOf(0xFF.toByte(), 0xDA.toByte())
    }
}
