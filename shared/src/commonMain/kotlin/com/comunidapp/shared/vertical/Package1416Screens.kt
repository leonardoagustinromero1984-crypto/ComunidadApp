package com.comunidapp.shared.vertical

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.shared.adoption.AdoptionApplicationId
import com.comunidapp.shared.adoption.AdoptionApplicationRepository
import com.comunidapp.shared.adoption.AdoptionApplicationResult
import com.comunidapp.shared.adoption.AdoptionApplicationReviewDetail
import com.comunidapp.shared.adoption.AdoptionApplicationReviewSummary
import com.comunidapp.shared.adoption.AdoptionApplicationStatus
import com.comunidapp.shared.pets.PetCreateDraft
import com.comunidapp.shared.pets.PetCreateResult
import com.comunidapp.shared.pets.PetEditDraft
import com.comunidapp.shared.pets.PetEditResult
import com.comunidapp.shared.pets.SharedPetsRepository
import com.comunidapp.shared.poc.m08.model.FileRef
import com.comunidapp.shared.poc.m08.model.ImagePickResult
import com.comunidapp.shared.poc.m08.platform.ImagePicker
import com.comunidapp.shared.ui.VerticalLoadState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun SharedReceivedApplicationsScreen(
    applicationRepository: AdoptionApplicationRepository,
    onBack: () -> Unit,
    onOpenDetail: (AdoptionApplicationId) -> Unit
) {
    var items by remember { mutableStateOf<List<AdoptionApplicationReviewSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            error = null
            applicationRepository.listReceived().fold(
                onSuccess = { items = it; loading = false },
                onFailure = {
                    error = "No pudimos cargar las postulaciones recibidas."
                    loading = false
                }
            )
        }
    }

    LaunchedEffect(applicationRepository) { reload() }

    Scaffold { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Volver") }
            Text(
                "Postulaciones recibidas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = { reload() }) { Text("Actualizar") }
            when {
                loading -> CircularProgressIndicator()
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                items.isEmpty() -> Text("No hay postulaciones recibidas.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id.value }) { app ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenDetail(app.id) }
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(app.petName, fontWeight = FontWeight.SemiBold)
                            Text(app.adoptionTitle)
                            Text(app.applicantDisplayName)
                            Text("${app.status.labelEs} · ${app.submittedAtLabel}")
                            Text(app.messagePreview, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SharedApplicationReviewDetailScreen(
    applicationId: AdoptionApplicationId,
    applicationRepository: AdoptionApplicationRepository,
    onBack: () -> Unit
) {
    var detail by remember { mutableStateOf<AdoptionApplicationReviewDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var rejectReason by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            error = null
            applicationRepository.getForReview(applicationId).fold(
                onSuccess = { detail = it; loading = false },
                onFailure = {
                    error = "No pudimos cargar el detalle."
                    loading = false
                }
            )
        }
    }

    fun runAction(block: suspend () -> AdoptionApplicationResult) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            when (val r = block()) {
                is AdoptionApplicationResult.Success -> reload()
                is AdoptionApplicationResult.ValidationError -> error = r.message
                is AdoptionApplicationResult.Unauthenticated -> error = r.message
                is AdoptionApplicationResult.Forbidden -> error = r.message
                is AdoptionApplicationResult.Conflict -> error = r.message
                is AdoptionApplicationResult.BackendError -> error = r.message
            }
            busy = false
        }
    }

    LaunchedEffect(applicationId, applicationRepository) { reload() }

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
            Text("Revisión de postulación", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            when {
                loading -> CircularProgressIndicator()
                detail == null -> Text(error ?: "Sin datos", color = MaterialTheme.colorScheme.error)
                else -> {
                    val d = detail!!
                    Text(d.petName, fontWeight = FontWeight.SemiBold)
                    Text(d.adoptionTitle)
                    Text("Estado: ${d.status.labelEs}")
                    Text("Postulante: ${d.applicantDisplayName}")
                    Text("Enviada: ${d.submittedAtLabel}")
                    Text(d.message)
                    d.housingType?.let { Text("Vivienda: $it") }
                    d.hasOtherPets?.let { Text("Otras mascotas: ${if (it) "Sí" else "No"}") }
                    d.previousExperience?.let { Text("Experiencia: $it") }
                    d.contactPhone?.let { Text("Teléfono: $it") }
                    d.rejectionReason?.let { Text("Motivo rechazo: $it") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (AdoptionApplicationStatus.canMarkUnderReview(d.status)) {
                        Button(
                            onClick = { runAction { applicationRepository.markUnderReview(d.id) } },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Marcar en revisión") }
                    }
                    if (AdoptionApplicationStatus.canAccept(d.status)) {
                        Button(
                            onClick = { runAction { applicationRepository.accept(d.id) } },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Aceptar") }
                    }
                    if (AdoptionApplicationStatus.canReject(d.status)) {
                        OutlinedTextField(
                            value = rejectReason,
                            onValueChange = { rejectReason = it },
                            label = { Text("Motivo de rechazo (opcional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = {
                                runAction {
                                    applicationRepository.reject(
                                        d.id,
                                        rejectReason.trim().ifBlank { null }
                                    )
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Rechazar") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SharedPetCreateScreen(
    petsRepository: SharedPetsRepository,
    imagePicker: ImagePicker?,
    onBack: () -> Unit,
    onCreated: (PetId) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var species by remember { mutableStateOf("Perro") }
    var sex by remember { mutableStateOf("UNKNOWN") }
    var size by remember { mutableStateOf("UNKNOWN") }
    var description by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf<FileRef?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun speciesCode(label: String): String = when (label.trim().lowercase()) {
        "perro", "dog" -> "DOG"
        "gato", "cat" -> "CAT"
        else -> "UNKNOWN"
    }

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
            Text("Nueva mascota", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = species,
                onValueChange = { species = it },
                label = { Text("Especie") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = sex,
                onValueChange = { sex = it },
                label = { Text("Sexo (MALE/FEMALE/UNKNOWN)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = size,
                onValueChange = { size = it },
                label = { Text("Tamaño (SMALL/MEDIUM/LARGE/UNKNOWN)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción (opcional)") },
                modifier = Modifier.fillMaxWidth().height(100.dp)
            )
            if (imagePicker != null) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            when (val pick = imagePicker.pickImage()) {
                                is ImagePickResult.Success -> {
                                    avatar = pick.file
                                    info = "Foto seleccionada."
                                }
                                ImagePickResult.Cancelled -> Unit
                                is ImagePickResult.Failure -> error = pick.message
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (avatar == null) "Agregar foto (opcional)" else "Cambiar foto") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    error = null
                    info = null
                    scope.launch {
                        val result = petsRepository.create(
                            PetCreateDraft(
                                name = name,
                                species = speciesCode(species),
                                sex = sex.trim().ifBlank { "UNKNOWN" }.uppercase(),
                                size = size.trim().ifBlank { "UNKNOWN" }.uppercase(),
                                description = description,
                                avatarFile = avatar
                            )
                        )
                        busy = false
                        when (result) {
                            is PetCreateResult.Success -> onCreated(result.id)
                            is PetCreateResult.PartialSuccess -> {
                                info = result.mediaMessage
                                onCreated(result.id)
                            }
                            is PetCreateResult.ValidationError -> error = result.message
                            is PetCreateResult.Unauthenticated -> error = result.message
                            is PetCreateResult.Forbidden -> error = result.message
                            is PetCreateResult.Conflict -> error = result.message
                            is PetCreateResult.BackendError -> error = result.message
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "Creando…" else "Crear mascota")
            }
        }
    }
}

@Composable
internal fun SharedPetEditScreen(
    petId: PetId,
    petsRepository: SharedPetsRepository,
    imagePicker: ImagePicker?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var species by remember { mutableStateOf("Perro") }
    var breed by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("UNKNOWN") }
    var size by remember { mutableStateOf("UNKNOWN") }
    var description by remember { mutableStateOf("") }
    var ageYears by remember { mutableStateOf("0") }
    var ageMonths by remember { mutableStateOf("0") }
    var color by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf<FileRef?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(petId, petsRepository) {
        val state = petsRepository.observePetDetail(petId).first { it !is VerticalLoadState.Loading }
        if (state is VerticalLoadState.Content) {
            val d = state.data
            name = d.displayName
            species = d.speciesLabel
            breed = d.breedText.orEmpty()
            sex = d.sexLabel?.uppercase() ?: "UNKNOWN"
            size = d.sizeLabel?.uppercase() ?: "UNKNOWN"
            description = d.description.orEmpty()
            ageYears = (d.ageYears ?: 0).toString()
            ageMonths = (d.ageMonths ?: 0).toString()
            color = d.color.orEmpty()
            loaded = true
        } else if (state is VerticalLoadState.Error) {
            error = state.message
            loaded = true
        } else {
            loaded = true
        }
    }

    fun speciesCode(label: String): String = when (label.trim().lowercase()) {
        "perro", "dog" -> "DOG"
        "gato", "cat" -> "CAT"
        else -> label.trim().ifBlank { "UNKNOWN" }.uppercase()
    }

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
            Text("Editar mascota", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!loaded) {
                CircularProgressIndicator()
            } else {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = species,
                    onValueChange = { species = it },
                    label = { Text("Especie") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = breed,
                    onValueChange = { breed = it },
                    label = { Text("Raza (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = sex,
                    onValueChange = { sex = it },
                    label = { Text("Sexo (MALE/FEMALE/UNKNOWN)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = size,
                    onValueChange = { size = it },
                    label = { Text("Tamaño (SMALL/MEDIUM/LARGE/UNKNOWN)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = ageYears,
                    onValueChange = { ageYears = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Años") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = ageMonths,
                    onValueChange = { ageMonths = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Meses (0-11)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = color,
                    onValueChange = { color = it },
                    label = { Text("Color (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (opcional)") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
                if (imagePicker != null) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                when (val pick = imagePicker.pickImage()) {
                                    is ImagePickResult.Success -> {
                                        avatar = pick.file
                                        info = "Foto seleccionada."
                                    }
                                    ImagePickResult.Cancelled -> Unit
                                    is ImagePickResult.Failure -> error = pick.message
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (avatar == null) "Cambiar foto (opcional)" else "Foto lista") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        error = null
                        info = null
                        scope.launch {
                            val result = petsRepository.update(
                                petId,
                                PetEditDraft(
                                    name = name,
                                    species = speciesCode(species),
                                    breed = breed.trim().takeIf { it.isNotEmpty() },
                                    sex = sex.trim().ifBlank { "UNKNOWN" }.uppercase(),
                                    size = size.trim().ifBlank { "UNKNOWN" }.uppercase(),
                                    description = description,
                                    ageYears = ageYears.toIntOrNull() ?: 0,
                                    ageMonths = ageMonths.toIntOrNull() ?: 0,
                                    color = color.trim().takeIf { it.isNotEmpty() },
                                    avatarFile = avatar
                                )
                            )
                            busy = false
                            when (result) {
                                is PetEditResult.Success -> onSaved()
                                is PetEditResult.PartialSuccess -> {
                                    info = result.mediaMessage
                                    onSaved()
                                }
                                is PetEditResult.ValidationError -> error = result.message
                                is PetEditResult.Unauthenticated -> error = result.message
                                is PetEditResult.Forbidden -> error = result.message
                                is PetEditResult.BackendError -> error = result.message
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (busy) "Guardando…" else "Guardar cambios")
                }
            }
        }
    }
}
