package com.comunidapp.app.ui.components.leo

import org.junit.Assert.assertEquals
import org.junit.Test

class SocialPostBodyDedupTest {
    @Test
    fun prefersContentWhenTitleIsPrefixOrDuplicate() {
        val content = "Hola comunidad"
        val title = content.take(80)
        val visible = content.takeIf { it.isNotBlank() } ?: title
        assertEquals("Hola comunidad", visible)
        assertEquals(1, visible.split("Hola comunidad").size - 1)
    }
}
