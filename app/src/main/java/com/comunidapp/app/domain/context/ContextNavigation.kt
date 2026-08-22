package com.comunidapp.app.domain.context

import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Storefront
import com.comunidapp.app.navigation.NavRoutes
import com.comunidapp.app.ui.components.BottomNavItem

/**
 * NavigationSpec por contexto.
 * PERSONAL está cerrado (board UI V2).
 * Otros contextos reutilizan rutas funcionales actuales; no inventan UX final.
 */
object ContextNavigation {

    fun itemsFor(context: OperationalContext): List<BottomNavItem> = when (context.kind) {
        OperationalContextKind.PERSONAL -> personalItems()
        OperationalContextKind.ORGANIZATION -> {
            if (ContextIdentityMapping.isRefugeNav(context)) {
                organizationItems()
            } else {
                professionalItems(context)
            }
        }
        OperationalContextKind.FOSTER -> fosterItems()
        OperationalContextKind.RESCUER -> rescuerItems()
        OperationalContextKind.PROVIDER,
        OperationalContextKind.VETERINARY,
        OperationalContextKind.SHOP -> professionalItems(context)
    }

    fun personalItems(): List<BottomNavItem> = listOf(
        BottomNavItem(NavRoutes.HOME, "Inicio", Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem(NavRoutes.SUMATE, "Sumate", Icons.Filled.Handshake, Icons.Outlined.Handshake),
        BottomNavItem(NavRoutes.PUBLISH, "Publicar", Icons.Filled.AddCircle, Icons.Filled.AddCircle, prominent = true),
        BottomNavItem(NavRoutes.COMUNIDAD, "Comunidad", Icons.Filled.Groups, Icons.Outlined.Groups),
        BottomNavItem(NavRoutes.PROFILE, "Perfil", Icons.Filled.Person, Icons.Outlined.Person)
    )

    private fun organizationItems(): List<BottomNavItem> = listOf(
        BottomNavItem(NavRoutes.HOME, "Inicio", Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem(NavRoutes.MY_SHELTERS, "Animales", Icons.Filled.Pets, Icons.Outlined.Pets),
        BottomNavItem(NavRoutes.PUBLISH, "Publicar", Icons.Filled.AddCircle, Icons.Filled.AddCircle, prominent = true),
        BottomNavItem(NavRoutes.SHELTERS, "Gestión", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
        BottomNavItem(NavRoutes.PROFILE, "Perfil", Icons.Filled.Person, Icons.Outlined.Person)
    )

    private fun rescuerItems(): List<BottomNavItem> = listOf(
        BottomNavItem(NavRoutes.HOME, "Inicio", Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem(NavRoutes.MY_PETS, "Animales", Icons.Filled.Pets, Icons.Outlined.Pets),
        BottomNavItem(NavRoutes.PUBLISH, "Publicar", Icons.Filled.AddCircle, Icons.Filled.AddCircle, prominent = true),
        BottomNavItem(NavRoutes.SHELTERS, "Gestión", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
        BottomNavItem(NavRoutes.PROFILE, "Perfil", Icons.Filled.Person, Icons.Outlined.Person)
    )

    private fun fosterItems(): List<BottomNavItem> = listOf(
        BottomNavItem(NavRoutes.HOME, "Inicio", Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem(NavRoutes.FOSTER_PLACEMENTS, "Tránsitos", Icons.Filled.HomeWork, Icons.Outlined.HomeWork),
        BottomNavItem(NavRoutes.PUBLISH, "Publicar", Icons.Filled.AddCircle, Icons.Filled.AddCircle, prominent = true),
        BottomNavItem(
            NavRoutes.FOSTER_REQUESTS_RECEIVED,
            "Solicitudes",
            Icons.Filled.Assignment,
            Icons.Outlined.Assignment
        ),
        BottomNavItem(NavRoutes.PROFILE, "Perfil", Icons.Filled.Person, Icons.Outlined.Person)
    )

    private fun professionalItems(context: OperationalContext): List<BottomNavItem> {
        val token = when (context) {
            is OperationalContext.Provider -> context.category.orEmpty()
            is OperationalContext.Organization -> context.organizationType.orEmpty()
            is OperationalContext.Veterinary -> "VETERINARY"
            is OperationalContext.Shop -> "SHOP"
            else -> ""
        }
        val daycare = ContextIdentityMapping.isDaycare(token)
        val veterinary = context is OperationalContext.Veterinary ||
            ContextIdentityMapping.isVeterinary(token)
        val secondLabel: String
        val secondRoute: String
        val fourthLabel: String
        val fourthRoute: String
        when {
            veterinary -> {
                secondLabel = "Agenda"
                secondRoute = NavRoutes.MY_VETERINARY_APPOINTMENTS
                fourthLabel = "Consultorio"
                fourthRoute = NavRoutes.MY_BUSINESS
            }
            daycare -> {
                secondLabel = "Reservas"
                secondRoute = NavRoutes.DAYCARE_RESERVATIONS
                fourthLabel = "Huéspedes"
                fourthRoute = NavRoutes.DAYCARE_GUESTS
            }
            else -> {
                secondLabel = "Agenda"
                secondRoute = NavRoutes.MY_BUSINESS
                fourthLabel = "Mi servicio"
                fourthRoute = NavRoutes.MY_BUSINESS
            }
        }
        return listOf(
            BottomNavItem(NavRoutes.HOME, "Inicio", Icons.Filled.Home, Icons.Outlined.Home),
            BottomNavItem(secondRoute, secondLabel, Icons.Filled.Event, Icons.Filled.Event),
            BottomNavItem(NavRoutes.PUBLISH, "Publicar", Icons.Filled.AddCircle, Icons.Filled.AddCircle, prominent = true),
            BottomNavItem(fourthRoute, fourthLabel, Icons.Filled.Storefront, Icons.Outlined.Storefront),
            BottomNavItem(NavRoutes.PROFILE, "Perfil", Icons.Filled.Person, Icons.Outlined.Person)
        )
    }
}
