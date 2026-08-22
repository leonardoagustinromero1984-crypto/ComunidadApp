package com.comunidapp.app.domain.media

/**
 * Inspects JPEG bytes for an EXIF GPS IFD. A newly encoded avatar must not
 * carry source GPS. Bitmap.compress does not copy EXIF; this verifies it.
 */
object AvatarPhotoJpeg {

    fun containsGpsExif(jpeg: ByteArray): Boolean {
        if (jpeg.size < 4 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) return false
        var i = 2
        while (i + 4 < jpeg.size) {
            if (jpeg[i] != 0xFF.toByte()) return false
            val marker = jpeg[i + 1].toInt() and 0xFF
            if (marker == 0xDA || marker == 0xD9) return false
            if (i + 4 >= jpeg.size) return false
            val length = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            if (length < 2 || i + 2 + length > jpeg.size) return false
            if (marker == 0xE1) {
                val start = i + 4
                val end = i + 2 + length
                if (end - start >= 6 && isExif(jpeg, start) && segmentHasGps(jpeg, start, end)) {
                    return true
                }
            }
            i += 2 + length
        }
        return false
    }

    private fun isExif(bytes: ByteArray, start: Int): Boolean {
        if (start + 6 > bytes.size) return false
        return bytes[start] == 'E'.code.toByte() &&
            bytes[start + 1] == 'x'.code.toByte() &&
            bytes[start + 2] == 'i'.code.toByte() &&
            bytes[start + 3] == 'f'.code.toByte() &&
            bytes[start + 4] == 0.toByte() &&
            bytes[start + 5] == 0.toByte()
    }

    private fun segmentHasGps(bytes: ByteArray, start: Int, end: Int): Boolean {
        val tiff = start + 6
        if (tiff + 8 > end) return false
        val little = bytes[tiff] == 'I'.code.toByte() && bytes[tiff + 1] == 'I'.code.toByte()
        val ifd0 = tiff + readInt(bytes, tiff + 4, little)
        val gpsOffset = findGpsIfdOffset(bytes, ifd0, end, tiff, little) ?: return false
        val gpsIfd = tiff + gpsOffset
        if (gpsIfd + 2 > end) return false
        val count = readUShort(bytes, gpsIfd, little)
        return count > 0
    }

    private fun findGpsIfdOffset(
        bytes: ByteArray,
        ifd: Int,
        end: Int,
        tiff: Int,
        little: Boolean
    ): Int? {
        if (ifd < tiff || ifd + 2 > end) return null
        val count = readUShort(bytes, ifd, little)
        var entry = ifd + 2
        repeat(count) {
            if (entry + 12 > end) return null
            val tag = readUShort(bytes, entry, little)
            if (tag == 0x8825) {
                return readInt(bytes, entry + 8, little)
            }
            entry += 12
        }
        return null
    }

    private fun readUShort(bytes: ByteArray, offset: Int, little: Boolean): Int {
        val a = bytes[offset].toInt() and 0xFF
        val b = bytes[offset + 1].toInt() and 0xFF
        return if (little) a or (b shl 8) else (a shl 8) or b
    }

    private fun readInt(bytes: ByteArray, offset: Int, little: Boolean): Int {
        val a = bytes[offset].toInt() and 0xFF
        val b = bytes[offset + 1].toInt() and 0xFF
        val c = bytes[offset + 2].toInt() and 0xFF
        val d = bytes[offset + 3].toInt() and 0xFF
        return if (little) {
            a or (b shl 8) or (c shl 16) or (d shl 24)
        } else {
            (a shl 24) or (b shl 16) or (c shl 8) or d
        }
    }
}
