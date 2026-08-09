package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.M28CreatePassportProposalInput
import com.comunidapp.app.data.model.M28GrantProfessionalAccessInput
import com.comunidapp.app.data.model.M28GrantPurpose
import com.comunidapp.app.data.model.M28PassportUpdateProposal
import com.comunidapp.app.data.model.M28ProfessionalAccessGrant
import com.comunidapp.app.data.model.M28ProposalDecision
import com.comunidapp.app.data.model.M28ProposalStatus
import com.comunidapp.app.data.model.M28ProposalType
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.M28Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class M28GrantsUiState {
    data object Loading : M28GrantsUiState()
    data class Content(val grants: List<M28ProfessionalAccessGrant>) : M28GrantsUiState()
    data class Error(val message: String) : M28GrantsUiState()
}

class M28PetGrantsViewModel(
    private val petId: String,
    private val repository: M28Repository = DataProvider.m28Repository
) : ViewModel() {
    private val _ui = MutableStateFlow<M28GrantsUiState>(M28GrantsUiState.Loading)
    val uiState: StateFlow<M28GrantsUiState> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = M28GrantsUiState.Loading
            repository.listGrantsForResponsible(petId)
                .onSuccess { _ui.value = M28GrantsUiState.Content(it) }
                .onFailure { _ui.value = M28GrantsUiState.Error(it.message ?: "Error") }
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
                .onFailure { _ui.value = M28GrantsUiState.Error(it.message ?: "Error") }
        }
    }

    fun revoke(grantId: String) {
        viewModelScope.launch {
            repository.revokeAccess(grantId).onSuccess { refresh() }
                .onFailure { _ui.value = M28GrantsUiState.Error(it.message ?: "Error") }
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
                .onFailure { _ui.value = M28ProposalsUiState.Error(it.message ?: "Error") }
        }
    }

    fun decide(proposalId: String, decision: M28ProposalDecision, note: String?) {
        viewModelScope.launch {
            repository.decideProposal(proposalId, decision, note, actorId)
                .onSuccess { refresh() }
                .onFailure { _ui.value = M28ProposalsUiState.Error(it.message ?: "Error") }
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
                com.comunidapp.app.data.model.M28CreateCareDraftInput(
                    clinicId = clinicId,
                    petId = petId,
                    appointmentId = appointmentId,
                    clientRequestId = "care_${appointmentId ?: petId}_${System.currentTimeMillis()}"
                ),
                actorId
            ).getOrElse {
                _message.value = it.message
                return@launch
            }
            val updated = repository.updateCareDraft(
                com.comunidapp.app.data.model.M28UpdateCareDraftInput(
                    careId = draft.id,
                    reason = reason,
                    weightKg = weight
                ),
                actorId
            ).getOrElse {
                _message.value = it.message
                return@launch
            }
            repository.finalizeCare(updated.id, actorId)
                .onSuccess { _message.value = "Atención finalizada" }
                .onFailure { _message.value = it.message }
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
