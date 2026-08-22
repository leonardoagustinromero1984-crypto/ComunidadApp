package com.comunidapp.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LostPetPrefillRouteTest {
    @Test
    fun reportLostCarriesPetId() {
        val route = NavRoutes.publishLostFound("pet-123")
        assertTrue(route.contains("pet-123"))
        assertEquals(NavRoutes.PUBLISH_LOST_FOUND, NavRoutes.publishLostFound(null))
    }
}
