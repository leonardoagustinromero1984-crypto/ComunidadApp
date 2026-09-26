package com.comunidapp.app.ui.screens.pets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.ui.components.PetCard
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.ui.components.leo.LeoFilterChip
import com.comunidapp.app.ui.components.leo.LeoEmptyState
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.BrandWhite
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.util.formatDisplayDate
import com.comunidapp.app.viewmodel.MyPetsViewModel

@Composable
fun MyPetsScreen(
    onNavigateBack: () -> Unit,
    onPetClick: (String) -> Unit,
    onAddPet: () -> Unit = {},
    onImportRescuer: (() -> Unit)? = null,
    onOpenIncomingTransfer: (String) -> Unit = {},
    viewModel: MyPetsViewModel = viewModel()
) {
    val pets by viewModel.pets.collectAsState()
    val incomingTransfers by viewModel.incomingTransfers.collectAsState()
    val acceptNotice by viewModel.acceptNotice.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(acceptNotice) {
        val message = acceptNotice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeAcceptNotice()
    }
    val activeContext by com.comunidapp.app.domain.context.OperationalContextProvider.active.collectAsState()
    val showImportTools = onImportRescuer != null && (
        com.comunidapp.app.data.provider.DataProvider.personCapabilityRepository.hasActive(
            com.comunidapp.app.domain.capability.PersonCapabilityCode.RESCUER
        ) ||
            activeContext is com.comunidapp.app.domain.context.OperationalContext.Rescuer
        )
    var needsPhotoOnly by remember { mutableStateOf(false) }
    val visible = if (showImportTools && needsPhotoOnly) {
        pets.filter { it.photoUrl.isNullOrBlank() && it.avatarFileAssetId.isNullOrBlank() }
    } else {
        pets
    }
    var showImportHelp by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.onVisible()
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Mis mascotas",
                subtitle = "Identidad y salud de tus compañeros",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = LeoDimens.SpaceMd,
                    end = LeoDimens.SpaceMd,
                    top = padding.calculateTopPadding() + LeoDimens.SpaceSm,
                    bottom = padding.calculateBottomPadding() + 72.dp
                ),
                verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
            ) {
                item {
                    com.comunidapp.app.ui.components.ContextualFirstVisitHelp(
                        helpId = com.comunidapp.app.domain.onboarding.ContextualHelpId.PET_PASSPORT,
                        message = com.comunidapp.app.ui.components.ContextualHelpMessages.PET_PASSPORT
                    )
                }
                if (incomingTransfers.isNotEmpty()) {
                    items(incomingTransfers, key = { "incoming-${it.id.value}" }) { transfer ->
                        IncomingCareTransferCard(
                            transfer = transfer,
                            enabled = true,
                            onAccept = { viewModel.acceptIncoming(transfer) },
                            onReject = { viewModel.rejectIncoming(transfer) }
                        )
                    }
                }
                if (showImportTools) {
                    val importRescuer = onImportRescuer
                    item {
                        LeoFilterChip(
                            label = "Necesitan foto",
                            selected = needsPhotoOnly,
                            onClick = { needsPhotoOnly = !needsPhotoOnly }
                        )
                    }
                    item {
                        androidx.compose.material3.OutlinedButton(
                            onClick = onAddPet,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ Agregar mascota")
                        }
                    }
                    item {
                        androidx.compose.material3.OutlinedButton(
                            onClick = importRescuer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Importar mascotas")
                        }
                    }
                    item {
                        androidx.compose.material3.TextButton(onClick = { showImportHelp = true }) {
                            Text("Cómo funciona la importación")
                        }
                    }
                }
                if (visible.isEmpty()) {
                    item {
                        LeoEmptyState(
                            title = "Todavía no tenés mascotas",
                            message = "Tocá + para registrar la primera.",
                            icon = Icons.Default.Pets
                        )
                    }
                }
                items(visible, key = { it.id }) { pet ->
                    PetCard(pet = pet, onClick = { onPetClick(pet.id) })
                    PetHealthCard(pet = pet)
                }
            }
            FloatingActionButton(
                onClick = onAddPet,
                containerColor = BrandWhite,
                contentColor = BrandText,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = LeoDimens.SpaceMd,
                        bottom = padding.calculateBottomPadding() + LeoDimens.SpaceMd
                    )
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar mascota")
            }
        }
    }
    if (showImportHelp) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showImportHelp = false },
            title = { Text("Cómo funciona la importación") },
            text = {
                Text(
                    "1. Descargá la plantilla.\n" +
                        "2. Completá los datos.\n" +
                        "3. Subí el Excel.\n" +
                        "4. Revisá la validación.\n" +
                        "5. Confirmá.\n" +
                        "6. Las mascotas se crean en LeoVer.\n" +
                        "7. Las que no tienen imagen quedan marcadas “Necesita foto”.\n" +
                        "8. Abrí la mascota y agregá la foto después.\n\n" +
                        "Este texto no se vuelve a mostrar solo: siempre podés abrirlo desde Tutorial / Ayuda."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showImportHelp = false }) {
                    Text("Entendido")
                }
            }
        )
    }
}

@Composable
private fun PetHealthCard(pet: Pet) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LeoDimens.SpaceMd, vertical = LeoDimens.SpaceS)
    ) {
        Text(
            text = "Salud de ${pet.name}",
            style = LeoCardTitle,
            color = BrandText
        )
        pet.sterilized?.let {
            Text(
                text = "Castración: ${it.toDisplayName()}",
                style = LeoCaption,
                color = BrandTextSecondary,
                modifier = Modifier.padding(top = LeoDimens.SpaceMicro)
            )
        }
        pet.lastVetVisit?.let {
            Text(
                text = "Última consulta: ${formatDisplayDate(it)}",
                style = LeoCaption,
                color = BrandTextSecondary,
                modifier = Modifier.padding(top = LeoDimens.SpaceMicro)
            )
        }
        pet.vaccinations.forEach { vac ->
            val next = vac.nextDueDate?.takeIf { d -> d.isNotBlank() }?.let { " · Próx: ${formatDisplayDate(it)}" }.orEmpty()
            Text(
                text = "💉 ${vac.name}: ${formatDisplayDate(vac.date)}$next",
                style = LeoCaption,
                color = BrandTextSecondary,
                modifier = Modifier.padding(top = LeoDimens.SpaceMicro)
            )
        }
        pet.lastDeworming?.let {
            val product = pet.dewormingProduct?.let { p -> " ($p)" }.orEmpty()
            Text(
                text = "🪱 Desparasitación: ${formatDisplayDate(it)}$product",
                style = LeoCaption,
                color = BrandTextSecondary,
                modifier = Modifier.padding(top = LeoDimens.SpaceMicro)
            )
        }
        pet.lastFleaTreatment?.let {
            val product = pet.fleaTreatmentProduct?.let { p -> " ($p)" }.orEmpty()
            Text(
                text = "🐾 Antiparasitarios: ${formatDisplayDate(it)}$product",
                style = LeoCaption,
                color = BrandTextSecondary,
                modifier = Modifier.padding(top = LeoDimens.SpaceMicro)
            )
        }
        pet.healthNotes?.let {
            Text(
                text = "Notas: $it",
                style = LeoCaption,
                color = BrandTextSecondary,
                modifier = Modifier.padding(top = LeoDimens.SpaceMicro)
            )
        }
        if (pet.reminders.isNotEmpty()) {
            Text(
                text = "Recordatorios:",
                style = LeoCaption,
                fontWeight = FontWeight.SemiBold,
                color = BrandText,
                modifier = Modifier.padding(top = LeoDimens.SpaceSm)
            )
            pet.reminders.forEach { reminder ->
                Text(
                    text = "⏰ ${reminder.title} — ${reminder.date}",
                    style = LeoCaption,
                    color = BrandTextSecondary
                )
            }
        }
        LeoHairline(modifier = Modifier.padding(top = LeoDimens.SpaceS))
    }
}
