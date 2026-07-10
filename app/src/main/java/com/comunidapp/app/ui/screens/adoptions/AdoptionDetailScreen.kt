package com.comunidapp.app.ui.screens.adoptions

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
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
import com.comunidapp.app.viewmodel.AdoptionDetailViewModel
import com.comunidapp.app.viewmodel.MyAdoptionsViewModel
import com.comunidapp.app.viewmodel.RequestUiState

@Composable
fun AdoptionDetailScreen(
    onNavigateBack: () -> Unit,
    onMessagePublisher: (String, String) -> Unit = { _, _ -> },
    viewModel: AdoptionDetailViewModel = viewModel()
) {
    val post by viewModel.post.collectAsState()
    val showDialog by viewModel.showRequestDialog.collectAsState()
    val requestState by viewModel.requestState.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = post?.name ?: "Adopción",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when (val adoption = post) {
            null -> LoadingState(Modifier.padding(padding))
            else -> Column(
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
                    text = adoption.name,
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
                if (adoption.status == AdoptionStatus.AVAILABLE) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = viewModel::openRequestDialog,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Quiero adoptar")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            adoption.publisherId?.let { id ->
                                onMessagePublisher(id, adoption.shelterName)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !adoption.publisherId.isNullOrBlank()
                    ) {
                        Text("Enviar mensaje al publicador")
                    }
                }
            }
        }
    }

    if (showDialog) {
        AdoptionRequestDialog(
            requestState = requestState,
            onDismiss = viewModel::closeRequestDialog,
            onSubmit = viewModel::submitRequest
        )
    }
}

@Composable
private fun AdoptionRequestDialog(
    requestState: RequestUiState,
    onDismiss: () -> Unit,
    onSubmit: (String, String?) -> Unit
) {
    var message by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Solicitud de adopción") },
        text = {
            Column {
                when (requestState) {
                    RequestUiState.Success -> Text("¡Solicitud enviada! El publicador te contactará pronto.")
                    RequestUiState.Loading -> CircularProgressIndicator()
                    is RequestUiState.Error -> Text(
                        requestState.message,
                        color = MaterialTheme.colorScheme.error
                    )
                    RequestUiState.Idle -> {
                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            label = { Text("Contanos por qué querés adoptar") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Teléfono (opcional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (requestState !is RequestUiState.Success) {
                TextButton(
                    onClick = { onSubmit(message, phone) },
                    enabled = requestState !is RequestUiState.Loading
                ) {
                    Text("Enviar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun MyAdoptionsScreen(
    onNavigateBack: () -> Unit,
    onAdoptionClick: (String) -> Unit,
    viewModel: MyAdoptionsViewModel = viewModel()
) {
    val adoptions by viewModel.myAdoptions.collectAsState()
    val requests by viewModel.requests.collectAsState()
    val matches by viewModel.matches.collectAsState()
    val message by viewModel.message.collectAsState()
    var interviewRequestId by remember { mutableStateOf<String?>(null) }
    var interviewDate by remember { mutableStateOf("") }
    var interviewNotes by remember { mutableStateOf("") }

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
                Text(
                    text = "Publicaciones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(adoptions, key = { it.id }) { post ->
                Column {
                    AdoptionCard(post = post, onClick = { onAdoptionClick(post.id) })
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.updateStatus(post.id, AdoptionStatus.IN_PROCESS) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Pausar") }
                        OutlinedButton(
                            onClick = { viewModel.updateStatus(post.id, AdoptionStatus.ADOPTED) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Adoptada") }
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
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "Usuario ${match.userId}",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Score: ${match.score.toInt()} · ${match.reasons.joinToString(" · ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                items(requests, key = { it.id }) { request ->
                    CardRequestItem(
                        request = request,
                        onAccept = { viewModel.respondToRequest(request.id, true) },
                        onReject = { viewModel.respondToRequest(request.id, false) },
                        onScheduleInterview = {
                            interviewRequestId = request.id
                            interviewDate = ""
                            interviewNotes = ""
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CardRequestItem(
    request: com.comunidapp.app.data.model.AdoptionRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onScheduleInterview: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = request.applicantName, fontWeight = FontWeight.SemiBold)
        Text(text = request.message, style = MaterialTheme.typography.bodySmall)
        Text(
            text = when (request.status) {
                AdoptionRequestStatus.PENDING -> "Pendiente"
                AdoptionRequestStatus.ACCEPTED -> "Aceptada"
                AdoptionRequestStatus.REJECTED -> "Rechazada"
            },
            style = MaterialTheme.typography.labelMedium
        )
        if (request.interviewStatus == InterviewStatus.SCHEDULED) {
            Text(
                text = "Entrevista agendada${request.interviewNotes?.let { ": $it" }.orEmpty()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (request.status == AdoptionRequestStatus.PENDING) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAccept) { Text("Aceptar") }
                TextButton(onClick = onReject) { Text("Rechazar") }
            }
        }
        if (request.status == AdoptionRequestStatus.ACCEPTED) {
            TextButton(onClick = onScheduleInterview) { Text("Agendar entrevista") }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
