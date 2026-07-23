package com.comunidapp.app.ui.screens.adoptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.AdoptionDocumentType
import com.comunidapp.app.data.model.AdoptionFollowUpStatus
import com.comunidapp.app.data.model.AdoptionInterviewType
import com.comunidapp.app.data.model.AdoptionWelfareStatus
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.LoadingState
import com.comunidapp.app.viewmodel.AdoptionAgreementViewModel
import com.comunidapp.app.viewmodel.AdoptionDocumentsViewModel
import com.comunidapp.app.viewmodel.AdoptionFinalizeViewModel
import com.comunidapp.app.viewmodel.AdoptionFollowUpCheckDetailViewModel
import com.comunidapp.app.viewmodel.AdoptionFollowUpViewModel
import com.comunidapp.app.viewmodel.AdoptionInterviewDetailViewModel
import com.comunidapp.app.viewmodel.AdoptionInterviewsViewModel
import com.comunidapp.app.viewmodel.AdoptionProcessUiState
import com.comunidapp.app.viewmodel.AdoptionProcessViewModel

@Composable
fun AdoptionProcessScreen(
    onNavigateBack: () -> Unit,
    onInterviews: (String) -> Unit,
    onDocuments: (String) -> Unit,
    onAgreement: (String) -> Unit,
    onFinalize: (String) -> Unit,
    onFollowUp: (String) -> Unit,
    viewModel: AdoptionProcessViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Proceso de adopción",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when (val state = uiState) {
            AdoptionProcessUiState.Loading -> LoadingState(Modifier.padding(padding))
            AdoptionProcessUiState.NotFound -> CenterMsg("No encontramos esa publicación.", padding)
            is AdoptionProcessUiState.Error -> Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.message)
                Button(onClick = viewModel::load) { Text("Reintentar") }
            }
            is AdoptionProcessUiState.Content -> {
                val s = state.snapshot
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Estado publicación: ${s.adoptionStatus.name}")
                    Text(
                        "Candidato: ${s.acceptedApplication?.applicantName?.ifBlank { s.acceptedApplication?.applicantUserId } ?: "—"}"
                    )
                    Text("Entrevistas: ${s.interviews.size} (completadas: ${s.interviews.count { it.status.name == "COMPLETED" }})")
                    Text("Documentos: ${s.documents.size}")
                    Text("Acuerdo: ${s.agreement?.status?.displayNameEs ?: "Sin acuerdo"}")
                    if (s.finalized != null) {
                        Text("Finalizada", fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = { onFollowUp(s.adoptionId) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Ver seguimiento") }
                    } else {
                        s.finalizeBlockers.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
                        OutlinedButton(onClick = { onInterviews(s.adoptionId) }, Modifier.fillMaxWidth()) {
                            Text("Entrevistas")
                        }
                        OutlinedButton(onClick = { onDocuments(s.adoptionId) }, Modifier.fillMaxWidth()) {
                            Text("Documentación")
                        }
                        OutlinedButton(onClick = { onAgreement(s.adoptionId) }, Modifier.fillMaxWidth()) {
                            Text("Acuerdo")
                        }
                        Button(
                            onClick = { onFinalize(s.adoptionId) },
                            Modifier.fillMaxWidth(),
                            enabled = s.canFinalize
                        ) { Text("Ir a finalización") }
                    }
                }
            }
        }
    }
}

@Composable
fun AdoptionInterviewsScreen(
    onNavigateBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    viewModel: AdoptionInterviewsViewModel = viewModel()
) {
    val interviews by viewModel.interviews.collectAsState()
    val message by viewModel.message.collectAsState()
    val applicationId by viewModel.applicationId.collectAsState()
    var showSchedule by remember { mutableStateOf(false) }
    if (showSchedule && !applicationId.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showSchedule = false },
            title = { Text("Agendar entrevista") },
            text = { Text("Se agendará una entrevista presencial para dentro de 2 días.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.schedule(
                        scheduledAt = System.currentTimeMillis() + 2L * 24 * 60 * 60 * 1000,
                        type = AdoptionInterviewType.IN_PERSON,
                        locationOrLink = null,
                        notes = null
                    )
                    showSchedule = false
                }) { Text("Agendar") }
            },
            dismissButton = { TextButton(onClick = { showSchedule = false }) { Text("Cancelar") } }
        )
    }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Entrevistas", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            message?.let { Text(it) }
            if (!applicationId.isNullOrBlank()) {
                Button(onClick = { showSchedule = true }, Modifier.fillMaxWidth()) { Text("Agendar") }
            }
            if (interviews.isEmpty()) Text("No hay entrevistas.")
            interviews.forEach { item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDetail(item.id) }
                        .padding(vertical = 6.dp)
                ) {
                    Text(item.type.displayNameEs, fontWeight = FontWeight.SemiBold)
                    Text(item.status.displayNameEs)
                }
            }
        }
    }
}

@Composable
fun AdoptionInterviewDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdoptionInterviewDetailViewModel = viewModel()
) {
    val interview by viewModel.interview.collectAsState()
    val error by viewModel.error.collectAsState()
    val notFound by viewModel.notFound.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Entrevista", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when {
            notFound -> CenterMsg("No encontramos esa entrevista.", padding)
            interview == null && error == null -> LoadingState(Modifier.padding(padding))
            else -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                interview?.let { i ->
                    Text(i.type.displayNameEs, fontWeight = FontWeight.Bold)
                    Text(i.status.displayNameEs)
                    i.outcome?.let { Text("Resultado: $it") }
                    OutlinedButton(onClick = viewModel::confirm, Modifier.fillMaxWidth()) { Text("Confirmar") }
                    OutlinedButton(onClick = { viewModel.complete("OK") }, Modifier.fillMaxWidth()) {
                        Text("Completar")
                    }
                    OutlinedButton(onClick = viewModel::cancel, Modifier.fillMaxWidth()) { Text("Cancelar") }
                }
            }
        }
    }
}

@Composable
fun AdoptionDocumentsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdoptionDocumentsViewModel = viewModel()
) {
    val docs by viewModel.documents.collectAsState()
    val message by viewModel.message.collectAsState()
    val applicationId by viewModel.applicationId.collectAsState()
    var ref by remember { mutableStateOf("m05://adoption-doc/demo") }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Documentación", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!applicationId.isNullOrBlank()) {
                Button(
                    onClick = { viewModel.request(AdoptionDocumentType.IDENTITY, true) },
                    Modifier.fillMaxWidth()
                ) { Text("Solicitar identidad") }
            }
            OutlinedTextField(
                value = ref,
                onValueChange = { ref = it },
                label = { Text("Referencia de documento") },
                modifier = Modifier.fillMaxWidth()
            )
            docs.forEach { d ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text("${d.type.displayNameEs} — ${d.status.displayNameEs}")
                    d.rejectionReason?.let { Text("Motivo: $it") }
                    RowActions(
                        onSubmit = { viewModel.submit(d.id, ref) },
                        onApprove = { viewModel.review(d.id, true, null) },
                        onReject = { viewModel.review(d.id, false, "Documentación incompleta") }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowActions(onSubmit: () -> Unit, onApprove: () -> Unit, onReject: () -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onSubmit) { Text("Presentar") }
        TextButton(onClick = onApprove) { Text("Aprobar") }
        TextButton(onClick = onReject) { Text("Rechazar") }
    }
}

@Composable
fun AdoptionAgreementScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdoptionAgreementViewModel = viewModel()
) {
    val agreement by viewModel.agreement.collectAsState()
    val message by viewModel.message.collectAsState()
    val applicationId by viewModel.applicationId.collectAsState()
    val terms = remember {
        "Acuerdo interno LeoVer: el adoptante se compromete al cuidado responsable de la mascota."
    }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Acuerdo de adopción", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(terms)
            if (agreement == null && !applicationId.isNullOrBlank()) {
                Button(
                    onClick = { viewModel.create("1.0", terms) },
                    Modifier.fillMaxWidth()
                ) { Text("Crear acuerdo") }
            }
            agreement?.let { a ->
                Text("Versión: ${a.termsVersion}")
                Text("Estado: ${a.status.displayNameEs}")
                Text("Adoptante: ${if (a.adopterAcceptedAt != null) "aceptó" else "pendiente"}")
                Text("Responsable: ${if (a.publisherAcceptedAt != null) "aceptó" else "pendiente"}")
                Button(onClick = { viewModel.accept(a.id) }, Modifier.fillMaxWidth()) {
                    Text("Aceptar acuerdo")
                }
            }
        }
    }
}

@Composable
fun AdoptionFinalizeScreen(
    onNavigateBack: () -> Unit,
    onFinalized: () -> Unit = {},
    viewModel: AdoptionFinalizeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { ev ->
            if (ev.contains("finalizada", ignoreCase = true)) onFinalized()
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Finalizar adopción") },
            text = {
                Text(
                    "Se marcará la publicación como adoptada, se transferirá la responsabilidad " +
                        "y se creará el seguimiento. Esta acción no se puede deshacer fácilmente."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.finalizeAdoption()
                    confirm = false
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } }
        )
    }
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Finalizar adopción", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val state = uiState) {
            AdoptionProcessUiState.Loading -> LoadingState(Modifier.padding(padding))
            AdoptionProcessUiState.NotFound -> CenterMsg("No encontrada.", padding)
            is AdoptionProcessUiState.Error -> CenterMsg(state.message, padding)
            is AdoptionProcessUiState.Content -> Column(
                Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val s = state.snapshot
                Text("Checklist", fontWeight = FontWeight.Bold)
                Text("Candidato aceptado: ${s.acceptedApplication != null}")
                Text("Entrevistas completas: ${s.interviews.any { it.status.name == "COMPLETED" }}")
                Text("Documentos OK: ${s.documents.filter { it.required }.all { it.status.name == "APPROVED" || it.status.name == "NOT_REQUIRED" } && s.documents.any { it.required }}")
                Text("Acuerdo aceptado: ${s.agreement?.status?.name == "ACCEPTED"}")
                s.finalizeBlockers.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = { confirm = true },
                    Modifier.fillMaxWidth(),
                    enabled = s.canFinalize
                ) { Text("Finalizar adopción") }
            }
        }
    }
}

@Composable
fun AdoptionFollowUpScreen(
    onNavigateBack: () -> Unit,
    onOpenCheck: (String) -> Unit,
    viewModel: AdoptionFollowUpViewModel = viewModel()
) {
    val plan by viewModel.plan.collectAsState()
    val checks by viewModel.checks.collectAsState()
    val message by viewModel.message.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Seguimiento", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            message?.let { Text(it) }
            if (plan == null) Text("Todavía no hay plan de seguimiento.")
            else Text("Plan: ${plan!!.status.name}")
            checks.forEach { c ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCheck(c.id) }
                        .padding(vertical = 4.dp)
                ) {
                    Text(c.status.displayNameEs, fontWeight = FontWeight.SemiBold)
                    c.welfareStatus?.let { Text("Bienestar: ${it.displayNameEs}") }
                    if (c.status == AdoptionFollowUpStatus.PENDING ||
                        c.status == AdoptionFollowUpStatus.OVERDUE
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.complete(c.id, "Control OK", AdoptionWelfareStatus.GOOD)
                            }
                        ) { Text("Completar") }
                    }
                }
            }
        }
    }
}

@Composable
fun AdoptionFollowUpCheckDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdoptionFollowUpCheckDetailViewModel = viewModel()
) {
    val check by viewModel.check.collectAsState()
    val error by viewModel.error.collectAsState()
    val notFound by viewModel.notFound.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Control", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when {
            notFound -> CenterMsg("Control no encontrado.", padding)
            check == null && error == null -> LoadingState(Modifier.padding(padding))
            else -> Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                check?.let { c ->
                    Text(c.status.displayNameEs)
                    c.notes?.let { Text(it) }
                    if (c.status != AdoptionFollowUpStatus.COMPLETED) {
                        Button(
                            onClick = {
                                viewModel.complete("Seguimiento", AdoptionWelfareStatus.CRITICAL)
                            },
                            Modifier.fillMaxWidth()
                        ) { Text("Completar (crítico)") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterMsg(text: String, padding: androidx.compose.foundation.layout.PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Text(text)
    }
}
