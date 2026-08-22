package com.comunidapp.app.ui.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.comunidapp.app.R
import com.comunidapp.app.domain.media.MediaDiagnostic
import com.comunidapp.app.domain.media.MediaIngestionPolicy
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream

enum class LeoVerAvatarCropKind {
    PERSON,
    PET,
    ORGANIZATION
}

@Composable
fun rememberLeoVerAvatarCropLauncher(
    kind: LeoVerAvatarCropKind,
    onCropped: (Uri) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit
): (Uri) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val output = result.data?.let { UCrop.getOutput(it) }
                if (output != null) onCropped(output) else onError(MediaDiagnostic.CROP)
            }
            UCrop.RESULT_ERROR -> onError(MediaDiagnostic.CROP)
            else -> onCancel()
        }
    }
    return remember(kind, launcher) {
        { source ->
            val intent = LeoVerUCrop.intentFor(context, source, kind)
            if (intent == null) onError(MediaDiagnostic.URI) else launcher.launch(intent)
        }
    }
}

object LeoVerUCrop {
    fun intentFor(context: Context, source: Uri, kind: LeoVerAvatarCropKind): Intent? {
        val flattened = flattenOrientedJpeg(context, source) ?: return null
        val dest = File(context.cacheDir, "leover_ucrop_out_${System.nanoTime()}.jpg")
        val sourceUri = Uri.fromFile(flattened)
        val destUri = Uri.fromFile(dest)
        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(92)
            setHideBottomControls(false)
            setFreeStyleCropEnabled(false)
            setCircleDimmedLayer(true)
            setShowCropFrame(true)
            setShowCropGrid(true)
            setToolbarTitle("Ajustar foto")
            setToolbarColor(ContextCompat.getColor(context, R.color.brand_background))
            setStatusBarColor(ContextCompat.getColor(context, R.color.brand_background))
            setToolbarWidgetColor(ContextCompat.getColor(context, R.color.brand_text))
            setActiveControlsWidgetColor(ContextCompat.getColor(context, R.color.brand_orange))
        }
        val edge = when (kind) {
            LeoVerAvatarCropKind.PERSON -> MediaIngestionPolicy.AVATAR_MASTER_EDGE_PX
            LeoVerAvatarCropKind.PET -> MediaIngestionPolicy.PET_MASTER_EDGE_PX
            LeoVerAvatarCropKind.ORGANIZATION -> MediaIngestionPolicy.ORGANIZATION_MASTER_EDGE_PX
        }
        val intent = UCrop.of(sourceUri, destUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(edge, edge)
            .withOptions(options)
            .getIntent(context)
        intent.setClass(context, LeoVerUCropActivity::class.java)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        return intent
    }

    /**
     * Decode picker URI, bake EXIF into pixels, write a real JPEG.
     * uCrop then crops what the user sees; later encode must not re-rotate.
     */
    fun flattenOrientedJpeg(context: Context, source: Uri): File? = runCatching {
        val resolver = context.contentResolver
        val orientation = resolver.openInputStream(source)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val maxEdge = 4096
        var sample = 1
        val w = bounds.outWidth.coerceAtLeast(1)
        val h = bounds.outHeight.coerceAtLeast(1)
        while (w / sample > maxEdge * 2 || h / sample > maxEdge * 2) sample *= 2
        val decoded = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val oriented = applyExif(decoded, orientation)
        if (oriented != decoded && !decoded.isRecycled) decoded.recycle()
        val out = File(context.cacheDir, "leover_ucrop_src_${System.nanoTime()}.jpg")
        FileOutputStream(out).use { stream ->
            if (!oriented.compress(Bitmap.CompressFormat.JPEG, 92, stream)) {
                if (!oriented.isRecycled) oriented.recycle()
                return null
            }
        }
        if (!oriented.isRecycled) oriented.recycle()
        if (!out.exists() || out.length() <= 0L) return null
        out
    }.getOrNull()

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
}
