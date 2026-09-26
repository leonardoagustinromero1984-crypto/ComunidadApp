package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.PersonalMemoriesRepository
import com.comunidapp.app.data.repository.PersonalMemory
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.perf.ScreenPerfProbe
import com.comunidapp.app.domain.pets.PetCareTransferCopy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PersonalPetMemoriesUiState(
    val petId: String?,
    val title: String,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val items: List<PersonalMemory> = emptyList(),
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    val playingAssetId: String? = null
)

class PersonalPetMemoriesViewModel(
    private val petId: String?,
    private val petNameHint: String?,
    private val repository: PersonalMemoriesRepository = DataProvider.personalMemoriesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PersonalPetMemoriesUiState(
            petId = petId,
            title = PetCareTransferCopy.memoriesOfPet(
                petNameHint?.takeIf { it.isNotBlank() }
                    ?: if (petId.isNullOrBlank()) PetCareTransferCopy.MEMORY_UNASSIGNED else "mascota"
            )
        )
    )
    val uiState: StateFlow<PersonalPetMemoriesUiState> = _uiState.asStateFlow()

    private var cursorCreatedAt: String? = null
    private var cursorId: String? = null
    private var loadingMore = false

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val hadContent = _uiState.value.items.isNotEmpty()
            if (!hadContent) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }
            cursorCreatedAt = null
            cursorId = null
            val probe = ScreenPerfProbe.begin("memories_pet_detail")
            val page = runCatching {
                probe.network {
                    repository.listForPet(
                        petId = petId,
                        limit = CanonicalBackend.MEMORY_PAGE_LIMIT
                    )
                }
            }.getOrElse {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "No pudimos cargar estos recuerdos."
                    )
                }
                probe.finish("error=1")
                return@launch
            }
            cursorCreatedAt = page.nextCursorCreatedAt
            cursorId = page.nextCursorId
            val titleFromPage = page.items.firstOrNull()?.petName?.trim()?.takeIf { it.isNotEmpty() }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    items = page.items,
                    hasMore = page.hasMore,
                    errorMessage = null,
                    title = PetCareTransferCopy.memoriesOfPet(
                        titleFromPage
                            ?: petNameHint
                            ?: if (petId.isNullOrBlank()) PetCareTransferCopy.MEMORY_UNASSIGNED else "mascota"
                    )
                )
            }
            probe.markFirstContent()
            // Visible-first: hydrate first 8 thumbs concurrently, then the rest.
            hydratePreviewsProgressive(page.items, probe)
            probe.finish("page=${page.items.size} hasMore=${page.hasMore}")
        }
    }

    fun loadMore() {
        if (loadingMore || !_uiState.value.hasMore) return
        val createdAt = cursorCreatedAt ?: return
        val id = cursorId ?: return
        loadingMore = true
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val page = runCatching {
                repository.listForPet(
                    petId = petId,
                    limit = CanonicalBackend.MEMORY_PAGE_LIMIT,
                    cursorCreatedAt = createdAt,
                    cursorId = id
                )
            }.getOrElse {
                _uiState.update { it.copy(isLoadingMore = false) }
                loadingMore = false
                return@launch
            }
            cursorCreatedAt = page.nextCursorCreatedAt
            cursorId = page.nextCursorId
            _uiState.update {
                it.copy(
                    items = it.items + page.items,
                    hasMore = page.hasMore,
                    isLoadingMore = false
                )
            }
            hydratePreviewsProgressive(page.items, null)
            loadingMore = false
        }
    }

    fun play(memoryId: String) {
        _uiState.update { it.copy(playingAssetId = memoryId) }
        viewModelScope.launch {
            val item = _uiState.value.items.firstOrNull { it.memoryId == memoryId } ?: return@launch
            val coverId = item.assetId
            val url = item.previewUrls[coverId] ?: repository.previewUrl(coverId) ?: return@launch
            _uiState.update { state ->
                state.copy(
                    items = state.items.map {
                        if (it.memoryId == memoryId) {
                            it.copy(
                                previewUrl = url,
                                previewUrls = it.previewUrls + (coverId to url)
                            )
                        } else {
                            it
                        }
                    }
                )
            }
        }
    }

    fun stopPlayback() {
        _uiState.update { it.copy(playingAssetId = null) }
    }

    private suspend fun hydratePreviewsProgressive(
        batch: List<PersonalMemory>,
        probe: ScreenPerfProbe.Session?
    ) {
        if (batch.isEmpty()) return
        // Visible-first: up to 4 thumbs per content, first 6 contents.
        val firstWave = batch.take(6).flatMap { it.mediaAssetIds.take(4) }.distinct()
        val firstUrls = if (probe != null) {
            probe.secondary { repository.previewUrls(firstWave) }
        } else {
            repository.previewUrls(firstWave)
        }
        applyUrls(firstUrls)
        val rest = batch.drop(6).flatMap { it.mediaAssetIds.take(1) }.distinct()
        if (rest.isNotEmpty()) {
            val restUrls = repository.previewUrls(rest)
            applyUrls(restUrls)
        }
    }

    private fun applyUrls(urls: Map<String, String?>) {
        if (urls.isEmpty()) return
        _uiState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    val merged = item.previewUrls + urls.filterKeys { it in item.mediaAssetIds || it == item.assetId }
                    val cover = merged[item.assetId] ?: item.mediaAssetIds.firstOrNull()?.let { merged[it] }
                    item.copy(
                        previewUrl = cover ?: item.previewUrl,
                        previewUrls = if (merged.isEmpty()) item.previewUrls else merged
                    )
                }
            )
        }
    }

    companion object {
        fun factory(petId: String?, petName: String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PersonalPetMemoriesViewModel(petId, petName) as T
                }
            }
    }
}
