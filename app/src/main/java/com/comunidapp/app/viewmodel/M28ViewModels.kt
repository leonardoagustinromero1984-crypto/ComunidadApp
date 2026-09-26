package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.M28CreateCareDraftInput
import com.comunidapp.app.data.model.M28GrantProfessionalAccessInput
import com.comunidapp.app.data.model.M28GrantPurpose
import com.comunidapp.app.data.model.M28PassportUpdateProposal
import com.comunidapp.app.data.model.M28ProfessionalAccessGrant
import com.comunidapp.app.data.model.M28ProposalDecision
import com.comunidapp.app.data.model.M28UpdateCareDraftInput
import com.comunidapp.app.data.model.VitacoraAccessTarget
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.M28Repository
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.put
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class M28GrantsUiState {
    data object Loading : M28GrantsUiState()
    data class Content(
        val grants: List<M28ProfessionalAccessGrant>,
        val searchQuery: String = "",
        val searchResults: List<VitacoraAccessTarget> = emptyList(),
        val searchInProgress: Boolean = false,
        val selectedTarget: VitacoraAccessTarget? = null,
        val actionMessage: String? = null
    ) : M28GrantsUiState()
    data class Error(val message: String) : M28GrantsUiState()
}

class M28PetGrantsViewModel(
    private val petId: String,
    private val repository: M28Repository = DataProvider.m28Repository
) : ViewModel() {
    private val _ui = MutableStateFlow<M28GrantsUiState>(M28GrantsUiState.Loading)
    val uiState: StateFlow<M28GrantsUiState> = _ui.asStateFlow()
    private var searchJob: Job? = null

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val previous = (_ui.value as? M28GrantsUiState.Content)
            _ui.value = M28GrantsUiState.Loading
            repository.listGrantsForResponsible(petId)
                .onSuccess { grants ->
                    _ui.value = M28GrantsUiState.Content(
                        grants = grants,
                        searchQuery = previous?.searchQuery.orEmpty(),
                        searchResults = previous?.searchResults.orEmpty()
                    )
                    if (previous?.searchResults.isNullOrEmpty()) {
                        updateSearchQuery(previous?.searchQuery.orEmpty())
                    }
                }
                .onFailure {
                    _ui.value = M28GrantsUiState.Error(
                        com.comunidapp.app.domain.m28.M28UserErrorMapper.message(it)
                    )
                }
        }
    }

    fun updateSearchQuery(query: String) {
        val current = _ui.value as? M28GrantsUiState.Content ?: return
        _ui.value = current.copy(searchQuery = query, actionMessage = null)
        searchJob?.cancel()
        if (query.trim().isNotEmpty() && query.trim().length < 2) {
            _ui.value = current.copy(searchQuery = query, searchResults = emptyList(), searchInProgress = false)
            return
        }
        searchJob = viewModelScope.launch {
            if (query.trim().isNotEmpty()) delay(350)
            _ui.value = (_ui.value as? M28GrantsUiState.Content)?.copy(searchInProgress = true) ?: return@launch
            repository.searchAccessTargets(query.trim())
                .onSuccess { results ->
                    val state = _ui.value as? M28GrantsUiState.Content ?: return@onSuccess
                    _ui.value = state.copy(searchResults = results, searchInProgress = false)
                }
                .onFailure {
                    val state = _ui.value as? M28GrantsUiState.Content ?: return@onFailure
                    _ui.value = state.copy(
                        searchResults = emptyList(),
                        searchInProgress = false,
                        actionMessage = com.comunidapp.app.domain.m28.M28UserErrorMapper.message(it)
                    )
                }
        }
    }

    fun selectTarget(target: VitacoraAccessTarget?) {
        val current = _ui.value as? M28GrantsUiState.Content ?: return
        _ui.value = current.copy(selectedTarget = target, actionMessage = null)
    }

    fun grantSelectedTarget(purposes: List<M28GrantPurpose>) {
        val current = _ui.value as? M28GrantsUiState.Content ?: return
        val target = current.selectedTarget ?: return
        if (purposes.isEmpty()) {
            _ui.value = current.copy(actionMessage = "Elegí al menos un permiso")
            return
        }
        viewModelScope.launch {
            val input = M28GrantProfessionalAccessInput(
                petId = petId,
                clinicId = target.targetId.takeIf { target.targetKind == "ORGANIZATION" },
                professionalId = target.targetId.takeIf { target.targetKind == "PERSON" },
                purposes = purposes
            )
            repository.grantAccess(input)
                .onSuccess {
                    selectTarget(null)
                    refresh()
                }
                .onFailure {
                    _ui.value = current.copy(
                        actionMessage = com.comunidapp.app.domain.m28.M28UserErrorMapper.message(it)
                    )
                }
        }
    }

    fun grantClinicAccess(clinicId: String) {
        viewModelScope.launch {
            val input = M28GrantProfessionalAccessInput(
                petId = petId,
                clinicId = clinicId,
                professionalId = null,
                purposes = listOf(
                    M28GrantPurpose.CURRENT_CARE,
                    M28GrantPurpose.HISTORICAL_READ,
                    M28GrantPurpose.DOCUMENTS,
                    M28GrantPurpose.PASSPORT_PROPOSAL
                )
            )
            repository.grantAccess(input).onSuccess { refresh() }
                .onFailure {
                    _ui.value = M28GrantsUiState.Error(
                        com.comunidapp.app.domain.m28.M28UserErrorMapper.message(it)
                    )
                }
        }
    }

    fun revoke(grantId: String) {
        viewModelScope.launch {
            repository.revokeAccess(grantId).onSuccess { refresh() }
                .onFailure {
                    _ui.value = M28GrantsUiState.Error(
                        com.comunidapp.app.domain.m28.M28UserErrorMapper.message(it)
                    )
                }
        }
    }

    companion object {
        fun factory(petId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M28PetGrantsViewModel(petId) as T
        }
    }
}

sealed class M28ProposalsUiState {
    data object Loading : M28ProposalsUiState()
    data class Content(val proposals: List<M28PassportUpdateProposal>) : M28ProposalsUiState()
    data class Error(val message: String) : M28ProposalsUiState()
}

class M28PassportProposalsViewModel(
    private val petId: String,
    private val repository: M28Repository = DataProvider.m28Repository
) : ViewModel() {
    private val _ui = MutableStateFlow<M28ProposalsUiState>(M28ProposalsUiState.Loading)
    val uiState: StateFlow<M28ProposalsUiState> = _ui.asStateFlow()
    private val actorId = AuthProvider.repository.getCurrentUser()?.id.orEmpty()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = M28ProposalsUiState.Loading
            repository.listProposalsForResponsible(petId, actorId)
                .onSuccess { _ui.value = M28ProposalsUiState.Content(it) }
                .onFailure {
                    _ui.value = M28ProposalsUiState.Error(
                        com.comunidapp.app.domain.m28.M28UserErrorMapper.message(it)
                    )
                }
        }
    }

    fun decide(proposalId: String, decision: M28ProposalDecision, note: String?) {
        viewModelScope.launch {
            repository.decideProposal(proposalId, decision, note, actorId)
                .onSuccess { refresh() }
                .onFailure {
                    _ui.value = M28ProposalsUiState.Error(
                        com.comunidapp.app.domain.m28.M28UserErrorMapper.message(it)
                    )
                }
        }
    }

    companion object {
        fun factory(petId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                M28PassportProposalsViewModel(petId) as T
        }
    }
}

class M28ClinicCareViewModel(
    private val clinicId: String,
    private val petId: String,
    private val appointmentId: String?,
    private val repository: M28Repository = DataProvider.m28Repository
) : ViewModel() {
    private val actorId = AuthProvider.repository.getCurrentUser()?.id.orEmpty()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun createAndFinalize(reason: String, weight: Double?) {
        viewModelScope.launch {
            val draft = repository.createCareDraft(
                M28CreateCareDraftInput(
                    clinicId = clinicId,
                    petId = petId,
                    appointmentId = appointmentId,
                    clientRequestId = "care_${appointmentId ?: petId}_${System.currentTimeMillis()}"
                ),
                actorId
            ).getOrElse {
                _message.value = com.comunidapp.app.domain.m28.M28UserErrorMapper.message(it)
                return@launch
            }
            val updated = repository.updateCareDraft(
                M28UpdateCareDraftInput(
                    careId = draft.id,
                    reason = reason,
                    weightKg = weight
                ),
                actorId
            ).getOrElse {
                _message.value = com.comunidapp.app.domain.m28.M28UserErrorMapper.message(it)
                return@launch
            }
            repository.finalizeCare(updated.id, actorId)
                .onSuccess { _message.value = "Atención finalizada" }
                .onFailure { _message.value = com.comunidapp.app.domain.m28.M28UserErrorMapper.message(it) }
        }
    }

    fun proposeToVitacora(reason: String, notes: String) {
        viewModelScope.launch {
            runCatching {
                com.comunidapp.app.data.remote.supabase.supabase.postgrest.rpc(
                    function = com.comunidapp.app.domain.canonical.CanonicalBackend.RPC_CREATE_PROPOSAL,
                    parameters = kotlinx.serialization.json.buildJsonObject {
                        put("p_pet_id", petId)
                        put("p_origin", "PROFESSIONAL")
                        put("p_payload", "{\"reason\":\"$reason\",\"notes\":\"$notes\"}")
                    }
                )
            }.onSuccess { _message.value = "Propuesta enviada al dueño." }
                .onFailure { _message.value = "No se pudo proponer a VitaCora." }
        }
    }

    companion object {
        fun factory(clinicId: String, petId: String, appointmentId: String?) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    M28ClinicCareViewModel(clinicId, petId, appointmentId) as T
            }
    }
}
