package com.comunidapp.app.ui.screens.social

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReelComposerUiTest {

    @Test
    fun reelComposerDoesNotExposeAaTextoField() {
        val source = File("src/main/java/com/comunidapp/app/ui/screens/social/SocialComposerScreens.kt")
            .takeIf { it.exists() }
            ?: File("app/src/main/java/com/comunidapp/app/ui/screens/social/SocialComposerScreens.kt")
        val lines = source.readLines()
        val start = lines.indexOfFirst { it.contains("fun ReelComposerScreen") }
        val end = lines.drop(start + 1).indexOfFirst { line ->
            line.trimStart().startsWith("@Composable")
        }.let { if (it < 0) lines.size else start + 1 + it }
        val reelBlock = lines.subList(start, end).joinToString("\n")
        assertFalse(reelBlock.contains("Aa Texto"))
        assertTrue(reelBlock.contains("Descripción"))
    }
}
