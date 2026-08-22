package com.comunidapp.app.domain.vitacora

import com.comunidapp.app.data.model.M14PassportHistory
import com.comunidapp.app.data.model.M14PassportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun filter_blocksGrantEvents() {
        assertFalse(
            VitaCoraUserHistoryFilter.isUserFacing("VITACORA GRANT CREATED", "ACTIVE")
        )
    }
}
