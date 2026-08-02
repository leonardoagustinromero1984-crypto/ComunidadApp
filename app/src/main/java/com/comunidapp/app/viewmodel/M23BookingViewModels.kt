package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.M23AvailabilityRule
import com.comunidapp.app.data.model.M23Booking
import com.comunidapp.app.data.model.M23BookingCancellation
import com.comunidapp.app.data.model.M23BookingFilter
import com.comunidapp.app.data.model.M23BookingHistoryEntry
import com.comunidapp.app.data.model.M23BookingListScope
import com.comunidapp.app.data.model.M23BookingMetrics
import com.comunidapp.app.data.model.M23BookingPolicy
import com.comunidapp.app.data.model.M23BookingRejectRequest
import com.comunidapp.app.data.model.M23BookingRescheduleRequest
import com.comunidapp.app.data.model.M23BookingStatusFilter
import com.comunidapp.app.data.model.M23BookingSummary
import com.comunidapp.app.data.model.M23ProviderBookingFilter
import com.comunidapp.app.data.model.M23SlotPage
import com.comunidapp.app.data.model.M23SlotQuery
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.M23AvailabilityRepository
import com.comunidapp.app.data.repository.M23BookingPolicyRepository
import com.comunidapp.app.data.repository.M23BookingRepository
import com.comunidapp.app.data.repository.M23BookingReviewEligibilityAdapter
import com.comunidapp.app.domain.m23.M23BookingFilters
import com.comunidapp.app.domain.m23.M23BookingResilience
import com.comunidapp.app.domain.m23.M23PrivacySanitizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed class M23HomeUiState {
    data object Loading : M23HomeUiState()
    data class Content(val bookingCount: Int) : M23HomeUiState()
    data class Error(val message: String) : M23HomeUiState()
}

sealed class M23AvailabilityUiState {
    data object Loading : M23AvailabilityUiState()
    data class Content(val page: M23SlotPage) : M23AvailabilityUiState()
    data object Empty : M23AvailabilityUiState()
    data class Error(val message: String) : M23AvailabilityUiState()
}

sealed class M23MyBookingsUiState {
    data object Loading : M23MyBookingsUiState()
    data class Content(val bookings: List<M23BookingSummary>, val filter: M23BookingFilter) : M23MyBookingsUiState()
    data object Empty : M23MyBookingsUiState()
    data class Error(val message: String) : M23MyBookingsUiState()
}

sealed class M23BookingDetailUiState {
    data object Loading : M23BookingDetailUiState()
    data class Content(
        val booking: M23Booking,
        val providerName: String,
        val offeringName: String,
        val history: List<M23BookingHistoryEntry>,
        val policy: M23BookingPolicy?,
        val reviewEligible: Boolean,
        val canOpenConversation: Boolean
    ) : M23BookingDetailUiState()
    data object Empty : M23BookingDetailUiState()
    data class Error(val message: String) : M23BookingDetailUiState()
}

sealed class M23ManageCalendarUiState {
    data object Loading : M23ManageCalendarUiState()
    data class Content(val rules: List<M23AvailabilityRule>) : M23ManageCalendarUiState()
    data object Empty : M23ManageCalendarUiState()
    data class Error(val message: String) : M23ManageCalendarUiState()
}

sealed class M23ManageBookingsUiState {
    data object Loading : M23ManageBookingsUiState()
    data class Content(val bookings: List<M23Booking>, val metrics: M23BookingMetrics, val filter: M23ProviderBookingFilter) : M23ManageBookingsUiState()
    data object Empty : M23ManageBookingsUiState()
    data class Error(val message: String) : M23ManageBookingsUiState()
}

class M23HomeViewModel(private val bookings: M23BookingRepository = DataProvider.m23BookingRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M23HomeUiState>(M23HomeUiState.Loading)
    val uiState: StateFlow<M23HomeUiState> = _uiState
    init {
        viewModelScope.launch {
            bookings.observeMyBookings(M23BookingFilter(scope = M23BookingListScope.UPCOMING))
                .catch { _uiState.value = M23HomeUiState.Error(M23BookingResilience.safeUserMessage(it)) }
                .collect { _uiState.value = M23HomeUiState.Content(it.size) }
        }
    }
}

class M23AvailabilityViewModel(
    query: M23SlotQuery,
    availability: M23AvailabilityRepository = DataProvider.m23AvailabilityRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M23AvailabilityUiState>(M23AvailabilityUiState.Loading)
    val uiState: StateFlow<M23AvailabilityUiState> = _uiState
    init {
        viewModelScope.launch {
            availability.observeSlots(query)
                .catch { _uiState.value = M23AvailabilityUiState.Error(M23BookingResilience.safeUserMessage(it)) }
                .collect { _uiState.value = if (it.days.all { day -> day.slots.isEmpty() }) M23AvailabilityUiState.Empty else M23AvailabilityUiState.Content(it) }
        }
    }
}

class M23MyBookingsViewModel(private val bookings: M23BookingRepository = DataProvider.m23BookingRepository) : ViewModel() {
    private val _filter = MutableStateFlow(M23BookingFilter.DEFAULT)
    private val _uiState = MutableStateFlow<M23MyBookingsUiState>(M23MyBookingsUiState.Loading)
    val uiState: StateFlow<M23MyBookingsUiState> = _uiState
    private var collectJob: Job? = null

    init { applyFilter(M23BookingFilter.DEFAULT) }

    fun applyFilter(filter: M23BookingFilter) {
        _filter.value = filter
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            bookings.observeMyBookings(filter)
                .catch { _uiState.value = M23MyBookingsUiState.Error(M23BookingResilience.safeUserMessage(it)) }
                .collect {
                    _uiState.value = if (it.isEmpty()) M23MyBookingsUiState.Empty
                    else M23MyBookingsUiState.Content(it, filter)
                }
        }
    }

    fun showUpcoming() = applyFilter(M23BookingFilter(scope = M23BookingListScope.UPCOMING))
    fun showHistory() = applyFilter(M23BookingFilter(scope = M23BookingListScope.HISTORY))
    fun filterStatus(status: M23BookingStatusFilter?) = applyFilter(_filter.value.copy(status = status))
}

class M23BookingDetailViewModel(
    id: String,
    private val bookings: M23BookingRepository = DataProvider.m23BookingRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M23BookingDetailUiState>(M23BookingDetailUiState.Loading)
    val uiState: StateFlow<M23BookingDetailUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(bookings.observeBooking(id), bookings.observeBookingHistory(id)) { booking, history ->
                booking to history
            }.catch { _uiState.value = M23BookingDetailUiState.Error(M23BookingResilience.safeUserMessage(it)) }
                .collect { (booking, history) ->
                    if (booking == null) {
                        _uiState.value = M23BookingDetailUiState.Empty
                        return@collect
                    }
                    val offeringName = "Baño completo"
                    _uiState.value = M23BookingDetailUiState.Content(
                        booking = booking,
                        providerName = M23PrivacySanitizer.scrubPublicText("Patitas Centro"),
                        offeringName = offeringName,
                        history = history,
                        policy = booking.policySnapshot,
                        reviewEligible = M23BookingReviewEligibilityAdapter.isReviewEligible(booking),
                        canOpenConversation = DataProvider.m23MessagingAvailable
                    )
                }
        }
    }

    fun confirm() = mutate { bookings.confirm(it) }
    fun reject(publicReason: String? = null, privateReason: String? = null) =
        mutate { bookings.reject(M23BookingRejectRequest(it, publicReason, privateReason)) }
    fun cancel(reason: String? = null) = mutate { bookings.cancel(M23BookingCancellation(it, reason)) }
    fun complete() = mutate { bookings.complete(it) }
    fun noShow() = mutate { bookings.noShow(it) }
    fun expire() = mutate { bookings.expire(it) }
    fun openConversation() = viewModelScope.launch {
        val id = (uiState.value as? M23BookingDetailUiState.Content)?.booking?.id ?: return@launch
        bookings.openConversation(id)
    }

    private fun mutate(operation: suspend (String) -> Result<M23Booking>) = viewModelScope.launch {
        (uiState.value as? M23BookingDetailUiState.Content)?.booking?.id?.let { operation(it) }
    }
}

class M23ManageCalendarViewModel(
    providerId: String,
    availability: M23AvailabilityRepository = DataProvider.m23AvailabilityRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<M23ManageCalendarUiState>(M23ManageCalendarUiState.Loading)
    val uiState: StateFlow<M23ManageCalendarUiState> = _uiState
    init {
        viewModelScope.launch {
            availability.observeRules(providerId)
                .catch { _uiState.value = M23ManageCalendarUiState.Error(M23BookingResilience.safeUserMessage(it)) }
                .collect { _uiState.value = if (it.isEmpty()) M23ManageCalendarUiState.Empty else M23ManageCalendarUiState.Content(it) }
        }
    }
}

class M23ManageBookingsViewModel(
    private val providerId: String,
    private val bookings: M23BookingRepository = DataProvider.m23BookingRepository
) : ViewModel() {
    private val _filter = MutableStateFlow(M23ProviderBookingFilter.DEFAULT)
    private val _uiState = MutableStateFlow<M23ManageBookingsUiState>(M23ManageBookingsUiState.Loading)
    val uiState: StateFlow<M23ManageBookingsUiState> = _uiState
    private var collectJob: Job? = null

    init { applyFilter(M23ProviderBookingFilter.DEFAULT) }

    fun applyFilter(filter: M23ProviderBookingFilter) {
        _filter.value = filter
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            bookings.observeProviderBookings(providerId, filter)
                .catch { _uiState.value = M23ManageBookingsUiState.Error(M23BookingResilience.safeUserMessage(it)) }
                .collect {
                    _uiState.value = if (it.isEmpty()) M23ManageBookingsUiState.Empty
                    else M23ManageBookingsUiState.Content(it, M23BookingFilters.metrics(it), filter)
                }
        }
    }

    fun filterDay(day: LocalDate) = applyFilter(_filter.value.copy(day = day, weekStart = null))
    fun filterWeek(weekStart: LocalDate) = applyFilter(_filter.value.copy(weekStart = weekStart, day = null))
    fun filterStatus(status: M23BookingStatusFilter?) = applyFilter(_filter.value.copy(status = status))
}

object M23ViewModelFactories {
    fun availability(query: M23SlotQuery) = factory { M23AvailabilityViewModel(query) }
    fun detail(id: String) = factory { M23BookingDetailViewModel(id) }
    fun calendar(providerId: String) = factory { M23ManageCalendarViewModel(providerId) }
    fun bookings(providerId: String) = factory { M23ManageBookingsViewModel(providerId) }
    fun myBookings() = factory { M23MyBookingsViewModel() }
    private fun factory(create: () -> ViewModel): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
        }
}
