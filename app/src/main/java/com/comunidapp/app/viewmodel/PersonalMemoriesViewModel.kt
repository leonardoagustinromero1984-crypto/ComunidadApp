package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.PersonalMemoriesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Legacy flat list VM retained for compile safety; root screen uses
 * [PersonalMemoryGroupsViewModel].
 */
@Deprecated("Use PersonalMemoryGroupsViewModel / PersonalPetMemoriesViewModel")
data class PersonalMemoriesUiState(
    val isLoading: Boolean = true,
    val items: List<com.comunidapp.app.data.repository.PersonalMemory> = emptyList(),
    val errorMessage: String? = null
)

@Deprecated("Use PersonalMemoryGroupsViewModel")
class PersonalMemoriesViewModel(
    private val repository: PersonalMemoriesRepository = DataProvider.personalMemoriesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonalMemoriesUiState())
    val uiState: StateFlow<PersonalMemoriesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = false, items = emptyList()) }
        }
    }
}
