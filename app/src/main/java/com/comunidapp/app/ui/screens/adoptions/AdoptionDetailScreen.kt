package com.comunidapp.app.ui.screens.adoptions

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.comunidapp.app.data.model.AdoptionRequestStatus
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.InterviewStatus
import com.comunidapp.app.ui.components.AdoptionCard
import com.comunidapp.app.ui.components.AdoptionStatusBadge
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.ui.components.PetImage
import com.comunidapp.app.ui.components.ageDisplay
import com.comunidapp.app.ui.components.toDisplayName
import com.comunidapp.app.viewmodel.AdoptionDetailUiState
import com.comunidapp.app.viewmodel.AdoptionDetailViewModel
import com.comunidapp.app.viewmodel.MyAdoptionsViewModel

@Composable
fun AdoptionDetailScreen(
    onNavigateBack: () -> Unit,
    onEdit: (String) -> Unit = {},
    onApply: (String) -> Unit = {},
    onMessagePublisher: (String, String) -> Unit = { _, _ -> },
    onProcess: (String) -> Unit = {},
    viewModel: AdoptionDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }

    val title = when (val s = uiState) {
        is AdoptionDetailUiState.Content -> s.post.displayTitle
        else -> "Adopción"
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = title,
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when (val state = uiState) {
            AdoptionDetailUiState.Loading -> LoadingState(Modifier.padding(padding))
            AdoptionDetailUiState.NotFound -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No encontramos esa publicación.")
            }
            is AdoptionDetailUiState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message)
                    Button(onClick = viewModel::load) { Text("Reintentar") }
                }
            }
            is AdoptionDetailUiState.Content -> {
                val adoption = state.post
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    PetImage(
                        imageUrl = adoption.photoUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        cornerRadius = 12.dp,
                        contentDescription = adoption.name
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = adoption.displayTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = adoption.shelterName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AdoptionStatusBadge(status = adoption.status)
                    Spacer(modifier = Modifier.height(16.dp))
                    DetailRow("Mascota", adoption.name)
                    DetailRow("Especie", adoption.species.toDisplayName())
                    DetailRow("Sexo", adoption.sex.toDisplayName())
                    DetailRow("Edad", adoption.ageDisplay())
                    DetailRow("Tamaño", adoption.size.toDisplayName())
                    DetailRow("Zona", adoption.location)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Descripción",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = adoption.description,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (adoption.requirements.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Requisitos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = adoption.requirements,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    state.actionMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    if (state.isOwner) {
                        Spacer(modifier = Modifier.height(24.dp))
                        if (adoption.status != AdoptionStatus.CLOSED &&
                            adoption.status != AdoptionStatus.ADOPTED
                        ) {
                            OutlinedButton(
                                onClick = { onEdit(adoption.id) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.actionInFlight
                            ) { Text("Editar publicación") }
                        }
                        when (adoption.status) {
                            AdoptionStatus.PUBLISHED -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { confirmAction = ConfirmAction.Pause },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.actionInFlight
                                ) { Text("Pausar") }
                            }
                            AdoptionStatus.PAUSED, AdoptionStatus.DRAFT -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { confirmAction = ConfirmAction.Resume },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.actionInFlight
                                ) { Text("Publicar / reanudar") }
                            }
                            else -> Unit
                        }
                        if (adoption.status != AdoptionStatus.CLOSED &&
                            adoption.status != AdoptionStatus.ADOPTED
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { confirmAction = ConfirmAction.Close },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.actionInFlight
                            ) { Text("Cerrar publicación") }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onProcess(adoption.id) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.actionInFlight
                            ) { Text("Proceso / finalizar adopción") }
                        }
                    } else if (adoption.status == AdoptionStatus.PUBLISHED) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { onApply(adoption.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Quiero adoptar") }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                adoption.publisherId?.let { id ->
                                    onMessagePublisher(id, adoption.shelterName)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !adoption.publisherId.isNullOrBlank()
                        ) { Text("Enviar mensaje al publicador") }
                    }
                }
            }
        }
    }

    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (action) {
                            ConfirmAction.Pause -> viewModel.pause()
                            ConfirmAction.Resume -> viewModel.resume()
                            ConfirmAction.Close -> viewModel.close()
                        }
                        confirmAction = null
                    }
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) { Text("Cancelar") }
            }
        )
    }
}

private enum class ConfirmAction(val title: String, val message: String) {
    Pause("Pausar publicación", "La publicación dejará de verse en el listado público."),
    Resume("Reanudar publicación", "La publicación volverá a mostrarse como publicada."),
    Close("Cerrar publicación", "No podrás editarla después de cerrarla.")
}

@Composable
fun MyAdoptionsScreen(
    onNavigateBack: () -> Unit,
    onAdoptionClick: (String) -> Unit,
    onCreateAdoption: () -> Unit = {},
    onEditAdoption: (String) -> Unit = {},
    onReceivedApplications: () -> Unit = {},
    onProcess: (String) -> Unit = {},
    viewModel: MyAdoptionsViewModel = viewModel()
) {
    val adoptions by viewModel.myAdoptions.collectAsState()
    val requests by viewModel.requests.collectAsState()
    val matches by viewModel.matches.collectAsState()
    val message by viewModel.message.collectAsState()
    var interviewRequestId by remember { mutableStateOf<String?>(null) }
    var interviewDate by remember { mutableStateOf("") }
    var interviewNotes by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Pair<String, ConfirmAction>?>(null) }

    if (interviewRequestId != null) {
        AlertDialog(
            onDismissRequest = {
                interviewRequestId = null
                interviewDate = ""
                interviewNotes = ""
            },
            title = { Text("Agendar entrevista") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = interviewDate,
                        onValueChange = { interviewDate = it },
                        label = { Text("Fecha y hora (dd/MM/yyyy HH:mm)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = interviewNotes,
                        onValueChange = { interviewNotes = it },
                        label = { Text("Notas") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    message?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = interviewRequestId ?: return@TextButton
                        viewModel.scheduleInterview(id, interviewDate, interviewNotes)
                        interviewRequestId = null
                        interviewDate = ""
                        interviewNotes = ""
                        viewModel.clearMessage()
                    }
                ) { Text("Agendar") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        interviewRequestId = null
                        interviewDate = ""
                        interviewNotes = ""
                        viewModel.clearMessage()
                    }
                ) { Text("Cancelar") }
            }
        )
    }

    confirm?.let { (id, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (action) {
                            ConfirmAction.Pause -> viewModel.pause(id)
                            ConfirmAction.Resume -> viewModel.resume(id)
                            ConfirmAction.Close -> viewModel.close(id)
                        }
                        confirm = null
                    }
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Mis adopciones",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Button(
                    onClick = onCreateAdoption,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Nueva publicación") }
            }
            item {
                OutlinedButton(
                    onClick = onReceivedApplications,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Ver postulaciones recibidas") }
            }
            item {
                Text(
                    text = "Publicaciones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
            if (adoptions.isEmpty()) {
                item { Text("Todavía no tenés publicaciones.") }
            }
            items(adoptions, key = { it.id }) { post ->
                Column {
                    AdoptionCard(post = post, onClick = { onAdoptionClick(post.id) })
                    OutlinedButton(
                        onClick = { onProcess(post.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Proceso post-aceptación") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (post.status) {
                            AdoptionStatus.DRAFT -> {
                                OutlinedButton(
                                    onClick = { onEditAdoption(post.id) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Editar") }
                                OutlinedButton(
                                    onClick = { confirm = post.id to ConfirmAction.Resume },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Publicar") }
                            }
                            AdoptionStatus.PUBLISHED -> {
                                OutlinedButton(
                                    onClick = { confirm = post.id to ConfirmAction.Pause },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Pausar") }
                                OutlinedButton(
                                    onClick = { onProcess(post.id) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Proceso") }
                            }
                            AdoptionStatus.PAUSED -> {
                                OutlinedButton(
                                    onClick = { confirm = post.id to ConfirmAction.Resume },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Reanudar") }
                                OutlinedButton(
                                    onClick = { confirm = post.id to ConfirmAction.Close },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Cerrar") }
                            }
                            AdoptionStatus.ADOPTED, AdoptionStatus.CLOSED -> {
                                Text(
                                    text = if (post.status == AdoptionStatus.ADOPTED) {
                                        "Finalizada (adoptada)"
                                    } else {
                                        "Cerrada"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            if (matches.isNotEmpty()) {
                item {
                    Text(
                        text = "Matching sugerido",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Text(
                        text = "Candidatos sugeridos por LeoVer para tu primera publicación",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(matches, key = { it.id }) { match ->
                    Text("Candidato ${match.userId} — score ${"%.0f".format(match.score)}")
                    if (match.reasons.isNotEmpty()) {
                        Text(
                            match.reasons.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (requests.isNotEmpty()) {
                item {
                    Text(
                        text = "Solicitudes recibidas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(requests, key = { it.id }) { req ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(req.applicantName, fontWeight = FontWeight.SemiBold)
                        Text(req.message)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (req.status == AdoptionRequestStatus.PENDING) {
                                TextButton(
                                    onClick = { viewModel.respondToRequest(req.id, true) }
                                ) { Text("Aceptar") }
                                TextButton(
                                    onClick = { viewModel.respondToRequest(req.id, false) }
                                ) { Text("Rechazar") }
                                TextButton(
                                    onClick = { interviewRequestId = req.id }
                                ) { Text("Entrevista") }
                            } else {
                                Text(req.status.name)
                                if (req.interviewStatus == InterviewStatus.SCHEDULED) {
                                    Text("Entrevista agendada")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
