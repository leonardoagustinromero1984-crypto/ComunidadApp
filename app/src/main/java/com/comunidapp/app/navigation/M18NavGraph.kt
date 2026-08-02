package com.comunidapp.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.comunidapp.app.ui.screens.m18.M18EventDetailScreen
import com.comunidapp.app.ui.screens.m18.M18EventEditScreen
import com.comunidapp.app.ui.screens.m18.M18EventManageScreen
import com.comunidapp.app.ui.screens.m18.M18EventsListScreen
import java.nio.charset.StandardCharsets

/** M18 eventos — rutas Bloque 1 (fundación local/mock). */
fun NavGraphBuilder.m18EventRoutes(navController: NavHostController) {
    composable(NavRoutes.M18_EVENTS) {
        M18EventsListScreen(
            onNavigateBack = { navController.popBackStack() },
            onEventClick = { id -> navController.navigate(NavRoutes.m18EventDetail(id)) },
            onManage = { navController.navigate(NavRoutes.M18_EVENTS_MANAGE) },
            onCreate = { navController.navigate(NavRoutes.M18_EVENTS_CREATE) }
        )
    }
    composable(
        route = NavRoutes.M18_EVENT_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_M18_EVENT_ID) { type = NavType.StringType })
    ) { entry ->
        val eventId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M18_EVENT_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M18EventDetailScreen(
            eventId = eventId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.M18_EVENTS_MANAGE) {
        M18EventManageScreen(
            onNavigateBack = { navController.popBackStack() },
            onEditEvent = { id -> navController.navigate(NavRoutes.m18EventEdit(id)) },
            onCreate = { navController.navigate(NavRoutes.M18_EVENTS_CREATE) }
        )
    }
    composable(NavRoutes.M18_EVENTS_CREATE) {
        M18EventEditScreen(
            eventId = null,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { id ->
                navController.popBackStack()
                navController.navigate(NavRoutes.m18EventDetail(id))
            }
        )
    }
    composable(
        route = NavRoutes.M18_EVENT_EDIT,
        arguments = listOf(navArgument(NavRoutes.ARG_M18_EVENT_ID) { type = NavType.StringType })
    ) { entry ->
        val eventId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M18_EVENT_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M18EventEditScreen(
            eventId = eventId,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() }
        )
    }
}
