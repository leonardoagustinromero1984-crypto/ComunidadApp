package com.comunidapp.app.domain.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class AvatarPhotoProcessor(
    private val contentResolver: ContentResolver,
    private val cacheDir: File
) {
    fun process(state: AvatarPhotoEditorState): File {
        AvatarPhotoTempStore.cleanup(cacheDir)
        val uri = Uri.parse(state.sourceUri)
        val orientation = readOrientation(uri)
        val source = decodeBounded(uri)
            ?: error("PHOTO_DECODE_FAILED")
        val oriented = applyExif(source, orientation)
        if (oriented != source && !source.isRecycled) source.recycle()
        val crop = AvatarPhotoCropMath.sourceCropRect(
            sourceWidth = oriented.width,
            sourceHeight = oriented.height,
            offsetX = state.offsetX,
            offsetY = state.offsetY,
            scale = state.scale,
            viewportPx = state.viewportPx
        )
        val square = Bitmap.createBitmap(oriented, crop.left, crop.top, crop.size, crop.size)
        if (square != oriented && !oriented.isRecycled) oriented.recycle()
        val outputSize = AvatarPhotoCropMath.outputSide()
        val output = if (square.width == outputSize && square.height == outputSize) {
            square
        } else {
            Bitmap.createScaledBitmap(square, outputSize, outputSize, true).also {
                if (it != square && !square.isRecycled) square.recycle()
            }
        }
        val file = AvatarPhotoTempStore.newFile(cacheDir)
        var quality = MediaIngestionPolicy.JPEG_QUALITY
        while (quality >= MediaIngestionPolicy.JPEG_QUALITY_FLOOR) {
            FileOutputStream(file).use { stream ->
                if (!output.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                    error("PHOTO_ENCODE_FAILED")
                }
            }
            if (file.length() <= MediaIngestionPolicy.AVATAR_MASTER_MAX_BYTES) break
            quality -= 4
        }
        if (!output.isRecycled) output.recycle()
        val encoded = file.readBytes()
        if (AvatarPhotoJpeg.containsGpsExif(encoded)) {
            file.delete()
            error("PHOTO_GPS_METADATA_PRESENT")
        }
        return file
    }

    private fun readOrientation(uri: Uri): Int = runCatching {
        open(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    private fun decodeBounded(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val (orientedW, orientedH) = AvatarPhotoCropMath.orientedSize(
            bounds.outWidth,
            bounds.outHeight,
            readOrientation(uri)
        )
        var sample = 1
        val maxSide = AvatarPhotoEditorState.DECODE_MAX_SIDE_PX
        while (orientedW / sample > maxSide || orientedH / sample > maxSide) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return open(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun applyExif(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun open(uri: Uri) = when (uri.scheme) {
        "file" -> uri.path?.let { java.io.File(it).inputStream() }
        else -> contentResolver.openInputStream(uri)
    }
}
