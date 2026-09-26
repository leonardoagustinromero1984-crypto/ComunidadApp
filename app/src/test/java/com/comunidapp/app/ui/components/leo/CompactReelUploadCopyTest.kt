package com.comunidapp.app.ui.components.leo

import com.comunidapp.app.domain.social.PendingSocialPublish
import com.comunidapp.app.domain.social.PendingSocialPublishErrors
import com.comunidapp.app.domain.social.PendingSocialPublishState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompactReelUploadCopyTest {

    @Test
    fun compactLabelsKeepRealProgress() {
        val uploading = PendingSocialPublish(
            jobId = "j1",
            actorUserId = "u1",
            caption = "x",
            state = PendingSocialPublishState.UPLOADING,
            progressPercent = 42
        )
        assertEquals("Subiendo Clip… 42 %", CompactReelUploadCopy.label(uploading))
        assertEquals(
            "Publicando Clip…",
            CompactReelUploadCopy.label(uploading.copy(state = PendingSocialPublishState.PUBLISHING))
        )
        assertEquals(
            "Clip publicado",
            CompactReelUploadCopy.label(uploading.copy(state = PendingSocialPublishState.SUCCESS))
        )
        assertEquals(
            "No pudimos publicar el Clip",
            CompactReelUploadCopy.label(uploading.copy(state = PendingSocialPublishState.FAILED))
        )
        assertEquals(
            "El Reel se publicó, pero no pudimos terminar de vincularlo con la mascota.",
            CompactReelUploadCopy.label(
                uploading.copy(
                    state = PendingSocialPublishState.FAILED,
                    backendPostId = "post-1",
                    createCompleted = true,
                    errorCategory = PendingSocialPublishErrors.LINK_FAILED
                )
            )
        )
    }

    @Test
    fun shellPlacesCompactChipBelowStatusBar() {
        val nav = File("src/main/java/com/comunidapp/app/navigation/ComunidappNavGraph.kt").readText()
        val banner = File("src/main/java/com/comunidapp/app/ui/components/leo/ReelPublishStatusBanner.kt").readText()
        assertTrue(nav.contains("ReelPublishStatusBanner()"))
        assertFalseBottom(nav)
        assertTrue(banner.contains("statusBarsPadding"))
        assertTrue(banner.contains("reel_compact_upload_indicator"))
        assertTrue(!banner.contains("LinearProgressIndicator"))
    }

    private fun assertFalseBottom(nav: String) {
        org.junit.Assert.assertFalse(nav.contains("Alignment.BottomCenter"))
    }
}
