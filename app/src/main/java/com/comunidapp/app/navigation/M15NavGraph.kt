package com.comunidapp.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.comunidapp.app.ui.screens.m15.M15DischargeScreen
import com.comunidapp.app.ui.screens.m15.M15EvolutionFormScreen
import com.comunidapp.app.ui.screens.m15.M15EvolutionListScreen
import com.comunidapp.app.ui.screens.m15.M15ExpenseFormScreen
import com.comunidapp.app.ui.screens.m15.M15ExpensesScreen
import com.comunidapp.app.ui.screens.m15.M15FosterHomeDetailScreen
import com.comunidapp.app.ui.screens.m15.M15FosterHomesListScreen
import com.comunidapp.app.ui.screens.m15.M15FosterHubScreen
import com.comunidapp.app.ui.screens.m15.M15FosterRequestFormScreen
import com.comunidapp.app.ui.screens.m15.M15FosterRequestsScreen
import com.comunidapp.app.ui.screens.m15.M15HelpFormScreen
import com.comunidapp.app.ui.screens.m15.M15HelpListScreen
import com.comunidapp.app.ui.screens.m15.M15MyFosterHomeScreen
import com.comunidapp.app.ui.screens.m15.M15OperationsScreen
import com.comunidapp.app.ui.screens.m15.M15PlacementDetailScreen
import com.comunidapp.app.ui.screens.m15.M15PlacementsListScreen
import java.nio.charset.StandardCharsets

/** M15 hogares de tránsito routes (Bloques 1–3). */
fun NavGraphBuilder.m15FosterRoutes(navController: NavHostController) {
    composable(NavRoutes.M15_HUB) {
        M15FosterHubScreen(
            onNavigateBack = { navController.popBackStack() },
            onBrowseHomes = { navController.navigate(NavRoutes.M15_HOMES) },
            onMyHome = { navController.navigate(NavRoutes.M15_MY_HOME) },
            onReceivedRequests = { navController.navigate(NavRoutes.M15_REQUESTS_RECEIVED) },
            onMyPlacements = { navController.navigate(NavRoutes.M15_PLACEMENTS) },
            onOperations = { navController.navigate(NavRoutes.M15_OPERATIONS) }
        )
    }
    composable(NavRoutes.M15_OPERATIONS) {
        M15OperationsScreen(onNavigateBack = { navController.popBackStack() })
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

    composable(NavRoutes.M15_PLACEMENTS) {
        M15PlacementsListScreen(
            onNavigateBack = { navController.popBackStack() },
            onPlacementClick = { id -> navController.navigate(NavRoutes.m15PlacementDetail(id)) }
        )
    }
    composable(
        route = NavRoutes.M15_PLACEMENT_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_M15_PLACEMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val placementId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M15_PLACEMENT_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M15PlacementDetailScreen(
            placementId = placementId,
            onNavigateBack = { navController.popBackStack() },
            onEvolution = { navController.navigate(NavRoutes.m15PlacementEvolution(placementId)) },
            onDischarge = { navController.navigate(NavRoutes.m15PlacementDischarge(placementId)) },
            onExpenses = { navController.navigate(NavRoutes.m15PlacementExpenses(placementId)) },
            onHelp = { navController.navigate(NavRoutes.m15PlacementHelp(placementId)) }
        )
    }
    composable(
        route = NavRoutes.M15_PLACEMENT_EVOLUTION,
        arguments = listOf(navArgument(NavRoutes.ARG_M15_PLACEMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val placementId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M15_PLACEMENT_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M15EvolutionListScreen(
            placementId = placementId,
            onNavigateBack = { navController.popBackStack() },
            onAdd = { navController.navigate(NavRoutes.m15PlacementEvolutionNew(placementId)) }
        )
    }
    composable(
        route = NavRoutes.M15_PLACEMENT_EVOLUTION_NEW,
        arguments = listOf(navArgument(NavRoutes.ARG_M15_PLACEMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val placementId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M15_PLACEMENT_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M15EvolutionFormScreen(
            placementId = placementId,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.M15_PLACEMENT_DISCHARGE,
        arguments = listOf(navArgument(NavRoutes.ARG_M15_PLACEMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val placementId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M15_PLACEMENT_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M15DischargeScreen(
            placementId = placementId,
            onNavigateBack = { navController.popBackStack() },
            onCompleted = {
                navController.popBackStack(NavRoutes.m15PlacementDetail(placementId), inclusive = false)
            }
        )
    }
    composable(
        route = NavRoutes.M15_PLACEMENT_EXPENSES,
        arguments = listOf(navArgument(NavRoutes.ARG_M15_PLACEMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val placementId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M15_PLACEMENT_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M15ExpensesScreen(
            placementId = placementId,
            onNavigateBack = { navController.popBackStack() },
            onAdd = { navController.navigate(NavRoutes.m15PlacementExpensesNew(placementId)) }
        )
    }
    composable(
        route = NavRoutes.M15_PLACEMENT_EXPENSES_NEW,
        arguments = listOf(navArgument(NavRoutes.ARG_M15_PLACEMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val placementId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M15_PLACEMENT_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M15ExpenseFormScreen(
            placementId = placementId,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() }
        )
    }
    composable(
        route = NavRoutes.M15_PLACEMENT_HELP,
        arguments = listOf(navArgument(NavRoutes.ARG_M15_PLACEMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val placementId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M15_PLACEMENT_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M15HelpListScreen(
            placementId = placementId,
            onNavigateBack = { navController.popBackStack() },
            onAdd = { navController.navigate(NavRoutes.m15PlacementHelpNew(placementId)) }
        )
    }
    composable(
        route = NavRoutes.M15_PLACEMENT_HELP_NEW,
        arguments = listOf(navArgument(NavRoutes.ARG_M15_PLACEMENT_ID) { type = NavType.StringType })
    ) { entry ->
        val placementId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M15_PLACEMENT_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M15HelpFormScreen(
            placementId = placementId,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() }
        )
    }
}
