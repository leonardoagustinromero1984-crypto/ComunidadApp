package com.comunidapp.app.ui.screens.sumate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.v2.V2NavRow
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.ui.theme.BrandGreen
import com.comunidapp.app.ui.theme.BrandGreenContainer
import com.comunidapp.app.ui.theme.BrandGreenDark
import com.comunidapp.app.ui.theme.BrandOrange
import com.comunidapp.app.ui.theme.BrandOrangeContainer
import com.comunidapp.app.ui.theme.BrandOrangeSoft
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.UrgentContainer
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.ui.theme.UrgentRed
import com.comunidapp.app.viewmodel.SumateViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SumateScreen(
    onAdoptionClick: (String) -> Unit,
    onShelterClick: (String) -> Unit,
    onNavigateToMap: () -> Unit = {},
    onMyApplications: () -> Unit = {},
    onReceivedApplications: () -> Unit = {},
    onFosterHomes: () -> Unit = {},
    onShelterOps: () -> Unit = {},
    onVeterinaryDirectory: () -> Unit = {},
    onM16Shelters: () -> Unit = {},
    onM17Campaigns: () -> Unit = {},
    onM18Events: () -> Unit = {},
    onNavigateToPublish: () -> Unit = {},
    onCreateAdoption: () -> Unit = {},
    onCreateLost: () -> Unit = {},
    onCreateFound: () -> Unit = {},
    onCreateFoster: () -> Unit = {},
    onCreateEvent: () -> Unit = {},
    onOpenAdoptions: () -> Unit = {},
    onOpenLostFound: () -> Unit = {},
    onOpenFosterRequests: () -> Unit = {},
    context: OperationalContext = OperationalContext.Personal,
    viewModel: SumateViewModel = viewModel()
) {
    @Suppress("UNUSED_PARAMETER")
    val preserved = remember {
        listOf(
            onAdoptionClick,
            onShelterClick,
            onNavigateToMap,
            onMyApplications,
            onReceivedApplications,
            onVeterinaryDirectory,
            onM16Shelters,
            onNavigateToPublish,
            onCreateAdoption,
            onCreateLost,
            onCreateFound,
            onCreateFoster,
            onCreateEvent,
            viewModel
        )
    }

    Scaffold(
        containerColor = BrandBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LeoTopAppBar(
                title = "Sumate",
                subtitle = "Elegí cómo querés ayudar hoy"
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
        ) {
            V2NavRow(
                title = "Adopción",
                description = if (context.isPersonal) {
                    "Explorá mascotas, quiero adoptar, mi perfil y postulaciones"
                } else {
                    "Publicar en adopción, postulaciones recibidas y seguimiento"
                },
                icon = Icons.Default.Pets,
                onClick = onOpenAdoptions,
                iconTint = BrandOrange,
                iconContainer = BrandOrangeContainer
            )
            V2NavRow(
                title = "Refugios / ONG",
                description = if (context.isPersonal) {
                    "Encontrá refugios y organizaciones cerca"
                } else {
                    "Organizaciones que rescatan y cuidan"
                },
                icon = Icons.Default.Store,
                onClick = if (context.isPersonal) onM16Shelters else onShelterOps,
                iconTint = BrandGreenDark,
                iconContainer = BrandGreenContainer
            )
            if (context.isPersonal) {
                V2NavRow(
                    title = "Ofrecer hogar de tránsito",
                    description = "Quiero colaborar como tránsito",
                    icon = Icons.Default.HomeWork,
                    onClick = onCreateFoster,
                    iconTint = BrandOrangeSoft,
                    iconContainer = BrandOrangeContainer
                )
                V2NavRow(
                    title = "Solicitudes de tránsito",
                    description = "Mascotas que buscan un hogar temporal",
                    icon = Icons.Default.HomeWork,
                    onClick = onOpenFosterRequests,
                    iconTint = BrandOrangeSoft,
                    iconContainer = BrandOrangeContainer
                )
            } else if (context is OperationalContext.Foster) {
                V2NavRow(
                    title = "Hogares de tránsito",
                    description = "Gestión de tránsitos y solicitudes",
                    icon = Icons.Default.HomeWork,
                    onClick = onFosterHomes,
                    iconTint = BrandOrangeSoft,
                    iconContainer = BrandOrangeContainer
                )
                V2NavRow(
                    title = "Solicitudes de tránsito",
                    description = "Mascotas que buscan un hogar temporal",
                    icon = Icons.Default.HomeWork,
                    onClick = onOpenFosterRequests,
                    iconTint = BrandOrangeSoft,
                    iconContainer = BrandOrangeContainer
                )
            }
            V2NavRow(
                title = "Perdidos / Encontrados",
                description = "Alertas y avistamientos de mascotas",
                icon = Icons.Default.Search,
                onClick = onOpenLostFound,
                iconTint = UrgentRed,
                iconContainer = UrgentContainer
            )
            V2NavRow(
                title = "Donaciones",
                description = "Campañas e insumos para causas reales",
                icon = Icons.Default.VolunteerActivism,
                onClick = onM17Campaigns,
                iconTint = BrandGreen,
                iconContainer = BrandGreenContainer
            )
            V2NavRow(
                title = "Eventos",
                description = "Ferias, jornadas y encuentros solidarios",
                icon = Icons.Default.Event,
                onClick = onM18Events,
                iconTint = BrandGreenDark,
                iconContainer = BrandGreenContainer
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFDF8, widthDp = 390)
@Composable
private fun SumateHubPreview() {
    ComunidappTheme {
        SumateScreen(onAdoptionClick = {}, onShelterClick = {})
    }
}
