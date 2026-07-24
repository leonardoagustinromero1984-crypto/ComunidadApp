package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.VeterinaryAppointment
import com.comunidapp.app.data.model.VeterinaryAppointmentSlot
import com.comunidapp.app.data.model.VeterinaryAppointmentStatus
import com.comunidapp.app.data.model.VeterinaryAppointmentStatusHistory
import com.comunidapp.app.data.model.VeterinaryAppointmentTimelineStep
import com.comunidapp.app.data.model.VeterinaryProfessional
import com.comunidapp.app.data.model.VeterinaryReminderSchedule
import com.comunidapp.app.data.model.VeterinaryScheduleSettings
import com.comunidapp.app.data.model.VeterinaryService
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m12.M12VeterinaryErrorMapper
import com.comunidapp.app.data.repository.CreateVeterinaryAvailabilityRuleInput
import com.comunidapp.app.data.repository.ManagedVeterinaryAvailability
import com.comunidapp.app.data.repository.RequestVeterinaryAppointmentInput
import com.comunidapp.app.data.repository.VeterinaryAppointmentRepository
import com.comunidapp.app.data.repository.VeterinaryClinicRepository
import com.comunidapp.app.data.repository.VeterinaryRetryAction
import com.comunidapp.app.data.repository.VeterinaryScheduleRepository
import com.comunidapp.app.data.repository.VeterinaryServiceRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private fun errorMessageOf(t: Throwable): String =
    M12VeterinaryErrorMapper.userMessage(M12VeterinaryErrorMapper.codeOf(t))

private fun errorCodeOf(t: Throwable): String = M12VeterinaryErrorMapper.codeOf(t)

// --- Reserva de turno ---

sealed class VeterinaryBookAppointmentUiState {
    data object Loading : VeterinaryBookAppointmentUiState()
    data class Content(
        val services: List<VeterinaryService>,
        val professionals: List<VeterinaryProfessional>
    ) : VeterinaryBookAppointmentUiState()
    data class Error(val message: String, val code: String) : VeterinaryBookAppointmentUiState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class VeterinaryBookAppointmentViewModel(
    private val clinicId: String,
    private val schedule: VeterinaryScheduleRepository = DataProvider.veterinaryScheduleRepository,
    private val appointments: VeterinaryAppointmentRepository = DataProvider.veterinaryAppointmentRepository,
    private val serviceRepo: VeterinaryServiceRepository = DataProvider.veterinaryServiceRepository,
    private val clinicRepo: VeterinaryClinicRepository = DataProvider.veterinaryClinicRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<VeterinaryBookAppointmentUiState>(VeterinaryBookAppointmentUiState.Loading)
    val uiState: StateFlow<VeterinaryBookAppointmentUiState> = _ui.asStateFlow()

    private val _selectedServiceId = MutableStateFlow<String?>(null)
    val selectedServiceId: StateFlow<String?> = _selectedServiceId.asStateFlow()
    private val _selectedProfessionalId = MutableStateFlow<String?>(null)
    val selectedProfessionalId: StateFlow<String?> = _selectedProfessionalId.asStateFlow()
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()
    private val _selectedSlot = MutableStateFlow<Instant?>(null)
    val selectedSlot: StateFlow<Instant?> = _selectedSlot.asStateFlow()
    private val _petId = MutableStateFlow("")
    val petId: StateFlow<String> = _petId.asStateFlow()
    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    val slots: StateFlow<List<VeterinaryAppointmentSlot>> =
        combine(_selectedServiceId, _selectedProfessionalId, _selectedDate) { s, p, d ->
            Triple(s, p, d)
        }.flatMapLatest { (serviceId, professionalId, date) ->
            if (serviceId.isNullOrBlank()) flowOf(emptyList())
            else schedule.observeAvailableSlots(clinicId, serviceId, date, professionalId)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _requested = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val requested = _requested.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                serviceRepo.observeClinicServices(clinicId),
                clinicRepo.observeClinicProfessionals(clinicId)
            ) { services, professionals -> services to professionals }
                .collect { (services, professionals) ->
                    _ui.value = VeterinaryBookAppointmentUiState.Content(
                        services = services.filter { it.active },
                        professionals = professionals
                    )
                }
        }
    }

    fun setService(serviceId: String?) {
        _selectedServiceId.value = serviceId
        _selectedSlot.value = null
    }

    fun setProfessional(professionalId: String?) {
        _selectedProfessionalId.value = professionalId
        _selectedSlot.value = null
    }

    fun setDate(date: LocalDate) {
        _selectedDate.value = date
        _selectedSlot.value = null
    }

    fun shiftDate(days: Long) = setDate(_selectedDate.value.plusDays(days))
    fun setSlot(startsAt: Instant?) { _selectedSlot.value = startsAt }
    fun setPetId(value: String) { _petId.value = value }
    fun setNote(value: String) { _note.value = value }

    fun request() {
        if (_submitting.value) return
        val serviceId = _selectedServiceId.value
        val slotInstant = _selectedSlot.value
        val pet = _petId.value.trim()
        if (serviceId.isNullOrBlank() || slotInstant == null || pet.isEmpty()) {
            _error.value = "Elegí servicio, horario y mascota."
            return
        }
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            appointments.requestAppointment(
                RequestVeterinaryAppointmentInput(
                    clinicId = clinicId,
                    serviceId = serviceId,
                    petId = pet,
                    startsAt = slotInstant,
                    professionalId = _selectedProfessionalId.value,
                    requestNote = _note.value.takeIf { it.isNotBlank() }
                )
            ).onSuccess { _requested.tryEmit(it.id) }
                .onFailure { _error.value = errorMessageOf(it) }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(clinicId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                VeterinaryBookAppointmentViewModel(clinicId) as T
        }
    }
}

// --- Mis turnos / agenda gestionada (listado) ---

sealed class VeterinaryAppointmentsListUiState {
    data object Loading : VeterinaryAppointmentsListUiState()
    data class Content(val items: List<VeterinaryAppointment>) : VeterinaryAppointmentsListUiState()
    data class Error(val message: String, val code: String) : VeterinaryAppointmentsListUiState()
}

class MyVeterinaryAppointmentsViewModel(
    private val repo: VeterinaryAppointmentRepository = DataProvider.veterinaryAppointmentRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<VeterinaryAppointmentsListUiState>(VeterinaryAppointmentsListUiState.Loading)
    val uiState: StateFlow<VeterinaryAppointmentsListUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeMyAppointments().collect { list ->
                _ui.value = VeterinaryAppointmentsListUiState.Content(list)
            }
        }
    }

    companion object {
        fun factory() = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MyVeterinaryAppointmentsViewModel() as T
        }
    }
}

class VeterinaryManagedAgendaViewModel(
    private val clinicId: String,
    private val repo: VeterinaryAppointmentRepository = DataProvider.veterinaryAppointmentRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<VeterinaryAppointmentsListUiState>(VeterinaryAppointmentsListUiState.Loading)
    val uiState: StateFlow<VeterinaryAppointmentsListUiState> = _ui.asStateFlow()
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeManagedAppointments(clinicId).collect { list ->
                _ui.value = VeterinaryAppointmentsListUiState.Content(list)
            }
        }
    }

    fun confirm(appointmentId: String) = runAction { repo.confirmAppointment(appointmentId) }
    fun reject(appointmentId: String, reason: String) =
        runAction { repo.rejectAppointment(appointmentId, reason) }
    fun cancel(appointmentId: String, reason: String?) =
        runAction { repo.cancelManagedAppointment(appointmentId, reason) }

    private fun runAction(block: suspend () -> Result<VeterinaryAppointment>) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            _message.value = null
            block()
                .onSuccess { _message.value = "Turno actualizado (${it.status.name})" }
                .onFailure { _error.value = errorMessageOf(it) }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(clinicId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                VeterinaryManagedAgendaViewModel(clinicId) as T
        }
    }
}

// --- Detalle de turno (requester) y gestión (clínica) ---

sealed class VeterinaryAppointmentDetailUiState {
    data object Loading : VeterinaryAppointmentDetailUiState()
    data class Content(val appointment: VeterinaryAppointment) : VeterinaryAppointmentDetailUiState()
    data class Error(val message: String, val code: String) : VeterinaryAppointmentDetailUiState()
}

class VeterinaryAppointmentDetailViewModel(
    private val appointmentId: String,
    private val repo: VeterinaryAppointmentRepository = DataProvider.veterinaryAppointmentRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<VeterinaryAppointmentDetailUiState>(VeterinaryAppointmentDetailUiState.Loading)
    val uiState: StateFlow<VeterinaryAppointmentDetailUiState> = _ui.asStateFlow()
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val history: StateFlow<List<VeterinaryAppointmentStatusHistory>> =
        repo.observeAppointmentHistory(appointmentId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Línea de tiempo redactada para el solicitante (no ve notas operativas de clínica). */
    val timeline: StateFlow<List<VeterinaryAppointmentTimelineStep>> =
        repo.observeAppointmentHistory(appointmentId)
            .map { repo.buildTimeline(appointmentId, isManager = false).getOrDefault(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Recordatorios preparados (sin push real). null si aún no hay programación. */
    val reminders: StateFlow<VeterinaryReminderSchedule?> =
        repo.observeReminderSchedule(appointmentId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _ui.value = VeterinaryAppointmentDetailUiState.Loading
            repo.getAppointment(appointmentId)
                .onSuccess { _ui.value = VeterinaryAppointmentDetailUiState.Content(it) }
                .onFailure {
                    _ui.value = VeterinaryAppointmentDetailUiState.Error(errorMessageOf(it), errorCodeOf(it))
                }
        }
    }

    /** Cancelación segura ante reintentos: si el turno cambió de estado, mapea RETRY_CONFLICT. */
    fun cancel(reason: String?) {
        if (_submitting.value) return
        val current = (_ui.value as? VeterinaryAppointmentDetailUiState.Content)?.appointment
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            _message.value = null
            val result = if (current != null) {
                repo.retrySafeTransition(appointmentId, current.status, VeterinaryRetryAction.CANCEL_MY)
            } else {
                repo.cancelMyAppointment(appointmentId, reason?.takeIf { it.isNotBlank() })
            }
            result
                .onSuccess {
                    _message.value = "Turno cancelado"
                    _ui.value = VeterinaryAppointmentDetailUiState.Content(it)
                }
                .onFailure { _error.value = errorMessageOf(it) }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(appointmentId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                VeterinaryAppointmentDetailViewModel(appointmentId) as T
        }
    }
}

class VeterinaryAppointmentManagementViewModel(
    private val appointmentId: String,
    private val repo: VeterinaryAppointmentRepository = DataProvider.veterinaryAppointmentRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<VeterinaryAppointmentDetailUiState>(VeterinaryAppointmentDetailUiState.Loading)
    val uiState: StateFlow<VeterinaryAppointmentDetailUiState> = _ui.asStateFlow()
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val history: StateFlow<List<VeterinaryAppointmentStatusHistory>> =
        repo.observeAppointmentHistory(appointmentId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Línea de tiempo completa para el gestor (incluye motivos operativos). */
    val timeline: StateFlow<List<VeterinaryAppointmentTimelineStep>> =
        repo.observeAppointmentHistory(appointmentId)
            .map { repo.buildTimeline(appointmentId, isManager = true).getOrDefault(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Recordatorios preparados (sin push real). */
    val reminders: StateFlow<VeterinaryReminderSchedule?> =
        repo.observeReminderSchedule(appointmentId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _ui.value = VeterinaryAppointmentDetailUiState.Loading
            repo.getAppointment(appointmentId)
                .onSuccess { _ui.value = VeterinaryAppointmentDetailUiState.Content(it) }
                .onFailure {
                    _ui.value = VeterinaryAppointmentDetailUiState.Error(errorMessageOf(it), errorCodeOf(it))
                }
        }
    }

    private fun currentStatus(): VeterinaryAppointmentStatus? =
        (_ui.value as? VeterinaryAppointmentDetailUiState.Content)?.appointment?.status

    // Confirmar usa retrySafeTransition: si otro gestor confirmó primero (confirmación
    // simultánea), el repo devuelve RETRY_CONFLICT en lugar de duplicar la transición.
    fun confirm() {
        val from = currentStatus() ?: return
        runAction { repo.retrySafeTransition(appointmentId, from, VeterinaryRetryAction.CONFIRM) }
    }

    fun reject(reason: String) = runAction { repo.rejectAppointment(appointmentId, reason) }
    fun cancel(reason: String?) =
        runAction { repo.cancelManagedAppointment(appointmentId, reason?.takeIf { it.isNotBlank() }) }
    fun complete() = runAction { repo.completeAppointment(appointmentId) }
    fun markNoShow() = runAction { repo.markNoShow(appointmentId) }

    private fun runAction(block: suspend () -> Result<VeterinaryAppointment>) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            _message.value = null
            block()
                .onSuccess {
                    _message.value = "Turno actualizado (${it.status.name})"
                    _ui.value = VeterinaryAppointmentDetailUiState.Content(it)
                }
                .onFailure { _error.value = errorMessageOf(it) }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(appointmentId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                VeterinaryAppointmentManagementViewModel(appointmentId) as T
        }
    }
}

// --- Configuración de agenda ---

sealed class VeterinaryScheduleSettingsUiState {
    data object Loading : VeterinaryScheduleSettingsUiState()
    data class Content(val settings: VeterinaryScheduleSettings) : VeterinaryScheduleSettingsUiState()
    data class Error(val message: String, val code: String) : VeterinaryScheduleSettingsUiState()
}

class VeterinaryScheduleSettingsViewModel(
    private val clinicId: String,
    private val repo: VeterinaryScheduleRepository = DataProvider.veterinaryScheduleRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<VeterinaryScheduleSettingsUiState>(VeterinaryScheduleSettingsUiState.Loading)
    val uiState: StateFlow<VeterinaryScheduleSettingsUiState> = _ui.asStateFlow()
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _ui.value = VeterinaryScheduleSettingsUiState.Loading
            repo.getSettings(clinicId)
                .onSuccess { _ui.value = VeterinaryScheduleSettingsUiState.Content(it) }
                .onFailure {
                    _ui.value = VeterinaryScheduleSettingsUiState.Error(errorMessageOf(it), errorCodeOf(it))
                }
        }
    }

    fun save(settings: VeterinaryScheduleSettings) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            _message.value = null
            repo.saveSettings(settings.copy(clinicId = clinicId))
                .onSuccess {
                    _message.value = "Configuración guardada"
                    _ui.value = VeterinaryScheduleSettingsUiState.Content(it)
                }
                .onFailure { _error.value = errorMessageOf(it) }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(clinicId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                VeterinaryScheduleSettingsViewModel(clinicId) as T
        }
    }
}

// --- Reglas de disponibilidad ---

sealed class VeterinaryAvailabilityRulesUiState {
    data object Loading : VeterinaryAvailabilityRulesUiState()
    data class Content(val data: ManagedVeterinaryAvailability) : VeterinaryAvailabilityRulesUiState()
    data class Error(val message: String, val code: String) : VeterinaryAvailabilityRulesUiState()
}

class VeterinaryAvailabilityRulesViewModel(
    private val clinicId: String,
    private val repo: VeterinaryScheduleRepository = DataProvider.veterinaryScheduleRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<VeterinaryAvailabilityRulesUiState>(VeterinaryAvailabilityRulesUiState.Loading)
    val uiState: StateFlow<VeterinaryAvailabilityRulesUiState> = _ui.asStateFlow()
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeManagedAvailability(clinicId).collect { data ->
                _ui.value = VeterinaryAvailabilityRulesUiState.Content(data)
            }
        }
    }

    fun createRule(input: CreateVeterinaryAvailabilityRuleInput) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            repo.createRule(input.copy(clinicId = clinicId))
                .onFailure { _error.value = errorMessageOf(it) }
            _submitting.value = false
        }
    }

    fun toggleRule(ruleId: String, active: Boolean) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            repo.changeRuleStatus(ruleId, active)
                .onFailure { _error.value = errorMessageOf(it) }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(clinicId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                VeterinaryAvailabilityRulesViewModel(clinicId) as T
        }
    }
}
