package com.comunidapp.app.data.remote.storage

import android.net.Uri
import com.comunidapp.app.BuildConfig
import com.comunidapp.app.LeoverApplication
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType

class SupabaseStorageService : ImageStorageService {

    companion object {
        const val BUCKET = "leover"
    }

    override suspend fun uploadImage(path: String, uri: Uri): Result<String> {
        return try {
            val bytes = LeoverApplication.instance.contentResolver
                .openInputStream(uri)
                ?.use { it.readBytes() }
                ?: return Result.failure(IllegalArgumentException("No se pudo leer la imagen"))

            val cleanPath = path.substringBefore('?')
            val bucket = supabase.storage.from(BUCKET)

            // Reemplazar archivo existente (upsert puede fallar según políticas RLS del bucket).
            runCatching { bucket.delete(cleanPath) }

            bucket.upload(cleanPath, bytes) {
                upsert = true
                contentType = ContentType.Image.JPEG
            }

            Result.success(publicUrl(cleanPath))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun publicUrl(path: String): String {
        val version = System.currentTimeMillis()
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        return "$baseUrl/storage/v1/object/public/$BUCKET/$path?v=$version"
    }
}
