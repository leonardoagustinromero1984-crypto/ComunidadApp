package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.VeterinaryClinicProfile
import com.comunidapp.app.data.model.VeterinaryDirectoryFilter
import com.comunidapp.app.data.model.VeterinaryOpeningHours
import com.comunidapp.app.data.model.VeterinaryProfessional
import com.comunidapp.app.data.model.VeterinaryPublicListing
import com.comunidapp.app.data.model.VeterinaryService
import com.comunidapp.app.data.model.VeterinaryServiceCategory
import com.comunidapp.app.data.model.VeterinarySpecialty
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m12.M12VeterinaryErrorMapper
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.CreateVeterinaryClinicDraftInput
import com.comunidapp.app.data.repository.UpdateVeterinaryClinicDraftInput
import com.comunidapp.app.data.repository.VeterinaryClinicRepository
import com.comunidapp.app.data.repository.VeterinaryDirectoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class VeterinaryDirectoryUiState {
    data object Loading : VeterinaryDirectoryUiState()
    data object Empty : VeterinaryDirectoryUiState()
    data class Content(val items: List<VeterinaryPublicListing>) : VeterinaryDirectoryUiState()
    data class Error(val message: String, val code: String) : VeterinaryDirectoryUiState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class VeterinaryDirectoryViewModel(
    private val repo: VeterinaryDirectoryRepository = DataProvider.veterinaryDirectoryRepository
) : ViewModel() {
    private val _filter = MutableStateFlow(VeterinaryDirectoryFilter())
    val filter: StateFlow<VeterinaryDirectoryFilter> = _filter.asStateFlow()

    private val _ui = MutableStateFlow<VeterinaryDirectoryUiState>(VeterinaryDirectoryUiState.Loading)
    val uiState: StateFlow<VeterinaryDirectoryUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            _filter.flatMapLatest { f -> repo.observePublicClinics(f) }
                .collect { list ->
                    _ui.value = when {
                        list.isEmpty() -> VeterinaryDirectoryUiState.Empty
                        else -> VeterinaryDirectoryUiState.Content(list)
                    }
                }
        }
    }

    fun updateFilter(filter: VeterinaryDirectoryFilter) {
        _ui.value = VeterinaryDirectoryUiState.Loading
        _filter.value = filter
    }

    fun setQuery(query: String?) = updateFilter(_filter.value.copy(query = query))
    fun setZone(zone: String?) = updateFilter(_filter.value.copy(zoneText = zone))
    fun setSpecialty(specialty: VeterinarySpecialty?) =
        updateFilter(_filter.value.copy(specialty = specialty))
    fun setServiceCategory(category: VeterinaryServiceCategory?) =
        updateFilter(_filter.value.copy(serviceCategory = category))
    fun setEmergencyOnly(value: Boolean) =
        updateFilter(_filter.value.copy(emergencyCareOnly = value))
    fun setOpen24Only(value: Boolean) =
        updateFilter(_filter.value.copy(open24HoursOnly = value))
    fun setVerifiedOnly(value: Boolean) =
        updateFilter(_filter.value.copy(verifiedOnly = value))

    fun forceError(message: String, code: String = "VETERINARY_REPOSITORY_FAILURE") {
        _ui.value = VeterinaryDirectoryUiState.Error(message, code)
    }

    companion object {
        fun factory() = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                VeterinaryDirectoryViewModel() as T
        }
    }
}

data class VeterinaryClinicDetailData(
    val clinic: VeterinaryClinicProfile,
    val professionals: List<VeterinaryProfessional>,
    val services: List<VeterinaryService>,
    val openingHours: List<VeterinaryOpeningHours>
)

sealed class VeterinaryClinicDetailUiState {
    data object Loading : VeterinaryClinicDetailUiState()
    data class Content(val data: VeterinaryClinicDetailData) : VeterinaryClinicDetailUiState()
    data class Error(val message: String, val code: String) : VeterinaryClinicDetailUiState()
}

class VeterinaryClinicDetailViewModel(
    private val clinicId: String,
    private val directory: VeterinaryDirectoryRepository = DataProvider.veterinaryDirectoryRepository,
    private val clinics: VeterinaryClinicRepository = DataProvider.veterinaryClinicRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<VeterinaryClinicDetailUiState>(VeterinaryClinicDetailUiState.Loading)
    val uiState: StateFlow<VeterinaryClinicDetailUiState> = _ui.asStateFlow()

    val professionals: StateFlow<List<VeterinaryProfessional>> =
        clinics.observeClinicProfessionals(clinicId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val services: StateFlow<List<VeterinaryService>> =
        clinics.observeClinicServices(clinicId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val openingHours: StateFlow<List<VeterinaryOpeningHours>> =
        clinics.observeClinicOpeningHours(clinicId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { reload() }

    fun reload() {
        if (clinicId.isBlank()) {
            _ui.value = VeterinaryClinicDetailUiState.Error(
                M12VeterinaryErrorMapper.userMessage("VETERINARY_CLINIC_NOT_FOUND"),
                "VETERINARY_CLINIC_NOT_FOUND"
            )
            return
        }
        viewModelScope.launch {
            _ui.value = VeterinaryClinicDetailUiState.Loading
            directory.getPublicClinic(clinicId)
                .onSuccess { clinic ->
                    _ui.value = VeterinaryClinicDetailUiState.Content(
                        VeterinaryClinicDetailData(
                            clinic = clinic,
                            professionals = professionals.value,
                            services = services.value,
                            openingHours = openingHours.value
                        )
                    )
                }
                .onFailure {
                    val code = M12VeterinaryErrorMapper.codeOf(it)
                    _ui.value = VeterinaryClinicDetailUiState.Error(
                        M12VeterinaryErrorMapper.userMessage(code),
                        code
                    )
                }
        }
    }

    companion object {
        fun factory(clinicId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                VeterinaryClinicDetailViewModel(clinicId) as T
        }
    }
}

sealed class ManagedVeterinaryClinicsUiState {
    data object Loading : ManagedVeterinaryClinicsUiState()
    data object Empty : ManagedVeterinaryClinicsUiState()
    data class Content(val items: List<VeterinaryClinicProfile>) : ManagedVeterinaryClinicsUiState()
    data class Error(val message: String, val code: String) : ManagedVeterinaryClinicsUiState()
}

class ManagedVeterinaryClinicsViewModel(
    private val auth: AuthRepository = AuthProvider.repository,
    private val repo: VeterinaryClinicRepository = DataProvider.veterinaryClinicRepository
) : ViewModel() {
    private val _ui = MutableStateFlow<ManagedVeterinaryClinicsUiState>(ManagedVeterinaryClinicsUiState.Loading)
    val uiState: StateFlow<ManagedVeterinaryClinicsUiState> = _ui.asStateFlow()

    init {
        val uid = auth.getCurrentUser()?.id.orEmpty()
        if (uid.isBlank()) {
            _ui.value = ManagedVeterinaryClinicsUiState.Error(
                M12VeterinaryErrorMapper.userMessage("NOT_AUTHENTICATED"),
                "NOT_AUTHENTICATED"
            )
        } else {
            viewModelScope.launch {
                repo.observeManagedClinics().collect { list ->
                    _ui.value = when {
                        list.isEmpty() -> ManagedVeterinaryClinicsUiState.Empty
                        else -> ManagedVeterinaryClinicsUiState.Content(list)
                    }
                }
            }
        }
    }

    companion object {
        fun factory() = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ManagedVeterinaryClinicsViewModel() as T
        }
    }
}

class VeterinaryClinicDraftViewModel(
    private val clinicId: String? = null,
    private val repo: VeterinaryClinicRepository = DataProvider.veterinaryClinicRepository
) : ViewModel() {
    private val _submitting = MutableStateFlow(false)
    val submitting = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _errorCode = MutableStateFlow<String?>(null)
    val errorCode = _errorCode.asStateFlow()
    private val _saved = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()
    private val _existing = MutableStateFlow<VeterinaryClinicProfile?>(null)
    val existing = _existing.asStateFlow()
    private val _loading = MutableStateFlow(clinicId != null)
    val loading = _loading.asStateFlow()

    init {
        if (!clinicId.isNullOrBlank()) {
            viewModelScope.launch {
                _loading.value = true
                repo.getManagedClinic(clinicId)
                    .onSuccess { _existing.value = it }
                    .onFailure {
                        val code = M12VeterinaryErrorMapper.codeOf(it)
                        _errorCode.value = code
                        _error.value = M12VeterinaryErrorMapper.userMessage(code)
                    }
                _loading.value = false
            }
        }
    }

    fun create(input: CreateVeterinaryClinicDraftInput) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            _errorCode.value = null
            repo.createLocalDraft(input)
                .onSuccess { _saved.tryEmit(it.id) }
                .onFailure {
                    val code = M12VeterinaryErrorMapper.codeOf(it)
                    _errorCode.value = code
                    _error.value = M12VeterinaryErrorMapper.userMessage(code)
                }
            _submitting.value = false
        }
    }

    fun update(input: UpdateVeterinaryClinicDraftInput) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            _error.value = null
            _errorCode.value = null
            repo.updateLocalDraft(input)
                .onSuccess { _saved.tryEmit(it.id) }
                .onFailure {
                    val code = M12VeterinaryErrorMapper.codeOf(it)
                    _errorCode.value = code
                    _error.value = M12VeterinaryErrorMapper.userMessage(code)
                }
            _submitting.value = false
        }
    }

    companion object {
        fun factory(clinicId: String? = null) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                VeterinaryClinicDraftViewModel(clinicId) as T
        }
    }
}
