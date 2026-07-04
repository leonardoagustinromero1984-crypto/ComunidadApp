package com.comunidapp.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.comunidapp.app.navigation.NavRoutes

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(NavRoutes.HOME, "Inicio", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(NavRoutes.ADOPTIONS, "Adopciones", Icons.Filled.Pets, Icons.Outlined.Pets),
    BottomNavItem(NavRoutes.PUBLISH, "Publicar", Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline),
    BottomNavItem(NavRoutes.SHELTERS, "Refugios", Icons.Filled.VolunteerActivism, Icons.Outlined.VolunteerActivism),
    BottomNavItem(NavRoutes.PROFILE, "Perfil", Icons.Filled.Person, Icons.Outlined.Person)
)

@Composable
fun ComunidappBottomBar(navController: NavController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    NavigationBar {
        bottomNavItems.forEach { item ->
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
