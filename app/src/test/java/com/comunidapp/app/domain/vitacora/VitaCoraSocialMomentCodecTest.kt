package com.comunidapp.app.domain.vitacora

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VitaCoraSocialMomentCodecTest {

    @Test
    fun decodeKeepsMediaReferenceWithoutExposingJsonToUiHelpers() {
        val encoded = VitaCoraSocialMomentCodec.encode(
            contentId = "post-1",
            compositionJson = "{}",
            mediaUrl = "8e0c0c60-7c3a-4c2a-9d1a-0b6c1d2e3f40",
            mediaAssetId = "8e0c0c60-7c3a-4c2a-9d1a-0b6c1d2e3f40",
            mediaMime = "video/mp4",
            contentKind = "REEL"
        )
        val payload = VitaCoraSocialMomentCodec.decode(encoded)
        assertEquals("post-1", payload?.contentId)
        assertEquals("video/mp4", payload?.mediaMime)
        assertEquals("REEL", payload?.contentKind)
        assertEquals(
            "8e0c0c60-7c3a-4c2a-9d1a-0b6c1d2e3f40",
            payload?.let { VitaCoraSocialMomentCodec.mediaAssetIdOf(it) }
        )
    }

    @Test
    fun legacyAssetIdInMediaUrlStillResolves() {
        val payload = VitaCoraSocialMomentCodec.decode(
            """{"contentId":"reel-1","mediaUrl":"8e0c0c60-7c3a-4c2a-9d1a-0b6c1d2e3f40"}"""
        )
        assertEquals("reel-1", payload?.contentId)
        assertEquals(
            "8e0c0c60-7c3a-4c2a-9d1a-0b6c1d2e3f40",
            payload?.let { VitaCoraSocialMomentCodec.mediaAssetIdOf(it) }
        )
        assertNull(payload?.let { VitaCoraSocialMomentCodec.mediaAssetIdOf(it.copy(mediaUrl = "https://cdn.example/x")) })
    }
}
