package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.OrganizationInvitationRepository
import com.comunidapp.app.domain.context.OperationalContextProvider
import com.comunidapp.app.domain.onboarding.onb03.CanonicalTutorialEvent
import com.comunidapp.app.domain.onboarding.onb03.PendingTutorialQueue
import com.comunidapp.app.domain.onboarding.onb03.PendingTutorialRun
import com.comunidapp.app.domain.onboarding.onb03.TutorialQueueResolver
import com.comunidapp.app.domain.organization.OrganizationInvitation
import com.comunidapp.app.domain.organization.OrganizationInvitationStatus
import com.comunidapp.app.domain.organization.authorization.MembershipDisplay
import com.comunidapp.app.domain.organization.authorization.OrganizationRoleCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrgInvitationAcceptUiState(
    val loading: Boolean = true,
    val invitation: OrganizationInvitation? = null,
    val resolvedStatus: OrganizationInvitationStatus? = null,
    val error: String? = null,
    val busy: Boolean = false,
    val accepted: Boolean = false,
    val rejected: Boolean = false
)

class OrgInvitationAcceptViewModel(
    private val invitationId: String,
    private val invitationRepository: OrganizationInvitationRepository =
        DataProvider.organizationInvitationRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(OrgInvitationAcceptUiState())
    val ui: StateFlow<OrgInvitationAcceptUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            val pending = invitationRepository.listMyPending().getOrDefault(emptyList())
            val found = pending.firstOrNull { it.id == invitationId }
                ?: invitationRepository.getById(invitationId)
            _ui.update {
                it.copy(
                    loading = false,
                    invitation = found,
                    resolvedStatus = found?.status
                )
            }
        }
    }

    fun accept() {
        val invitation = _ui.value.invitation ?: return
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            val actor = AuthProvider.repository.getCurrentUser()?.id.orEmpty()
            invitationRepository.accept(
                invitation.id,
                actor,
                null,
                System.currentTimeMillis()
            ).onSuccess {
                OperationalContextProvider.refresh(actor)
                val admin = invitation.invitedRole == OrganizationRoleCode.ADMIN ||
                    invitation.invitedRole == OrganizationRoleCode.OWNER
                val queue = TutorialQueueResolver.queue(
                    event = CanonicalTutorialEvent.ORGANIZATION_INVITATION_ACCEPTED,
                    consumed = { false },
                    invitedAsAdmin = admin,
                    landingRouteHint = "manage_organization/${invitation.organizationId.value}"
                )
                if (queue.tutorials.isNotEmpty()) {
                    PendingTutorialQueue.set(
                        PendingTutorialRun(
                            tutorials = queue.tutorials,
                            landingRoute = queue.landingRouteHint
                        )
                    )
                }
                _ui.update {
                    it.copy(
                        busy = false,
                        accepted = true,
                        resolvedStatus = OrganizationInvitationStatus.ACCEPTED
                    )
                }
            }.onFailure { error ->
                refresh()
                _ui.update {
                    it.copy(busy = false, error = error.message ?: "No se pudo aceptar")
                }
            }
        }
    }

    fun reject() {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            invitationRepository.rejectMine(invitationId).onSuccess {
                _ui.update {
                    it.copy(
                        busy = false,
                        rejected = true,
                        resolvedStatus = OrganizationInvitationStatus.REJECTED
                    )
                }
            }.onFailure { error ->
                refresh()
                _ui.update {
                    it.copy(busy = false, error = error.message ?: "No se pudo rechazar")
                }
            }
        }
    }

    companion object {
        fun factory(invitationId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    OrgInvitationAcceptViewModel(invitationId) as T
            }
    }
}

fun OrganizationInvitation.headline(): String {
    val org = organizationName ?: "una organización"
    return "$org te invitó a formar parte de su equipo"
}

fun OrganizationInvitation.roleLabel(): String = MembershipDisplay.visibleRole(invitedRole)
