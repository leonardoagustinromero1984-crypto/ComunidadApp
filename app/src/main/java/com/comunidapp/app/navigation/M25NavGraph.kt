package com.comunidapp.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.comunidapp.app.ui.screens.m25.*
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun NavGraphBuilder.m25MarketplaceRoutes(navController: NavHostController) {
    composable(NavRoutes.M25_HUB) {
        M25HubScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenCatalog = { navController.navigate(NavRoutes.M25_CATALOG) },
            onOpenCart = { navController.navigate(NavRoutes.M25_CART) },
            onOpenOrders = { navController.navigate(NavRoutes.M25_ORDERS) },
            onOpenManage = { navController.navigate(NavRoutes.M25_MANAGE) }
        )
    }
    composable(NavRoutes.M25_CATALOG) {
        M25CatalogScreen(
            onNavigateBack = { navController.popBackStack() },
            onShopClick = { id -> navController.navigate(NavRoutes.m25ShopDetail(id)) }
        )
    }
    composable(
        NavRoutes.M25_SHOP_DETAIL,
        arguments = listOf(navArgument(NavRoutes.ARG_M25_SHOP_ID) { type = NavType.StringType })
    ) { entry ->
        val id = URLDecoder.decode(entry.arguments?.getString(NavRoutes.ARG_M25_SHOP_ID).orEmpty(), StandardCharsets.UTF_8.name())
        M25ShopDetailScreen(shopId = id, onNavigateBack = { navController.popBackStack() })
    }
    composable(NavRoutes.M25_CART) { M25CartScreen(onNavigateBack = { navController.popBackStack() }) }
    composable(NavRoutes.M25_ORDERS) { M25OrdersScreen(onNavigateBack = { navController.popBackStack() }) }
    composable(NavRoutes.M25_MANAGE) { M25ManageScreen(onNavigateBack = { navController.popBackStack() }) }
}
