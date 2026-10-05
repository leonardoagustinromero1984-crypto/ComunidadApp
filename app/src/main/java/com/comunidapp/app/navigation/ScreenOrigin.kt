package com.comunidapp.app.navigation

/**
 * When the back stack cannot pop, return to the screen that opened this one.
 * Settings is only the fallback when that screen was Settings.
 */
object ScreenOrigin {
    fun fallback(previousRoute: String?): String? {
        val route = previousRoute?.substringBefore("?").orEmpty()
        return when {
            route == NavRoutes.SETTINGS || route.startsWith("${NavRoutes.SETTINGS}/") -> NavRoutes.SETTINGS
            route == NavRoutes.USE_LEOVER_AS || route.startsWith("${NavRoutes.USE_LEOVER_AS}/") ->
                NavRoutes.USE_LEOVER_AS
            else -> null
        }
    }
}
