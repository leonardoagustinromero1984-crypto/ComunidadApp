package com.comunidapp.shared.profile

import com.comunidapp.shared.media.FileContent
import com.comunidapp.shared.media.FileContentReader
import com.comunidapp.shared.media.M05LostFoundMediaRules
import com.comunidapp.shared.poc.m08.model.FileRef
import com.comunidapp.shared.ui.ErrorSanitizer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType

/**
 * Avatar write legacy M02 — bucket `profile-avatars`, path `users/{uid}/avatar/{file}`.
 * Compatible con validación de `update_my_profile` (no usa path M05 `avatars/{assetId}`).
 */
internal interface ProfileAvatarUploadGateway {
    suspend fun upload(userId: String, file: FileRef): ProfileAvatarUploadResult
}

internal class SupabaseProfileAvatarUploadGateway(
    private val client: SupabaseClient,
    private val fileContentReader: FileContentReader
) : ProfileAvatarUploadGateway {

    override suspend fun upload(userId: String, file: FileRef): ProfileAvatarUploadResult {
        if (userId.isBlank()) {
            return ProfileAvatarUploadResult.ValidationError("Sesión inválida.")
        }
        val content = fileContentReader.read(file).getOrElse {
            return ProfileAvatarUploadResult.BackendError(ErrorSanitizer.sanitize(it))
        }
        M05LostFoundMediaRules.validate(content).exceptionOrNull()?.let {
            return ProfileAvatarUploadResult.ValidationError(ErrorSanitizer.sanitize(it))
        }
        if (content.sizeBytes > MAX_AVATAR_BYTES) {
            return ProfileAvatarUploadResult.ValidationError("La foto supera el tamaño permitido.")
        }
        val safeName = M05LostFoundMediaRules.sanitizeFilename(content.name).getOrElse {
            return ProfileAvatarUploadResult.ValidationError(ErrorSanitizer.sanitize(it))
        }
        val path = "users/$userId/avatar/$safeName"
        return try {
            val bucket = client.storage.from(BUCKET)
            runCatching { bucket.delete(path) }
            bucket.upload(path, content.bytes) {
                upsert = true
                contentType = ContentType.parse(content.mimeType.trim().lowercase())
            }
            ProfileAvatarUploadResult.Success(path)
        } catch (t: Throwable) {
            ProfileAvatarUploadResult.BackendError(ErrorSanitizer.sanitize(t))
        }
    }

    companion object {
        const val BUCKET = "profile-avatars"
        const val MAX_AVATAR_BYTES = 5_242_880L
    }
}

internal class FakeProfileAvatarUploadGateway(
    var result: ProfileAvatarUploadResult = ProfileAvatarUploadResult.Success("users/u1/avatar/a.jpg"),
    var calls: Int = 0
) : ProfileAvatarUploadGateway {
    override suspend fun upload(userId: String, file: FileRef): ProfileAvatarUploadResult {
        calls++
        return result
    }
}
