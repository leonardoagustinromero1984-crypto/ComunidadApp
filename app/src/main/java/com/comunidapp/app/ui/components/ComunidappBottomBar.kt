package com.comunidapp.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.domain.context.ContextNavigation
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.navigation.NavRoutes
import com.comunidapp.app.ui.theme.BrandGrayMedium
import com.comunidapp.app.ui.theme.BrandGreen
import com.comunidapp.app.ui.theme.BrandGreenContainer
import com.comunidapp.app.ui.theme.BrandOrange
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

fun bottomNavItemsFor(context: OperationalContext): List<BottomNavItem> =
    ContextNavigation.itemsFor(context)

@Deprecated("AccountType is LEGACY. Use OperationalContext.")
fun bottomNavItemsFor(accountType: AccountType): List<BottomNavItem> =
    bottomNavItemsFor(accountType.toLegacyOperationalContext())

internal fun AccountType.toLegacyOperationalContext(): OperationalContext = when (this) {
    AccountType.PERSON -> OperationalContext.Personal
    AccountType.SHELTER -> OperationalContext.Organization("legacy-shelter", "Organización", "SHELTER")
    AccountType.FOSTER_HOME -> OperationalContext.Foster("legacy-foster", "Hogar de tránsito")
    AccountType.VET -> OperationalContext.Veterinary("legacy-vet", "Consultorio")
    AccountType.SHOP -> OperationalContext.Shop("legacy-shop", "Tienda")
    AccountType.TRAINER -> OperationalContext.Provider("legacy-trainer", "Servicio", "TRAINING")
    AccountType.WALKER -> OperationalContext.Provider("legacy-walker", "Paseos", "WALKING")
}

@Composable
fun ComunidappBottomBar(
    navController: NavController,
    context: OperationalContext
) {
    val items = bottomNavItemsFor(context)
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val onProfileCreator = currentRoute == NavRoutes.PUBLISH_FROM_PROFILE
    val onHomeStoryCreator = currentRoute == NavRoutes.PUBLISH_STORY
    val onMiManada = com.comunidapp.app.domain.capability.StartupNavigationPolicy
        .keepsTopLevelNavigation(currentRoute)

    NavigationBar(
        containerColor = BrandWhite,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        items.forEach { item ->
            val selected = when {
                onProfileCreator && item.route == NavRoutes.PUBLISH -> false
                onProfileCreator && item.route == NavRoutes.PROFILE -> true
                onHomeStoryCreator && item.route == NavRoutes.PUBLISH -> false
                onHomeStoryCreator && item.route == NavRoutes.HOME -> true
                onMiManada && item.route == NavRoutes.PROFILE -> true
                else -> currentRoute == item.route
            }
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute == item.route && !onProfileCreator && !onHomeStoryCreator && !onMiManada) {
                        return@NavigationBarItem
                    }
                    val goingHome = item.route == NavRoutes.HOME
                    navController.navigate(item.route) {
                        popUpTo(NavRoutes.HOME) { saveState = !goingHome }
                        launchSingleTop = true
                        restoreState = !goingHome
                    }
                },
                icon = {
                    if (item.prominent) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .border(
                                    width = 1.5.dp,
                                    color = if (selected) BrandOrange else BrandGrayMedium,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = item.label,
                                tint = if (selected) BrandOrange else BrandGrayMedium,
                                modifier = Modifier.size(22.dp)
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
                    indicatorColor = Color.Transparent,
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
        val items = bottomNavItemsFor(OperationalContext.Personal)
        NavigationBar(containerColor = BrandWhite, tonalElevation = 0.dp) {
            items.forEachIndexed { index, item ->
                val selected = index == 0
                NavigationBarItem(
                    selected = selected,
                    onClick = {},
                    icon = {
                        if (item.prominent) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .border(1.5.dp, BrandOrange, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = item.label, tint = BrandOrange, modifier = Modifier.size(22.dp))
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
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = BrandGrayMedium,
                        unselectedTextColor = BrandGrayMedium
                    )
                )
            }
        }
    }
}
