package com.comunidapp.app.ui.screens.pets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.components.ageDisplay
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoCardTitle
import com.comunidapp.app.viewmodel.PetDetailViewModel

@Composable
fun PetDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit = {},
    onNavigateToEditHealth: (String) -> Unit = onNavigateToEdit,
    onDeleteSuccess: () -> Unit = {},
    onNavigateToResponsibilities: (String) -> Unit = {},
    onNavigateToAuthorizations: (String) -> Unit = {},
    onNavigateToTransfers: (String) -> Unit = {},
    onNavigateToStatusHistory: (String) -> Unit = {},
    onNavigateToPassport: (String) -> Unit = {},
    onNavigateToShareQr: (String) -> Unit = onNavigateToPassport,
    onNavigateToM28Grants: (String) -> Unit = {},
    onNavigateToM28Proposals: (String) -> Unit = {},
    onNavigateToReportLost: () -> Unit = {},
    onNavigateToFosterTransit: (String) -> Unit = {},
    viewModel: PetDetailViewModel = viewModel()
) {
    val pet by viewModel.pet.collectAsState()
    val photoDisplayUrl by viewModel.photoDisplayUrl.collectAsState()
    val isPetLoading by viewModel.isPetLoading.collectAsState()
    val isHealthLoading by viewModel.isHealthLoading.collectAsState()
    val healthLoadError by viewModel.healthLoadError.collectAsState()
    val petLoadError by viewModel.petLoadError.collectAsState()
    val statusReasonCode by viewModel.statusReasonCode.collectAsState()
    val canManage by viewModel.canManage.collectAsState()
    val canViewGovernance by viewModel.canViewGovernance.collectAsState()
    @Suppress("UNUSED_VARIABLE")
    val canMarkDeceased by viewModel.canMarkDeceased.collectAsState()
    val canRestore by viewModel.canRestore.collectAsState()
    @Suppress("UNUSED_VARIABLE")
    val canViewHistory by viewModel.canViewHistory.collectAsState()
    val deleteSuccess by viewModel.deleteSuccess.collectAsState()
    val lifecycleSuccess by viewModel.lifecycleSuccess.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val principalDisplayName by viewModel.principalDisplayName.collectAsState()
    val principalLoading by viewModel.principalLoading.collectAsState()
    val access by viewModel.access.collectAsState()
    val fosterSignals by com.comunidapp.app.domain.foster.FosterTransitSignals.live.state.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeceasedDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showAdminMenu by remember { mutableStateOf(false) }
    var deceasedReason by remember { mutableStateOf("") }
    val healthEditable = access?.canManageHealth == true || canManage

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadPet()
                viewModel.refreshFosterTransit()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(deleteSuccess) {
        if (deleteSuccess) {
            viewModel.clearDeleteSuccess()
            onDeleteSuccess()
        }
    }

    LaunchedEffect(lifecycleSuccess) {
        if (lifecycleSuccess) {
            viewModel.clearLifecycleSuccess()
            showDeceasedDialog = false
            showRestoreDialog = false
            deceasedReason = ""
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Archivar mascota") },
            text = {
                Text(
                    com.comunidapp.app.domain.pets.PetCareTransferCopy.ARCHIVE_BODY
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deletePet()
                    }
                ) {
                    Text("Archivar mascota")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showDeceasedDialog) {
        MarkPetDeceasedDialog(
            reason = deceasedReason,
            onReasonChange = { deceasedReason = it },
            isSubmitting = isSubmitting,
            onConfirm = { viewModel.markPetDeceased(deceasedReason) },
            onDismiss = {
                if (!isSubmitting) {
                    showDeceasedDialog = false
                    deceasedReason = ""
                }
            }
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showRestoreDialog = false },
            title = { Text("Reactivar mascota") },
            text = { Text("¿Volver a activar esta mascota archivada?") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.restorePet() },
                    enabled = !isSubmitting
                ) {
                    Text("Reactivar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestoreDialog = false },
                    enabled = !isSubmitting
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        containerColor = PetDetailV2Background(),
        topBar = {
            PetDetailV2TopBar(
                onBack = onNavigateBack,
                showMenu = access?.canArchive == true || canRestore,
                onMenuClick = { showAdminMenu = true }
            ) {
                DropdownMenu(
                    expanded = showAdminMenu,
                    onDismissRequest = { showAdminMenu = false }
                ) {
                    if (access?.canArchive == true && pet?.status == "ACTIVE") {
                        DropdownMenuItem(
                            text = { Text("Archivar mascota") },
                            onClick = {
                                showAdminMenu = false
                                showDeleteDialog = true
                            }
                        )
                    }
                    if (canRestore) {
                        DropdownMenuItem(
                            text = { Text("Reactivar mascota") },
                            onClick = {
                                showAdminMenu = false
                                showRestoreDialog = true
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        val data = pet
        when {
            isPetLoading && data == null -> {
                LoadingState(contentModifier = Modifier.padding(padding))
            }
            data == null && !petLoadError.isNullOrBlank() -> {
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    ErrorState(
                        message = petLoadError.orEmpty(),
                        title = "No se pudo abrir la mascota",
                        onRetry = viewModel::loadPet
                    )
                    TextButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        Text("Volver")
                    }
                }
            }
            data == null -> {
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    EmptyState(
                        title = "Mascota no disponible",
                        message = "No encontramos esta mascota o ya no tenés acceso.",
                        actionLabel = "Reintentar",
                        onAction = viewModel::loadPet
                    )
                    TextButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        Text("Volver")
                    }
                }
            }
            else -> {
                val isActive = data.status.equals("ACTIVE", ignoreCase = true)
                val isArchived = data.status.equals("ARCHIVED", ignoreCase = true)
                val isDeceased = data.status.equals("DECEASED", ignoreCase = true)
                val displayName = com.comunidapp.app.domain.pets.PetDisplayName.of(
                    data.originKind,
                    data.name
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 28.dp)
                ) {
                    PetHero(
                        imageUrl = photoDisplayUrl ?: data.photoUrl,
                        petName = displayName
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    PetIdentityBlock(
                        name = com.comunidapp.app.domain.vitacora.import.VitacoraNumberQuery.petTitle(
                            displayName,
                            data.publicVitacoraNumber
                        ),
                        subtitle = petIdentitySubtitle(data),
                        ageLabel = data.ageDisplay().takeIf { it.isNotBlank() },
                        status = data.status,
                        reasonCode = statusReasonCode
                    )
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = com.comunidapp.app.domain.pets.PetCareTransferCopy.UNDER_THE_CARE_OF,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandText
                        )
                        Text(
                            text = when {
                                principalLoading -> "Cargando…"
                                else -> principalDisplayName?.trim().orEmpty().ifBlank { "no disponible" }
                            },
                            style = LeoCardTitle,
                            color = BrandText,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (isArchived) {
                        Spacer(modifier = Modifier.height(16.dp))
                        PetArchivedBanner(
                            canRestore = canRestore,
                            onRestore = { showRestoreDialog = true }
                        )
                    }
                    if (isDeceased) {
                        Spacer(modifier = Modifier.height(16.dp))
                        PetDeceasedBanner(petName = displayName)
                    }

                    if (!isDeceased) {
                        Spacer(modifier = Modifier.height(18.dp))
                        PetPrimaryActions(
                            canEdit = canManage && isActive,
                            onOpenVitacora = { onNavigateToPassport(data.id) },
                            onEdit = { onNavigateToEdit(data.id) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    PetInfoCard(rows = petInfoRows(data))

                    if (!isDeceased) {
                        Spacer(modifier = Modifier.height(12.dp))
                        PetHealthSummary(
                            pet = data,
                            canOpenHealth = healthEditable && isActive,
                            onOpenHealth = { onNavigateToEditHealth(data.id) },
                            healthLoading = isHealthLoading,
                            healthLoadError = healthLoadError
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    PetPassportSummary(
                        petName = displayName,
                        onOpenPassport = { onNavigateToPassport(data.id) }
                    ) {
                        com.comunidapp.app.ui.components.leo.LeoListRow(
                            title = "Compartir / QR",
                            subtitle = "Vista pública y código QR",
                            onClick = { onNavigateToShareQr(data.id) }
                        )
                        com.comunidapp.app.ui.components.leo.LeoListRow(
                            title = "Propuestas VitaCora",
                            subtitle = "Aportes de personas autorizadas",
                            onClick = { onNavigateToM28Proposals(data.id) }
                        )
                        com.comunidapp.app.ui.components.leo.LeoListRow(
                            title = "Acceso profesional",
                            subtitle = "Quién puede consultar o colaborar",
                            onClick = { onNavigateToM28Grants(data.id) }
                        )
                    }

                    if (isActive) {
                        Spacer(modifier = Modifier.height(16.dp))
                        PetEmergencyAction(
                            label = com.comunidapp.app.domain.pets.PetDisplayName.lostActionLabel(
                                data.originKind,
                                data.name
                            ),
                            onReportLost = onNavigateToReportLost
                        )
                    }

                    if (access?.canAcceptTransfer == true && !isDeceased) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onNavigateToTransfers(data.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(com.comunidapp.app.domain.pets.PetCareTransferCopy.RECEIVER_TITLE)
                        }
                    }

                    if (canViewGovernance && !isDeceased) {
                        Spacer(modifier = Modifier.height(12.dp))
                        PetResponsiblesSection(
                            petName = displayName,
                            mutationsEnabled = isActive,
                            showTransfer = access?.canInitiateTransfer == true,
                            showFosterTransit = isActive &&
                                com.comunidapp.app.domain.foster.FosterTransitVisibility.forOrigin(data.originKind),
                            fosterTransitLabel = com.comunidapp.app.domain.foster.FosterTransitVisibility.primaryLabel(
                                com.comunidapp.app.domain.foster.FosterTransitVisibility.isActiveRequest(
                                    fosterSignals[data.id]?.status
                                )
                            ),
                            onOpenResponsibles = { onNavigateToResponsibilities(data.id) },
                            onOpenTransfers = { onNavigateToTransfers(data.id) },
                            onOpenFosterTransit = { onNavigateToFosterTransit(data.id) }
                        )
                    }

                    @Suppress("UNUSED_EXPRESSION")
                    onNavigateToAuthorizations
                    @Suppress("UNUSED_EXPRESSION")
                    onNavigateToStatusHistory

                    errorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MarkPetDeceasedDialog(
    reason: String,
    onReasonChange: (String) -> Unit,
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Informar fallecimiento") },
        text = {
            Column {
                Text(
                    text = "Sentimos mucho tu pérdida. Al confirmar, el perfil dejará de " +
                        "mostrarse como activo y conservará su información."
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = { Text("Motivo (opcional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    enabled = !isSubmitting,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isSubmitting
            ) {
                Text("Confirmar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
internal fun PetLifecycleStatusBadge(
    status: String,
    reasonCode: String? = null
) {
    PetStatusChip(status = status, reasonCode = reasonCode)
}

@Composable
private fun PetResponsiblesSection(
    petName: String,
    mutationsEnabled: Boolean,
    showTransfer: Boolean,
    showFosterTransit: Boolean,
    fosterTransitLabel: String = "Buscar hogar de tránsito",
    onOpenResponsibles: () -> Unit,
    onOpenTransfers: () -> Unit,
    onOpenFosterTransit: () -> Unit
) {
    PetV2Card {
        Text(
            text = "Responsables",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = BrandText
        )
        Text(
            text = "Comparten el cuidado de $petName.",
            style = MaterialTheme.typography.bodySmall,
            color = BrandTextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        if (!mutationsEnabled) {
            Text(
                text = "La gestión está bloqueada para este estado.",
                style = MaterialTheme.typography.bodySmall,
                color = BrandTextSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Button(
            onClick = onOpenResponsibles,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Compartir mascota")
        }
        if (showTransfer) {
            TextButton(
                onClick = onOpenTransfers,
                enabled = mutationsEnabled
            ) {
                Text(com.comunidapp.app.domain.pets.PetCareTransferCopy.SCREEN_TITLE)
            }
        }
        if (showFosterTransit) {
            TextButton(
                onClick = onOpenFosterTransit,
                enabled = mutationsEnabled
            ) {
                Text(fosterTransitLabel)
            }
        }
    }
}
