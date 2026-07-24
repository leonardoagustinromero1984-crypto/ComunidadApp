package com.comunidapp.app.ui.screens.veterinary

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.VeterinaryClinicStatus
import com.comunidapp.app.data.model.VeterinaryServiceCategory
import com.comunidapp.app.data.model.VeterinarySpecialty
import com.comunidapp.app.data.model.VeterinaryVerificationStatus
import com.comunidapp.app.data.repository.CreateVeterinaryClinicDraftInput
import com.comunidapp.app.data.repository.UpdateVeterinaryClinicDraftInput
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.ManagedVeterinaryClinicsUiState
import com.comunidapp.app.viewmodel.ManagedVeterinaryClinicsViewModel
import com.comunidapp.app.viewmodel.VeterinaryClinicDetailUiState
import com.comunidapp.app.viewmodel.VeterinaryClinicDetailViewModel
import com.comunidapp.app.viewmodel.VeterinaryClinicDraftViewModel
import com.comunidapp.app.viewmodel.VeterinaryDirectoryUiState
import com.comunidapp.app.viewmodel.VeterinaryDirectoryViewModel

@Composable
fun VeterinaryDirectoryScreen(
    onNavigateBack: () -> Unit,
    onClinicClick: (String) -> Unit,
    onMyClinics: () -> Unit,
    onMyAppointments: () -> Unit = {},
    viewModel: VeterinaryDirectoryViewModel = viewModel(factory = VeterinaryDirectoryViewModel.factory())
) {
    val state by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    var query by remember { mutableStateOf(filter.query.orEmpty()) }
    var zone by remember { mutableStateOf(filter.zoneText.orEmpty()) }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Veterinarias",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            OutlinedButton(onClick = onMyClinics, modifier = Modifier.fillMaxWidth()) {
                Text("Mis veterinarias (borrador local)")
            }
            OutlinedButton(onClick = onMyAppointments, modifier = Modifier.fillMaxWidth()) {
                Text("Mis turnos")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.setQuery(it.takeIf { s -> s.isNotBlank() })
                },
                label = { Text("Buscar") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = zone,
                onValueChange = {
                    zone = it
                    viewModel.setZone(it.takeIf { s -> s.isNotBlank() })
                },
                label = { Text("Zona") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = filter.emergencyCareOnly,
                    onCheckedChange = viewModel::setEmergencyOnly
                )
                Text("Solo guardia")
                Checkbox(
                    checked = filter.open24HoursOnly,
                    onCheckedChange = viewModel::setOpen24Only
                )
                Text("24 h")
                Checkbox(
                    checked = filter.verifiedOnly,
                    onCheckedChange = viewModel::setVerifiedOnly
                )
                Text("Verificadas")
            }
            Text(
                "Especialidad: ${filter.specialty?.name ?: "todas"} · Servicio: ${filter.serviceCategory?.name ?: "todos"}",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                onClick = {
                    val next = when (filter.specialty) {
                        null -> VeterinarySpecialty.GENERAL_MEDICINE
                        VeterinarySpecialty.GENERAL_MEDICINE -> VeterinarySpecialty.SURGERY
                        else -> null
                    }
                    viewModel.setSpecialty(next)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Ciclar especialidad") }
            OutlinedButton(
                onClick = {
                    val next = when (filter.serviceCategory) {
                        null -> VeterinaryServiceCategory.CONSULTATION
                        VeterinaryServiceCategory.CONSULTATION -> VeterinaryServiceCategory.EMERGENCY_GUARD
                        else -> null
                    }
                    viewModel.setServiceCategory(next)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Ciclar servicio") }
            Spacer(Modifier.height(8.dp))
            when (val s = state) {
                VeterinaryDirectoryUiState.Loading -> LoadingState()
                VeterinaryDirectoryUiState.Empty -> EmptyState(title = "No hay veterinarias públicas.")
                is VeterinaryDirectoryUiState.Error -> ErrorState(message = s.message)
                is VeterinaryDirectoryUiState.Content -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(s.items, key = { it.id }) { item ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onClinicClick(item.id) }
                                .padding(8.dp)
                        ) {
                            Text(item.displayName, fontWeight = FontWeight.SemiBold)
                            Text(item.publicZoneText)
                            Text("Verificación: ${item.verificationStatus.name}")
                            if (item.specialties.isNotEmpty()) {
                                Text("Especialidades: ${item.specialties.take(3).joinToString { it.name }}")
                            }
                            if (item.serviceCategories.isNotEmpty()) {
                                Text("Servicios: ${item.serviceCategories.take(3).joinToString { it.name }}")
                            }
                            if (item.offersEmergencyCare) Text("Guardia / emergencias")
                            if (item.isOpen24Hours) Text("Abierta 24 horas")
                            Text(
                                "Logo: ${item.logoAssetRef ?: "placeholder"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VeterinaryClinicDetailScreen(
    clinicId: String,
    onNavigateBack: () -> Unit,
    onBookAppointment: (String) -> Unit = {},
    viewModel: VeterinaryClinicDetailViewModel = viewModel(
        factory = VeterinaryClinicDetailViewModel.factory(clinicId)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val professionals by viewModel.professionals.collectAsState()
    val services by viewModel.services.collectAsState()
    val hours by viewModel.openingHours.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Detalle veterinaria",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when (val s = state) {
            VeterinaryClinicDetailUiState.Loading -> LoadingState(contentModifier = Modifier.padding(padding))
            is VeterinaryClinicDetailUiState.Error -> ErrorState(
                message = s.message,
                onRetry = viewModel::reload,
                contentModifier = Modifier.padding(padding)
            )
            is VeterinaryClinicDetailUiState.Content -> {
                val clinic = s.data.clinic
                Column(
                    Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(clinic.displayName, style = MaterialTheme.typography.headlineSmall)
                    Text(clinic.publicZoneText)
                    Text("Verificación: ${clinic.verificationStatus.name}")
                    clinic.description?.let { Text(it) }
                    if (clinic.offersEmergencyCare) Text("Ofrece guardia / emergencias")
                    if (clinic.isOpen24Hours) Text("Abierta 24 horas")
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onBookAppointment(clinic.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Solicitar turno") }
                    Spacer(Modifier.height(8.dp))
                    Text("Horarios", fontWeight = FontWeight.SemiBold)
                    hours.forEach { h ->
                        Text(
                            if (h.closed) "${h.dayOfWeek}: cerrado"
                            else "${h.dayOfWeek}: ${h.opensAt}–${h.closesAt}" +
                                if (h.emergencyOnly) " (solo guardia)" else ""
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Profesionales", fontWeight = FontWeight.SemiBold)
                    professionals.forEach { p ->
                        Text("${p.displayName} · ${p.verificationStatus.name}")
                        Text("Especialidades: ${p.specialties.joinToString { it.name }}")
                        if (p.publicContactEnabled) {
                            Text("Contacto: ${p.publicPhone ?: "—"} / ${p.publicEmail ?: "—"}")
                        } else {
                            Text("Contacto público deshabilitado")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Servicios", fontWeight = FontWeight.SemiBold)
                    services.filter { it.active }.forEach { svc ->
                        Text("${svc.name} (${svc.category.name})")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Contacto de la clínica", fontWeight = FontWeight.SemiBold)
                    Text("Tel: ${clinic.publicPhone ?: "no informado"}")
                    Text("Email: ${clinic.publicEmail ?: "no informado"}")
                    clinic.websiteUrl?.let { Text("Web: $it") }
                    clinic.socialLinks.forEach { (k, v) -> Text("$k: $v") }
                    Text("Logo: ${clinic.logoAssetRef ?: "placeholder"}")
                    Text("Portada: ${clinic.coverAssetRef ?: "—"}")
                }
            }
        }
    }
}

@Composable
fun ManagedVeterinaryClinicsScreen(
    onNavigateBack: () -> Unit,
    onClinicClick: (String) -> Unit,
    onCreate: () -> Unit,
    viewModel: ManagedVeterinaryClinicsViewModel = viewModel(
        factory = ManagedVeterinaryClinicsViewModel.factory()
    )
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Mis veterinarias",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text(
                "Persistencia remota: pendiente Bloque 2. Los borradores son solo locales.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text("Nuevo borrador local")
            }
            Spacer(Modifier.height(12.dp))
            when (val s = state) {
                ManagedVeterinaryClinicsUiState.Loading -> LoadingState()
                ManagedVeterinaryClinicsUiState.Empty -> EmptyState(title = "Sin veterinarias vinculadas.")
                is ManagedVeterinaryClinicsUiState.Error -> ErrorState(message = s.message)
                is ManagedVeterinaryClinicsUiState.Content -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(s.items, key = { it.id }) { item ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onClinicClick(item.id) }
                                .padding(8.dp)
                        ) {
                            Text(
                                "${item.displayName} · ${item.status.name}",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(item.publicZoneText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VeterinaryClinicDraftScreen(
    clinicId: String?,
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: VeterinaryClinicDraftViewModel = viewModel(
        factory = VeterinaryClinicDraftViewModel.factory(clinicId)
    )
) {
    val existing by viewModel.existing.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    val loading by viewModel.loading.collectAsState()

    var organizationId by remember { mutableStateOf("org-vet-demo-1") }
    var displayName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var emergency by remember { mutableStateOf(false) }
    var open24 by remember { mutableStateOf(false) }
    var draftStatus by remember { mutableStateOf(VeterinaryClinicStatus.DRAFT) }

    LaunchedEffect(existing) {
        existing?.let {
            organizationId = it.organizationId
            displayName = it.displayName
            description = it.description.orEmpty()
            zone = it.publicZoneText
            phone = it.publicPhone.orEmpty()
            email = it.publicEmail.orEmpty()
            website = it.websiteUrl.orEmpty()
            emergency = it.offersEmergencyCare
            open24 = it.isOpen24Hours
            draftStatus = it.status
        }
    }

    LaunchedEffect(Unit) {
        viewModel.saved.collect { id -> onSaved(id) }
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = if (clinicId.isNullOrBlank()) "Borrador veterinaria" else "Editar borrador",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        if (loading) {
            LoadingState(contentModifier = Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Solo borrador local. La persistencia remota se implementará en el Bloque 2.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            if (clinicId.isNullOrBlank()) {
                OutlinedTextField(
                    value = organizationId,
                    onValueChange = { organizationId = it },
                    label = { Text("Organización M03") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = zone,
                onValueChange = { zone = it },
                label = { Text("Zona pública") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Teléfono público") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email público") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = website,
                onValueChange = { website = it },
                label = { Text("Sitio web") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = emergency, onCheckedChange = { emergency = it })
                Text("Guardia / emergencias")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = open24, onCheckedChange = { open24 = it })
                Text("24 horas")
            }
            Text("Estado borrador: ${draftStatus.name}")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (clinicId.isNullOrBlank()) {
                        viewModel.create(
                            CreateVeterinaryClinicDraftInput(
                                organizationId = organizationId,
                                displayName = displayName,
                                description = description.takeIf { it.isNotBlank() },
                                publicZoneText = zone,
                                publicPhone = phone.takeIf { it.isNotBlank() },
                                publicEmail = email.takeIf { it.isNotBlank() },
                                websiteUrl = website.takeIf { it.isNotBlank() },
                                offersEmergencyCare = emergency,
                                isOpen24Hours = open24
                            )
                        )
                    } else {
                        viewModel.update(
                            UpdateVeterinaryClinicDraftInput(
                                clinicId = clinicId,
                                displayName = displayName,
                                description = description.takeIf { it.isNotBlank() },
                                publicZoneText = zone,
                                publicPhone = phone.takeIf { it.isNotBlank() },
                                publicEmail = email.takeIf { it.isNotBlank() },
                                websiteUrl = website.takeIf { it.isNotBlank() },
                                offersEmergencyCare = emergency,
                                isOpen24Hours = open24,
                                status = draftStatus
                            )
                        )
                    }
                },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (submitting) "Guardando…" else "Guardar borrador local")
            }
        }
    }
}
