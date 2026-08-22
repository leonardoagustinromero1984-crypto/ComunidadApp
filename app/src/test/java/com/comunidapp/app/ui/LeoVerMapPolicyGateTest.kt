package com.comunidapp.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LeoVerMapPolicyGateTest {

    @Test
    fun PAID_MAP_API_DEPENDENCIES_ZERO() {
        val roots = listOf(
            File(UiRegressionGateTest.repoRoot(), "app/src/main"),
            File(UiRegressionGateTest.repoRoot(), "gradle")
        )
        val forbidden = listOf(
            "com.google.android.libraries.places",
            "com.google.android.libraries.navigation",
            "PlacesClient",
            "GeoApiContext",
            "DirectionsApi",
            "GeocodingApi",
            "com.google.maps.GeoApiContext",
            "play-services-places",
            "places-ktx",
            "navigation-sdk",
            "StreetViewPanorama"
        )
        val hits = roots.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "kts", "xml", "toml") }
                .flatMap { file ->
                    val text = file.readText()
                    forbidden.mapNotNull { needle ->
                        if (text.contains(needle)) "${file.relativeTo(UiRegressionGateTest.repoRoot())}: $needle" else null
                    }
                }
        }
        assertTrue(hits.joinToString("\n"), hits.isEmpty())
    }

    @Test
    fun NO_BACKGROUND_LOCATION_REQUIRED() {
        val manifest = UiRegressionGateTest.sourceFile("app/src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("ACCESS_BACKGROUND_LOCATION"))
        assertTrue(manifest.contains("ACCESS_FINE_LOCATION"))
    }

    @Test
    fun COMMUNITY_MAP_AUTO_SEARCH_ON_CAMERA_MOVE_NO() {
        val screen = UiRegressionGateTest.sourceFile(
            "app/src/main/java/com/comunidapp/app/ui/screens/comunidad/ComunidadScreen.kt"
        ).readText()
        assertTrue(screen.contains("onCameraIdle = { }"))
        assertFalse(screen.contains("onCameraIdle = { viewModel.search"))
    }
}
