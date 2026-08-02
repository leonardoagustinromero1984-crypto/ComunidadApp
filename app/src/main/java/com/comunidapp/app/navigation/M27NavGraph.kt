package com.comunidapp.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.comunidapp.app.ui.screens.m27.M27ApiKeysScreen
import com.comunidapp.app.ui.screens.m27.M27AppsScreen
import com.comunidapp.app.ui.screens.m27.M27AuditScreen
import com.comunidapp.app.ui.screens.m27.M27DeliveriesScreen
import com.comunidapp.app.ui.screens.m27.M27ContractsScreen
import com.comunidapp.app.ui.screens.m27.M27HubScreen
import com.comunidapp.app.ui.screens.m27.M27OAuthScreen
import com.comunidapp.app.ui.screens.m27.M27RateLimitsScreen
import com.comunidapp.app.ui.screens.m27.M27WebhooksScreen

fun NavGraphBuilder.m27IntegrationRoutes(navController: NavHostController) {
    composable(NavRoutes.M27_HUB) {
        M27HubScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenWebhooks = { navController.navigate(NavRoutes.M27_WEBHOOKS) },
            onOpenOAuth = { navController.navigate(NavRoutes.M27_OAUTH) },
            onOpenApiKeys = { navController.navigate(NavRoutes.M27_API_KEYS) },
            onOpenContracts = { navController.navigate(NavRoutes.M27_CONTRACTS) },
            onOpenRateLimits = { navController.navigate(NavRoutes.M27_RATE_LIMITS) },
            onOpenApps = { navController.navigate(NavRoutes.M27_APPS) },
            onOpenDeliveries = { navController.navigate(NavRoutes.M27_DELIVERIES) },
            onOpenAudit = { navController.navigate(NavRoutes.M27_AUDIT) }
        )
    }
    composable(NavRoutes.M27_WEBHOOKS) {
        M27WebhooksScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.M27_OAUTH) {
        M27OAuthScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.M27_API_KEYS) {
        M27ApiKeysScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.M27_CONTRACTS) {
        M27ContractsScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.M27_RATE_LIMITS) {
        M27RateLimitsScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.M27_APPS) {
        M27AppsScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.M27_DELIVERIES) {
        M27DeliveriesScreen(onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.M27_AUDIT) {
        M27AuditScreen(onNavigateBack = { navController.popBackStack() })
    }
}
