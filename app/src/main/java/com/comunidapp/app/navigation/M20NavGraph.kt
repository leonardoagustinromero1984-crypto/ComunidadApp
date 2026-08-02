package com.comunidapp.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.comunidapp.app.ui.screens.m20.M20ConversationListScreen
import com.comunidapp.app.ui.screens.m20.M20ThreadScreen
import java.nio.charset.StandardCharsets

/** M20 mensajería — rutas Bloque 1 (fundación local/mock). */
fun NavGraphBuilder.m20MessagingRoutes(navController: NavHostController) {
    composable(NavRoutes.M20_CONVERSATIONS) {
        M20ConversationListScreen(
            onNavigateBack = { navController.popBackStack() },
            onConversationClick = { id -> navController.navigate(NavRoutes.m20Thread(id)) }
        )
    }
    composable(
        route = NavRoutes.M20_THREAD,
        arguments = listOf(navArgument(NavRoutes.ARG_M20_CONVERSATION_ID) { type = NavType.StringType })
    ) { entry ->
        val conversationId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M20_CONVERSATION_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M20ThreadScreen(
            conversationId = conversationId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
}
