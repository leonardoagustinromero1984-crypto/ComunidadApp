package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m08.M08PetErrorMapper
import com.comunidapp.app.data.remote.supabase.m08.PetStatusHistoryM08Row
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PetStatusHistoryUiState(
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val entries: List<PetStatusHistoryM08Row> = emptyList(),
    val canViewHistory: Boolean = false
) {
    val isEmpty: Boolean
        get() = !isLoading && loadErrorMessage == null && entries.isEmpty()
}

/**
 * LeoVer M08 Etapa 6 — historial de estados de una mascota (RLS SELECT).
 * Visible con canViewHistory / canRead; sin mutaciones.
 */
class PetStatusHistoryViewModel(
    private val petId: String,
    private val petRepository: PetRepository = DataProvider.petRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PetStatusHistoryUiState())
    val uiState: StateFlow<PetStatusHistoryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadErrorMessage = null) }
            if (petId.isBlank()) {
                failLoad("PET_NOT_FOUND")
                return@launch
            }
            if (authRepository.getCurrentUser() == null) {
                failLoad("NOT_AUTHENTICATED")
                return@launch
            }
            val access = petRepository.getPetAccessContext(petId).getOrElse { error ->
                failLoad(M08PetErrorMapper.codeOf(error))
                return@launch
            }
            val allowed = access.canViewHistory || access.canRead
            if (!allowed) {
                failLoad("FORBIDDEN")
                return@launch
            }
            val history = petRepository.listStatusHistory(petId).getOrElse { error ->
                failLoad(M08PetErrorMapper.codeOf(error))
                return@launch
            }
            // Backend orders by changed_at desc; keep stable if already sorted.
            val sorted = history.sortedByDescending { it.createdAt.orEmpty() }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    loadErrorMessage = null,
                    entries = sorted,
                    canViewHistory = true
                )
            }
        }
    }

    private fun failLoad(code: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                loadErrorMessage = M08PetErrorMapper.userMessage(code),
                canViewHistory = false
            )
        }
    }

    companion object {
        fun factory(petId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PetStatusHistoryViewModel(petId = petId) as T
                }
            }
    }
}
