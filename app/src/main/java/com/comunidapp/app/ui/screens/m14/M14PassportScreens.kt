package com.comunidapp.app.ui.screens.m14

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.SterilizationStatus
import com.comunidapp.app.domain.vitacora.VitaCoraHistoryPresentation
import com.comunidapp.app.ui.components.PetImage
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandCream
import com.comunidapp.app.data.model.M14CredentialType
import com.comunidapp.app.data.model.M14PassportStatus
import com.comunidapp.app.data.model.M14Visibility
import com.comunidapp.app.data.repository.M14Validators
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "VitaCora",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Text(
                "Información pública resumida cuando VitaCora es visible.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            when (val s = state) {
                M14PassportListUiState.Loading -> LoadingState()
                M14PassportListUiState.Empty -> EmptyState(
                    title = "Sin VitaCora",
                    message = "Abrí VitaCora desde el detalle de una mascota."
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
    onProfessionalAccess: (String) -> Unit = {},
    onProposals: (String) -> Unit = {},
    viewModel: M14PetPassportViewModel = viewModel(
        factory = M14PetPassportViewModel.factory(petId)
    )
) {
    val passport by viewModel.passport.collectAsState()
    val pet by viewModel.pet.collectAsState()
    val historyPreview by viewModel.historyPreview.collectAsState()
    val message by viewModel.message.collectAsState()
    val messageIsError by viewModel.messageIsError.collectAsState()
    val busy by viewModel.busy.collectAsState()
    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "VitaCora",
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
            val petName = pet?.name?.takeIf { it.isNotBlank() } ?: "tu mascota"
            if (p == null) {
                val createFailed =
                    message?.contains("No pudimos abrir VitaCora", ignoreCase = true) == true ||
                    message?.contains("No pudimos crear el pasaporte", ignoreCase = true) == true
                if (createFailed) {
                    Text(
                        text = "No pudimos abrir VitaCora",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Revisá tu conexión e intentá nuevamente.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.clearMessage()
                            viewModel.createFromPet()
                        },
                        enabled = !busy && petId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "Creando…" else "Reintentar") }
                    TextButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Volver") }
                } else {
                    Text(
                        text = "VitaCora de $petName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Reuní en un solo lugar su información más importante.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.createFromPet() },
                        enabled = !busy && petId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "Abriendo…" else "Abrir VitaCora") }
                }
            } else {
                val currentPet = pet
                VitaCoraHubHeader(
                    petName = p.displayName,
                    vitacoraNumber = p.passportNumber,
                    statusLabel = passportStatusLabel(p.status),
                    visibilityLabel = passportVisibilityLabel(p.visibility),
                    photoUrl = currentPet?.photoUrl,
                    avatarAssetId = currentPet?.avatarFileAssetId
                )
                Spacer(Modifier.height(16.dp))
                VitaCoraSectionCard(
                    title = "Resumen",
                    description = "Información clave de ${p.displayName}."
                ) {
                    currentPet?.let { petSummary ->
                        Text("Especie: ${petSummary.species.name}")
                        petSummary.breed?.takeIf { it.isNotBlank() }?.let { Text("Raza: $it") }
                        Text(
                            "Sexo: ${
                                when (petSummary.sex) {
                                    PetSex.MALE -> "Macho"
                                    PetSex.FEMALE -> "Hembra"
                                    PetSex.UNKNOWN -> "Sin especificar"
                                }
                            }"
                        )
                        if (petSummary.ageYears > 0 || petSummary.ageMonths > 0) {
                            Text("Edad: ${petSummary.ageYears}a ${petSummary.ageMonths}m")
                        }
                        petSummary.weightKg?.takeIf { it > 0 }?.let {
                            Text("Peso: ${it.toString().replace('.', ',')} kg")
                        }
                        petSummary.sterilized?.takeIf { it != SterilizationStatus.UNKNOWN }?.let {
                            Text(
                                "Castración: ${
                                    if (it == SterilizationStatus.YES) "Sí" else "No"
                                }"
                            )
                        }
                    }
                    TextButton(onClick = { onEdit(petId) }) { Text("Editar datos") }
                }
                Spacer(Modifier.height(12.dp))
                VitaCoraSectionCard(
                    title = "Salud",
                    description = "Vacunas, peso y cuidados registrados.",
                    actionLabel = "Ver salud",
                    onAction = { onEdit(petId) }
                )
                Spacer(Modifier.height(12.dp))
                VitaCoraSectionCard(
                    title = "Últimos movimientos",
                    description = if (historyPreview.isEmpty()) {
                        "Todavía no hay eventos recientes."
                    } else {
                        historyPreview.joinToString("\n") { item ->
                            "• ${VitaCoraHistoryPresentation.titleFor(item)}"
                        }
                    },
                    actionLabel = "Ver historial completo",
                    onAction = { onHistory(p.id) }
                )
                Spacer(Modifier.height(12.dp))
                VitaCoraSectionCard(
                    title = "Compartir QR",
                    description = "Compartí un QR para abrir la vista pública de la VitaCora de ${p.displayName}.",
                    actionLabel = "Compartir QR",
                    onAction = { onShare(p.id) }
                )
                Spacer(Modifier.height(12.dp))
                VitaCoraSectionCard(
                    title = "Acceso profesional",
                    description = "Administrá quién puede consultar o colaborar con la VitaCora de ${p.displayName}.",
                    actionLabel = "Gestionar accesos",
                    onAction = { onProfessionalAccess(petId) }
                )
                Spacer(Modifier.height(12.dp))
                VitaCoraSectionCard(
                    title = "Propuestas VitaCora",
                    description = "Revisá aportes o cambios enviados por profesionales o personas autorizadas.",
                    actionLabel = "Ver propuestas",
                    onAction = { onProposals(petId) }
                )
                if (p.status == M14PassportStatus.DRAFT) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.activate() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Activar VitaCora") }
                }
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    color = if (messageIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
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

    LaunchedEffect(passport?.id) {
        passport?.let {
            name = it.displayName
            breed = it.breedText.orEmpty()
            color = it.primaryColor.orEmpty()
            marks = it.distinctiveMarks.orEmpty()
        }
    }
    LaunchedEffect(saved) {
        if (saved) onSaved()
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Editar VitaCora",
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
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.save(
                        displayName = name,
                        breedText = breed.ifBlank { null },
                        primaryColor = color.ifBlank { null },
                        distinctiveMarks = marks.ifBlank { null },
                        microchip = null,
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
                    message = "Agregá identidad u otras atestaciones documentales."
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
                label = { Text("Referencia de archivo (opcional)") },
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
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

@Composable
private fun VitaCoraHubHeader(
    petName: String,
    vitacoraNumber: String,
    statusLabel: String,
    visibilityLabel: String,
    photoUrl: String?,
    avatarAssetId: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BrandCream)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PetImage(
                imageUrl = photoUrl,
                contentDescription = petName,
                modifier = Modifier.size(72.dp),
                cornerRadius = 36.dp
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "VitaCora de $petName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("N.º $vitacoraNumber", style = MaterialTheme.typography.bodyMedium)
                Text("Estado: $statusLabel", style = MaterialTheme.typography.bodySmall)
                Text("Visibilidad: $visibilityLabel", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun VitaCoraSectionCard(
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp)
            )
            content?.invoke()
            actionLabel?.let { label ->
                TextButton(onClick = { onAction?.invoke() }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(label, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun passportStatusLabel(status: M14PassportStatus): String = when (status) {
    M14PassportStatus.DRAFT -> "Borrador"
    M14PassportStatus.ACTIVE -> "Activa"
    M14PassportStatus.SUSPENDED -> "Suspendida"
    M14PassportStatus.REVOKED -> "Revocada"
    M14PassportStatus.ARCHIVED -> "Archivada"
}

private fun passportVisibilityLabel(visibility: M14Visibility): String = when (visibility) {
    M14Visibility.PRIVATE -> "Privada"
    M14Visibility.RESPONSIBLES -> "Red de cuidado"
    M14Visibility.AUTHORIZED_ORGANIZATIONS -> "Organizaciones autorizadas"
    M14Visibility.PUBLIC_REDACTED -> "Pública resumida"
}

@Preview(showBackground = true, name = "PassportCreationEmptyPreview")
@Composable
private fun PassportCreationEmptyPreview() {
    ComunidappTheme {
        Column(Modifier.padding(16.dp)) {
            Text("VitaCora de Luna", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Reuní en un solo lugar su información más importante.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir VitaCora")
            }
        }
    }
}

@Preview(showBackground = true, name = "PassportCreationErrorPreview")
@Composable
private fun PassportCreationErrorPreview() {
    ComunidappTheme {
        Column(Modifier.padding(16.dp)) {
            Text(
                "No pudimos abrir VitaCora",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            Text("Revisá tu conexión e intentá nuevamente.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
            TextButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
        }
    }
}
