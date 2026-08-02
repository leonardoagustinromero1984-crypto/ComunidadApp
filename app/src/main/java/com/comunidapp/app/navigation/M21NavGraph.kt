package com.comunidapp.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.comunidapp.app.data.model.M21ReviewTargetType
import com.comunidapp.app.ui.screens.m21.M21HubScreen
import com.comunidapp.app.ui.screens.m21.M21ReviewDetailScreen
import com.comunidapp.app.ui.screens.m21.M21ReviewsScreen
import com.comunidapp.app.ui.screens.m21.M21SubjectScreen
import com.comunidapp.app.ui.screens.m21.M21VerificationsScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun NavGraphBuilder.m21ReputationRoutes(navController: NavHostController) {
    composable(NavRoutes.M21_HUB) {
        M21HubScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenReviews = { navController.navigate(NavRoutes.M21_REVIEWS) },
            onOpenVerifications = { navController.navigate(NavRoutes.M21_VERIFICATIONS) },
            onOpenSubject = { type, id -> navController.navigate(NavRoutes.m21Subject(type.name, id)) }
        )
    }
    composable(NavRoutes.M21_REVIEWS) {
        M21ReviewsScreen(
            onNavigateBack = { navController.popBackStack() },
            onReviewClick = { id -> navController.navigate(NavRoutes.m21ReviewDetail(id)) }
        )
    }
    composable(NavRoutes.M21_VERIFICATIONS) {
        M21VerificationsScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(
        route = NavRoutes.M21_SUBJECT,
        arguments = listOf(
            navArgument(NavRoutes.ARG_M21_SUBJECT_TYPE) { type = NavType.StringType },
            navArgument(NavRoutes.ARG_M21_SUBJECT_ID) { type = NavType.StringType }
        )
    ) { entry ->
        val typeRaw = URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M21_SUBJECT_TYPE).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        val id = URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M21_SUBJECT_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        val targetType = runCatching { M21ReviewTargetType.valueOf(typeRaw) }
            .getOrDefault(M21ReviewTargetType.SERVICE)
        M21SubjectScreen(
            targetType = targetType,
            targetId = id,
            onNavigateBack = { navController.popBackStack() },
            onReviewClick = { reviewId -> navController.navigate(NavRoutes.m21ReviewDetail(reviewId)) }
        )
    }
    composable(
        route = NavRoutes.M21_REVIEW_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_M21_REVIEW_ID) { type = NavType.StringType })
    ) { entry ->
        val reviewId = URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M21_REVIEW_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M21ReviewDetailScreen(
            reviewId = reviewId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
}
