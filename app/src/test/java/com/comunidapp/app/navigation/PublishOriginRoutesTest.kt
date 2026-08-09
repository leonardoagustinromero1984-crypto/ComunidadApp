package com.comunidapp.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PublishOriginRoutesTest {

    @Test
    fun publishFromProfile_isDistinctFromBottomBarPublish() {
        assertNotEquals(NavRoutes.PUBLISH, NavRoutes.PUBLISH_FROM_PROFILE)
        assertEquals("publish", NavRoutes.PUBLISH)
        assertEquals("publish_from_profile", NavRoutes.PUBLISH_FROM_PROFILE)
    }

    @Test
    fun petDetail_encodesId() {
        val route = NavRoutes.petDetail("abc def")
        assertEquals("pet_detail/abc+def", route)
    }

    @Test
    fun socialCreatorRoutes_exist() {
        assertEquals("publish_reel", NavRoutes.PUBLISH_REEL)
        assertEquals("publish_story", NavRoutes.PUBLISH_STORY)
    }
}
