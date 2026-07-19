package com.comunidapp.app.data.remote.storage

import android.net.Uri
import com.comunidapp.app.LeoverApplication
import com.comunidapp.app.core.logging.AppLog
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlin.time.Duration.Companion.seconds

/**
 * Media institucional en bucket privado `organization-media`.
 * Persiste paths (no URLs eternas); UI usa URLs firmadas temporales.
 */
class OrganizationMediaStorageService {

    companion object {
        const val BUCKET = "organization-media"
        private val PATH_PATTERN = Regex(
            "^organizations/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/(logo|cover)/[^/]+$"
        )
    }

    fun logoPath(organizationId: String, filename: String = "logo.jpg"): String =
        StoragePaths.organizationLogo(organizationId, filename)

    fun coverPath(organizationId: String, filename: String = "cover.jpg"): String =
        StoragePaths.organizationCover(organizationId, filename)

    fun isValidPath(path: String, organizationId: String): Boolean {
        if (path.contains("..")) return false
        if (!PATH_PATTERN.matches(path)) return false
        return path.startsWith("organizations/$organizationId/")
    }

    suspend fun uploadLogo(
        organizationId: String,
        uri: Uri,
        filename: String = "logo.jpg"
    ): Result<String> = upload(organizationId, uri, logoPath(organizationId, filename))

    suspend fun uploadCover(
        organizationId: String,
        uri: Uri,
        filename: String = "cover.jpg"
    ): Result<String> = upload(organizationId, uri, coverPath(organizationId, filename))

    private suspend fun upload(
        organizationId: String,
        uri: Uri,
        path: String
    ): Result<String> {
        if (!isValidPath(path, organizationId)) {
            return Result.failure(IllegalArgumentException("ORG_MEDIA_PATH_INVALID"))
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
            AppLog.warning("OrgMediaStorage", "upload failed", e)
            Result.failure(e)
        }
    }

    suspend fun createSignedUrl(path: String, expiresSeconds: Long = 3600): Result<String> {
        return try {
            if (path.contains("..") || !PATH_PATTERN.matches(path)) {
                return Result.failure(IllegalArgumentException("ORG_MEDIA_PATH_INVALID"))
            }
            val url = supabase.storage.from(BUCKET)
                .createSignedUrl(path = path, expiresIn = expiresSeconds.seconds)
            Result.success(url)
        } catch (e: Exception) {
            AppLog.warning("OrgMediaStorage", "signed url failed", e)
            Result.failure(e)
        }
    }

    suspend fun delete(path: String): Result<Unit> {
        return try {
            if (path.contains("..") || !PATH_PATTERN.matches(path)) {
                return Result.failure(IllegalArgumentException("ORG_MEDIA_PATH_INVALID"))
            }
            supabase.storage.from(BUCKET).delete(path)
            Result.success(Unit)
        } catch (e: Exception) {
            AppLog.warning("OrgMediaStorage", "delete failed", e)
            Result.failure(e)
        }
    }
}
