package com.comunidapp.app.domain.social

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedBodyFormatTest {

    @Test
    fun encodeAndDecodeTitleContent() {
        val encoded = FeedBodyFormat.encode("Mi título", "Mi contenido")
        assertEquals("Mi título\n\nMi contenido", encoded)
        val (title, content) = FeedBodyFormat.decode(encoded)
        assertEquals("Mi título", title)
        assertEquals("Mi contenido", content)
    }

    @Test
    fun decodeLegacyBodyAsContentOnly() {
        val (title, content) = FeedBodyFormat.decode("Solo cuerpo")
        assertEquals("", title)
        assertEquals("Solo cuerpo", content)
    }
}
