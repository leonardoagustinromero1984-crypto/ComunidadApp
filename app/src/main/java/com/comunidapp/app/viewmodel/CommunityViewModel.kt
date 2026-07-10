package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.AdoptionEvent
import com.comunidapp.app.data.model.DonationCampaign
import com.comunidapp.app.data.model.FosterHomeListing
import com.comunidapp.app.data.model.FosterRequest
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.CommunityRepository
import com.comunidapp.app.data.repository.ServiceRepository
import com.comunidapp.app.data.model.NotificationType
import com.comunidapp.app.notifications.NotificationDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CommunityViewModel(
    private val communityRepository: CommunityRepository = DataProvider.communityRepository,
    private val serviceRepository: ServiceRepository = DataProvider.serviceRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val fosterHomes: StateFlow<List<FosterHomeListing>> =
        communityRepository.observeFosterHomes()

    val events: StateFlow<List<AdoptionEvent>> =
        communityRepository.observeEvents()

    val donations: StateFlow<List<DonationCampaign>> =
        communityRepository.observeDonations()

    fun clearActionMessage() = _actionMessage.update { null }

    fun requestFoster(home: FosterHomeListing, message: String = "Me interesa el tránsito") {
        val user = authRepository.getCurrentUser()
        if (user == null) {
            _actionMessage.value = "Iniciá sesión para solicitar tránsito"
            return
        }
        viewModelScope.launch {
            val result = serviceRepository.createFosterRequest(
                FosterRequest(
                    id = "",
                    fosterHomeId = home.id,
                    applicantId = user.id,
                    applicantName = user.name,
                    message = message,
                    phone = null
                )
            )
            if (result.isSuccess && home.hostId.isNotBlank()) {
                NotificationDispatcher.notify(
                    userId = home.hostId,
                    type = NotificationType.FOSTER_REQUEST,
                    title = "Solicitud de tránsito",
                    body = "${user.name} quiere un hogar de tránsito",
                    relatedId = home.id,
                    relatedType = "foster"
                )
            }
            _actionMessage.value = result.fold(
                onSuccess = { "Solicitud de tránsito enviada" },
                onFailure = { it.message ?: "No se pudo enviar la solicitud" }
            )
        }
    }

    fun expressEventInterest(event: AdoptionEvent) {
        val user = authRepository.getCurrentUser()
        if (user == null) {
            _actionMessage.value = "Iniciá sesión para marcar interés"
            return
        }
        viewModelScope.launch {
            val result = serviceRepository.expressEventInterest(event.id, user.id)
            _actionMessage.value = result.fold(
                onSuccess = { "Marcado: me interesa este evento" },
                onFailure = { it.message ?: "No se pudo registrar el interés" }
            )
        }
    }
}
