package com.comunidapp.app.domain.vitacora

import com.comunidapp.app.data.model.M14PassportHistory
import com.comunidapp.app.data.model.M14PassportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VitaCoraHistoryPresentationTest {
    @Test
    fun titleFor_momentKind_isHuman() {
        val item = M14PassportHistory(
            id = "1",
            passportId = "p1",
            fromStatus = null,
            toStatus = M14PassportStatus.ACTIVE,
            actorUserId = null,
            reason = "Recuerdo en el parque",
            createdAt = 1L,
            metadataEvent = "MEMORY"
        )
        assertEquals("Se guardó un recuerdo", VitaCoraHistoryPresentation.titleFor(item))
    }

    @Test
    fun titleFor_socialReel_isNotPlainNote() {
        val item = M14PassportHistory(
            id = "2",
            passportId = "p1",
            fromStatus = null,
            toStatus = M14PassportStatus.ACTIVE,
            actorUserId = null,
            reason = "Reel en VitaCora",
            createdAt = 1L,
            metadataEvent = "SOCIAL",
            mediaDisplayUrl = "https://example.invalid/reel.mp4",
            mediaMime = "video/mp4",
            sourceContentKind = "REEL"
        )
        assertEquals("Se guardó un Reel", VitaCoraHistoryPresentation.titleFor(item))
        assertNull(VitaCoraHistoryPresentation.detailFor(item))
        assertTrue(VitaCoraHistoryPresentation.isPlayableVideo(item))
    }

    @Test
    fun titleFor_acceptedCareTransfer_usesFriendlySentence() {
        val item = M14PassportHistory(
            id = "3",
            passportId = "p1",
            fromStatus = null,
            toStatus = M14PassportStatus.ACTIVE,
            actorUserId = null,
            reason = "Luna pasó a estar bajo el cuidado de Carolina Gómez.",
            createdAt = 2L,
            metadataEvent = "CARE_TRANSFER"
        )
        assertEquals(
            "Luna pasó a estar bajo el cuidado de Carolina Gómez.",
            VitaCoraHistoryPresentation.titleFor(item)
        )
        assertNull(VitaCoraHistoryPresentation.detailFor(item))
        assertFalse(VitaCoraHistoryPresentation.titleFor(item).contains("uuid", ignoreCase = true))
    }

    @Test
    fun titleFor_careCreated_isFriendly() {
        val item = M14PassportHistory(
            id = "4",
            passportId = "p1",
            fromStatus = null,
            toStatus = M14PassportStatus.ACTIVE,
            actorUserId = null,
            reason = "Se creó la VitaCora de Luna.",
            createdAt = 1L,
            metadataEvent = "CARE_CREATED"
        )
        assertEquals("Se creó la VitaCora de Luna.", VitaCoraHistoryPresentation.titleFor(item))
        assertNull(VitaCoraHistoryPresentation.detailFor(item))
    }

    @Test
    fun filter_blocksGrantEvents() {
        assertFalse(
            VitaCoraUserHistoryFilter.isUserFacing("VITACORA GRANT CREATED", "ACTIVE")
        )
    }
}
