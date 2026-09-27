package com.comunidapp.app.domain.social

import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalSocialPostVisibilityTest {

    @Test
    fun privadoMapsToFollowersRpc() {
        assertEquals("FOLLOWERS", CanonicalSocialPostVisibility.FOLLOWERS.toRpcValue())
        assertEquals("Privado", CanonicalSocialPostVisibility.label(CanonicalSocialPostVisibility.FOLLOWERS))
    }

    @Test
    fun legacyPrivateAlsoMapsToFollowersRpc() {
        assertEquals("FOLLOWERS", CanonicalSocialPostVisibility.PRIVATE.toRpcValue())
    }

    @Test
    fun fromRawParsesBackendValues() {
        assertEquals(CanonicalSocialPostVisibility.PUBLIC, CanonicalSocialPostVisibility.fromRaw("PUBLIC"))
        assertEquals(CanonicalSocialPostVisibility.FOLLOWERS, CanonicalSocialPostVisibility.fromRaw("followers"))
        assertEquals(CanonicalSocialPostVisibility.PRIVATE, CanonicalSocialPostVisibility.fromRaw("PRIVATE"))
        assertEquals(CanonicalSocialPostVisibility.FOLLOWERS, CanonicalSocialPostVisibility.fromRaw(null))
    }

    @Test
    fun uiPublishOptionsArePublicAndPrivadoOnly() {
        assertEquals(
            listOf(CanonicalSocialPostVisibility.PUBLIC, CanonicalSocialPostVisibility.FOLLOWERS),
            CanonicalSocialPostVisibility.uiPublishOptions()
        )
        assertEquals("Público", CanonicalSocialPostVisibility.label(CanonicalSocialPostVisibility.PUBLIC))
    }
}
