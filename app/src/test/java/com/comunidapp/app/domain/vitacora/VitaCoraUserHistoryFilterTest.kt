package com.comunidapp.app.domain.vitacora

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VitaCoraUserHistoryFilterTest {
    @Test
    fun hidesInternalAuditEvents() {
        assertFalse(VitaCoraUserHistoryFilter.isUserFacing("CREATED_OWNER", "ACTIVE"))
        assertFalse(VitaCoraUserHistoryFilter.isUserFacing("PUBLIC_CODE_ROTATED", "ACTIVE"))
        assertFalse(VitaCoraUserHistoryFilter.isUserFacing(null, "ACTIVE"))
    }

    @Test
    fun keepsUserFacingMomentsAndStatus() {
        assertTrue(VitaCoraUserHistoryFilter.isUserFacing("Se perdió", "ARCHIVED"))
        assertTrue(VitaCoraUserHistoryFilter.isUserFacing("SOCIAL", "ACTIVE"))
    }
}
