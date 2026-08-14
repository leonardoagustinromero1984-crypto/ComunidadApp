package com.comunidapp.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * RC1.2 — guards de UX: sin microchip visible ni códigos Mxx en pantallas de perfil/pasaporte/comunidad.
 */
class PetProfileUxStaticGuardsTest {

    private val rootPaths = listOf(
        "app/src/main/java/com/comunidapp/app/ui/screens/pets",
        "app/src/main/java/com/comunidapp/app/ui/screens/m14",
        "app/src/main/java/com/comunidapp/app/ui/screens/comunidad",
        "app/src/main/java/com/comunidapp/app/ui/components/PetHealthFormSection.kt"
    )

    @Test
    fun productiveUi_hasNoVisibleMicrochipLabels() {
        val hits = scanQuotedStrings(Regex("""(?i)microchip"""))
        assertTrue(
            "Referencias visibles a Microchip:\n${hits.joinToString("\n")}",
            hits.isEmpty()
        )
    }

    @Test
    fun productiveUi_hasNoMxxModuleCodesInUserStrings() {
        val hits = scanQuotedStrings(Regex("""\bM(?:0[0-9]|1[0-9]|2[0-6])\b"""))
        assertTrue(
            "Códigos Mxx en strings de UI:\n${hits.joinToString("\n")}",
            hits.isEmpty()
        )
    }

    @Test
    fun petDetail_exposesSingleCareNetworkSectionLabel() {
        val detail = sourceFile("app/src/main/java/com/comunidapp/app/ui/screens/pets/PetDetailScreen.kt").readText()
        val v2 = sourceFile("app/src/main/java/com/comunidapp/app/ui/screens/pets/PetDetailV2Components.kt").readText()
        assertTrue(detail.contains("Red de cuidado"))
        assertFalse(detail.contains("Personas autorizadas"))
        assertFalse(detail.contains("Responsables y custodias"))
        assertTrue(detail.contains("Archivar mascota"))
        assertTrue(detail.contains("Informar fallecimiento"))
        assertFalse(detail.contains("Marcar como fallecida"))
        assertTrue(v2.contains("Reactivar mascota"))
        assertFalse(detail.contains("\"Archivar perfil\""))
    }

    @Test
    fun healthEmptyState_offersAddAction() {
        val v2 = sourceFile("app/src/main/java/com/comunidapp/app/ui/screens/pets/PetDetailV2Components.kt").readText()
        assertTrue(v2.contains("Agregar información"))
        assertTrue(v2.contains("Todavía no agregaste información de salud"))
    }

    @Test
    fun passportEmptyState_isHumanLanguage() {
        val passport = sourceFile("app/src/main/java/com/comunidapp/app/ui/screens/m14/M14PassportScreens.kt").readText()
        assertTrue(passport.contains("Crear pasaporte"))
        assertTrue(passport.contains("Reuní en un solo lugar su información más importante."))
        assertFalse(passport.contains("el responsable M08"))
        assertFalse(passport.contains("\"Microchip"))
    }

    private fun scanQuotedStrings(contentPattern: Regex): List<String> {
        val quote = Regex("\"([^\"]*)\"")
        val hits = mutableListOf<String>()
        rootPaths.forEach { relative ->
            val file = sourcePath(relative)
            val files = if (file.isDirectory) {
                file.walkTopDown().filter { it.isFile && it.extension == "kt" }
            } else {
                sequenceOf(file)
            }
            files.forEach { f ->
                f.readLines().forEachIndexed { index, line ->
                    if (line.trimStart().startsWith("//")) return@forEachIndexed
                    quote.findAll(line).forEach { m ->
                        val literal = m.groupValues[1]
                        if (contentPattern.containsMatchIn(literal)) {
                            hits += "${f.path}:${index + 1}: ${line.trim()}"
                        }
                    }
                }
            }
        }
        return hits
    }

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath"),
            File(System.getProperty("user.dir"), relativePath),
            File(System.getProperty("user.dir"), "../$relativePath")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("$relativePath not found. cwd=${System.getProperty("user.dir")}")
    }

    private fun sourcePath(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath"),
            File(System.getProperty("user.dir"), relativePath),
            File(System.getProperty("user.dir"), "../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("$relativePath not found. cwd=${System.getProperty("user.dir")}")
    }
}
