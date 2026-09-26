package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.PersonalMemoriesRepository
import com.comunidapp.app.data.repository.PersonalMemoryPetGroup
import com.comunidapp.app.domain.perf.ScreenPerfProbe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PersonalMemoryGroupsUiState(
    val isLoading: Boolean = true,
    val groups: List<PersonalMemoryPetGroup> = emptyList(),
    val errorMessage: String? = null
)

class PersonalMemoryGroupsViewModel(
    private val repository: PersonalMemoriesRepository = DataProvider.personalMemoriesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonalMemoryGroupsUiState())
    val uiState: StateFlow<PersonalMemoryGroupsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val hadContent = _uiState.value.groups.isNotEmpty()
            if (!hadContent) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }
            val probe = ScreenPerfProbe.begin("memories_root")
            val listed = runCatching {
                probe.network { repository.listPetGroups() }
            }.getOrElse {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "No pudimos cargar tus recuerdos."
                    )
                }
                probe.finish("error=1")
                return@launch
            }
            _uiState.update {
                it.copy(isLoading = false, groups = listed, errorMessage = null)
            }
            probe.markFirstContent()
            val coverIds = listed.mapNotNull { it.coverAssetId }.take(8)
            val urls = probe.secondary { repository.previewUrls(coverIds) }
            val withCovers = listed.map { group ->
                val coverId = group.coverAssetId ?: return@map group
                group.copy(coverPreviewUrl = urls[coverId] ?: group.coverPreviewUrl)
            }
            _uiState.update { it.copy(groups = withCovers) }
            probe.finish("groups=${withCovers.size}")
        }
    }
}
