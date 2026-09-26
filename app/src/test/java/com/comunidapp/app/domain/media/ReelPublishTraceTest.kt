package com.comunidapp.app.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelPublishTraceTest {

    @Test
    fun compactOmitsSecretsAndKeepsStages() {
        ReelPublishTrace.begin()
        ReelPublishTrace.mark(
            ReelPublishTrace.Stage.URI_READY,
            mime = "video/mp4",
            result = "OK"
        )
        ReelPublishTrace.mark(
            ReelPublishTrace.Stage.UPLOAD_FAIL,
            fileSize = 1_024L,
            mime = "video/mp4",
            result = "TIMEOUT"
        )
        val compact = ReelPublishTrace.compact()
        assertTrue(compact.contains("SUBMIT_START"))
        assertTrue(compact.contains("UPLOAD_FAIL[TIMEOUT]"))
        assertFalse(compact.contains("http"))
        assertFalse(compact.contains("bearer", ignoreCase = true))
        assertEquals("TIMEOUT", ReelPublishTrace.userFacingCategory())
        assertEquals("La publicación tardó demasiado. Intentá de nuevo.", ReelPublishTrace.userFacingMessage())
    }

    @Test
    fun knownFailuresDoNotUseGenericPublishCopy() {
        ReelPublishTrace.begin()
        ReelPublishTrace.mark(ReelPublishTrace.Stage.REGISTER_FAIL, result = "FILE_TOO_LARGE")
        assertEquals("FILE_TOO_LARGE", ReelPublishTrace.userFacingCategory())
        assertFalse(ReelPublishTrace.userFacingMessage().contains("MEDIA-DB"))
        assertFalse(ReelPublishTrace.userFacingMessage().contains("REGISTER"))
    }
}
