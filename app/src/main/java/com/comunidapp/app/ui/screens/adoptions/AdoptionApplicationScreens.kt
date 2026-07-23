package com.comunidapp.app.ui.screens.adoptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.AdoptionApplication
import com.comunidapp.app.data.model.AdoptionApplicationStatus
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.viewmodel.AdoptionApplyUiState
import com.comunidapp.app.viewmodel.AdoptionApplyViewModel
import com.comunidapp.app.viewmodel.AdoptionApplicationDetailViewModel
import com.comunidapp.app.viewmodel.ApplicationDetailUiState
import com.comunidapp.app.viewmodel.MyAdoptionApplicationsViewModel
import com.comunidapp.app.viewmodel.MyApplicationsUiState
import com.comunidapp.app.viewmodel.ReceivedAdoptionApplicationsViewModel
import com.comunidapp.app.viewmodel.ReceivedApplicationsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdoptionApplyScreen(
    onNavigateBack: () -> Unit,
    onSubmitted: () -> Unit = onNavigateBack,
    viewModel: AdoptionApplyViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event.contains("enviada", ignoreCase = true)) onSubmitted()
        }
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Postularme",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when (val state = uiState) {
            AdoptionApplyUiState.Loading -> LoadingState(Modifier.padding(padding))
            AdoptionApplyUiState.NotFound -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("No encontramos esa publicación.") }
            is AdoptionApplyUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message)
                    Button(onClick = viewModel::load) { Text("Reintentar") }
                }
            }
            is AdoptionApplyUiState.Ready -> {
                val form = state.form
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        state.post.displayTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Mascota: ${state.post.name}")
                    if (form.submitted) {
                        Text("¡Postulación enviada! El responsable la revisará.")
                        Button(onClick = onSubmitted, modifier = Modifier.fillMaxWidth()) {
                            Text("Continuar")
                        }
                    } else {
                        OutlinedTextField(
                            value = form.message,
                            onValueChange = viewModel::onMessageChange,
                            label = { Text("Mensaje para el responsable *") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            enabled = !form.submitting,
                            isError = form.fieldError != null
                        )
                        form.fieldError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        OutlinedTextField(
                            value = form.housingType,
                            onValueChange = viewModel::onHousingTypeChange,
                            label = { Text("Tipo de vivienda") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !form.submitting,
                            singleLine = true
                        )
                        Text("¿Convive con otros animales?")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = form.hasOtherPets == true,
                                onClick = { viewModel.onHasOtherPetsChange(true) },
                                enabled = !form.submitting
                            )
                            Text("Sí", Modifier.clickable { viewModel.onHasOtherPetsChange(true) })
                            RadioButton(
                                selected = form.hasOtherPets == false,
                                onClick = { viewModel.onHasOtherPetsChange(false) },
                                enabled = !form.submitting
                            )
                            Text("No", Modifier.clickable { viewModel.onHasOtherPetsChange(false) })
                        }
                        OutlinedTextField(
                            value = form.previousExperience,
                            onValueChange = viewModel::onPreviousExperienceChange,
                            label = { Text("Experiencia previa") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            enabled = !form.submitting
                        )
                        OutlinedTextField(
                            value = form.contactPhone,
                            onValueChange = viewModel::onContactPhoneChange,
                            label = { Text("Teléfono de contacto (opcional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !form.submitting
                        )
                        form.submitError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = viewModel::submit,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !form.submitting && !form.submitted
                        ) {
                            Text(if (form.submitting) "Enviando…" else "Enviar postulación")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MyAdoptionApplicationsScreen(
    onNavigateBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    viewModel: MyAdoptionApplicationsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    var withdrawId by remember { mutableStateOf<String?>(null) }

    withdrawId?.let { id ->
        AlertDialog(
            onDismissRequest = { withdrawId = null },
            title = { Text("Retirar postulación") },
            text = { Text("¿Confirmás que querés retirar esta postulación?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.withdraw(id)
                    withdrawId = null
                }) { Text("Retirar") }
            },
            dismissButton = {
                TextButton(onClick = { withdrawId = null }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Mis postulaciones",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            message?.let {
                Text(
                    it,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            when (val state = uiState) {
                MyApplicationsUiState.Loading -> LoadingState()
                MyApplicationsUiState.Empty -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { Text("Todavía no enviaste postulaciones.") }
                is MyApplicationsUiState.Error -> Box(
                    Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message)
                        Button(onClick = viewModel::refresh) { Text("Reintentar") }
                    }
                }
                is MyApplicationsUiState.Content -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.items, key = { it.id }) { app ->
                        ApplicationListItem(
                            app = app,
                            showApplicant = false,
                            onClick = { onOpenDetail(app.id) },
                            trailing = {
                                if (app.status == AdoptionApplicationStatus.SUBMITTED ||
                                    app.status == AdoptionApplicationStatus.UNDER_REVIEW
                                ) {
                                    TextButton(onClick = { withdrawId = app.id }) {
                                        Text("Retirar")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReceivedAdoptionApplicationsScreen(
    onNavigateBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    viewModel: ReceivedAdoptionApplicationsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val filter by viewModel.statusFilter.collectAsState()
    val message by viewModel.message.collectAsState()
    var confirmAccept by remember { mutableStateOf<String?>(null) }
    var confirmReject by remember { mutableStateOf<String?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    confirmAccept?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmAccept = null },
            title = { Text("Aceptar postulación") },
            text = {
                Text(
                    "Se aceptará este candidato, se rechazarán las demás postulaciones activas " +
                        "y la publicación se pausará. Esto no finaliza la adopción."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.accept(id)
                    confirmAccept = null
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAccept = null }) { Text("Cancelar") }
            }
        )
    }

    confirmReject?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmReject = null },
            title = { Text("Rechazar postulación") },
            text = {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = { Text("Motivo (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reject(id, rejectReason.ifBlank { null })
                    confirmReject = null
                    rejectReason = ""
                }) { Text("Rechazar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmReject = null
                    rejectReason = ""
                }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Postulaciones recibidas",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { viewModel.onStatusFilterChange(null) },
                    label = { Text("Todas") }
                )
                AdoptionApplicationStatus.entries.forEach { status ->
                    FilterChip(
                        selected = filter == status,
                        onClick = {
                            viewModel.onStatusFilterChange(
                                if (filter == status) null else status
                            )
                        },
                        label = { Text(status.displayNameEs) }
                    )
                }
            }
            message?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            when (val state = uiState) {
                ReceivedApplicationsUiState.Loading -> LoadingState()
                ReceivedApplicationsUiState.Empty -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { Text("No hay postulaciones recibidas.") }
                is ReceivedApplicationsUiState.Error -> Box(
                    Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message)
                        Button(onClick = viewModel::refresh) { Text("Reintentar") }
                    }
                }
                is ReceivedApplicationsUiState.Content -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.items, key = { it.id }) { app ->
                        ApplicationListItem(
                            app = app,
                            showApplicant = true,
                            onClick = { onOpenDetail(app.id) },
                            trailing = {
                                if (app.status == AdoptionApplicationStatus.SUBMITTED ||
                                    app.status == AdoptionApplicationStatus.UNDER_REVIEW
                                ) {
                                    Row {
                                        if (app.status == AdoptionApplicationStatus.SUBMITTED) {
                                            TextButton(onClick = {
                                                viewModel.markUnderReview(app.id)
                                            }) { Text("Revisar") }
                                        }
                                        TextButton(onClick = { confirmAccept = app.id }) {
                                            Text("Aceptar")
                                        }
                                        TextButton(onClick = { confirmReject = app.id }) {
                                            Text("Rechazar")
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdoptionApplicationDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdoptionApplicationDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var confirmAccept by remember { mutableStateOf(false) }
    var confirmReject by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }
    var confirmWithdraw by remember { mutableStateOf(false) }

    if (confirmAccept) {
        AlertDialog(
            onDismissRequest = { confirmAccept = false },
            title = { Text("Aceptar postulación") },
            text = {
                Text(
                    "Se rechazarán las demás activas y se pausará la publicación. " +
                        "No marca la adopción como finalizada."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.accept()
                    confirmAccept = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAccept = false }) { Text("Cancelar") }
            }
        )
    }
    if (confirmReject) {
        AlertDialog(
            onDismissRequest = { confirmReject = false },
            title = { Text("Rechazar") },
            text = {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = { Text("Motivo (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reject(rejectReason.ifBlank { null })
                    confirmReject = false
                    rejectReason = ""
                }) { Text("Rechazar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReject = false }) { Text("Cancelar") }
            }
        )
    }
    if (confirmWithdraw) {
        AlertDialog(
            onDismissRequest = { confirmWithdraw = false },
            title = { Text("Retirar postulación") },
            text = { Text("¿Confirmás retirar tu postulación?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.withdraw()
                    confirmWithdraw = false
                }) { Text("Retirar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWithdraw = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Detalle de postulación",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when (val state = uiState) {
            ApplicationDetailUiState.Loading -> LoadingState(Modifier.padding(padding))
            ApplicationDetailUiState.NotFound -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("No encontramos esa postulación.") }
            is ApplicationDetailUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message)
                    Button(onClick = viewModel::load) { Text("Reintentar") }
                }
            }
            is ApplicationDetailUiState.Content -> {
                val app = state.application
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(app.adoptionTitle.ifBlank { "Publicación" }, fontWeight = FontWeight.Bold)
                    Text("Mascota: ${app.petName.ifBlank { "—" }}")
                    Text("Postulante: ${app.applicantName.ifBlank { app.applicantUserId }}")
                    Text("Estado: ${app.status.displayNameEs}")
                    Text("Enviada: ${formatAppDate(app.submittedAt)}")
                    app.reviewedAt?.let { Text("Revisada: ${formatAppDate(it)}") }
                    Spacer(Modifier.height(8.dp))
                    Text("Mensaje", fontWeight = FontWeight.SemiBold)
                    Text(app.message)
                    app.housingType?.takeIf { it.isNotBlank() }?.let {
                        Text("Vivienda: $it")
                    }
                    app.hasOtherPets?.let {
                        Text("Otros animales: ${if (it) "Sí" else "No"}")
                    }
                    app.previousExperience?.takeIf { it.isNotBlank() }?.let {
                        Text("Experiencia: $it")
                    }
                    state.visiblePhone?.let { Text("Teléfono: $it") }
                    app.rejectionReason?.takeIf { it.isNotBlank() }?.let {
                        Text("Motivo de rechazo: $it", color = MaterialTheme.colorScheme.error)
                    }
                    if (app.status == AdoptionApplicationStatus.ACCEPTED) {
                        Text(
                            "Postulación aceptada (la adopción aún no está finalizada).",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.actionMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                    if (state.isApplicant &&
                        (app.status == AdoptionApplicationStatus.SUBMITTED ||
                            app.status == AdoptionApplicationStatus.UNDER_REVIEW)
                    ) {
                        OutlinedButton(
                            onClick = { confirmWithdraw = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.actionInFlight
                        ) { Text("Retirar postulación") }
                    }
                    if (state.isManager &&
                        (app.status == AdoptionApplicationStatus.SUBMITTED ||
                            app.status == AdoptionApplicationStatus.UNDER_REVIEW)
                    ) {
                        if (app.status == AdoptionApplicationStatus.SUBMITTED) {
                            OutlinedButton(
                                onClick = viewModel::markUnderReview,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.actionInFlight
                            ) { Text("Marcar en revisión") }
                        }
                        Button(
                            onClick = { confirmAccept = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.actionInFlight
                        ) { Text("Aceptar") }
                        OutlinedButton(
                            onClick = { confirmReject = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.actionInFlight
                        ) { Text("Rechazar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplicationListItem(
    app: AdoptionApplication,
    showApplicant: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Text(
            app.adoptionTitle.ifBlank { "Publicación" },
            fontWeight = FontWeight.SemiBold
        )
        Text("Mascota: ${app.petName.ifBlank { "—" }}")
        if (showApplicant) {
            Text("Postulante: ${app.applicantName.ifBlank { app.applicantUserId }}")
        }
        Text("Estado: ${app.status.displayNameEs}")
        Text(formatAppDate(app.submittedAt), style = MaterialTheme.typography.bodySmall)
        Text(app.messagePreview(), style = MaterialTheme.typography.bodyMedium)
        if (app.status == AdoptionApplicationStatus.ACCEPTED) {
            Text(
                "Aceptada (adopción no finalizada)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing()
    }
}

private fun formatAppDate(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))
