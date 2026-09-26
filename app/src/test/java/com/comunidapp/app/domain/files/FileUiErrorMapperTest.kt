package com.comunidapp.app.domain.files

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FileUiErrorMapperTest {

    @Test
    fun `missing migration 024 variants have recoverable safe message`() {
        val expected = "El servicio de archivos no está disponible todavía. Intentá más tarde."
        for (technical in listOf(
            "PGRST202 function create_file_upload_session not found",
            "Could not find the function in the schema cache",
            "function public.create_file_upload_session does not exist",
            "MIGRATION_UNAVAILABLE"
        )) {
            assertEquals(expected, FileUiErrorMapper.message(null, technical))
        }
    }

    @Test
    fun `file too large is not media db diagnostic`() {
        val message = FileUiErrorMapper.message(
            AppError(
                kind = AppErrorKind.QUOTA_EXCEEDED,
                userMessage = "quota",
                technicalMessage = "FILE_TOO_LARGE / REGISTER / RPC",
                code = "FILE_TOO_LARGE"
            )
        )
        assertEquals("El video supera el tamaño máximo permitido.", message)
        assertFalse(message.contains("MEDIA-DB", ignoreCase = true))
        assertFalse(message.contains("REGISTER", ignoreCase = true))
    }

    @Test
    fun `daily quota keeps terminal copy without media db`() {
        val message = FileUiErrorMapper.message(
            AppError(
                kind = AppErrorKind.QUOTA_EXCEEDED,
                userMessage = "quota",
                technicalMessage = "QUOTA_EXCEEDED media.upload.bytes.daily",
                code = "QUOTA_EXCEEDED"
            )
        )
        assertEquals("Alcanzaste el límite diario de subidas. Intentá más tarde.", message)
        assertFalse(message.contains("MEDIA-DB", ignoreCase = true))
    }

    @Test
    fun `story quota uses historias copy not generic uploads`() {
        val message = FileUiErrorMapper.message(
            AppError(
                kind = AppErrorKind.RATE_LIMITED,
                userMessage = "limit",
                technicalMessage = "RATE_LIMITED social.story.create",
                code = "RATE_LIMITED"
            )
        )
        assertEquals(FileUiErrorMapper.STORY_DAILY_LIMIT, message)
        assertFalse(message.contains("subidas", ignoreCase = true))
    }

    @Test
    fun `timeout is recoverable and not media db`() {
        val message = FileUiErrorMapper.message(
            AppError(
                kind = AppErrorKind.NETWORK,
                userMessage = "timeout",
                technicalMessage = "TIMEOUT / REGISTER / RPC",
                code = "TIMEOUT"
            )
        )
        assertEquals("La publicación tardó demasiado. Intentá de nuevo.", message)
        assertFalse(message.contains("MEDIA-DB", ignoreCase = true))
        assertFalse(message.contains("REGISTER", ignoreCase = true))
        assertFalse(message.contains("RPC", ignoreCase = true))
    }

    @Test
    fun `technical storage details are never reflected to users`() {
        val message = FileUiErrorMapper.message(
            "NETWORK",
            "bucket=private path=users/secret token=abc SQL select *"
        )
        assertFalse(message.contains("bucket", true))
        assertFalse(message.contains("path", true))
        assertFalse(message.contains("token", true))
        assertFalse(message.contains("sql", true))
    }
}
