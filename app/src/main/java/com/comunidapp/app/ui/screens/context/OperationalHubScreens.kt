package com.comunidapp.app.ui.screens.context

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.comunidapp.app.domain.context.ContextHumanLabels
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.ui.components.v2.V2NavRow
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoPageTitle
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.MutedText
import androidx.compose.material3.Text

data class OperationalHubActions(
    val onProfile: () -> Unit = {},
    val onAnimals: () -> Unit = {},
    val onAddPet: () -> Unit = {},
    val onImportPets: () -> Unit = {},
    val onAdoptions: () -> Unit = {},
    val onCampaigns: () -> Unit = {},
    val onEvents: () -> Unit = {},
    val onManagement: () -> Unit = {},
    val onTeam: () -> Unit = {},
    val onBranches: () -> Unit = {},
    val onFoster: () -> Unit = {},
    val onLostFound: () -> Unit = {},
    val onRescuerProfile: () -> Unit = {},
    val onVolunteer: () -> Unit = {}
)

@Composable
fun RefugeOperationalHub(
    context: OperationalContext,
    actions: OperationalHubActions,
    showTeam: Boolean = true,
    showBranches: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceSm),
        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
    ) {
        Text("Refugio", style = LeoCaption, color = MutedText)
        Text(
            context.displayName.removePrefix("Refugio · ").ifBlank { context.displayName },
            style = LeoPageTitle,
            color = BrandText
        )
        Spacer(Modifier.height(LeoDimens.SpaceMicro))
        V2NavRow("Perfil del refugio", "Datos públicos de la organización", Icons.Default.Storefront, actions.onProfile)
        V2NavRow("Animales", "Ver y administrar animales a cargo", Icons.Default.Pets, actions.onAnimals)
        V2NavRow("+ Agregar mascota", "Alta manual de un animal", Icons.Default.Pets, actions.onAddPet)
        V2NavRow("Importar mascotas", "Carga masiva VitaCora", Icons.Default.Pets, actions.onImportPets)
        V2NavRow("Adopciones", "Publicaciones y postulaciones", Icons.Default.Favorite, actions.onAdoptions)
        V2NavRow("Campañas", "Ayuda y donaciones", Icons.Default.Campaign, actions.onCampaigns)
        V2NavRow("Eventos", "Actividades del refugio", Icons.Default.Event, actions.onEvents)
        V2NavRow("Gestión", "Operación diaria", Icons.Default.Settings, actions.onManagement)
        if (showTeam) {
            V2NavRow("Equipo e invitaciones", "Quién opera este refugio", Icons.Default.Groups, actions.onTeam)
        }
        if (showBranches) {
            V2NavRow("Sucursales", "Sedes del refugio", Icons.Default.HomeWork, actions.onBranches)
        }
    }
}

@Composable
fun RescuerOperationalHub(
    context: OperationalContext,
    actions: OperationalHubActions
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceSm),
        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
    ) {
        Text(ContextHumanLabels.homeBrandLine(context), style = LeoCaption, color = MutedText)
        Text("Rescatista", style = LeoPageTitle, color = BrandText)
        Text(
            "Operación de rescate independiente. Sin equipo ni sucursales.",
            style = LeoCaption,
            color = MutedText
        )
        Spacer(Modifier.height(LeoDimens.SpaceMicro))
        V2NavRow("Animales rescatados", "Mascotas a tu cargo", Icons.Default.Pets, actions.onAnimals)
        V2NavRow("+ Agregar mascota", "Alta manual de un rescatado", Icons.Default.Pets, actions.onAddPet)
        V2NavRow("Importar mascotas", "Carga VitaCora", Icons.Default.Pets, actions.onImportPets)
        V2NavRow("Adopciones", "Publicaciones y postulaciones", Icons.Default.Favorite, actions.onAdoptions)
        V2NavRow("Perdidos / Encontrados", "Casos cuando corresponda", Icons.Default.Pets, actions.onLostFound)
        V2NavRow("Hogares de tránsito", "Coordinación de tránsitos", Icons.Default.HomeWork, actions.onFoster)
        V2NavRow("Campañas", "Ayuda permitida", Icons.Default.Campaign, actions.onCampaigns)
        V2NavRow("Bienes / Voluntariado", "Pedidos y ofertas de ayuda", Icons.Default.Groups, actions.onVolunteer)
        V2NavRow("Eventos", "Actividades de rescate", Icons.Default.Event, actions.onEvents)
        V2NavRow("Perfil de Rescatista", "Cómo te ven en LeoVer", Icons.Default.Storefront, actions.onRescuerProfile)
        Spacer(Modifier.height(LeoDimens.SpaceLg))
    }
}

@Composable
fun FosterOperationalHub(
    context: OperationalContext,
    actions: OperationalHubActions
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceSm)
    ) {
        Text(ContextHumanLabels.homeBrandLine(context), style = LeoCaption, color = MutedText)
        Text("Hogar de tránsito", style = LeoPageTitle, color = BrandText)
        Text(
            "Cuidado temporal. El responsable de la mascota no cambia.",
            style = LeoCaption,
            color = MutedText
        )
        Spacer(Modifier.height(LeoDimens.SpaceMicro))
        V2NavRow("Tránsitos", "Mascotas que estás alojando", Icons.Default.HomeWork, actions.onFoster)
        V2NavRow("Solicitudes", "Pedidos de tránsito recibidos", Icons.Default.Groups, actions.onVolunteer)
        V2NavRow("+ Agregar mascota", "Alta de un animal a tu cuidado", Icons.Default.Pets, actions.onAddPet)
    }
}
