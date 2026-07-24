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
import androidx.compose.material3.HorizontalDivider
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
import com.comunidapp.app.data.model.VeterinaryAppointment
import com.comunidapp.app.data.model.VeterinaryAppointmentFollowUp
import com.comunidapp.app.data.model.VeterinaryAppointmentStatus
import com.comunidapp.app.data.model.VeterinaryAppointmentTimelineStep
import com.comunidapp.app.data.model.VeterinaryReminderDeliveryStatus
import com.comunidapp.app.data.model.VeterinaryReminderSchedule
import com.comunidapp.app.data.repository.CreateVeterinaryAvailabilityRuleInput
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.MyVeterinaryAppointmentsViewModel
import com.comunidapp.app.viewmodel.VeterinaryAppointmentDetailUiState
import com.comunidapp.app.viewmodel.VeterinaryAppointmentDetailViewModel
import com.comunidapp.app.viewmodel.VeterinaryAppointmentManagementViewModel
import com.comunidapp.app.viewmodel.VeterinaryAppointmentsListUiState
import com.comunidapp.app.viewmodel.VeterinaryAvailabilityRulesUiState
import com.comunidapp.app.viewmodel.VeterinaryAvailabilityRulesViewModel
import com.comunidapp.app.viewmodel.VeterinaryBookAppointmentUiState
import com.comunidapp.app.viewmodel.VeterinaryBookAppointmentViewModel
import com.comunidapp.app.viewmodel.VeterinaryManagedAgendaViewModel
import com.comunidapp.app.viewmodel.VeterinaryScheduleSettingsUiState
import com.comunidapp.app.viewmodel.VeterinaryScheduleSettingsViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val VET_ZONE: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
private val VET_DATE_TIME_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(VET_ZONE)
private val VET_TIME_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(VET_ZONE)

private fun formatDateTime(instant: Instant): String = VET_DATE_TIME_FMT.format(instant)
private fun formatTime(instant: Instant): String = VET_TIME_FMT.format(instant)

private fun statusLabel(status: VeterinaryAppointmentStatus): String = when (status) {
    VeterinaryAppointmentStatus.REQUESTED -> "Solicitado"
    VeterinaryAppointmentStatus.CONFIRMED -> "Confirmado"
    VeterinaryAppointmentStatus.REJECTED -> "Rechazado"
    VeterinaryAppointmentStatus.CANCELLED_BY_USER -> "Cancelado por vos"
    VeterinaryAppointmentStatus.CANCELLED_BY_CLINIC -> "Cancelado por la clínica"
    VeterinaryAppointmentStatus.COMPLETED -> "Completado"
    VeterinaryAppointmentStatus.NO_SHOW -> "No asistió"
    VeterinaryAppointmentStatus.EXPIRED -> "Vencido"
    VeterinaryAppointmentStatus.UNKNOWN -> "Desconocido"
}

private fun reminderTypeLabel(type: com.comunidapp.app.data.model.VeterinaryReminderType): String =
    when (type) {
        com.comunidapp.app.data.model.VeterinaryReminderType.REMINDER_24H -> "24 h antes"
        com.comunidapp.app.data.model.VeterinaryReminderType.REMINDER_2H -> "2 h antes"
    }

/** Próximo paso sugerido según estado y rol. */
@Composable
private fun NextStepSection(status: VeterinaryAppointmentStatus, isManager: Boolean) {
    Text("Próximo paso", fontWeight = FontWeight.SemiBold)
    Text(
        VeterinaryAppointmentFollowUp.nextStep(status, isManager),
        style = MaterialTheme.typography.bodyMedium
    )
}

/**
 * Indicador de recordatorio. Nunca afirma push enviado: solo estado "preparado".
 */
@Composable
private fun ReminderSection(schedule: VeterinaryReminderSchedule?) {
    val active = schedule?.reminders?.filter {
        it.status == VeterinaryReminderDeliveryStatus.PREPARED ||
            it.status == VeterinaryReminderDeliveryStatus.FIRED_PREPARED
    }.orEmpty()
    if (active.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    Text("Recordatorios", fontWeight = FontWeight.SemiBold)
    Text("Recordatorio preparado (sin push)", style = MaterialTheme.typography.bodyMedium)
    active.forEach { r ->
        Text(
            "• ${reminderTypeLabel(r.type)} · ${formatDateTime(r.dueAt)}",
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (schedule?.infrastructureAvailable == false) {
        Text(
            "El envío automático depende de infraestructura externa.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Línea de tiempo de estados (etiquetas y motivos ya redactados por el repositorio). */
@Composable
private fun TimelineSection(steps: List<VeterinaryAppointmentTimelineStep>) {
    Text("Línea de tiempo", fontWeight = FontWeight.SemiBold)
    if (steps.isEmpty()) {
        Text("Sin cambios registrados.", style = MaterialTheme.typography.bodySmall)
    } else {
        steps.forEach { step ->
            Text(
                "${formatDateTime(step.at)} · ${step.label}" +
                    (step.reason?.let { " — $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// --- Reserva de turno ---

@Composable
fun VeterinaryBookAppointmentScreen(
    clinicId: String,
    onNavigateBack: () -> Unit,
    onBooked: (String) -> Unit,
    viewModel: VeterinaryBookAppointmentViewModel = viewModel(
        factory = VeterinaryBookAppointmentViewModel.factory(clinicId)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val slots by viewModel.slots.collectAsState()
    val selectedService by viewModel.selectedServiceId.collectAsState()
    val selectedProfessional by viewModel.selectedProfessionalId.collectAsState()
    val date by viewModel.selectedDate.collectAsState()
    val selectedSlot by viewModel.selectedSlot.collectAsState()
    val petId by viewModel.petId.collectAsState()
    val note by viewModel.note.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.requested.collect { onBooked(it) }
    }

    Scaffold(
        topBar = {
            ComunidappTopBar(
                title = "Solicitar turno",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        when (val s = state) {
            VeterinaryBookAppointmentUiState.Loading ->
                LoadingState(contentModifier = Modifier.padding(padding))
            is VeterinaryBookAppointmentUiState.Error ->
                ErrorState(message = s.message, contentModifier = Modifier.padding(padding))
            is VeterinaryBookAppointmentUiState.Content -> {
                Column(
                    Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Mascota", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = petId,
                        onValueChange = viewModel::setPetId,
                        label = { Text("ID de mascota autorizada") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))

                    Text("Servicio", fontWeight = FontWeight.SemiBold)
                    if (s.services.isEmpty()) {
                        Text("La clínica no tiene servicios activos.", style = MaterialTheme.typography.bodySmall)
                    }
                    s.services.forEach { svc ->
                        SelectableRow(
                            label = "${svc.name} (${svc.category.name})",
                            selected = svc.id == selectedService,
                            onClick = { viewModel.setService(svc.id) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    Text("Profesional (opcional)", fontWeight = FontWeight.SemiBold)
                    SelectableRow(
                        label = "Cualquiera disponible",
                        selected = selectedProfessional == null,
                        onClick = { viewModel.setProfessional(null) }
                    )
                    s.professionals.forEach { pro ->
                        SelectableRow(
                            label = pro.displayName,
                            selected = pro.id == selectedProfessional,
                            onClick = { viewModel.setProfessional(pro.id) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    Text("Fecha", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedButton(onClick = { viewModel.shiftDate(-1) }) { Text("−1 día") }
                        Text(
                            date.toString(),
                            modifier = Modifier.padding(horizontal = 12.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedButton(onClick = { viewModel.shiftDate(1) }) { Text("+1 día") }
                    }
                    Spacer(Modifier.height(8.dp))

                    Text("Horarios disponibles", fontWeight = FontWeight.SemiBold)
                    when {
                        selectedService.isNullOrBlank() ->
                            Text("Elegí un servicio para ver horarios.", style = MaterialTheme.typography.bodySmall)
                        slots.isEmpty() ->
                            Text("No hay horarios para esa fecha.", style = MaterialTheme.typography.bodySmall)
                        else -> slots.forEach { slot ->
                            SelectableRow(
                                label = "${formatTime(slot.startsAt)}–${formatTime(slot.endsAt)} · " +
                                    "${slot.available} cupo(s)",
                                selected = slot.startsAt == selectedSlot,
                                onClick = { viewModel.setSlot(slot.startsAt) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = note,
                        onValueChange = viewModel::setNote,
                        label = { Text("Nota privada (opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = viewModel::request,
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (submitting) "Enviando…" else "Solicitar turno") }
                }
            }
        }
    }
}

@Composable
private fun SelectableRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = (if (selected) "● " else "○ ") + label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    )
}

// --- Mis turnos ---

@Composable
fun MyVeterinaryAppointmentsScreen(
    onNavigateBack: () -> Unit,
    onAppointmentClick: (String) -> Unit,
    viewModel: MyVeterinaryAppointmentsViewModel = viewModel(
        factory = MyVeterinaryAppointmentsViewModel.factory()
    )
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Mis turnos", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (val s = state) {
                VeterinaryAppointmentsListUiState.Loading -> LoadingState()
                is VeterinaryAppointmentsListUiState.Error -> ErrorState(message = s.message)
                is VeterinaryAppointmentsListUiState.Content ->
                    if (s.items.isEmpty()) {
                        EmptyState(title = "Todavía no solicitaste turnos.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(s.items, key = { it.id }) { appt ->
                                AppointmentRow(appt) { onAppointmentClick(appt.id) }
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun AppointmentRow(appt: VeterinaryAppointment, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Text(formatDateTime(appt.startsAt), fontWeight = FontWeight.SemiBold)
        Text("Mascota: ${appt.petId}")
        Text("Estado: ${statusLabel(appt.status)}")
    }
}

// --- Detalle de turno (requester) ---

@Composable
fun VeterinaryAppointmentDetailScreen(
    appointmentId: String,
    onNavigateBack: () -> Unit,
    viewModel: VeterinaryAppointmentDetailViewModel = viewModel(
        factory = VeterinaryAppointmentDetailViewModel.factory(appointmentId)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val timeline by viewModel.timeline.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    var reason by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Detalle del turno", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val s = state) {
            VeterinaryAppointmentDetailUiState.Loading ->
                LoadingState(contentModifier = Modifier.padding(padding))
            is VeterinaryAppointmentDetailUiState.Error ->
                ErrorState(
                    message = s.message,
                    onRetry = viewModel::reload,
                    contentModifier = Modifier.padding(padding)
                )
            is VeterinaryAppointmentDetailUiState.Content -> {
                val appt = s.appointment
                val canCancel = appt.status == VeterinaryAppointmentStatus.REQUESTED ||
                    appt.status == VeterinaryAppointmentStatus.CONFIRMED
                Column(
                    Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    AppointmentSummary(appt)
                    // Motivos visibles al solicitante solo cuando existen.
                    appt.rejectionReason?.let { Text("Motivo de rechazo: $it") }
                    appt.cancellationReason?.let { Text("Motivo de cancelación: $it") }
                    Spacer(Modifier.height(12.dp))
                    NextStepSection(appt.status, isManager = false)
                    ReminderSection(reminders)
                    Spacer(Modifier.height(12.dp))
                    if (canCancel) {
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Motivo (opcional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { viewModel.cancel(reason) },
                            enabled = !submitting,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (submitting) "Cancelando…" else "Cancelar turno") }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    message?.let { Text(it) }
                    Spacer(Modifier.height(12.dp))
                    TimelineSection(timeline)
                }
            }
        }
    }
}

@Composable
private fun AppointmentSummary(appt: VeterinaryAppointment) {
    Text(formatDateTime(appt.startsAt), style = MaterialTheme.typography.headlineSmall)
    Text("Estado: ${statusLabel(appt.status)}")
    Text("Mascota: ${appt.petId}")
    Text("Servicio: ${appt.serviceId}")
    appt.professionalId?.let { Text("Profesional: $it") }
    appt.requestNote?.let { Text("Nota: $it") }
}

// --- Agenda gestionada (clínica) ---

@Composable
fun VeterinaryManagedAgendaScreen(
    clinicId: String,
    onNavigateBack: () -> Unit,
    onManageAppointment: (String) -> Unit,
    onSettings: () -> Unit,
    onRules: () -> Unit,
    viewModel: VeterinaryManagedAgendaViewModel = viewModel(
        factory = VeterinaryManagedAgendaViewModel.factory(clinicId)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Agenda de turnos", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Configuración de agenda")
            }
            OutlinedButton(onClick = onRules, modifier = Modifier.fillMaxWidth()) {
                Text("Reglas de disponibilidad")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            message?.let { Text(it) }
            Spacer(Modifier.height(8.dp))
            when (val s = state) {
                VeterinaryAppointmentsListUiState.Loading -> LoadingState()
                is VeterinaryAppointmentsListUiState.Error -> ErrorState(message = s.message)
                is VeterinaryAppointmentsListUiState.Content ->
                    if (s.items.isEmpty()) {
                        EmptyState(title = "Sin turnos para esta veterinaria.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(s.items, key = { it.id }) { appt ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onManageAppointment(appt.id) }
                                        .padding(8.dp)
                                ) {
                                    Text(formatDateTime(appt.startsAt), fontWeight = FontWeight.SemiBold)
                                    Text("Mascota: ${appt.petId} · ${statusLabel(appt.status)}")
                                    if (appt.status == VeterinaryAppointmentStatus.REQUESTED) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { viewModel.confirm(appt.id) },
                                                enabled = !submitting
                                            ) { Text("Confirmar") }
                                            OutlinedButton(
                                                onClick = { viewModel.reject(appt.id, "Rechazado por la clínica") },
                                                enabled = !submitting
                                            ) { Text("Rechazar") }
                                        }
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
            }
        }
    }
}

// --- Gestión de un turno (clínica) ---

@Composable
fun VeterinaryAppointmentManagementScreen(
    appointmentId: String,
    onNavigateBack: () -> Unit,
    viewModel: VeterinaryAppointmentManagementViewModel = viewModel(
        factory = VeterinaryAppointmentManagementViewModel.factory(appointmentId)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val timeline by viewModel.timeline.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    var reason by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Gestionar turno", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val s = state) {
            VeterinaryAppointmentDetailUiState.Loading ->
                LoadingState(contentModifier = Modifier.padding(padding))
            is VeterinaryAppointmentDetailUiState.Error ->
                ErrorState(
                    message = s.message,
                    onRetry = viewModel::reload,
                    contentModifier = Modifier.padding(padding)
                )
            is VeterinaryAppointmentDetailUiState.Content -> {
                val appt = s.appointment
                Column(
                    Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    AppointmentSummary(appt)
                    // Gestor autorizado: ve motivos operativos cuando existen.
                    appt.rejectionReason?.let { Text("Motivo de rechazo: $it") }
                    appt.cancellationReason?.let { Text("Motivo de cancelación: $it") }
                    Spacer(Modifier.height(12.dp))
                    NextStepSection(appt.status, isManager = true)
                    ReminderSection(reminders)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Motivo (rechazo/cancelación)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    when (appt.status) {
                        VeterinaryAppointmentStatus.REQUESTED -> Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = viewModel::confirm, enabled = !submitting) { Text("Confirmar") }
                            OutlinedButton(
                                onClick = { viewModel.reject(reason.ifBlank { "Rechazado" }) },
                                enabled = !submitting
                            ) { Text("Rechazar") }
                            OutlinedButton(
                                onClick = { viewModel.cancel(reason) },
                                enabled = !submitting
                            ) { Text("Cancelar") }
                        }
                        VeterinaryAppointmentStatus.CONFIRMED -> Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = viewModel::complete, enabled = !submitting) {
                                    Text("Completar")
                                }
                                OutlinedButton(onClick = viewModel::markNoShow, enabled = !submitting) {
                                    Text("No asistió")
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.cancel(reason) },
                                enabled = !submitting,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Cancelar") }
                        }
                        else -> Text("Este turno ya está finalizado.", style = MaterialTheme.typography.bodySmall)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    message?.let { Text(it) }
                    Spacer(Modifier.height(12.dp))
                    TimelineSection(timeline)
                }
            }
        }
    }
}

// --- Configuración de agenda ---

@Composable
fun VeterinaryScheduleSettingsScreen(
    clinicId: String,
    onNavigateBack: () -> Unit,
    viewModel: VeterinaryScheduleSettingsViewModel = viewModel(
        factory = VeterinaryScheduleSettingsViewModel.factory(clinicId)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()

    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Configuración de agenda", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        when (val s = state) {
            VeterinaryScheduleSettingsUiState.Loading ->
                LoadingState(contentModifier = Modifier.padding(padding))
            is VeterinaryScheduleSettingsUiState.Error ->
                ErrorState(
                    message = s.message,
                    onRetry = viewModel::reload,
                    contentModifier = Modifier.padding(padding)
                )
            is VeterinaryScheduleSettingsUiState.Content -> {
                var timezone by remember { mutableStateOf(s.settings.timezoneName) }
                var horizon by remember { mutableStateOf(s.settings.bookingHorizonDays.toString()) }
                var minNotice by remember { mutableStateOf(s.settings.minimumNoticeMinutes.toString()) }
                var cancelNotice by remember { mutableStateOf(s.settings.cancellationNoticeMinutes.toString()) }
                var slotDuration by remember { mutableStateOf(s.settings.defaultSlotDurationMinutes.toString()) }
                Column(
                    Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = timezone,
                        onValueChange = { timezone = it },
                        label = { Text("Zona horaria (IANA)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = horizon,
                        onValueChange = { horizon = it },
                        label = { Text("Horizonte de reserva (días)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = minNotice,
                        onValueChange = { minNotice = it },
                        label = { Text("Aviso mínimo (minutos)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = cancelNotice,
                        onValueChange = { cancelNotice = it },
                        label = { Text("Plazo de cancelación (minutos)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = slotDuration,
                        onValueChange = { slotDuration = it },
                        label = { Text("Duración por turno (minutos)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    message?.let { Text(it) }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.save(
                                s.settings.copy(
                                    timezoneName = timezone.trim(),
                                    bookingHorizonDays = horizon.toIntOrNull() ?: s.settings.bookingHorizonDays,
                                    minimumNoticeMinutes = minNotice.toIntOrNull() ?: s.settings.minimumNoticeMinutes,
                                    cancellationNoticeMinutes = cancelNotice.toIntOrNull()
                                        ?: s.settings.cancellationNoticeMinutes,
                                    defaultSlotDurationMinutes = slotDuration.toIntOrNull()
                                        ?: s.settings.defaultSlotDurationMinutes
                                )
                            )
                        },
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (submitting) "Guardando…" else "Guardar configuración") }
                }
            }
        }
    }
}

// --- Reglas de disponibilidad ---

@Composable
fun VeterinaryAvailabilityRulesScreen(
    clinicId: String,
    onNavigateBack: () -> Unit,
    viewModel: VeterinaryAvailabilityRulesViewModel = viewModel(
        factory = VeterinaryAvailabilityRulesViewModel.factory(clinicId)
    )
) {
    val state by viewModel.uiState.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()

    var day by remember { mutableStateOf(DayOfWeek.MONDAY) }
    var start by remember { mutableStateOf("09:00") }
    var end by remember { mutableStateOf("13:00") }
    var duration by remember { mutableStateOf("30") }
    var capacity by remember { mutableStateOf("1") }
    var serviceId by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            ComunidappTopBar(title = "Disponibilidad", showBackButton = true, onBackClick = onNavigateBack)
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text("Nueva regla recurrente", fontWeight = FontWeight.SemiBold)
            OutlinedButton(
                onClick = { day = day.plus(1L) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Día: ${day.name}") }
            OutlinedTextField(
                value = start,
                onValueChange = { start = it },
                label = { Text("Desde (HH:mm)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = end,
                onValueChange = { end = it },
                label = { Text("Hasta (HH:mm)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = duration,
                onValueChange = { duration = it },
                label = { Text("Duración (minutos)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = capacity,
                onValueChange = { capacity = it },
                label = { Text("Cupo por turno") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = serviceId,
                onValueChange = { serviceId = it },
                label = { Text("ID de servicio (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val startTime = runCatching { LocalTime.parse(start.trim()) }.getOrNull()
                    val endTime = runCatching { LocalTime.parse(end.trim()) }.getOrNull()
                    if (startTime == null || endTime == null) return@Button
                    viewModel.createRule(
                        CreateVeterinaryAvailabilityRuleInput(
                            clinicId = clinicId,
                            dayOfWeek = day,
                            startsAt = startTime,
                            endsAt = endTime,
                            slotDurationMinutes = duration.toIntOrNull() ?: 30,
                            capacityPerSlot = capacity.toIntOrNull() ?: 1,
                            serviceId = serviceId.trim().takeIf { it.isNotEmpty() }
                        )
                    )
                },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (submitting) "Guardando…" else "Crear regla") }
            Spacer(Modifier.height(16.dp))

            Text("Reglas actuales", fontWeight = FontWeight.SemiBold)
            when (val s = state) {
                VeterinaryAvailabilityRulesUiState.Loading -> LoadingState()
                is VeterinaryAvailabilityRulesUiState.Error -> ErrorState(message = s.message)
                is VeterinaryAvailabilityRulesUiState.Content -> {
                    if (s.data.rules.isEmpty()) {
                        Text("Sin reglas configuradas.", style = MaterialTheme.typography.bodySmall)
                    }
                    s.data.rules.forEach { rule ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                "${rule.dayOfWeek.name} ${rule.startsAt}–${rule.endsAt} · " +
                                    "${rule.slotDurationMinutes}min · cupo ${rule.capacityPerSlot}" +
                                    (if (rule.active) "" else " (inactiva)"),
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedButton(
                                onClick = { viewModel.toggleRule(rule.id, !rule.active) },
                                enabled = !submitting
                            ) { Text(if (rule.active) "Desactivar" else "Activar") }
                        }
                        HorizontalDivider()
                    }
                    if (s.data.exceptions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Excepciones", fontWeight = FontWeight.SemiBold)
                        s.data.exceptions.forEach { ex ->
                            Text(
                                "${ex.exceptionDate} · ${ex.type.name}" +
                                    (if (ex.active) "" else " (inactiva)"),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
