package com.comunidapp.app.ui.screens.publish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.domain.AppMode
import com.comunidapp.app.domain.RolePermissions
import com.comunidapp.app.domain.toAppMode
import com.comunidapp.app.ui.components.ComunidappTopBar
import kotlinx.coroutines.launch

data class PublishOption(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit
)

@Composable
fun PublishScreen(
    accountType: AccountType,
    onNavigateToGeneral: () -> Unit,
    onNavigateToQuestion: () -> Unit,
    onNavigateToPromo: () -> Unit,
    onNavigateToAdoption: () -> Unit,
    onNavigateToLostFound: () -> Unit,
    onNavigateToUrgent: () -> Unit,
    onNavigateToFoster: () -> Unit = {},
    onNavigateToEvent: () -> Unit = {},
    onNavigateToDonation: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showSoon: () -> Unit = {
        scope.launch { snackbarHostState.showSnackbar("Próximamente en Leover") }
    }

    val options = buildPublishOptions(
        accountType = accountType,
        onNavigateToGeneral = onNavigateToGeneral,
        onNavigateToQuestion = onNavigateToQuestion,
        onNavigateToPromo = onNavigateToPromo,
        onNavigateToAdoption = onNavigateToAdoption,
        onNavigateToLostFound = onNavigateToLostFound,
        onNavigateToUrgent = onNavigateToUrgent,
        onNavigateToFoster = onNavigateToFoster,
        onNavigateToEvent = onNavigateToEvent,
        onNavigateToDonation = onNavigateToDonation,
        onSoon = showSoon
    )

    val subtitle = when (accountType.toAppMode()) {
        AppMode.NEGOCIO -> "Promocioná tu negocio y conectá con la comunidad"
        AppMode.SOLIDARIO -> "Publicá adopciones, causas y avisos solidarios"
        AppMode.PERSONA -> "Compartí con la comunidad animal de tu zona"
    }

    Scaffold(
        topBar = { ComunidappTopBar(title = "Publicar") },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "¿Qué querés publicar?", style = MaterialTheme.typography.titleLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            options.forEach { option ->
                PublishOptionCard(
                    icon = option.icon,
                    title = option.title,
                    description = option.description,
                    onClick = option.onClick
                )
            }
        }
    }
}

private fun buildPublishOptions(
    accountType: AccountType,
    onNavigateToGeneral: () -> Unit,
    onNavigateToQuestion: () -> Unit,
    onNavigateToPromo: () -> Unit,
    onNavigateToAdoption: () -> Unit,
    onNavigateToLostFound: () -> Unit,
    onNavigateToUrgent: () -> Unit,
    onNavigateToFoster: () -> Unit,
    onNavigateToEvent: () -> Unit,
    onNavigateToDonation: () -> Unit,
    onSoon: () -> Unit
): List<PublishOption> = buildList {
    add(
        PublishOption(
            icon = Icons.Default.Add,
            title = "Publicación general",
            description = "Foto, historia o novedad para el feed",
            onClick = onNavigateToGeneral
        )
    )
    add(
        PublishOption(
            icon = Icons.Default.Search,
            title = "Aviso urgente",
            description = "Alerta prioritaria para la comunidad",
            onClick = onNavigateToUrgent
        )
    )
    if (RolePermissions.canPublishPromo(accountType)) {
        add(
            PublishOption(
                icon = Icons.Default.Campaign,
                title = "Publicidad / promo",
                description = "Oferta, descuento o novedad de tu negocio",
                onClick = onNavigateToPromo
            )
        )
    }
    if (RolePermissions.canPublishQuestion(accountType)) {
        add(
            PublishOption(
                icon = Icons.Default.Help,
                title = "Pregunta a la comunidad",
                description = "Consultá sobre salud, alimento, conducta y más",
                onClick = onNavigateToQuestion
            )
        )
    }
    if (RolePermissions.canPublishAdoption(accountType)) {
        add(
            PublishOption(
                icon = Icons.Default.Favorite,
                title = "Animal en adopción",
                description = "Publicá un animal que busca familia",
                onClick = onNavigateToAdoption
            )
        )
    }
    if (RolePermissions.canPublishLostFound(accountType)) {
        add(
            PublishOption(
                icon = Icons.Default.Search,
                title = "Perdido / Encontrado",
                description = "Ayudá a reunir mascotas con sus familias",
                onClick = onNavigateToLostFound
            )
        )
    }
    if (RolePermissions.canPublishFosterHome(accountType)) {
        add(
            PublishOption(
                icon = Icons.Default.Home,
                title = "Ofrecer hogar de tránsito",
                description = "Indicá que podés recibir mascotas temporalmente",
                onClick = onNavigateToFoster
            )
        )
    }
    if (RolePermissions.canPublishShelterNeeds(accountType)) {
        add(
            PublishOption(
                icon = Icons.Default.CalendarMonth,
                title = "Evento de adopción",
                description = "Feria, jornada o encuentro comunitario",
                onClick = onNavigateToEvent
            )
        )
        add(
            PublishOption(
                icon = Icons.Default.VolunteerActivism,
                title = "Donación / voluntariado",
                description = "Campaña solidaria o llamado a colaborar",
                onClick = onNavigateToDonation
            )
        )
    }
}

@Composable
private fun PublishOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
