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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.PetClinicalRecord
import com.comunidapp.app.ui.components.ageDisplay
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.util.formatRelativeTime
import com.comunidapp.app.viewmodel.PetDetailViewModel

@Composable
fun PetDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit = {},
    onDeleteSuccess: () -> Unit = {},
    onNavigateToResponsibilities: (String) -> Unit = {},
    onNavigateToAuthorizations: (String) -> Unit = {},
    onNavigateToTransfers: (String) -> Unit = {},
    onNavigateToStatusHistory: (String) -> Unit = {},
    onNavigateToPassport: (String) -> Unit = {},
    onNavigateToM28Grants: (String) -> Unit = {},
    onNavigateToM28Proposals: (String) -> Unit = {},
    onNavigateToReportLost: () -> Unit = {},
    viewModel: PetDetailViewModel = viewModel()
) {
    val pet by viewModel.pet.collectAsState()
    val isPetLoading by viewModel.isPetLoading.collectAsState()
    val petLoadError by viewModel.petLoadError.collectAsState()
    val statusReasonCode by viewModel.statusReasonCode.collectAsState()
    val canManage by viewModel.canManage.collectAsState()
    val canViewGovernance by viewModel.canViewGovernance.collectAsState()
    @Suppress("UNUSED_VARIABLE")
    val canMarkDeceased by viewModel.canMarkDeceased.collectAsState()
    val canRestore by viewModel.canRestore.collectAsState()
    @Suppress("UNUSED_VARIABLE")
    val canViewHistory by viewModel.canViewHistory.collectAsState()
    val clinicalRecords by viewModel.clinicalRecords.collectAsState()
    val clinicalTitle by viewModel.clinicalTitle.collectAsState()
    val clinicalNote by viewModel.clinicalNote.collectAsState()
    val deleteSuccess by viewModel.deleteSuccess.collectAsState()
    val lifecycleSuccess by viewModel.lifecycleSuccess.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val principalDisplayName by viewModel.principalDisplayName.collectAsState()
    val principalLoading by viewModel.principalLoading.collectAsState()
    val access by viewModel.access.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeceasedDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showAdminMenu by remember { mutableStateOf(false) }
    var deceasedReason by remember { mutableStateOf("") }
    val healthEditable = access?.canManageHealth == true || canManage

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
                    "El perfil dejará de aparecer entre tus mascotas activas, pero conservará " +
                        "toda su información. Podrás restaurarlo más adelante."
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
                showMenu = canManage || canRestore,
                onMenuClick = { showAdminMenu = true }
            ) {
                DropdownMenu(
                    expanded = showAdminMenu,
                    onDismissRequest = { showAdminMenu = false }
                ) {
                    if (canManage && pet?.status == "ACTIVE") {
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 28.dp)
                ) {
                    PetHero(
                        imageUrl = data.photoUrl,
                        petName = data.name
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    PetIdentityBlock(
                        name = data.name,
                        subtitle = petIdentitySubtitle(data),
                        ageLabel = data.ageDisplay().takeIf { it.isNotBlank() },
                        status = data.status,
                        reasonCode = statusReasonCode
                    )
                    Text(
                        text = when {
                            principalLoading -> "Cargando responsable…"
                            !principalDisplayName.isNullOrBlank() ->
                                "Responsable: $principalDisplayName"
                            else -> "Responsable: no disponible"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandTextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    if (isArchived) {
                        Spacer(modifier = Modifier.height(16.dp))
                        PetArchivedBanner(
                            canRestore = canRestore,
                            onRestore = { showRestoreDialog = true }
                        )
                    }
                    if (isDeceased) {
                        Spacer(modifier = Modifier.height(16.dp))
                        PetDeceasedBanner(petName = data.name)
                    }

                    if (!isDeceased) {
                        Spacer(modifier = Modifier.height(18.dp))
                        PetPrimaryActions(
                            canEdit = canManage && isActive,
                            onEdit = { onNavigateToEdit(data.id) },
                            onShare = { onNavigateToPassport(data.id) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    PetInfoCard(rows = petInfoRows(data))

                    if (!isDeceased) {
                        Spacer(modifier = Modifier.height(12.dp))
                        PetHealthSummary(
                            pet = data,
                            canOpenHealth = healthEditable && isActive,
                            onOpenHealth = { onNavigateToEdit(data.id) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    PetPassportSummary(
                        petName = data.name,
                        onOpenPassport = { onNavigateToPassport(data.id) }
                    ) {
                        OutlinedButton(
                            onClick = { onNavigateToM28Proposals(data.id) },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) { Text("Propuestas Pasaporte") }
                        OutlinedButton(
                            onClick = { onNavigateToM28Grants(data.id) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text("Acceso profesional") }
                    }

                    if (isActive) {
                        Spacer(modifier = Modifier.height(16.dp))
                        PetEmergencyAction(
                            petName = data.name,
                            onReportLost = onNavigateToReportLost
                        )
                    }

                    if (canViewGovernance && !isDeceased) {
                        Spacer(modifier = Modifier.height(12.dp))
                        PetCareNetworkSection(
                            petName = data.name,
                            mutationsEnabled = isActive,
                            onOpenCareNetwork = { onNavigateToResponsibilities(data.id) },
                            onOpenTransfers = { onNavigateToTransfers(data.id) }
                        )
                    }

                    @Suppress("UNUSED_EXPRESSION")
                    onNavigateToAuthorizations
                    @Suppress("UNUSED_EXPRESSION")
                    onNavigateToStatusHistory

                    if (!isDeceased) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ClinicalRecordsSection(
                            records = clinicalRecords,
                            title = clinicalTitle,
                            note = clinicalNote,
                            canManage = healthEditable && isActive,
                            onTitleChange = viewModel::updateClinicalTitle,
                            onNoteChange = viewModel::updateClinicalNote,
                            onAdd = viewModel::addClinicalNote
                        )
                    }

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
private fun PetCareNetworkSection(
    petName: String,
    mutationsEnabled: Boolean,
    onOpenCareNetwork: () -> Unit,
    onOpenTransfers: () -> Unit
) {
    PetV2Card {
        Text(
            text = "Red de cuidado",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = BrandText
        )
        Text(
            text = "Personas que colaboran en el cuidado de $petName.",
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
            onClick = onOpenCareNetwork,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ver red de cuidado")
        }
        TextButton(
            onClick = onOpenTransfers,
            enabled = mutationsEnabled
        ) {
            Text("Transferencias")
        }
    }
}

@Composable
private fun ClinicalRecordsSection(
    records: List<PetClinicalRecord>,
    title: String,
    note: String,
    canManage: Boolean,
    onTitleChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    PetV2Card {
        Text(
            text = "Historial clínico",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = BrandText
        )
        if (records.isEmpty()) {
            Text(
                text = "Sin registros clínicos todavía.",
                style = MaterialTheme.typography.bodySmall,
                color = BrandTextSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            records.forEach { record ->
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp),
                    color = BrandText
                )
                Text(
                    text = record.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandText
                )
                Text(
                    text = "${record.authorName} · ${record.recordedAt?.let(::formatRelativeTime).orEmpty()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandTextSecondary
                )
            }
        }
        if (canManage) {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("Título") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                label = { Text("Nota clínica") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                minLines = 2
            )
            Button(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("Agregar nota")
            }
        }
    }
}
