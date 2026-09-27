package com.comunidapp.app.ui.screens.sumate.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.AdoptionEvent
import com.comunidapp.app.data.model.DonationCampaign
import com.comunidapp.app.data.model.FosterHomeListing
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.ui.components.v2.V2NavRow
import com.comunidapp.app.ui.screens.shelters.ShelterListCard
import com.comunidapp.app.ui.theme.BrandGreen
import com.comunidapp.app.ui.theme.BrandGreenDark
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.viewmodel.CommunityViewModel
import com.comunidapp.app.viewmodel.SheltersViewModel

@Composable
fun FosterHomesContent(
    bottomPadding: Dp = 0.dp,
    onOpenFosterHomes: () -> Unit = {},
    viewModel: CommunityViewModel = viewModel()
) {
    val homes by viewModel.fosterHomes.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    androidx.compose.material3.Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = bottomPadding + 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                LeoPrimaryButton(
                    text = "Abrir hogares de tránsito",
                    onClick = onOpenFosterHomes
                )
            }
            items(homes, key = { it.id }) { home ->
                FosterHomeCard(
                    home = home,
                    onRequest = { viewModel.requestFoster(home) }
                )
            }
        }
    }
}

@Composable
private fun FosterHomeCard(
    home: FosterHomeListing,
    onRequest: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(vertical = LeoDimens.SpaceCompact),
            horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            PetImage(
                imageUrl = home.photoUrl,
                modifier = Modifier.size(72.dp),
                contentDescription = home.hostName
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = home.hostName,
                    style = LeoCardTitle,
                    color = BrandText
                )
                Text(
                    text = home.location,
                    style = LeoCaption,
                    color = BrandTextSecondary
                )
                Text(
                    text = if (home.available) "Disponible" else "No disponible",
                    style = LeoCaption,
                    color = if (home.available) BrandGreen else BrandTextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "Capacidad: ${home.capacity} · ${home.acceptedSpecies.joinToString { it.toDisplayName() }}",
                    style = LeoCaption,
                    color = BrandTextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = home.notes,
                    style = LeoCaption,
                    color = BrandText,
                    modifier = Modifier.padding(top = 6.dp),
                    maxLines = 3
                )
                Text(
                    text = "Contacto: ${home.contactInfo}",
                    style = LeoCaption,
                    color = BrandTextSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
                if (home.available) {
                    LeoPrimaryButton(
                        text = "Solicitar tránsito",
                        onClick = onRequest,
                        fillMaxWidth = false
                    )
                }
            }
        }
        LeoHairline()
    }
}

@Composable
fun AdoptionEventsContent(
    onM18Events: () -> Unit = {},
    bottomPadding: Dp = 0.dp,
    viewModel: CommunityViewModel = viewModel()
) {
    val events by viewModel.events.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    androidx.compose.material3.Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = bottomPadding + 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                LeoOutlinedButton(
                    text = "Eventos comunitarios",
                    onClick = onM18Events
                )
            }
            items(events, key = { it.id }) { event ->
                AdoptionEventCard(
                    event = event,
                    onInterest = { viewModel.expressEventInterest(event) }
                )
            }
        }
    }
}

@Composable
private fun AdoptionEventCard(
    event: AdoptionEvent,
    onInterest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = LeoDimens.SpaceCompact)
    ) {
            if (event.photoUrl != null) {
                PetImage(
                    imageUrl = event.photoUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = LeoDimens.SpaceCompact),
                    contentDescription = event.title
                )
            }
            Text(
                text = event.title,
                style = LeoCardTitle,
                color = BrandText
            )
            Text(
                text = event.date,
                style = LeoCaption,
                color = BrandTextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = event.location,
                style = LeoCaption,
                color = BrandTextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = "Organiza: ${event.organizerName}",
                style = LeoCaption,
                color = BrandTextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = event.description,
                style = LeoCaption,
                color = BrandText,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "Contacto: ${event.contactInfo}",
                style = LeoCaption,
                color = BrandTextSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
            TextButton(onClick = onInterest, modifier = Modifier.padding(top = 4.dp)) {
                Text("Me interesa")
            }
        LeoHairline()
    }
}

@Composable
fun SheltersContent(
    onShelterClick: (String) -> Unit,
    onShelterOps: () -> Unit = {},
    onVeterinaryDirectory: () -> Unit = {},
    onM16Shelters: () -> Unit = {},
    bottomPadding: Dp = 0.dp,
    viewModel: SheltersViewModel = viewModel()
) {
    val shelters by viewModel.shelters.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = bottomPadding + 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            V2NavRow(
                title = "Refugios",
                description = "Conocé organizaciones y animales que necesitan ayuda",
                icon = Icons.Default.Home,
                onClick = onM16Shelters,
                iconTint = BrandGreenDark
            )
        }
        item {
            V2NavRow(
                title = "Mis organizaciones",
                description = "Administrá equipos, publicaciones y casos",
                icon = Icons.Default.Groups,
                onClick = onShelterOps
            )
        }
        item {
            V2NavRow(
                title = "Veterinarias",
                description = "Encontrá atención cerca de tu ubicación",
                icon = Icons.Default.LocalHospital,
                onClick = onVeterinaryDirectory,
                iconTint = BrandGreenDark
            )
        }
        items(shelters, key = { it.id }) { shelter ->
            ShelterListCard(shelter = shelter, onClick = { onShelterClick(shelter.id) })
        }
    }
}

@Composable
fun DonationsContent(
    onM17Campaigns: () -> Unit = {},
    bottomPadding: Dp = 0.dp,
    viewModel: CommunityViewModel = viewModel()
) {
    val campaigns by viewModel.donations.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = bottomPadding + 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            V2NavRow(
                title = "Campañas solidarias",
                description = "Apoyá causas y donaciones de la comunidad",
                icon = Icons.Default.VolunteerActivism,
                onClick = onM17Campaigns
            )
        }
        items(campaigns, key = { it.id }) { campaign ->
            DonationCampaignCard(campaign = campaign)
        }
    }
}

@Composable
private fun DonationCampaignCard(campaign: DonationCampaign) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = LeoDimens.SpaceCompact)
    ) {
            Text(
                text = campaign.title,
                style = LeoCardTitle,
                color = BrandText
            )
            Text(
                text = campaign.location,
                style = LeoCaption,
                color = BrandTextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = campaign.description,
                style = LeoCaption,
                color = BrandText,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "Tipo: ${campaign.donationType.name}",
                style = LeoCaption,
                color = BrandTextSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
            campaign.goalAmount?.let { goal ->
                Text(
                    text = "Meta: $$goal · Recaudado: $${campaign.raisedAmount}",
                    style = LeoCaption,
                    color = BrandTextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        LeoHairline(modifier = Modifier.padding(top = LeoDimens.SpaceCompact))
    }
}
