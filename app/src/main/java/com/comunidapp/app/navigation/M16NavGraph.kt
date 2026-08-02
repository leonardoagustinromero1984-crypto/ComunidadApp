package com.comunidapp.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.comunidapp.app.ui.screens.m16.M16ShelterDetailScreen
import com.comunidapp.app.ui.screens.m16.M16ShelterManageScreen
import com.comunidapp.app.ui.screens.m16.M16SheltersListScreen
import java.nio.charset.StandardCharsets

/** M16 refugios — rutas Bloque 1 (fundación local). */
fun NavGraphBuilder.m16ShelterRoutes(navController: NavHostController) {
    composable(NavRoutes.M16_SHELTERS) {
        M16SheltersListScreen(
            onNavigateBack = { navController.popBackStack() },
            onShelterClick = { id -> navController.navigate(NavRoutes.m16ShelterDetail(id)) },
            onManage = { navController.navigate(NavRoutes.M16_SHELTERS_MANAGE) }
        )
    }
    composable(
        route = NavRoutes.M16_SHELTER_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_M16_SHELTER_ID) { type = NavType.StringType })
    ) { entry ->
        val shelterId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M16_SHELTER_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M16ShelterDetailScreen(
            shelterId = shelterId,
            onNavigateBack = { navController.popBackStack() },
            onM17Hub = { navController.navigate(NavRoutes.M17_HUB) },
            onM18Events = { navController.navigate(NavRoutes.M18_EVENTS) }
        )
    }
    composable(NavRoutes.M16_SHELTERS_MANAGE) {
        M16ShelterManageScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToPet = { petId -> navController.navigate(NavRoutes.petDetail(petId)) },
            onNavigateToAdoption = { id -> navController.navigate(NavRoutes.adoptionDetail(id)) },
            onNavigateToFoster = { id -> navController.navigate(NavRoutes.m15PlacementDetail(id)) }
        )
    }
}
