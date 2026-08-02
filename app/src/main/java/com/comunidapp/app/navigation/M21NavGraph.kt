package com.comunidapp.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.comunidapp.app.ui.screens.m21.M21HubScreen
import com.comunidapp.app.ui.screens.m21.M21ReviewsScreen
import com.comunidapp.app.ui.screens.m21.M21VerificationsScreen

fun NavGraphBuilder.m21ReputationRoutes(navController: NavHostController) {
    composable(NavRoutes.M21_HUB) {
        M21HubScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenReviews = { navController.navigate(NavRoutes.M21_REVIEWS) },
            onOpenVerifications = { navController.navigate(NavRoutes.M21_VERIFICATIONS) }
        )
    }
    composable(NavRoutes.M21_REVIEWS) {
        M21ReviewsScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.M21_VERIFICATIONS) {
        M21VerificationsScreen(onNavigateBack = { navController.popBackStack() })
    }
}
