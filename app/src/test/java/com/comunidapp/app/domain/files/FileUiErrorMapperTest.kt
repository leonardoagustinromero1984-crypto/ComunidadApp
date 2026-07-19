package com.comunidapp.app.domain.files

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
