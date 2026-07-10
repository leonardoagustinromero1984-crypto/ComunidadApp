package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.ContentReport
import com.comunidapp.app.data.model.ReportStatus
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PlatformRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AdminModerationViewModel(
    private val platformRepository: PlatformRepository = DataProvider.platformRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    val reports: StateFlow<List<ContentReport>> = platformRepository.observeOpenReports()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun dismissReport(id: String) = updateStatus(id, ReportStatus.DISMISSED, "Reporte desestimado")

    fun actionReport(id: String) = updateStatus(id, ReportStatus.ACTIONED, "Acción aplicada al reporte")

    fun clearMessage() = _message.update { null }

    private fun updateStatus(id: String, status: ReportStatus, successMessage: String) {
        val reviewerId = authRepository.getCurrentUser()?.id ?: return
        viewModelScope.launch {
            platformRepository.updateReportStatus(id, status, reviewerId)
                .onSuccess { _message.value = successMessage }
                .onFailure { error ->
                    _message.value = error.message ?: "No se pudo actualizar el reporte"
                }
        }
    }
}
