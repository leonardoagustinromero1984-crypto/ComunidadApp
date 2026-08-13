package com.comunidapp.shared.vertical

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.shared.adoption.AdoptionApplicationDraft
import com.comunidapp.shared.adoption.AdoptionApplicationId
import com.comunidapp.shared.adoption.AdoptionApplicationRepository
import com.comunidapp.shared.adoption.AdoptionApplicationResult
import com.comunidapp.shared.adoption.AdoptionApplicationStatus
import com.comunidapp.shared.adoption.AdoptionApplicationSummary
import com.comunidapp.shared.adoption.AdoptionId
import com.comunidapp.shared.adoption.AdoptionPublishDraft
import com.comunidapp.shared.adoption.AdoptionPublishResult
import com.comunidapp.shared.adoption.AdoptionRepository
import com.comunidapp.shared.location.ApproximateLocation
import com.comunidapp.shared.pets.PetSummary
import com.comunidapp.shared.pets.SharedPetsRepository
import com.comunidapp.shared.poc.m08.model.ImagePickResult
import com.comunidapp.shared.poc.m08.platform.ImagePicker
import com.comunidapp.shared.profile.ProfileUpdateDraft
import com.comunidapp.shared.profile.ProfileUpdateResult
import com.comunidapp.shared.profile.UserProfileRepository
import com.comunidapp.shared.profile.UserProfileSummary
import com.comunidapp.shared.session.SessionRepository
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.ui.VerticalLoadState
import kotlinx.coroutines.launch

@Composable
internal fun SharedAdoptionPublishScreen(
    sessionRepository: SessionRepository,
    petsRepository: SharedPetsRepository,
    adoptionRepository: AdoptionRepository,
    onBack: () -> Unit,
    onPublished: (AdoptionId) -> Unit
) {
    val sessionVm = remember(sessionRepository) { SessionViewModelShared(sessionRepository) }
    DisposableEffect(sessionVm) { onDispose { sessionVm.clear() } }
    val session by sessionVm.state.collectAsState()
    val userId = (session as? SessionState.Authenticated)?.user?.userId
    val petsVm = remember(sessionRepository, petsRepository) {
        PetListViewModelShared(sessionRepository, petsRepository)
    }
    DisposableEffect(petsVm) { onDispose { petsVm.clear() } }
    val petsState by petsVm.state.collectAsState()

    var selectedPet by remember { mutableStateOf<PetSummary?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var requirements by remember { mutableStateOf("") }
    var locality by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text("Publicar adopción", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "La foto se toma de la mascota (contrato M09). Sin upload M05 aparte.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Mascota", fontWeight = FontWeight.SemiBold)
            when (val s = petsState) {
                VerticalLoadState.Loading -> CircularProgressIndicator()
                VerticalLoadState.Empty -> Text("No tenés mascotas activas.")
                is VerticalLoadState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is VerticalLoadState.Content -> {
                    s.data.forEach { pet ->
                        val selected = selectedPet?.id == pet.id
                        Text(
                            "${if (selected) "✓ " else ""}${pet.displayName} · ${pet.speciesLabel}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPet = pet }
                                .padding(8.dp),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )
            OutlinedTextField(
                value = requirements,
                onValueChange = { requirements = it },
                label = { Text("Requisitos (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = locality,
                onValueChange = { locality = it },
                label = { Text("Zona aproximada (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val pet = selectedPet ?: return@Button
                    if (busy || userId == null) return@Button
                    busy = true
                    error = null
                    scope.launch {
                        val result = adoptionRepository.publish(
                            AdoptionPublishDraft(
                                petId = pet.id,
                                title = title,
                                description = description,
                                requirements = requirements,
                                approximateLocation = ApproximateLocation(
                                    locality = locality.trim().ifBlank { "Zona no especificada" }
                                ),
                                publishImmediately = true
                            )
                        )
                        busy = false
                        when (result) {
                            is AdoptionPublishResult.Success -> onPublished(result.id)
                            is AdoptionPublishResult.ValidationError -> error = result.message
                            is AdoptionPublishResult.Unauthenticated -> error = result.message
                            is AdoptionPublishResult.Forbidden -> error = result.message
                            is AdoptionPublishResult.Conflict -> error = result.message
                            is AdoptionPublishResult.BackendError -> error = result.message
                        }
                    }
                },
                enabled = !busy && selectedPet != null && userId != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "Publicando…" else "Publicar")
            }
        }
    }
}

@Composable
internal fun SharedAdoptionApplyScreen(
    adoptionId: AdoptionId,
    adoptionTitle: String,
    applicationRepository: AdoptionApplicationRepository,
    onBack: () -> Unit,
    onSubmitted: () -> Unit
) {
    var message by remember { mutableStateOf("") }
    var housing by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text("Postularme", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(adoptionTitle, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Mensaje") },
                modifier = Modifier.fillMaxWidth().height(140.dp)
            )
            OutlinedTextField(
                value = housing,
                onValueChange = { housing = it },
                label = { Text("Tipo de vivienda (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = experience,
                onValueChange = { experience = it },
                label = { Text("Experiencia previa (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Teléfono de contacto (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    error = null
                    scope.launch {
                        val result = applicationRepository.submit(
                            AdoptionApplicationDraft(
                                adoptionId = adoptionId,
                                message = message,
                                housingType = housing.trim().ifBlank { null },
                                previousExperience = experience.trim().ifBlank { null },
                                contactPhone = phone.trim().ifBlank { null }
                            )
                        )
                        busy = false
                        when (result) {
                            is AdoptionApplicationResult.Success -> onSubmitted()
                            is AdoptionApplicationResult.ValidationError -> error = result.message
                            is AdoptionApplicationResult.Unauthenticated -> error = result.message
                            is AdoptionApplicationResult.Forbidden -> error = result.message
                            is AdoptionApplicationResult.Conflict -> error = result.message
                            is AdoptionApplicationResult.BackendError -> error = result.message
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "Enviando…" else "Enviar postulación")
            }
        }
    }
}

@Composable
internal fun SharedMyApplicationsScreen(
    applicationRepository: AdoptionApplicationRepository,
    onBack: () -> Unit
) {
    var items by remember { mutableStateOf<List<AdoptionApplicationSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            error = null
            applicationRepository.listMine().fold(
                onSuccess = { items = it; loading = false },
                onFailure = {
                    error = "No pudimos cargar tus postulaciones."
                    loading = false
                }
            )
        }
    }

    LaunchedEffect(applicationRepository) { reload() }

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text("Mis postulaciones", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = { reload() }) { Text("Actualizar") }
            when {
                loading -> CircularProgressIndicator()
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                items.isEmpty() -> Text("Todavía no enviaste postulaciones.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id.value }) { app ->
                        Column(
                            Modifier.fillMaxWidth().padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(app.petName, fontWeight = FontWeight.SemiBold)
                            Text(app.adoptionTitle)
                            Text("${app.status.labelEs} · ${app.submittedAtLabel}")
                            Text(app.messagePreview, style = MaterialTheme.typography.bodySmall)
                            if (AdoptionApplicationStatus.canWithdraw(app.status)) {
                                OutlinedButton(
                                    onClick = {
                                        if (busyId != null) return@OutlinedButton
                                        busyId = app.id.value
                                        scope.launch {
                                            applicationRepository.withdraw(app.id)
                                            busyId = null
                                            reload()
                                        }
                                    },
                                    enabled = busyId == null
                                ) {
                                    Text("Retirar")
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
internal fun SharedProfileEditScreen(
    profile: UserProfileSummary,
    profileRepository: UserProfileRepository,
    imagePicker: ImagePicker?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    var name by remember(profile) { mutableStateOf(profile.displayName) }
    var locality by remember(profile) { mutableStateOf(profile.approximateLocation.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text("Editar perfil", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre visible") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = locality,
                onValueChange = { locality = it },
                label = { Text("Zona aproximada") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            if (imagePicker != null) {
                OutlinedButton(
                    onClick = {
                        if (busy) return@OutlinedButton
                        scope.launch {
                            when (val pick = imagePicker.pickImage()) {
                                is ImagePickResult.Success -> {
                                    busy = true
                                    error = null
                                    info = null
                                    when (val up = profileRepository.uploadAvatar(pick.file)) {
                                        is ProfileUpdateResult.Success -> {
                                            info = "Avatar actualizado."
                                            onSaved()
                                        }
                                        is ProfileUpdateResult.ValidationError -> error = up.message
                                        is ProfileUpdateResult.Unauthenticated -> error = up.message
                                        is ProfileUpdateResult.Forbidden -> error = up.message
                                        is ProfileUpdateResult.BackendError -> error = up.message
                                    }
                                    busy = false
                                }
                                ImagePickResult.Cancelled -> Unit
                                is ImagePickResult.Failure -> error = pick.message
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cambiar avatar")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    error = null
                    scope.launch {
                        val city = locality.trim().substringBefore(',').trim().ifBlank { null }
                        val province = locality.trim().substringAfter(',', missingDelimiterValue = "")
                            .trim().ifBlank { null }
                        val result = profileRepository.updateProfile(
                            ProfileUpdateDraft(
                                displayName = name.trim(),
                                city = city,
                                province = province
                            )
                        )
                        busy = false
                        when (result) {
                            is ProfileUpdateResult.Success -> onSaved()
                            is ProfileUpdateResult.ValidationError -> error = result.message
                            is ProfileUpdateResult.Unauthenticated -> error = result.message
                            is ProfileUpdateResult.Forbidden -> error = result.message
                            is ProfileUpdateResult.BackendError -> error = result.message
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "Guardando…" else "Guardar")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
