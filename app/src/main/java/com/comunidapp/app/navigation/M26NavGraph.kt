package com.comunidapp.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.comunidapp.app.ui.screens.m26.M26AssistanceScreen
import com.comunidapp.app.ui.screens.m26.M26DuplicatesScreen
import com.comunidapp.app.ui.screens.m26.M26HubScreen
import com.comunidapp.app.ui.screens.m26.M26RecommendationsScreen
import com.comunidapp.app.ui.screens.m26.M26VisualMatchingScreen

fun NavGraphBuilder.m26AiRoutes(navController: NavHostController) {
    composable(NavRoutes.M26_HUB) {
        M26HubScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenVisualMatching = { navController.navigate(NavRoutes.M26_VISUAL_MATCHING) },
            onOpenDuplicates = { navController.navigate(NavRoutes.M26_DUPLICATES) },
            onOpenAssistance = { navController.navigate(NavRoutes.M26_ASSISTANCE) },
            onOpenRecommendations = { navController.navigate(NavRoutes.M26_RECOMMENDATIONS) }
        )
    }
    composable(NavRoutes.M26_VISUAL_MATCHING) {
        M26VisualMatchingScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.M26_DUPLICATES) {
        M26DuplicatesScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.M26_ASSISTANCE) {
        M26AssistanceScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.M26_RECOMMENDATIONS) {
        M26RecommendationsScreen(onNavigateBack = { navController.popBackStack() })
    }
}
