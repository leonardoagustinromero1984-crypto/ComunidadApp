package com.comunidapp.shared.poc.m08.platform

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.comunidapp.shared.poc.m08.model.FileRef
import com.comunidapp.shared.poc.m08.model.ImagePickResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Real Android Photo Picker → FileRef mapping.
 * Uri / ContentResolver stay in androidMain only.
 */
class AndroidImagePicker(
    private val contentResolver: ContentResolver,
    private val holder: PendingUriHolder,
    private val launch: (PickVisualMediaRequest) -> Unit
) : ImagePicker {

    private val mutex = Mutex()

    override suspend fun pickImage(): ImagePickResult = mutex.withLock {
        val deferred = CompletableDeferred<Uri?>()
        holder.deferred = deferred
        try {
            launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
            val uri = deferred.await()
            if (uri == null) return ImagePickResult.Cancelled
            return mapUriToFileRef(contentResolver, uri)
        } catch (t: Throwable) {
            return ImagePickResult.Failure(t.message ?: "PICKER_FAILURE")
        } finally {
            holder.deferred = null
        }
    }
}

class PendingUriHolder {
    var deferred: CompletableDeferred<Uri?>? = null
}

fun mapUriToFileRef(contentResolver: ContentResolver, uri: Uri): ImagePickResult {
    return try {
        var name: String? = null
        var size: Long? = null
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        val resolvedName = name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: return ImagePickResult.Failure("FILENAME_REQUIRED")
        val resolvedSize = size?.takeIf { it > 0L }
            ?: contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }?.takeIf { it > 0L }
            ?: return ImagePickResult.Failure("SIZE_INVALID")
        val mime = contentResolver.getType(uri)
        ImagePickResult.Success(
            FileRef(
                name = resolvedName,
                mimeType = mime,
                sizeBytes = resolvedSize,
                platformIdentifier = uri.toString()
            )
        )
    } catch (t: Throwable) {
        ImagePickResult.Failure(t.message ?: "METADATA_READ_FAILED")
    }
}

@Composable
fun rememberAndroidImagePicker(): ImagePicker {
    val context = LocalContext.current
    val holder = remember { PendingUriHolder() }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        holder.deferred?.complete(uri)
    }
    val launchUpdated = rememberUpdatedState(launcher)
    return remember(context.applicationContext, holder) {
        AndroidImagePicker(
            contentResolver = context.applicationContext.contentResolver,
            holder = holder,
            launch = { request -> launchUpdated.value.launch(request) }
        )
    }
}
