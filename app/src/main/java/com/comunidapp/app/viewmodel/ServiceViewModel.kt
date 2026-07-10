package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.BookingStatus
import com.comunidapp.app.data.model.PaymentIntent
import com.comunidapp.app.data.model.PaymentStatus
import com.comunidapp.app.data.model.ServiceBooking
import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.data.model.ServiceProfile
import com.comunidapp.app.data.model.ServiceReview
import com.comunidapp.app.data.model.ShopProduct
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.data.repository.PlatformRepository
import com.comunidapp.app.data.repository.ServiceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class ComunidadUiState(
    val selectedCategory: ServiceCategory = ServiceCategory.VET
)

data class ServiceDetailUiState(
    val service: ServiceProfile? = null,
    val notes: String = "",
    val scheduledDayOffset: Int = 1,
    val hour: Int = 10,
    val isSubmitting: Boolean = false,
    val message: String? = null,
    val bookingCreated: Boolean = false,
    val reviewRating: Int = 5,
    val reviewComment: String = "",
    val isSubmittingReview: Boolean = false
)

data class MiNegocioUiState(
    val profile: ServiceProfile? = null,
    val name: String = "",
    val location: String = "",
    val description: String = "",
    val contactInfo: String = "",
    val scheduleText: String = "",
    val priceFrom: String = "",
    val acceptsBookings: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null,
    val productName: String = "",
    val productPrice: String = "",
    val productStock: String = "",
    val isSavingProduct: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ComunidadViewModel(
    private val serviceRepository: ServiceRepository = DataProvider.serviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComunidadUiState())
    val uiState: StateFlow<ComunidadUiState> = _uiState.asStateFlow()

    val services: StateFlow<List<ServiceProfile>> = _uiState
        .flatMapLatest { state -> serviceRepository.observeServices(state.selectedCategory) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectCategory(category: ServiceCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }
}

class ServiceDetailViewModel(
    private val serviceId: String,
    private val serviceRepository: ServiceRepository = DataProvider.serviceRepository,
    private val platformRepository: PlatformRepository = DataProvider.platformRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ServiceDetailUiState(service = serviceRepository.getServiceById(serviceId))
    )
    val uiState: StateFlow<ServiceDetailUiState> = _uiState.asStateFlow()

    val reviews: StateFlow<List<ServiceReview>> = platformRepository.observeReviews(serviceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            serviceRepository.observeServices().collect { list ->
                _uiState.update { it.copy(service = list.find { s -> s.id == serviceId } ?: it.service) }
            }
        }
    }

    fun updateNotes(value: String) = _uiState.update { it.copy(notes = value) }
    fun updateDayOffset(value: Int) = _uiState.update { it.copy(scheduledDayOffset = value) }
    fun updateHour(value: Int) = _uiState.update { it.copy(hour = value) }
    fun updateReviewRating(value: Int) = _uiState.update { it.copy(reviewRating = value.coerceIn(1, 5)) }
    fun updateReviewComment(value: String) = _uiState.update { it.copy(reviewComment = value) }
    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun submitReview() {
        val user = authRepository.getCurrentUser()
        if (user == null) {
            _uiState.update { it.copy(message = "Iniciá sesión para dejar una reseña") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingReview = true, message = null) }
            val result = platformRepository.addReview(
                ServiceReview(
                    id = "",
                    serviceId = serviceId,
                    authorId = user.id,
                    authorName = user.name,
                    rating = _uiState.value.reviewRating,
                    comment = _uiState.value.reviewComment.trim()
                )
            )
            _uiState.update {
                it.copy(
                    isSubmittingReview = false,
                    reviewComment = if (result.isSuccess) "" else it.reviewComment,
                    message = result.fold(
                        onSuccess = { "Reseña publicada" },
                        onFailure = { e -> e.message ?: "No se pudo publicar la reseña" }
                    )
                )
            }
        }
    }

    fun requestBooking() {
        val service = _uiState.value.service ?: return
        val user = authRepository.getCurrentUser()
        if (user == null) {
            _uiState.update { it.copy(message = "Iniciá sesión para pedir un turno") }
            return
        }
        if (!service.acceptsBookings) {
            _uiState.update { it.copy(message = "Este servicio no acepta turnos online") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, message = null) }
            val scheduledAt = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, _uiState.value.scheduledDayOffset.coerceIn(0, 30))
                set(Calendar.HOUR_OF_DAY, _uiState.value.hour.coerceIn(8, 20))
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val booking = ServiceBooking(
                id = "",
                serviceId = service.id,
                providerId = service.ownerId,
                clientId = user.id,
                clientName = user.name,
                scheduledAt = scheduledAt,
                notes = _uiState.value.notes.trim(),
                amount = service.priceFrom
            )
            val result = serviceRepository.createBooking(user, booking)
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    bookingCreated = result.isSuccess,
                    message = result.fold(
                        onSuccess = { "Turno solicitado. El profesional te confirmará." },
                        onFailure = { e -> e.message ?: "No se pudo solicitar el turno" }
                    )
                )
            }
        }
    }

    class Factory(private val serviceId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ServiceDetailViewModel(serviceId) as T
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MiNegocioViewModel(
    private val serviceRepository: ServiceRepository = DataProvider.serviceRepository,
    private val platformRepository: PlatformRepository = DataProvider.platformRepository,
    private val authRepository: AuthRepository = AuthProvider.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MiNegocioUiState())
    val uiState: StateFlow<MiNegocioUiState> = _uiState.asStateFlow()

    val currentUser: StateFlow<User?> = authRepository.observeAuthState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), authRepository.getCurrentUser())

    val bookings: StateFlow<List<ServiceBooking>> = currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else serviceRepository.observeProviderBookings(user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val products: StateFlow<List<ShopProduct>> = currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else platformRepository.observeProducts(user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val payments: StateFlow<List<PaymentIntent>> = currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else platformRepository.observePaymentsForUser(user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user == null) return@collect
                val existing = serviceRepository.fetchMyServiceProfile(user.id)
                val category = ServiceCategory.fromAccountType(user.accountType)
                _uiState.update {
                    it.copy(
                        profile = existing,
                        name = existing?.name ?: user.name,
                        location = existing?.location ?: user.locationText.orEmpty(),
                        description = existing?.description ?: user.bio.orEmpty(),
                        contactInfo = existing?.contactInfo.orEmpty(),
                        scheduleText = existing?.scheduleText.orEmpty(),
                        priceFrom = existing?.priceFrom?.toInt()?.toString().orEmpty(),
                        acceptsBookings = existing?.acceptsBookings
                            ?: (category != null && category != ServiceCategory.SHOP)
                    )
                }
            }
        }
    }

    fun updateName(v: String) = _uiState.update { it.copy(name = v) }
    fun updateLocation(v: String) = _uiState.update { it.copy(location = v) }
    fun updateDescription(v: String) = _uiState.update { it.copy(description = v) }
    fun updateContact(v: String) = _uiState.update { it.copy(contactInfo = v) }
    fun updateSchedule(v: String) = _uiState.update { it.copy(scheduleText = v) }
    fun updatePrice(v: String) = _uiState.update { it.copy(priceFrom = v) }
    fun updateAcceptsBookings(v: Boolean) = _uiState.update { it.copy(acceptsBookings = v) }
    fun updateProductName(v: String) = _uiState.update { it.copy(productName = v) }
    fun updateProductPrice(v: String) = _uiState.update { it.copy(productPrice = v) }
    fun updateProductStock(v: String) = _uiState.update { it.copy(productStock = v) }
    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun addProduct() {
        val user = authRepository.getCurrentUser() ?: return
        if (user.accountType != AccountType.SHOP) return
        val state = _uiState.value
        if (state.productName.isBlank()) {
            _uiState.update { it.copy(message = "Indicá el nombre del producto") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingProduct = true, message = null) }
            val result = platformRepository.upsertProduct(
                ShopProduct(
                    id = "",
                    ownerId = user.id,
                    serviceId = state.profile?.id,
                    name = state.productName.trim(),
                    price = state.productPrice.toDoubleOrNull() ?: 0.0,
                    stock = state.productStock.toIntOrNull() ?: 0
                )
            )
            _uiState.update {
                it.copy(
                    isSavingProduct = false,
                    productName = if (result.isSuccess) "" else it.productName,
                    productPrice = if (result.isSuccess) "" else it.productPrice,
                    productStock = if (result.isSuccess) "" else it.productStock,
                    message = result.fold(
                        onSuccess = { "Producto agregado al catálogo" },
                        onFailure = { e -> e.message ?: "No se pudo agregar el producto" }
                    )
                )
            }
        }
    }

    fun markPaymentPaid(paymentId: String) {
        viewModelScope.launch {
            platformRepository.markPaymentPaid(paymentId)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(message = error.message ?: "No se pudo marcar el pago")
                    }
                }
        }
    }

    fun saveProfile() {
        val user = authRepository.getCurrentUser() ?: return
        val category = ServiceCategory.fromAccountType(user.accountType) ?: run {
            _uiState.update { it.copy(message = "Tu tipo de cuenta no es un negocio") }
            return
        }
        val state = _uiState.value
        if (state.name.isBlank() || state.location.isBlank()) {
            _uiState.update { it.copy(message = "Completá nombre y ubicación") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            val profile = ServiceProfile(
                id = state.profile?.id.orEmpty(),
                ownerId = user.id,
                category = category,
                name = state.name.trim(),
                location = state.location.trim(),
                description = state.description.trim(),
                contactInfo = state.contactInfo.trim().ifBlank { null },
                photoUrl = user.profileImageUrl ?: state.profile?.photoUrl,
                tags = state.profile?.tags.orEmpty(),
                scheduleText = state.scheduleText.trim().ifBlank { null },
                priceFrom = state.priceFrom.toDoubleOrNull(),
                acceptsBookings = state.acceptsBookings,
                active = true
            )
            val result = serviceRepository.upsertServiceProfile(profile)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    profile = if (result.isSuccess) {
                        profile.copy(id = result.getOrDefault(profile.id))
                    } else {
                        it.profile
                    },
                    message = result.fold(
                        onSuccess = { "Ficha publicada en Comunidad" },
                        onFailure = { e -> e.message ?: "No se pudo guardar" }
                    )
                )
            }
        }
    }

    fun confirmBooking(bookingId: String) = updateBooking(bookingId, BookingStatus.CONFIRMED)
    fun completeBooking(bookingId: String) =
        updateBooking(bookingId, BookingStatus.COMPLETED, PaymentStatus.PAID_CASH)
    fun cancelBooking(bookingId: String) = updateBooking(bookingId, BookingStatus.CANCELLED)

    private fun updateBooking(
        bookingId: String,
        status: BookingStatus,
        paymentStatus: PaymentStatus? = null
    ) {
        viewModelScope.launch {
            val result = serviceRepository.updateBookingStatus(bookingId, status, paymentStatus)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(message = result.exceptionOrNull()?.message ?: "No se pudo actualizar el turno")
                }
            }
        }
    }
}
