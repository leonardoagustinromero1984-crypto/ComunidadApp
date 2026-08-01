package com.comunidapp.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.comunidapp.app.ui.screens.m15.M15FosterHomeDetailScreen
import com.comunidapp.app.ui.screens.m15.M15FosterHomesListScreen
import com.comunidapp.app.ui.screens.m15.M15FosterHubScreen
import com.comunidapp.app.ui.screens.m15.M15FosterRequestFormScreen
import com.comunidapp.app.ui.screens.m15.M15FosterRequestsScreen
import com.comunidapp.app.ui.screens.m15.M15MyFosterHomeScreen
import java.nio.charset.StandardCharsets

/** M15 hogares de tránsito routes (Bloque 1 local). */
fun NavGraphBuilder.m15FosterRoutes(navController: NavHostController) {
    composable(NavRoutes.M15_HUB) {
        M15FosterHubScreen(
            onNavigateBack = { navController.popBackStack() },
            onBrowseHomes = { navController.navigate(NavRoutes.M15_HOMES) },
            onMyHome = { navController.navigate(NavRoutes.M15_MY_HOME) },
            onReceivedRequests = { navController.navigate(NavRoutes.M15_REQUESTS_RECEIVED) }
        )
    }
    composable(NavRoutes.M15_HOMES) {
        M15FosterHomesListScreen(
            onNavigateBack = { navController.popBackStack() },
            onHomeClick = { id -> navController.navigate(NavRoutes.m15HomeDetail(id)) }
        )
    }
    composable(
        route = NavRoutes.M15_HOME_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_M15_HOME_ID) { type = NavType.StringType })
    ) { entry ->
        val homeId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M15_HOME_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M15FosterHomeDetailScreen(
            homeId = homeId,
            onNavigateBack = { navController.popBackStack() },
            onRequest = { id -> navController.navigate(NavRoutes.m15RequestForm(id)) }
        )
    }
    composable(NavRoutes.M15_MY_HOME) {
        M15MyFosterHomeScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(
        route = NavRoutes.M15_REQUEST_FORM,
        arguments = listOf(navArgument(NavRoutes.ARG_M15_HOME_ID) { type = NavType.StringType })
    ) { entry ->
        val homeId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M15_HOME_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M15FosterRequestFormScreen(
            homeId = homeId,
            onNavigateBack = { navController.popBackStack() },
            onSubmitted = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.M15_REQUESTS_RECEIVED) {
        M15FosterRequestsScreen(onNavigateBack = { navController.popBackStack() })
    }
}
