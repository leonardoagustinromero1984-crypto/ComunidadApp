package com.comunidapp.shared.vertical

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.shared.lostfound.LostFoundEditDraft
import com.comunidapp.shared.lostfound.LostFoundEditDraftValidator
import com.comunidapp.shared.lostfound.LostFoundId
import com.comunidapp.shared.lostfound.LostFoundManageResult
import com.comunidapp.shared.lostfound.LostFoundRepository
import com.comunidapp.shared.notifications.NotificationPreferenceWriteResult
import com.comunidapp.shared.notifications.NotificationPreferencesRepository
import com.comunidapp.shared.notifications.SharedNotificationPreference
import com.comunidapp.shared.notifications.applyQuietHoursToAll
import com.comunidapp.shared.notifications.updatePreferenceSanitized
import com.comunidapp.shared.pets.PetHealthDraft
import com.comunidapp.shared.pets.PetHealthReminder
import com.comunidapp.shared.pets.PetHealthSummary
import com.comunidapp.shared.pets.PetHealthWriteResult
import com.comunidapp.shared.pets.PetVaccination
import com.comunidapp.shared.pets.SharedPetsRepository
import com.comunidapp.shared.poc.m08.model.FileRef
import com.comunidapp.shared.poc.m08.model.ImagePickResult
import com.comunidapp.shared.poc.m08.platform.ImagePicker
import com.comunidapp.shared.push.PushInstallationRepository
import com.comunidapp.shared.push.PushPermissionState
import com.comunidapp.shared.push.PushRegistrationCoordinator
import com.comunidapp.shared.push.PushRegistrationResult
import com.comunidapp.shared.ui.VerticalLoadState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun SharedPetHealthEditScreen(
    petId: PetId,
    petsRepository: SharedPetsRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    var vaccinations by remember { mutableStateOf(listOf<PetVaccination>()) }
    var reminders by remember { mutableStateOf(listOf<PetHealthReminder>()) }
    var sterilized by remember { mutableStateOf("") }
    var lastDeworming by remember { mutableStateOf("") }
    var dewormingProduct by remember { mutableStateOf("") }
    var lastFlea by remember { mutableStateOf("") }
    var fleaProduct by remember { mutableStateOf("") }
    var lastVet by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var newVaccineName by remember { mutableStateOf("") }
    var newVaccineDate by remember { mutableStateOf("") }
    var newReminderTitle by remember { mutableStateOf("") }
    var newReminderDate by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(petId, petsRepository) {
        val state = petsRepository.observePetDetail(petId).first { it !is VerticalLoadState.Loading }
        if (state is VerticalLoadState.Content) {
            val h = state.data.health ?: PetHealthSummary()
            vaccinations = h.vaccinations
            reminders = h.reminders
            sterilized = h.sterilized.orEmpty()
            lastDeworming = h.lastDeworming.orEmpty()
            dewormingProduct = h.dewormingProduct.orEmpty()
            lastFlea = h.lastFleaTreatment.orEmpty()
            fleaProduct = h.fleaTreatmentProduct.orEmpty()
            lastVet = h.lastVetVisit.orEmpty()
            notes = h.healthNotes.orEmpty()
            weight = h.weightKg?.toString().orEmpty()
        } else if (state is VerticalLoadState.Error) {
            error = state.message
        }
        loaded = true
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
            Text("Salud", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!loaded) {
                CircularProgressIndicator()
            } else {
                Text("Vacunas", style = MaterialTheme.typography.titleMedium)
                vaccinations.forEachIndexed { index, v ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${v.name} · ${v.date}")
                        TextButton(onClick = {
                            vaccinations = vaccinations.filterIndexed { i, _ -> i != index }
                        }) { Text("Quitar") }
                    }
                }
                OutlinedTextField(
                    value = newVaccineName,
                    onValueChange = { newVaccineName = it },
                    label = { Text("Vacuna") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = newVaccineDate,
                    onValueChange = { newVaccineDate = it },
                    label = { Text("Fecha (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedButton(
                    onClick = {
                        if (newVaccineName.isNotBlank() && newVaccineDate.isNotBlank()) {
                            vaccinations = vaccinations + PetVaccination(
                                name = newVaccineName.trim(),
                                date = newVaccineDate.trim()
                            )
                            newVaccineName = ""
                            newVaccineDate = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Agregar vacuna") }

                Text("Recordatorios", style = MaterialTheme.typography.titleMedium)
                reminders.forEachIndexed { index, r ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${r.title} · ${r.date}")
                        TextButton(onClick = {
                            reminders = reminders.filterIndexed { i, _ -> i != index }
                        }) { Text("Quitar") }
                    }
                }
                OutlinedTextField(
                    value = newReminderTitle,
                    onValueChange = { newReminderTitle = it },
                    label = { Text("Recordatorio") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = newReminderDate,
                    onValueChange = { newReminderDate = it },
                    label = { Text("Fecha") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedButton(
                    onClick = {
                        if (newReminderTitle.isNotBlank() && newReminderDate.isNotBlank()) {
                            reminders = reminders + PetHealthReminder(
                                id = "r-${newReminderTitle.hashCode()}",
                                title = newReminderTitle.trim(),
                                date = newReminderDate.trim(),
                                type = "GENERAL"
                            )
                            newReminderTitle = ""
                            newReminderDate = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Agregar recordatorio") }

                OutlinedTextField(
                    value = sterilized,
                    onValueChange = { sterilized = it },
                    label = { Text("Esterilizado (YES/NO)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = lastDeworming,
                    onValueChange = { lastDeworming = it },
                    label = { Text("Última desparasitación") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = dewormingProduct,
                    onValueChange = { dewormingProduct = it },
                    label = { Text("Producto desparasitación") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = lastFlea,
                    onValueChange = { lastFlea = it },
                    label = { Text("Último antipulgas") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = fleaProduct,
                    onValueChange = { fleaProduct = it },
                    label = { Text("Producto antipulgas") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = lastVet,
                    onValueChange = { lastVet = it },
                    label = { Text("Última visita vet") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas de salud") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Peso kg (opcional)") },
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
                            val draft = PetHealthDraft(
                                vaccinations = vaccinations,
                                reminders = reminders,
                                sterilized = sterilized.trim().ifBlank { null },
                                lastDeworming = lastDeworming.trim().ifBlank { null },
                                dewormingProduct = dewormingProduct.trim().ifBlank { null },
                                lastFleaTreatment = lastFlea.trim().ifBlank { null },
                                fleaTreatmentProduct = fleaProduct.trim().ifBlank { null },
                                lastVetVisit = lastVet.trim().ifBlank { null },
                                healthNotes = notes.trim().ifBlank { null },
                                weightKg = weight.trim().toFloatOrNull()
                            )
                            when (val result = petsRepository.updateHealth(petId, draft)) {
                                is PetHealthWriteResult.Success -> onSaved()
                                is PetHealthWriteResult.ValidationError -> error = result.message
                                is PetHealthWriteResult.Unauthenticated -> error = result.message
                                is PetHealthWriteResult.Forbidden -> error = result.message
                                is PetHealthWriteResult.BackendError -> error = result.message
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Guardando…" else "Guardar salud") }
            }
        }
    }
}

@Composable
internal fun SharedLostFoundEditScreen(
    id: LostFoundId,
    lostFoundRepository: LostFoundRepository,
    imagePicker: ImagePicker?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<FileRef?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var canManage by remember { mutableStateOf(false) }
    var statusLabel by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id, lostFoundRepository) {
        val state = lostFoundRepository.observeDetail(id).first { it !is VerticalLoadState.Loading }
        if (state is VerticalLoadState.Content) {
            val d = state.data
            description = d.description
            location = d.approximateLocation.displayLabel()
            canManage = d.viewerCanManage
            statusLabel = d.status.name
            // RESOLVED: RLS permite editar — mostramos el formulario.
        } else if (state is VerticalLoadState.Error) {
            error = state.message
        }
        loaded = true
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
            Text("Editar aviso", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!loaded) {
                CircularProgressIndicator()
            } else if (!canManage) {
                Text("No tenés permiso para editar este aviso.")
            } else {
                if (statusLabel == "RESOLVED") {
                    Text(
                        "Caso resuelto: podés actualizar descripción, zona y foto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Zona (aproximada)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (imagePicker != null) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                when (val pick = imagePicker.pickImage()) {
                                    is ImagePickResult.Success ->
                                        photo = pick.file
                                    else -> Unit
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (photo != null) "Foto seleccionada" else "Cambiar foto")
                    }
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
                            val draft = LostFoundEditDraft(description, location, photo)
                            LostFoundEditDraftValidator.validate(draft).exceptionOrNull()?.let {
                                error = com.comunidapp.shared.ui.ErrorSanitizer.sanitize(it)
                                busy = false
                                return@launch
                            }
                            val contentResult = lostFoundRepository.updateOwnerContent(
                                id,
                                description = draft.description.trim(),
                                location = draft.location.trim()
                            )
                            when (contentResult) {
                                LostFoundManageResult.Success -> Unit
                                is LostFoundManageResult.PartialSuccess -> {
                                    info = contentResult.message
                                }
                                is LostFoundManageResult.Forbidden -> {
                                    error = contentResult.message
                                    busy = false
                                    return@launch
                                }
                                is LostFoundManageResult.Unauthenticated -> {
                                    error = contentResult.message
                                    busy = false
                                    return@launch
                                }
                                is LostFoundManageResult.Conflict -> {
                                    error = contentResult.message
                                    busy = false
                                    return@launch
                                }
                                is LostFoundManageResult.BackendError -> {
                                    error = contentResult.message
                                    busy = false
                                    return@launch
                                }
                            }
                            val newPhoto = draft.newPhoto
                            if (newPhoto != null) {
                                when (val photoResult = lostFoundRepository.replacePhoto(id, newPhoto)) {
                                    LostFoundManageResult.Success -> onSaved()
                                    is LostFoundManageResult.PartialSuccess -> {
                                        info = photoResult.message
                                        onSaved()
                                    }
                                    is LostFoundManageResult.Forbidden -> error = photoResult.message
                                    is LostFoundManageResult.Unauthenticated -> error = photoResult.message
                                    is LostFoundManageResult.Conflict -> error = photoResult.message
                                    is LostFoundManageResult.BackendError -> error = photoResult.message
                                }
                            } else {
                                onSaved()
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Guardando…" else "Guardar") }
            }
        }
    }
}

@Composable
internal fun SharedNotificationSettingsScreen(
    preferencesRepository: NotificationPreferencesRepository,
    pushRegistrationCoordinator: PushRegistrationCoordinator?,
    pushInstallationRepository: PushInstallationRepository?,
    onBack: () -> Unit
) {
    var prefs by remember { mutableStateOf<List<SharedNotificationPreference>>(emptyList()) }
    var permission by remember { mutableStateOf<PushPermissionState?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var quietEnabled by remember { mutableStateOf(false) }
    var quietStart by remember { mutableStateOf("22:00") }
    var quietEnd by remember { mutableStateOf("07:00") }
    var timezone by remember { mutableStateOf("UTC") }
    var marketingConsent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loaded = false
            error = null
            permission = pushRegistrationCoordinator?.currentPermission()
            preferencesRepository.getPreferences().fold(
                onSuccess = {
                    prefs = it
                    val sample = it.firstOrNull()
                    if (sample != null) {
                        quietEnabled =
                            sample.quietHoursStart != null || sample.quietHoursEnd != null
                        quietStart = sample.quietHoursStart ?: "22:00"
                        quietEnd = sample.quietHoursEnd ?: "07:00"
                        timezone = sample.timezone.ifBlank { "UTC" }
                        marketingConsent = sample.marketingConsent
                    }
                },
                onFailure = {
                    error = com.comunidapp.shared.ui.ErrorSanitizer.sanitize(it)
                    prefs = emptyList()
                }
            )
            loaded = true
        }
    }

    LaunchedEffect(preferencesRepository, pushRegistrationCoordinator) {
        reload()
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
            Text("Notificaciones", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            when (permission) {
                PushPermissionState.NotDetermined -> {
                    OutlinedButton(
                        onClick = {
                            val coordinator = pushRegistrationCoordinator
                            val repo = pushInstallationRepository
                            if (coordinator == null || repo == null) {
                                error = "Notificaciones no configuradas."
                                return@OutlinedButton
                            }
                            scope.launch {
                                when (
                                    val result = coordinator.requestPermissionAndRegister(
                                        repository = repo,
                                        installationId = "ios-install-${preferencesRepository.hashCode().toUInt()}"
                                    )
                                ) {
                                    PushRegistrationResult.Success -> {
                                        info = "Notificaciones activadas."
                                        permission = PushPermissionState.Authorized
                                    }
                                    PushRegistrationResult.PermissionDenied ->
                                        permission = PushPermissionState.Denied
                                    else -> info = "No se pudo activar notificaciones."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Activar notificaciones") }
                }
                PushPermissionState.Denied -> {
                    Text(
                        "Las notificaciones están desactivadas en Ajustes del sistema.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                PushPermissionState.Authorized,
                PushPermissionState.Provisional -> {
                    Text(
                        "Permiso de push: activo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PushPermissionState.Unavailable, null -> {
                    Text(
                        "Estado de permiso no disponible en esta plataforma.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (!loaded) {
                CircularProgressIndicator()
            } else {
                Text("Horario silencioso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Activar horario silencioso")
                    Switch(
                        checked = quietEnabled,
                        onCheckedChange = { quietEnabled = it },
                        enabled = !busy
                    )
                }
                if (quietEnabled) {
                    OutlinedTextField(
                        value = quietStart,
                        onValueChange = { quietStart = it },
                        label = { Text("Desde (HH:MM)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    )
                    OutlinedTextField(
                        value = quietEnd,
                        onValueChange = { quietEnd = it },
                        label = { Text("Hasta (HH:MM)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    )
                    OutlinedTextField(
                        value = timezone,
                        onValueChange = { timezone = it },
                        label = { Text("Zona horaria (IANA)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Consentimiento marketing")
                    Switch(
                        checked = marketingConsent,
                        onCheckedChange = { marketingConsent = it },
                        enabled = !busy
                    )
                }
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        error = null
                        scope.launch {
                            val start = if (quietEnabled) quietStart.trim().ifBlank { null } else null
                            val end = if (quietEnabled) quietEnd.trim().ifBlank { null } else null
                            when (
                                val result = preferencesRepository.applyQuietHoursToAll(
                                    loaded = prefs,
                                    quietHoursStart = start,
                                    quietHoursEnd = end,
                                    timezone = timezone.ifBlank { "UTC" },
                                    marketingConsent = marketingConsent,
                                    quietHoursDays = null
                                )
                            ) {
                                is NotificationPreferenceWriteResult.Success -> {
                                    info = "Horario silencioso guardado."
                                    reload()
                                }
                                is NotificationPreferenceWriteResult.ValidationError ->
                                    error = result.message
                                is NotificationPreferenceWriteResult.Unauthenticated ->
                                    error = result.message
                                is NotificationPreferenceWriteResult.Forbidden ->
                                    error = result.message
                                is NotificationPreferenceWriteResult.BackendError ->
                                    error = result.message
                            }
                            busy = false
                        }
                    },
                    enabled = !busy && prefs.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Guardando…" else "Guardar horario silencioso") }

                Text("Categorías", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                prefs.forEach { pref ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(pref.category, fontWeight = FontWeight.SemiBold)
                            Text(
                                "In-app: ${if (pref.inAppEnabled) "sí" else "no"}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                if (busy) return@OutlinedButton
                                busy = true
                                scope.launch {
                                    val updated = pref.copy(
                                        pushEnabled = !pref.pushEnabled,
                                        emailEnabled = false
                                    )
                                    when (
                                        val result =
                                            preferencesRepository.updatePreferenceSanitized(updated)
                                    ) {
                                        is NotificationPreferenceWriteResult.Success -> {
                                            prefs = prefs.map {
                                                if (it.category == result.preference.category) {
                                                    result.preference
                                                } else it
                                            }
                                        }
                                        is NotificationPreferenceWriteResult.ValidationError ->
                                            error = result.message
                                        is NotificationPreferenceWriteResult.Unauthenticated ->
                                            error = result.message
                                        is NotificationPreferenceWriteResult.Forbidden ->
                                            error = result.message
                                        is NotificationPreferenceWriteResult.BackendError ->
                                            error = result.message
                                    }
                                    busy = false
                                }
                            },
                            enabled = !busy
                        ) {
                            Text(if (pref.pushEnabled) "Push ON" else "Push OFF")
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
