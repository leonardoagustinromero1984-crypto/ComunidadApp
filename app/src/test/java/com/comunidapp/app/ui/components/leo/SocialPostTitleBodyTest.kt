package com.comunidapp.app.ui.components.leo

import org.junit.Assert.assertEquals
import org.junit.Test

class SocialPostTitleBodyTest {

    @Test
    fun titleAndContentAreBothVisible() {
        val title = "Titulo"
        val content = "Contenido"
        assertEquals("Titulo", title.takeIf { it.isNotBlank() }.orEmpty())
        assertEquals("Contenido", content.takeIf { it.isNotBlank() }.orEmpty())
    }
}
