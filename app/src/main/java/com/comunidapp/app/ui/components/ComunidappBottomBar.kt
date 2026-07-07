package com.comunidapp.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.domain.AppMode
import com.comunidapp.app.domain.RolePermissions
import com.comunidapp.app.domain.toAppMode
import com.comunidapp.app.navigation.NavRoutes

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

fun bottomNavItemsFor(accountType: AccountType): List<BottomNavItem> {
    val businessTitle = RolePermissions.businessPanelTitle(accountType)
    return when (accountType.toAppMode()) {
        AppMode.NEGOCIO -> listOf(
            BottomNavItem(NavRoutes.HOME, "Inicio", Icons.Filled.Home, Icons.Outlined.Home),
            BottomNavItem(NavRoutes.PUBLISH, "Publicar", Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline),
            BottomNavItem(NavRoutes.MY_BUSINESS, businessTitle, Icons.Filled.Storefront, Icons.Outlined.Storefront),
            BottomNavItem(NavRoutes.PROFILE, "Perfil", Icons.Filled.Person, Icons.Outlined.Person)
        )
        else -> listOf(
            BottomNavItem(NavRoutes.HOME, "Inicio", Icons.Filled.Home, Icons.Outlined.Home),
            BottomNavItem(NavRoutes.SUMATE, "Sumate", Icons.Filled.Handshake, Icons.Outlined.Handshake),
            BottomNavItem(NavRoutes.PUBLISH, "Publicar", Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline),
            BottomNavItem(NavRoutes.COMUNIDAD, "Comunidad", Icons.Filled.Groups, Icons.Outlined.Groups),
            BottomNavItem(NavRoutes.PROFILE, "Perfil", Icons.Filled.Person, Icons.Outlined.Person)
        )
    }
}

@Composable
fun ComunidappBottomBar(
    navController: NavController,
    accountType: AccountType
) {
    val items = bottomNavItemsFor(accountType)
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    NavigationBar {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(NavRoutes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors()
            )
        }
    }
}
