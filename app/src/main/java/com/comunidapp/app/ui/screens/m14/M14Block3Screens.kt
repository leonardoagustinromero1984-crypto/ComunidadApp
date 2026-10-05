package com.comunidapp.app.ui.screens.m14

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.comunidapp.app.ui.util.QrCodeBitmapGenerator
import com.comunidapp.app.ui.theme.BrandBackground

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.comunidapp.app.data.model.M14CredentialType
import com.comunidapp.app.data.model.M14RemoteFallback
import com.comunidapp.app.data.model.M14VerificationNextStep
import com.comunidapp.app.data.model.M14VerificationRequestStatus
import com.comunidapp.app.data.model.nextStep
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.M14IssueCredentialViewModel
import com.comunidapp.app.viewmodel.M14ManagedVerificationsViewModel
import com.comunidapp.app.viewmodel.M14PassportHistoryViewModel
import com.comunidapp.app.viewmodel.M14RevokeCredentialViewModel
import com.comunidapp.app.viewmodel.M14SharePassportViewModel
import com.comunidapp.app.domain.vitacora.VitaCoraHistoryPresentation
import com.comunidapp.app.viewmodel.M14VerificationDetailViewModel

@Composable
fun M14ManagedVerificationsScreen(
    onNavigateBack: () -> Unit,
    onRequestClick: (String) -> Unit,
    viewModel: M14ManagedVerificationsViewModel = viewModel(
        factory = M14ManagedVerificationsViewModel.factory()
    )
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Verificaciones",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Text(
                "Cola de solicitudes para emisores autorizados. Sin autoverificación.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            when (val s = state) {
                M14ManagedVerificationsViewModel.UiState.Loading -> LoadingState()
                M14ManagedVerificationsViewModel.UiState.Empty -> EmptyState(
                    title = "Sin solicitudes",
                    message = "No hay verificaciones pendientes de revisión."
                )
                is M14ManagedVerificationsViewModel.UiState.Error -> ErrorState(message = s.message)
                is M14ManagedVerificationsViewModel.UiState.Content -> LazyColumn {
                    items(s.items, key = { it.id }) { req ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRequestClick(req.id) }
                                .padding(vertical = LeoDimens.SpaceCompact)
                        ) {
                            Text(req.status.name, fontWeight = FontWeight.Bold)
                            Text(
                                "Credencial: ${req.credentialId}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        LeoHairline()
                    }
                }
            }
        }
    }
}

@Composable
fun M14VerificationDetailScreen(
    requestId: String,
    onNavigateBack: () -> Unit,
    viewModel: M14VerificationDetailViewModel = viewModel(
        factory = M14VerificationDetailViewModel.factory(requestId)
    )
) {
    val request by viewModel.request.collectAsState()
    val decision by viewModel.decision.collectAsState()
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var reason by remember { mutableStateOf("REVIEWED") }
    var note by remember { mutableStateOf("") }
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Solicitud",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            val req = request
            if (req == null) {
                LoadingState()
            } else {
                Text("Estado: ${req.status.name}", fontWeight = FontWeight.Bold)
                Text("Credencial: ${req.credentialId}", style = MaterialTheme.typography.bodySmall)
                val nextLabel = when (req.status.nextStep()) {
                    M14VerificationNextStep.OPEN_REVIEW -> "Próxima acción: abrir revisión."
                    M14VerificationNextStep.DECIDE -> "Próxima acción: aprobar, rechazar o expirar."
                    M14VerificationNextStep.TERMINAL ->
                        "Estado terminal: no se reabre. Nueva verificación requiere otra solicitud."
                    M14VerificationNextStep.EXPIRE_ELIGIBLE ->
                        "Elegible a expiración por política local."
                    M14VerificationNextStep.NONE -> ""
                }
                if (nextLabel.isNotBlank()) {
                    Text(nextLabel, style = MaterialTheme.typography.bodyMedium)
                }
                if (req.status == M14VerificationRequestStatus.EXPIRED) {
                    Text(
                        "Solicitud expirada por política o acción manual.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                decision?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("Decisión: ${it.decision.name} (${it.actorAuthority})")
                    Text("Razón: ${it.reasonCode}")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Código de razón") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota privada (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                val canOpen = req.status == M14VerificationRequestStatus.PENDING && !busy
                val canDecide = req.status == M14VerificationRequestStatus.UNDER_REVIEW && !busy
                val canExpire =
                    (req.status == M14VerificationRequestStatus.PENDING ||
                        req.status == M14VerificationRequestStatus.UNDER_REVIEW) && !busy
                LeoPrimaryButton(
                    text = "Abrir revisión",
                    onClick = { viewModel.openReview() },
                    enabled = canOpen
                )
                LeoPrimaryButton(
                    text = "Aprobar",
                    onClick = { viewModel.approve(reason, note.ifBlank { null }) },
                    enabled = canDecide
                )
                LeoOutlinedButton(
                    text = "Rechazar",
                    onClick = { viewModel.reject(reason, note.ifBlank { null }) },
                    enabled = canDecide
                )
                LeoOutlinedButton(
                    text = "Expirar solicitud",
                    onClick = { viewModel.expire() },
                    enabled = canExpire
                )
                Text(
                    "Las notas privadas no se muestran en la vista pública.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun M14IssueVerifiedCredentialScreen(
    passportId: String,
    onNavigateBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: M14IssueCredentialViewModel = viewModel(
        factory = M14IssueCredentialViewModel.factory(passportId)
    )
) {
    val message by viewModel.message.collectAsState()
    val createdId by viewModel.createdId.collectAsState()
    var title by remember { mutableStateOf("") }
    var orgId by remember { mutableStateOf("") }
    var profId by remember { mutableStateOf("") }

    LaunchedEffect(createdId) {
        createdId?.takeIf { it.isNotBlank() }?.let(onCreated)
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Emitir verificada",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Emisión directa por actor autorizado. Sin autoverificación ni historia clínica.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = orgId,
                onValueChange = { orgId = it },
                label = { Text("ID organización emisora (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = profId,
                onValueChange = { profId = it },
                label = { Text("ID profesional veterinario (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            LeoPrimaryButton(
                text = "Emitir verificada",
                onClick = {
                    viewModel.issue(
                        title = title,
                        type = M14CredentialType.IDENTITY,
                        issuerOrganizationId = orgId.ifBlank { null },
                        issuerProfessionalId = profId.ifBlank { null }
                    )
                }
            )
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun M14RevokeCredentialScreen(
    credentialId: String,
    onNavigateBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: M14RevokeCredentialViewModel = viewModel(
        factory = M14RevokeCredentialViewModel.factory(credentialId)
    )
) {
    val message by viewModel.message.collectAsState()
    val done by viewModel.done.collectAsState()
    var reason by remember { mutableStateOf("REVOKED") }
    LaunchedEffect(done) { if (done) onDone() }
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Revocar credencial",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text(
                "Solo el emisor autorizado o un moderador puede revocar una credencial verificada.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Código de razón") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            LeoPrimaryButton(
                text = "Confirmar revocación",
                onClick = { viewModel.revoke(reason) }
            )
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun M14PassportShareScreen(
    passportId: String,
    onNavigateBack: () -> Unit,
    onPublic: (String) -> Unit,
    viewModel: M14SharePassportViewModel = viewModel(
        factory = M14SharePassportViewModel.factory(passportId)
    )
) {
    val passport by viewModel.passport.collectAsState()
    val payload by viewModel.payload.collectAsState()
    val message by viewModel.message.collectAsState()
    val remotePending by viewModel.remotePending.collectAsState()
    val context = LocalContext.current
    val qrBitmap = remember(payload) {
        payload?.let { QrCodeBitmapGenerator.encode(it) }
    }
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Compartir VitaCora",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            if (remotePending) {
                Text(
                    M14RemoteFallback.MESSAGE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                passport?.displayName ?: "Tu mascota",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Compartí este QR para que otras personas puedan ver la VitaCora pública de ${passport?.displayName ?: "tu mascota"}.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            qrBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Código QR VitaCora",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
            } ?: Text("QR no disponible todavía.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            LeoPrimaryButton(
                text = "Compartir",
                onClick = {
                    val shareText = buildString {
                        append("VitaCora de ${passport?.displayName ?: "mi mascota"}")
                        append("\n")
                        payload?.let { append(it) }
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Compartir VitaCora"))
                },
                enabled = payload != null
            )
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun M14PassportHistoryScreen(
    passportId: String,
    onNavigateBack: () -> Unit,
    onOpenDestination: (com.comunidapp.app.domain.vitacora.VitaCoraHistoryDestination) -> Unit = {},
    viewModel: M14PassportHistoryViewModel = viewModel(
        factory = M14PassportHistoryViewModel.factory(passportId)
    )
) {
    val items by viewModel.items.collectAsState()
    val shown = com.comunidapp.app.domain.vitacora.VitaCoraHistoryDuplicates.collapse(items)
    val message by viewModel.message.collectAsState()
    val remotePending by viewModel.remotePending.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Historial",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            if (remotePending) {
                Text(
                    M14RemoteFallback.MESSAGE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "Eventos recientes de la VitaCora de tu mascota.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            when {
                shown.isEmpty() && message != null -> ErrorState(message = message!!)
                shown.isEmpty() -> EmptyState(
                    title = "Sin historial",
                    message = "Todavía no hay eventos registrados para esta VitaCora."
                )
                else -> LazyColumn {
                    items(shown, key = { it.id }) { h ->
                        val destination = h.destination
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = LeoDimens.SpaceCompact)
                                .then(
                                    if (destination != null) {
                                        Modifier.clickable { onOpenDestination(destination) }
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            val date = VitaCoraHistoryPresentation.formatDate(h.createdAt)
                            if (date.isNotBlank()) {
                                Text(date, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(
                                VitaCoraHistoryPresentation.titleFor(h),
                                fontWeight = FontWeight.Bold
                            )
                            val mediaUrls = h.mediaDisplayUrls.ifEmpty {
                                listOfNotNull(h.mediaDisplayUrl)
                            }
                            if (mediaUrls.isNotEmpty() &&
                                VitaCoraHistoryPresentation.isPlayableVideo(h)
                            ) {
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 8.dp)
                                        .aspectRatio(9f / 16f)
                                ) {
                                    com.comunidapp.app.ui.media.ReelFeedMedia(
                                        url = mediaUrls.first(),
                                        modifier = Modifier.fillMaxSize(),
                                        previewLabel = "Clip"
                                    )
                                }
                            } else if (mediaUrls.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(top = 8.dp, bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    mediaUrls.forEach { url ->
                                        com.comunidapp.app.ui.components.PetImage(
                                            imageUrl = url,
                                            modifier = Modifier
                                                .size(if (mediaUrls.size == 1) 280.dp else 120.dp)
                                                .aspectRatio(1f),
                                            cornerRadius = 12.dp
                                        )
                                    }
                                }
                            }
                            VitaCoraHistoryPresentation.detailFor(h)?.let { detail ->
                                Text(detail, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        LeoHairline()
                    }
                }
            }
        }
    }
}
