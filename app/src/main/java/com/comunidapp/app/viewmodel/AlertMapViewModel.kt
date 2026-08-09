package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.LostFoundRepository
import com.comunidapp.app.domain.alerts.AlertDateFilter
import com.comunidapp.app.domain.alerts.AlertLocationPrivacy
import com.comunidapp.app.domain.alerts.AlertMapTypeFilter
import com.comunidapp.app.domain.alerts.AlertMapViewMode
import com.comunidapp.app.domain.alerts.AlertZoneCatalog
import com.comunidapp.app.domain.alerts.AlertZoneOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlertMapUiState(
    val viewMode: AlertMapViewMode = AlertMapViewMode.MAP,
    val typeFilter: AlertMapTypeFilter = AlertMapTypeFilter.ALL,
    val distanceKm: Int = 25,
    val dateFilter: AlertDateFilter = AlertDateFilter.ANY,
    val species: PetSpecies? = null,
    val zoneQuery: String = "",
    val selectedZone: AlertZoneOption? = null,
    val anchorLatitude: Double? = null,
    val anchorLongitude: Double? = null,
    val locationPermissionGranted: Boolean = false,
    val locationDisabled: Boolean = false,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val selectedAlertId: String? = null
)

data class AlertMapItem(
    val post: LostFoundPost,
    val zoneLabel: String,
    val displayLatitude: Double?,
    val displayLongitude: Double?,
    val distanceKm: Double?,
    val onMap: Boolean
)

class AlertMapViewModel(
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val lostFoundRepository: LostFoundRepository = DataProvider.lostFoundRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(
        AlertMapUiState(
            viewMode = runCatching {
                AlertMapViewMode.valueOf(savedStateHandle.get<String>(KEY_VIEW) ?: "MAP")
            }.getOrDefault(AlertMapViewMode.MAP),
            typeFilter = runCatching {
                AlertMapTypeFilter.valueOf(savedStateHandle.get<String>(KEY_TYPE) ?: "ALL")
            }.getOrDefault(AlertMapTypeFilter.ALL),
            distanceKm = savedStateHandle.get<Int>(KEY_DISTANCE) ?: 25,
            zoneQuery = savedStateHandle.get<String>(KEY_ZONE) ?: ""
        )
    )
    val uiState: StateFlow<AlertMapUiState> = _ui.asStateFlow()

    private val handle = savedStateHandle

    val alerts: StateFlow<List<AlertMapItem>> = combine(
        lostFoundRepository.observeLostFoundPosts(),
        _ui
    ) { all, state ->
        _ui.update { it.copy(isLoading = false, loadError = null) }
        filterAlerts(all, state)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            runCatching { lostFoundRepository.observeLostFoundPosts().value }
                .onFailure {
                    _ui.update {
                        it.copy(
                            isLoading = false,
                            loadError = "No pudimos cargar las alertas"
                        )
                    }
                }
        }
    }

    fun setViewMode(mode: AlertMapViewMode) {
        handle[KEY_VIEW] = mode.name
        _ui.update { it.copy(viewMode = mode) }
    }

    fun setTypeFilter(filter: AlertMapTypeFilter) {
        handle[KEY_TYPE] = filter.name
        _ui.update { it.copy(typeFilter = filter) }
    }

    fun setDistanceKm(km: Int) {
        handle[KEY_DISTANCE] = km
        _ui.update { it.copy(distanceKm = km) }
    }

    fun setDateFilter(filter: AlertDateFilter) {
        _ui.update { it.copy(dateFilter = filter) }
    }

    fun setSpecies(species: PetSpecies?) {
        _ui.update { it.copy(species = species) }
    }

    fun setZoneQuery(query: String) {
        handle[KEY_ZONE] = query
        _ui.update { it.copy(zoneQuery = query) }
    }

    fun selectZone(zone: AlertZoneOption) {
        handle[KEY_ZONE] = zone.label
        _ui.update {
            it.copy(
                selectedZone = zone,
                zoneQuery = zone.label,
                anchorLatitude = zone.latitude,
                anchorLongitude = zone.longitude,
                locationDisabled = false
            )
        }
    }

    fun expandSearch() {
        _ui.update { it.copy(distanceKm = (it.distanceKm * 2).coerceAtMost(100)) }
        handle[KEY_DISTANCE] = _ui.value.distanceKm
    }

    fun setLocationPermission(granted: Boolean) {
        _ui.update { it.copy(locationPermissionGranted = granted) }
    }

    fun setDeviceLocation(lat: Double?, lng: Double?, disabled: Boolean = false) {
        if (lat != null && lng != null && AlertLocationPrivacy.isValidCoordinate(lat, lng)) {
            val factor = 1000.0
            _ui.update {
                it.copy(
                    anchorLatitude = kotlin.math.round(lat * factor) / factor,
                    anchorLongitude = kotlin.math.round(lng * factor) / factor,
                    locationDisabled = false
                )
            }
        } else {
            _ui.update { it.copy(locationDisabled = disabled) }
        }
    }

    fun selectAlert(id: String?) {
        _ui.update { it.copy(selectedAlertId = id) }
    }

    fun retry() {
        _ui.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            runCatching { lostFoundRepository.observeLostFoundPosts().value }
                .onSuccess { _ui.update { s -> s.copy(isLoading = false) } }
                .onFailure {
                    _ui.update {
                        it.copy(isLoading = false, loadError = "No pudimos cargar las alertas")
                    }
                }
        }
    }

    fun zoneOptions(): List<AlertZoneOption> = AlertZoneCatalog.zones

    private fun filterAlerts(all: List<LostFoundPost>, state: AlertMapUiState): List<AlertMapItem> {
        val now = System.currentTimeMillis()
        val maxAgeMs = when (state.dateFilter) {
            AlertDateFilter.ANY -> null
            AlertDateFilter.LAST_7_DAYS -> 7L * 24 * 60 * 60 * 1000
            AlertDateFilter.LAST_30_DAYS -> 30L * 24 * 60 * 60 * 1000
        }
        return all.asSequence()
            .filter { it.status == LostFoundStatus.ACTIVE }
            .filter {
                when (state.typeFilter) {
                    AlertMapTypeFilter.ALL -> true
                    AlertMapTypeFilter.LOST -> it.type == LostFoundType.LOST
                    AlertMapTypeFilter.FOUND -> it.type == LostFoundType.FOUND
                }
            }
            .filter { state.species == null || it.species == state.species }
            .filter {
                state.zoneQuery.isBlank() ||
                    it.location.contains(state.zoneQuery, ignoreCase = true)
            }
            .filter { post ->
                if (maxAgeMs == null) return@filter true
                val created = post.createdAt ?: return@filter true
                now - created <= maxAgeMs
            }
            .map { post ->
                val pub = AlertLocationPrivacy.publicLocation(post)
                val dist = if (
                    pub.hasValidCoordinates &&
                    state.anchorLatitude != null &&
                    state.anchorLongitude != null
                ) {
                    AlertLocationPrivacy.distanceKm(
                        state.anchorLatitude!!,
                        state.anchorLongitude!!,
                        pub.displayLatitude!!,
                        pub.displayLongitude!!
                    )
                } else {
                    null
                }
                AlertMapItem(
                    post = post,
                    zoneLabel = pub.zoneLabel,
                    displayLatitude = pub.displayLatitude,
                    displayLongitude = pub.displayLongitude,
                    distanceKm = dist,
                    onMap = pub.hasValidCoordinates
                )
            }
            .filter { item ->
                val dist = item.distanceKm
                dist == null || dist <= state.distanceKm
            }
            .sortedWith(
                compareBy<AlertMapItem> { it.distanceKm ?: Double.MAX_VALUE }
                    .thenByDescending { it.post.createdAt ?: 0L }
            )
            .toList()
    }

    companion object {
        private const val KEY_VIEW = "alert_map_view"
        private const val KEY_TYPE = "alert_map_type"
        private const val KEY_DISTANCE = "alert_map_distance"
        private const val KEY_ZONE = "alert_map_zone"
    }
}
