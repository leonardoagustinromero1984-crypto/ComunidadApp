package com.comunidapp.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.comunidapp.app.ui.screens.m17.M17CampaignDetailScreen
import com.comunidapp.app.ui.screens.m17.M17CampaignEditScreen
import com.comunidapp.app.ui.screens.m17.M17CampaignManageScreen
import com.comunidapp.app.ui.screens.m17.M17CampaignsListScreen
import com.comunidapp.app.ui.screens.m17.M17HubScreen
import java.nio.charset.StandardCharsets

/** M17 donaciones — rutas Bloque 1 (fundación local/mock). */
fun NavGraphBuilder.m17DonationRoutes(navController: NavHostController) {
    composable(NavRoutes.M17_HUB) {
        M17HubScreen(
            onNavigateBack = { navController.popBackStack() },
            onCampaigns = { navController.navigate(NavRoutes.M17_CAMPAIGNS) }
        )
    }
    composable(NavRoutes.M17_CAMPAIGNS) {
        M17CampaignsListScreen(
            onNavigateBack = { navController.popBackStack() },
            onCampaignClick = { id -> navController.navigate(NavRoutes.m17CampaignDetail(id)) },
            onManage = { navController.navigate(NavRoutes.M17_CAMPAIGNS_MANAGE) },
            onCreate = { navController.navigate(NavRoutes.M17_CAMPAIGNS_CREATE) }
        )
    }
    composable(
        route = NavRoutes.M17_CAMPAIGN_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_M17_CAMPAIGN_ID) { type = NavType.StringType })
    ) { entry ->
        val campaignId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M17_CAMPAIGN_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M17CampaignDetailScreen(
            campaignId = campaignId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(NavRoutes.M17_CAMPAIGNS_MANAGE) {
        M17CampaignManageScreen(
            onNavigateBack = { navController.popBackStack() },
            onEditCampaign = { id -> navController.navigate(NavRoutes.m17CampaignEdit(id)) },
            onCreate = { navController.navigate(NavRoutes.M17_CAMPAIGNS_CREATE) }
        )
    }
    composable(NavRoutes.M17_CAMPAIGNS_CREATE) {
        M17CampaignEditScreen(
            campaignId = null,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { id ->
                navController.popBackStack()
                navController.navigate(NavRoutes.m17CampaignDetail(id))
            }
        )
    }
    composable(
        route = NavRoutes.M17_CAMPAIGN_EDIT,
        arguments = listOf(navArgument(NavRoutes.ARG_M17_CAMPAIGN_ID) { type = NavType.StringType })
    ) { entry ->
        val campaignId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M17_CAMPAIGN_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M17CampaignEditScreen(
            campaignId = campaignId,
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() }
        )
    }
}
