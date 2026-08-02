package com.comunidapp.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.comunidapp.app.ui.screens.m22.M22CatalogScreen
import com.comunidapp.app.ui.screens.m22.M22HubScreen
import com.comunidapp.app.ui.screens.m22.M22ManageScreen
import com.comunidapp.app.ui.screens.m22.M22ProviderDetailScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun NavGraphBuilder.m22ProviderRoutes(navController: NavHostController) {
    composable(NavRoutes.M22_HUB) {
        M22HubScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenCatalog = { navController.navigate(NavRoutes.M22_CATALOG) },
            onOpenManage = { navController.navigate(NavRoutes.M22_MANAGE) }
        )
    }
    composable(NavRoutes.M22_CATALOG) {
        M22CatalogScreen(
            onNavigateBack = { navController.popBackStack() },
            onProviderClick = { id -> navController.navigate(NavRoutes.m22ProviderDetail(id)) }
        )
    }
    composable(
        NavRoutes.M22_PROVIDER_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_M22_PROVIDER_ID) { type = NavType.StringType })
    ) { entry ->
        val id = URLDecoder.decode(entry.arguments?.getString(NavRoutes.ARG_M22_PROVIDER_ID).orEmpty(), StandardCharsets.UTF_8.name())
        M22ProviderDetailScreen(
            id,
            onNavigateBack = { navController.popBackStack() },
            onBook = { navController.navigate(NavRoutes.M23_PROVIDER) },
            onViewAvailability = { navController.navigate(NavRoutes.M23_AVAILABILITY) }
        )
    }
    composable(NavRoutes.M22_MANAGE) {
        M22ManageScreen(onNavigateBack = { navController.popBackStack() })
    }
}
