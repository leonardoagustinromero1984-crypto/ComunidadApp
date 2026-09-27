package com.comunidapp.app.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One cached JPEG frame per media URL. Extraction runs on IO — never on composition.
 */
object VideoPreviewFrameCache {
    private val memory = object : LruCache<String, File>(32) {}

    suspend fun frameFile(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        val key = Integer.toHexString(url.hashCode())
        memory.get(key)?.takeIf { it.exists() && it.length() > 0L }?.let { return@withContext it }
        val file = File(context.cacheDir, "leover_vthumb_$key.jpg")
        if (file.exists() && file.length() > 0L) {
            memory.put(key, file)
            return@withContext file
        }
        val retriever = MediaMetadataRetriever()
        try {
            if (url.startsWith("content:", ignoreCase = true) ||
                url.startsWith("file:", ignoreCase = true)
            ) {
                retriever.setDataSource(context, Uri.parse(url))
            } else {
                retriever.setDataSource(url, HashMap())
            }
            val bitmap: Bitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: return@withContext null
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 72, out)
            }
            if (!bitmap.isRecycled) bitmap.recycle()
            memory.put(key, file)
            file
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}

@Composable
fun VideoPreviewFrame(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    var file by remember(url) { mutableStateOf<File?>(null) }
    LaunchedEffect(url) {
        file = VideoPreviewFrameCache.frameFile(context, url)
    }
    if (file != null) {
        AsyncImage(
            model = file,
            contentDescription = contentDescription,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier.fillMaxSize().background(Color(0xFF1A1A1A)))
    }
}
