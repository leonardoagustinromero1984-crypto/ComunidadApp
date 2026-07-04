package com.comunidapp.app.data.remote.storage

import android.net.Uri
import com.comunidapp.app.BuildConfig
import com.comunidapp.app.LeoverApplication
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.storage.storage

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

            supabase.storage.from(BUCKET).upload(path, bytes) {
                upsert = true
            }

            val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
            Result.success("$baseUrl/storage/v1/object/public/$BUCKET/$path")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
