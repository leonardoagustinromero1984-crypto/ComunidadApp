package com.comunidapp.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.domain.AppMode
import com.comunidapp.app.domain.RolePermissions
import com.comunidapp.app.domain.toAppMode
import com.comunidapp.app.navigation.NavRoutes
import com.comunidapp.app.ui.theme.BrandGrayMedium
import com.comunidapp.app.ui.theme.BrandOrange
import com.comunidapp.app.ui.theme.BrandOrangeContainer
import com.comunidapp.app.ui.theme.BrandOrangeSoft
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.ui.theme.LeoNavLabel

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val prominent: Boolean = false
)

fun bottomNavItemsFor(accountType: AccountType): List<BottomNavItem> {
    val businessTitle = RolePermissions.businessPanelTitle(accountType)
    return when (accountType.toAppMode()) {
        AppMode.NEGOCIO -> listOf(
            BottomNavItem(NavRoutes.HOME, "Inicio", Icons.Filled.Home, Icons.Outlined.Home),
            BottomNavItem(NavRoutes.PUBLISH, "Publicar", Icons.Filled.AddCircle, Icons.Filled.AddCircle, prominent = true),
            BottomNavItem(NavRoutes.MY_BUSINESS, businessTitle, Icons.Filled.Storefront, Icons.Outlined.Storefront),
            BottomNavItem(NavRoutes.PROFILE, "Perfil", Icons.Filled.Person, Icons.Outlined.Person)
        )
        else -> listOf(
            BottomNavItem(NavRoutes.HOME, "Inicio", Icons.Filled.Home, Icons.Outlined.Home),
            BottomNavItem(NavRoutes.SUMATE, "Sumate", Icons.Filled.Handshake, Icons.Outlined.Handshake),
            BottomNavItem(NavRoutes.PUBLISH, "Publicar", Icons.Filled.AddCircle, Icons.Filled.AddCircle, prominent = true),
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
    // Desde Perfil el hub usa PUBLISH_FROM_PROFILE: no marcar Publicar (evita “scrim” del FAB).
    val onProfileCreator = currentRoute == NavRoutes.PUBLISH_FROM_PROFILE
    // Historia desde Inicio (`+` Tu historia): conservar Inicio seleccionado.
    val onHomeStoryCreator = currentRoute == NavRoutes.PUBLISH_STORY

    NavigationBar(
        containerColor = BrandWhite,
        tonalElevation = 2.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        items.forEach { item ->
            val selected = when {
                onProfileCreator && item.route == NavRoutes.PUBLISH -> false
                onProfileCreator && item.route == NavRoutes.PROFILE -> true
                onHomeStoryCreator && item.route == NavRoutes.PUBLISH -> false
                onHomeStoryCreator && item.route == NavRoutes.HOME -> true
                else -> currentRoute == item.route
            }
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute == item.route && !onProfileCreator && !onHomeStoryCreator) {
                        return@NavigationBarItem
                    }
                    navController.navigate(item.route) {
                        popUpTo(NavRoutes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    if (item.prominent) {
                        Box(
                            modifier = Modifier
                                .offset(y = (-6).dp)
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(if (selected) BrandOrange else BrandOrangeSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = item.label,
                                tint = BrandText,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label
                        )
                    }
                },
                label = { Text(item.label, style = LeoNavLabel) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BrandOrange,
                    selectedTextColor = BrandOrange,
                    indicatorColor = if (item.prominent) Color.Transparent else BrandOrangeContainer,
                    unselectedIconColor = BrandGrayMedium,
                    unselectedTextColor = BrandGrayMedium
                )
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390, name = "BottomBarPreview")
@Composable
private fun BottomBarPreview() {
    ComunidappTheme {
        val items = bottomNavItemsFor(AccountType.PERSON)
        NavigationBar(containerColor = BrandWhite, tonalElevation = 2.dp) {
            items.forEachIndexed { index, item ->
                val selected = index == 0
                NavigationBarItem(
                    selected = selected,
                    onClick = {},
                    icon = {
                        if (item.prominent) {
                            Box(
                                modifier = Modifier
                                    .offset(y = (-6).dp)
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(BrandOrangeSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = item.label, tint = BrandText)
                            }
                        } else {
                            Icon(
                                if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        }
                    },
                    label = { Text(item.label, style = LeoNavLabel) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandOrange,
                        selectedTextColor = BrandOrange,
                        indicatorColor = BrandOrangeContainer,
                        unselectedIconColor = BrandGrayMedium,
                        unselectedTextColor = BrandGrayMedium
                    )
                )
            }
        }
    }
}
