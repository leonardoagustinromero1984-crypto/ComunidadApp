package com.comunidapp.app.ui.screens.m14

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.comunidapp.app.data.model.M14PassportStatus
import com.comunidapp.app.data.model.M14Visibility
import com.comunidapp.app.data.repository.M14Validators
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.M14CredentialCreateViewModel
import com.comunidapp.app.viewmodel.M14CredentialDetailViewModel
import com.comunidapp.app.viewmodel.M14CredentialsViewModel
import com.comunidapp.app.viewmodel.M14PassportEditViewModel
import com.comunidapp.app.viewmodel.M14PassportListUiState
import com.comunidapp.app.viewmodel.M14PassportListViewModel
import com.comunidapp.app.viewmodel.M14PetPassportViewModel
import com.comunidapp.app.viewmodel.M14PublicPassportViewModel
import com.comunidapp.app.viewmodel.M14VerificationPrepViewModel

@Composable
fun M14PassportListScreen(
    onNavigateBack: () -> Unit,
    onPassportClick: (petId: String) -> Unit,
    viewModel: M14PassportListViewModel = viewModel(factory = M14PassportListViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Pasaportes",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Text(
                "Información pública resumida cuando el pasaporte es visible.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            when (val s = state) {
                M14PassportListUiState.Loading -> LoadingState()
                M14PassportListUiState.Empty -> EmptyState(
                    title = "Sin pasaportes",
                    message = "Creá un pasaporte desde el detalle de una mascota."
                )
                is M14PassportListUiState.Error -> ErrorState(message = s.message)
                is M14PassportListUiState.Content -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(s.items, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPassportClick(item.petId) }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(item.displayName, fontWeight = FontWeight.Bold)
                                Text("Nº ${item.passportNumber}")
                                Text("Estado: ${item.status}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M14PetPassportScreen(
    petId: String,
    onNavigateBack: () -> Unit,
    onEdit: (String) -> Unit,
    onCredentials: (String) -> Unit,
    onVerification: (String) -> Unit,
    onShare: (String) -> Unit,
    onHistory: (String) -> Unit,
    onManagedVerifications: () -> Unit,
    onPublic: (String) -> Unit,
    viewModel: M14PetPassportViewModel = viewModel(
        factory = M14PetPassportViewModel.factory(petId)
    )
) {
    val passport by viewModel.passport.collectAsState()
    val pet by viewModel.pet.collectAsState()
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Pasaporte",
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
            val p = passport
            if (p == null) {
                Text("Mascota: ${pet?.name ?: petId}")
                Text(
                    "Todavía no hay pasaporte. El responsable M08 puede crearlo.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.createFromPet() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Creando…" else "Crear pasaporte") }
            } else {
                Text(p.displayName, fontWeight = FontWeight.Bold)
                Text("Nº ${p.passportNumber}")
                Text("Estado: ${p.status}")
                Text(
                    "Microchip: ${
                        M14Validators.maskMicrochip(
                            M14Validators.normalizeMicrochip(p.microchipNumber)
                        ) ?: "—"
                    }"
                )
                Text("Visibilidad: ${p.visibility}")
                p.publicCode?.let { Text("Código público: $it") }
                Spacer(Modifier.height(8.dp))
                if (p.status == M14PassportStatus.DRAFT) {
                    Button(
                        onClick = { viewModel.activate() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Activar pasaporte") }
                }
                OutlinedButton(
                    onClick = { onEdit(petId) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Editar datos") }
                OutlinedButton(
                    onClick = { onCredentials(p.id) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Credenciales") }
                OutlinedButton(
                    onClick = { onVerification(p.id) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Mis solicitudes de verificación") }
                OutlinedButton(
                    onClick = onManagedVerifications,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cola de verificación (gestión)") }
                OutlinedButton(
                    onClick = { onShare(p.id) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Compartir / código público") }
                OutlinedButton(
                    onClick = { onHistory(p.id) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Historial") }
                OutlinedButton(
                    onClick = { viewModel.setPublicRedacted() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Marcar vista pública redactada") }
                p.publicCode?.let { code ->
                    OutlinedButton(
                        onClick = { onPublic(code) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Ver proyección pública") }
                }
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun M14PassportEditScreen(
    petId: String,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: M14PassportEditViewModel = viewModel(
        factory = M14PassportEditViewModel.factory(petId)
    )
) {
    val passport by viewModel.passport.collectAsState()
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val saved by viewModel.saved.collectAsState()
    var name by remember { mutableStateOf("") }
    var breed by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var marks by remember { mutableStateOf("") }
    var microchip by remember { mutableStateOf("") }

    LaunchedEffect(passport?.id) {
        passport?.let {
            name = it.displayName
            breed = it.breedText.orEmpty()
            color = it.primaryColor.orEmpty()
            marks = it.distinctiveMarks.orEmpty()
            microchip = it.microchipNumber.orEmpty()
        }
    }
    LaunchedEffect(saved) {
        if (saved) onSaved()
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Editar pasaporte",
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre visible") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = breed,
                onValueChange = { breed = it },
                label = { Text("Raza") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = color,
                onValueChange = { color = it },
                label = { Text("Color") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = marks,
                onValueChange = { marks = it },
                label = { Text("Marcas distintivas") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = microchip,
                onValueChange = { microchip = it },
                label = { Text("Microchip (se enmascara en público)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.save(
                        displayName = name,
                        breedText = breed.ifBlank { null },
                        primaryColor = color.ifBlank { null },
                        distinctiveMarks = marks.ifBlank { null },
                        microchip = microchip.ifBlank { null },
                        sex = passport?.sex
                    )
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Guardando…" else "Guardar") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
fun M14CredentialsScreen(
    passportId: String,
    onNavigateBack: () -> Unit,
    onCredentialClick: (String) -> Unit,
    onCreate: () -> Unit,
    onIssueVerified: () -> Unit,
    viewModel: M14CredentialsViewModel = viewModel(
        factory = M14CredentialsViewModel.factory(passportId)
    )
) {
    val items by viewModel.items.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Credenciales",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            OutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text("Nueva credencial (borrador)")
            }
            OutlinedButton(onClick = onIssueVerified, modifier = Modifier.fillMaxWidth()) {
                Text("Emitir credencial verificada")
            }
            Text(
                "La emisión verificada es solo para emisores autorizados. Sin autoverificación.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Spacer(Modifier.height(4.dp))
            if (items.isEmpty()) {
                EmptyState(
                    title = "Sin credenciales",
                    message = "Agregá identidad, microchip u otras atestaciones documentales."
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id }) { c ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCredentialClick(c.id) }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(c.title, fontWeight = FontWeight.Bold)
                                Text("${c.type} · ${c.status}")
                                Text(
                                    when (c.status.name) {
                                        "PENDING_VERIFICATION" -> "Pendiente de verificación"
                                        "VERIFIED" -> "Verificado por una organización"
                                        else -> c.status.name
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M14CredentialCreateScreen(
    passportId: String,
    onNavigateBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: M14CredentialCreateViewModel = viewModel(
        factory = M14CredentialCreateViewModel.factory(passportId)
    )
) {
    val message by viewModel.message.collectAsState()
    val createdId by viewModel.createdId.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var title by remember { mutableStateOf("") }
    var media by remember { mutableStateOf("") }

    LaunchedEffect(createdId) {
        createdId?.let(onCreated)
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Nueva credencial",
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
            Text("Tipo: IDENTITY (local B1)", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = media,
                onValueChange = { media = it },
                label = { Text("Media M05 (m05:// o file_asset:)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.create(
                        type = M14CredentialType.IDENTITY,
                        title = title,
                        mediaRef = media.ifBlank { null },
                        visibility = M14Visibility.PRIVATE
                    )
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Guardando…" else "Crear") }
            Text(
                "Documentos completos nunca son públicos. Sin autoverificación.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
fun M14CredentialDetailScreen(
    credentialId: String,
    onNavigateBack: () -> Unit,
    onRevoke: (String) -> Unit,
    viewModel: M14CredentialDetailViewModel = viewModel(
        factory = M14CredentialDetailViewModel.factory(credentialId)
    )
) {
    val credential by viewModel.credential.collectAsState()
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Credencial",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            val c = credential
            if (c == null) {
                LoadingState()
            } else {
                Text(c.title, fontWeight = FontWeight.Bold)
                Text("Tipo: ${c.type}")
                Text("Estado: ${c.status}")
                Text("Visibilidad: ${c.visibility}")
                Text("Media: ${c.mediaRefs.joinToString().ifBlank { "—" }}")
                Text(
                    "Notas privadas no se muestran en proyección pública.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.requestVerification() },
                    enabled = !busy && c.status.name == "DRAFT",
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Solicitar verificación") }
                if (c.status.name == "VERIFIED") {
                    OutlinedButton(
                        onClick = { onRevoke(c.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Revocar credencial verificada") }
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
fun M14VerificationPrepScreen(
    passportId: String,
    onNavigateBack: () -> Unit,
    onRequestClick: (String) -> Unit,
    viewModel: M14VerificationPrepViewModel = viewModel(
        factory = M14VerificationPrepViewModel.factory(passportId)
    )
) {
    val requests by viewModel.requests.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Verificación",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text(
                "Tus solicitudes. La revisión humana la hace un emisor autorizado (sin autoverificación).",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            if (requests.isEmpty()) {
                EmptyState(
                    title = "Sin solicitudes",
                    message = "Solicitá verificación desde el detalle de una credencial."
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(requests, key = { it.id }) { r ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRequestClick(r.id) }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(r.status.name, fontWeight = FontWeight.Bold)
                                Text(r.id, style = MaterialTheme.typography.bodySmall)
                                Text(r.resolutionReason ?: "—")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M14PublicPassportScreen(
    publicCode: String,
    onNavigateBack: () -> Unit,
    viewModel: M14PublicPassportViewModel = viewModel(
        factory = M14PublicPassportViewModel.factory(publicCode)
    )
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Vista pública",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text(
                "Información pública resumida",
                fontWeight = FontWeight.SemiBold
            )
            when (val s = state) {
                M14PublicPassportViewModel.UiState.Loading -> LoadingState()
                is M14PublicPassportViewModel.UiState.Error -> ErrorState(message = s.message)
                is M14PublicPassportViewModel.UiState.Content -> {
                    val p = s.projection
                    Text(p.displayName, fontWeight = FontWeight.Bold)
                    Text("${p.species} · ${p.breedText ?: "—"} · ${p.sex ?: "—"}")
                    Text("Color: ${p.primaryColor ?: "—"}")
                    Text("Marcas: ${p.distinctiveMarks ?: "—"}")
                    Text("Estado: ${p.passportStatus}")
                    Text("Microchip: ${p.microchipMasked ?: "—"}")
                    Spacer(Modifier.height(8.dp))
                    Text("Credenciales visibles:")
                    if (p.credentialsPublic.isEmpty()) {
                        Text("—", style = MaterialTheme.typography.bodySmall)
                    } else {
                        p.credentialsPublic.forEach { c ->
                            Text("• ${c.title} · ${c.statusLabel}")
                        }
                    }
                }
            }
        }
    }
}
