package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.M23AvailabilityRule
import com.comunidapp.app.data.model.M23Booking
import com.comunidapp.app.data.model.M23BookingSummary
import com.comunidapp.app.data.model.M23PublicBookingContext
import com.comunidapp.app.data.model.M23SlotPage
import com.comunidapp.app.data.model.M23SlotQuery
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.M23AvailabilityRepository
import com.comunidapp.app.data.repository.M23BookingRepository
import com.comunidapp.app.domain.m23.M23BookingResilience
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed class M23HomeUiState { data object Loading : M23HomeUiState(); data class Content(val bookingCount: Int) : M23HomeUiState(); data class Error(val message: String) : M23HomeUiState() }
sealed class M23AvailabilityUiState { data object Loading : M23AvailabilityUiState(); data class Content(val page: M23SlotPage) : M23AvailabilityUiState(); data object Empty : M23AvailabilityUiState(); data class Error(val message: String) : M23AvailabilityUiState() }
sealed class M23MyBookingsUiState { data object Loading : M23MyBookingsUiState(); data class Content(val bookings: List<M23BookingSummary>) : M23MyBookingsUiState(); data object Empty : M23MyBookingsUiState(); data class Error(val message: String) : M23MyBookingsUiState() }
sealed class M23BookingDetailUiState { data object Loading : M23BookingDetailUiState(); data class Content(val booking: M23Booking) : M23BookingDetailUiState(); data object Empty : M23BookingDetailUiState(); data class Error(val message: String) : M23BookingDetailUiState() }
sealed class M23ManageCalendarUiState { data object Loading : M23ManageCalendarUiState(); data class Content(val rules: List<M23AvailabilityRule>) : M23ManageCalendarUiState(); data object Empty : M23ManageCalendarUiState(); data class Error(val message: String) : M23ManageCalendarUiState() }
sealed class M23ManageBookingsUiState { data object Loading : M23ManageBookingsUiState(); data class Content(val bookings: List<M23Booking>) : M23ManageBookingsUiState(); data object Empty : M23ManageBookingsUiState(); data class Error(val message: String) : M23ManageBookingsUiState() }

class M23HomeViewModel(private val bookings: M23BookingRepository = DataProvider.m23BookingRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M23HomeUiState>(M23HomeUiState.Loading); val uiState: StateFlow<M23HomeUiState> = _uiState
    init { viewModelScope.launch { bookings.observeMyBookings().catch { _uiState.value = M23HomeUiState.Error(M23BookingResilience.safeUserMessage(it)) }.collect { _uiState.value = M23HomeUiState.Content(it.size) } } }
}
class M23AvailabilityViewModel(query: M23SlotQuery, availability: M23AvailabilityRepository = DataProvider.m23AvailabilityRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M23AvailabilityUiState>(M23AvailabilityUiState.Loading); val uiState: StateFlow<M23AvailabilityUiState> = _uiState
    init { viewModelScope.launch { availability.observeSlots(query).catch { _uiState.value = M23AvailabilityUiState.Error(M23BookingResilience.safeUserMessage(it)) }.collect { _uiState.value = if (it.days.all { day -> day.slots.isEmpty() }) M23AvailabilityUiState.Empty else M23AvailabilityUiState.Content(it) } } }
}
class M23MyBookingsViewModel(private val bookings: M23BookingRepository = DataProvider.m23BookingRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M23MyBookingsUiState>(M23MyBookingsUiState.Loading); val uiState: StateFlow<M23MyBookingsUiState> = _uiState
    init { viewModelScope.launch { bookings.observeMyBookings().catch { _uiState.value = M23MyBookingsUiState.Error(M23BookingResilience.safeUserMessage(it)) }.collect { _uiState.value = if (it.isEmpty()) M23MyBookingsUiState.Empty else M23MyBookingsUiState.Content(it) } } }
}
class M23BookingDetailViewModel(id: String, private val bookings: M23BookingRepository = DataProvider.m23BookingRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M23BookingDetailUiState>(M23BookingDetailUiState.Loading); val uiState: StateFlow<M23BookingDetailUiState> = _uiState
    init { viewModelScope.launch { bookings.observeBooking(id).catch { _uiState.value = M23BookingDetailUiState.Error(M23BookingResilience.safeUserMessage(it)) }.collect { _uiState.value = it?.let(M23BookingDetailUiState::Content) ?: M23BookingDetailUiState.Empty } } }
    fun confirm() = change { bookings.confirm(it) }; fun reject() = change { bookings.reject(it) }; fun complete() = change { bookings.complete(it) }
    private fun change(operation: suspend (String) -> Result<M23Booking>) = viewModelScope.launch { (uiState.value as? M23BookingDetailUiState.Content)?.booking?.id?.let { operation(it) } }
}
class M23ManageCalendarViewModel(providerId: String, availability: M23AvailabilityRepository = DataProvider.m23AvailabilityRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M23ManageCalendarUiState>(M23ManageCalendarUiState.Loading); val uiState: StateFlow<M23ManageCalendarUiState> = _uiState
    init { viewModelScope.launch { availability.observeRules(providerId).catch { _uiState.value = M23ManageCalendarUiState.Error(M23BookingResilience.safeUserMessage(it)) }.collect { _uiState.value = if (it.isEmpty()) M23ManageCalendarUiState.Empty else M23ManageCalendarUiState.Content(it) } } }
}
class M23ManageBookingsViewModel(providerId: String, bookings: M23BookingRepository = DataProvider.m23BookingRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<M23ManageBookingsUiState>(M23ManageBookingsUiState.Loading); val uiState: StateFlow<M23ManageBookingsUiState> = _uiState
    init { viewModelScope.launch { bookings.observeProviderBookings(providerId).catch { _uiState.value = M23ManageBookingsUiState.Error(M23BookingResilience.safeUserMessage(it)) }.collect { _uiState.value = if (it.isEmpty()) M23ManageBookingsUiState.Empty else M23ManageBookingsUiState.Content(it) } } }
}
object M23ViewModelFactories {
    fun availability(query: M23SlotQuery) = factory { M23AvailabilityViewModel(query) }
    fun detail(id: String) = factory { M23BookingDetailViewModel(id) }
    fun calendar(providerId: String) = factory { M23ManageCalendarViewModel(providerId) }
    fun bookings(providerId: String) = factory { M23ManageBookingsViewModel(providerId) }
    private fun factory(create: () -> ViewModel): ViewModelProvider.Factory = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T }
}
