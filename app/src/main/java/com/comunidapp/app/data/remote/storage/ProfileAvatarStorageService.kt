package com.comunidapp.app.data.remote.storage

import android.net.Uri
import com.comunidapp.app.LeoverApplication
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlin.time.Duration.Companion.seconds

/**
 * Avatares en bucket privado `profile-avatars` con ownership por path.
 * Devuelve path (no URL permanente); firmadas vía [createSignedUrl].
 */
class ProfileAvatarStorageService {

    companion object {
        const val BUCKET = "profile-avatars"
    }

    suspend fun uploadAvatar(userId: String, uri: Uri, filename: String = "avatar.jpg"): Result<String> {
        val path = StoragePaths.userAvatar(userId, filename)
        if (!path.startsWith("users/$userId/avatar/") || path.contains("..")) {
            return Result.failure(IllegalArgumentException("AVATAR_PATH_INVALID"))
        }
        return try {
            val bytes = LeoverApplication.instance.contentResolver
                .openInputStream(uri)
                ?.use { it.readBytes() }
                ?: return Result.failure(IllegalArgumentException("No se pudo leer la imagen"))

            val bucket = supabase.storage.from(BUCKET)
            runCatching { bucket.delete(path) }
            bucket.upload(path, bytes) {
                upsert = true
                contentType = ContentType.Image.JPEG
            }
            Result.success(path)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createSignedUrl(path: String, expiresSeconds: Long = 3600): Result<String> {
        return try {
            val url = supabase.storage.from(BUCKET)
                .createSignedUrl(path = path, expiresIn = expiresSeconds.seconds)
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAvatar(path: String): Result<Unit> {
        return try {
            supabase.storage.from(BUCKET).delete(path)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
