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
import com.comunidapp.app.ui.screens.m17.M17GoodsDetailScreen
import com.comunidapp.app.ui.screens.m17.M17GoodsListScreen
import com.comunidapp.app.ui.screens.m17.M17HubScreen
import com.comunidapp.app.ui.screens.m17.M17MyHelpScreen
import com.comunidapp.app.ui.screens.m17.M17VolunteerDetailScreen
import com.comunidapp.app.ui.screens.m17.M17VolunteerListScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.comunidapp.app.domain.RolePermissions
import com.comunidapp.app.domain.context.OperationalContextProvider
import com.comunidapp.app.domain.organization.OrganizationRoute
import java.nio.charset.StandardCharsets

private fun organizationArgument() = navArgument(OrganizationRoute.ARG) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

/** M17 ayuda comunitaria: descubrir y actividad personal. */
fun NavGraphBuilder.m17DonationRoutes(navController: NavHostController) {
    composable(
        route = OrganizationRoute.pattern(NavRoutes.M17_HUB),
        arguments = listOf(organizationArgument())
    ) { entry ->
        val organizationId = OrganizationRoute.read(entry.arguments?.getString(OrganizationRoute.ARG))
        M17HubScreen(
            onNavigateBack = { navController.popBackStack() },
            onCampaigns = {
                navController.navigate(OrganizationRoute.append(NavRoutes.M17_CAMPAIGNS, organizationId))
            },
            onGoods = {
                navController.navigate(OrganizationRoute.append(NavRoutes.M17_GOODS, organizationId))
            },
            onVolunteer = {
                navController.navigate(OrganizationRoute.append(NavRoutes.M17_VOLUNTEER, organizationId))
            }
        )
    }
    composable(NavRoutes.M17_MY_HELP) {
        M17MyHelpScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(
        route = OrganizationRoute.pattern(NavRoutes.M17_GOODS),
        arguments = listOf(organizationArgument())
    ) { entry ->
        val organizationId = OrganizationRoute.read(entry.arguments?.getString(OrganizationRoute.ARG))
        M17GoodsListScreen(
            organizationId = organizationId,
            onNavigateBack = { navController.popBackStack() },
            onNeedClick = { id -> navController.navigate(NavRoutes.m17GoodDetail(id)) }
        )
    }
    composable(
        route = NavRoutes.M17_GOOD_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_M17_NEED_ID) { type = NavType.StringType })
    ) { entry ->
        val needId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M17_NEED_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M17GoodsDetailScreen(
            needId = needId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(
        route = OrganizationRoute.pattern(NavRoutes.M17_VOLUNTEER),
        arguments = listOf(organizationArgument())
    ) { entry ->
        val organizationId = OrganizationRoute.read(entry.arguments?.getString(OrganizationRoute.ARG))
        M17VolunteerListScreen(
            organizationId = organizationId,
            onNavigateBack = { navController.popBackStack() },
            onOpportunityClick = { id -> navController.navigate(NavRoutes.m17VolunteerDetail(id)) }
        )
    }
    composable(
        route = NavRoutes.M17_VOLUNTEER_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_M17_OPPORTUNITY_ID) { type = NavType.StringType })
    ) { entry ->
        val opportunityId = java.net.URLDecoder.decode(
            entry.arguments?.getString(NavRoutes.ARG_M17_OPPORTUNITY_ID).orEmpty(),
            StandardCharsets.UTF_8.name()
        )
        M17VolunteerDetailScreen(
            opportunityId = opportunityId,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    composable(
        route = OrganizationRoute.pattern(NavRoutes.M17_CAMPAIGNS),
        arguments = listOf(organizationArgument())
    ) { entry ->
        val organizationId = OrganizationRoute.read(entry.arguments?.getString(OrganizationRoute.ARG))
        val context by OperationalContextProvider.active.collectAsState()
        M17CampaignsListScreen(
            organizationId = organizationId,
            onNavigateBack = { navController.popBackStack() },
            onCampaignClick = { id -> navController.navigate(NavRoutes.m17CampaignDetail(id)) },
            onManage = { navController.navigate(NavRoutes.M17_CAMPAIGNS_MANAGE) },
            onCreate = { navController.navigate(NavRoutes.M17_CAMPAIGNS_CREATE) },
            canAdminister = RolePermissions.canCreateCampaigns(context)
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
