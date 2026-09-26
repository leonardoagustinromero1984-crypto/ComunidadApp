package com.comunidapp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.comunidapp.app.data.model.BookingStatus
import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.context.OperationalContextProvider
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
import com.comunidapp.app.data.repository.CanonicalServiceRepository
import com.comunidapp.app.domain.canonical.CanonicalProviderWrite
import com.comunidapp.app.domain.schedule.ProviderWeeklySchedule
import com.comunidapp.app.domain.schedule.WeeklyHoursDay
import com.comunidapp.app.domain.ux.CanonicalUiErrorMapper
import com.comunidapp.app.data.model.NotificationType
import com.comunidapp.app.notifications.NotificationDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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

enum class CommunityResultsView { LIST, MAP }

data class ComunidadUiState(
    val selectedCategory: ServiceCategory? = null,
    val isLoading: Boolean = false,
    val locationQuery: String = "",
    val province: String = "",
    val city: String = "",
    val localityId: String? = null,
    val selectedTag: String? = null,
    val activeOnly: Boolean = false,
    val nearMeEnabled: Boolean = false,
    val deviceLat: Double? = null,
    val deviceLng: Double? = null,
    val resultsView: CommunityResultsView = CommunityResultsView.LIST,
    val hasSearched: Boolean = false,
    val resultsStale: Boolean = false,
    val searchError: String? = null,
    val nearbyItems: List<com.comunidapp.app.data.repository.CommunityNearbyItem> = emptyList()
) {
    val activeFilterCount: Int
        get() = listOf(
            locationQuery.isNotBlank(),
            selectedTag != null,
            nearMeEnabled,
            activeOnly
        ).count { it }
}

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
    val weeklyHours: List<WeeklyHoursDay> = emptyList(),
    val pinLat: Double? = null,
    val pinLng: Double? = null,
    val geoIsPublicPremises: Boolean = false,
    val priceFrom: String = "",
    val acceptsBookings: Boolean = true,
    val slotIntervalMinutes: Int = com.comunidapp.app.domain.schedule.AppointmentSlotPolicy.DEFAULT_INTERVAL_MINUTES,
    val missingRequirements: com.comunidapp.app.domain.validation.ValidationSummary =
        com.comunidapp.app.domain.validation.ValidationSummary(items = emptyList()),
    val isSaving: Boolean = false,
    val message: String? = null,
    val published: Boolean = false,
    val productName: String = "",
    val productPrice: String = "",
    val productStock: String = "",
    val isSavingProduct: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ComunidadViewModel(
    private val serviceRepository: ServiceRepository = DataProvider.serviceRepository,
    private val authRepository: AuthRepository = AuthProvider.repository,
    private val userRepository: com.comunidapp.app.data.repository.UserRepository =
        DataProvider.userRepository,
    private val nearbyRepository: com.comunidapp.app.data.repository.CanonicalCommunityNearbyRepository =
        com.comunidapp.app.data.repository.CanonicalCommunityNearbyRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComunidadUiState())
    val uiState: StateFlow<ComunidadUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var searchGeneration: Int = 0

    val services: StateFlow<List<ServiceProfile>> = _uiState
        .flatMapLatest { state ->
            val category = state.selectedCategory
            if (!state.hasSearched || category == null) flowOf(emptyList())
            else serviceRepository.observeServices(category)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectCategory(category: ServiceCategory) {
        _uiState.update { state ->
            if (state.selectedCategory == category) {
                state.copy(selectedCategory = null, selectedTag = null, isLoading = false)
            } else {
                state.copy(
                    selectedCategory = category,
                    selectedTag = null,
                    isLoading = false,
                    resultsStale = state.hasSearched
                )
            }
        }
    }

    fun search() {
        val category = _uiState.value.selectedCategory ?: return
        searchJob?.cancel()
        val generation = ++searchGeneration
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, searchError = null) }
            val probe = com.comunidapp.app.domain.perf.ScreenPerfProbe.begin("community")
            try {
                runCatching {
                    probe.network {
                        val lat = _uiState.value.deviceLat
                        val lng = _uiState.value.deviceLng
                        if (_uiState.value.nearMeEnabled && lat != null && lng != null) {
                            nearbyRepository.list(lat, lng, nearbyFilter(category)).getOrThrow()
                        } else {
                            emptyList()
                        }.also { nearby ->
                            _uiState.update { it.copy(nearbyItems = nearby) }
                        }
                        serviceRepository.refreshDirectory(
                            nearLat = _uiState.value.deviceLat.takeIf { _uiState.value.nearMeEnabled },
                            nearLng = _uiState.value.deviceLng.takeIf { _uiState.value.nearMeEnabled }
                        )
                    }
                }
                    .onFailure { error ->
                        if (generation == searchGeneration) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    hasSearched = true,
                                    resultsStale = false,
                                    searchError = error.message ?: "No pudimos buscar ahora. Intentá de nuevo."
                                )
                            }
                        }
                        return@launch
                    }
                if (generation == searchGeneration) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasSearched = true,
                            resultsStale = false,
                            searchError = null,
                            selectedCategory = category
                        )
                    }
                }
            } finally {
                if (generation == searchGeneration && _uiState.value.isLoading) {
                    _uiState.update { it.copy(isLoading = false, hasSearched = true, resultsStale = false) }
                }
                probe.markFirstContent()
                probe.finish(com.comunidapp.app.domain.perf.ScreenPerfProbe.Ledger.snapshot())
            }
        }
    }

    fun applyFilters(locationQuery: String, activeOnly: Boolean) {
        _uiState.update {
            it.copy(
                locationQuery = locationQuery.trim(),
                activeOnly = activeOnly,
                nearMeEnabled = false,
                resultsStale = it.hasSearched
            )
        }
    }

    fun applyGeography(province: String, city: String, localityId: String?) {
        val query = listOf(city.trim(), province.trim()).filter { it.isNotBlank() }.joinToString(", ")
        _uiState.update {
            it.copy(
                province = province.trim(),
                city = city.trim(),
                localityId = localityId,
                locationQuery = query,
                nearMeEnabled = false,
                resultsStale = it.hasSearched
            )
        }
    }

    fun selectTag(tag: String?) {
        _uiState.update { it.copy(selectedTag = tag, resultsStale = it.hasSearched) }
    }

    fun setResultsView(view: CommunityResultsView) {
        _uiState.update { it.copy(resultsView = view) }
    }

    fun enableNearMe(lat: Double, lng: Double) {
        _uiState.update {
            it.copy(
                nearMeEnabled = true,
                deviceLat = lat,
                deviceLng = lng,
                locationQuery = "",
                resultsStale = it.hasSearched
            )
        }
    }

    fun disableNearMe() {
        _uiState.update {
            it.copy(
                nearMeEnabled = false,
                deviceLat = null,
                deviceLng = null,
                resultsStale = it.hasSearched
            )
        }
    }

    fun toggleNearMe() {
        if (_uiState.value.nearMeEnabled) disableNearMe()
    }

    private fun nearbyFilter(category: ServiceCategory): String = when (category) {
        ServiceCategory.VET -> "VETERINARY"
        ServiceCategory.TRAINER, ServiceCategory.WALKER, ServiceCategory.GROOMING,
        ServiceCategory.CAREGIVER, ServiceCategory.SHOP, ServiceCategory.DAYCARE -> "SERVICES"
        else -> "NEAR"
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                locationQuery = "",
                province = "",
                city = "",
                localityId = null,
                selectedTag = null,
                activeOnly = false,
                nearMeEnabled = false,
                deviceLat = null,
                deviceLng = null,
                resultsStale = it.hasSearched
            )
        }
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
            if (result.isSuccess) {
                NotificationDispatcher.notify(
                    userId = service.ownerId,
                    type = NotificationType.BOOKING,
                    title = "Nuevo turno solicitado",
                    body = "${user.name} pidió un turno en ${service.name}",
                    relatedId = result.getOrNull(),
                    relatedType = "booking"
                )
            }
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

    private var formDirty = false
    private var loadedContextKey: String? = null

    init {
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user == null) return@collect
                reloadIsolatedProfile(user.id, force = false)
            }
        }
        viewModelScope.launch {
            OperationalContextProvider.active.collect { ctx ->
                val user = authRepository.getCurrentUser() ?: return@collect
                val key = ctx.kind.name + ":" + ctx.entityId
                reloadIsolatedProfile(user.id, force = loadedContextKey != null && loadedContextKey != key)
            }
        }
    }

    private suspend fun reloadIsolatedProfile(userId: String, force: Boolean) {
        val key = OperationalContextProvider.active.value.let { it.kind.name + ":" + it.entityId }
        if (formDirty && !force && loadedContextKey == key) return
        loadedContextKey = key
        formDirty = false
        val existing = serviceRepository.fetchMyServiceProfile(userId)
        _uiState.update {
            it.copy(
                profile = existing,
                name = existing?.name.orEmpty(),
                location = existing?.location.orEmpty(),
                description = existing?.description.orEmpty(),
                contactInfo = existing?.contactInfo.orEmpty(),
                scheduleText = "",
                weeklyHours = ProviderWeeklySchedule.forEditor(
                    existing?.weeklyHours.orEmpty()
                ).days,
                pinLat = existing?.latitude,
                pinLng = existing?.longitude,
                geoIsPublicPremises = existing?.geoIsPublicPremises == true,
                priceFrom = existing?.priceFrom?.takeIf { it.isFinite() }?.toInt()?.toString().orEmpty(),
                acceptsBookings = existing?.acceptsBookings == true,
                slotIntervalMinutes = existing?.slotIntervalMinutes
                    ?: com.comunidapp.app.domain.schedule.AppointmentSlotPolicy.DEFAULT_INTERVAL_MINUTES
            )
        }
    }

    fun updateName(v: String) { formDirty = true; _uiState.update { it.copy(name = v) } }
    fun updateLocation(v: String) { formDirty = true; _uiState.update { it.copy(location = v) } }
    fun updateDescription(v: String) { formDirty = true; _uiState.update { it.copy(description = v) } }
    fun updateContact(v: String) { formDirty = true; _uiState.update { it.copy(contactInfo = v) } }
    fun updateSchedule(v: String) { formDirty = true; _uiState.update { it.copy(scheduleText = v) } }
    fun updateWeeklyHours(days: List<WeeklyHoursDay>) {
        formDirty = true
        _uiState.update { it.copy(weeklyHours = ProviderWeeklySchedule.forEditor(days).days) }
    }
    fun updateMapPin(lat: Double, lng: Double, publicPremises: Boolean) {
        formDirty = true
        _uiState.update { it.copy(pinLat = lat, pinLng = lng, geoIsPublicPremises = publicPremises) }
    }
    fun updatePrice(v: String) = _uiState.update { it.copy(priceFrom = v) }
    fun updateAcceptsBookings(v: Boolean) = _uiState.update { it.copy(acceptsBookings = v) }
    fun updateSlotInterval(minutes: Int) = _uiState.update { it.copy(slotIntervalMinutes = minutes) }
    fun updateProductName(v: String) = _uiState.update { it.copy(productName = v) }
    fun updateProductPrice(v: String) = _uiState.update { it.copy(productPrice = v) }
    fun updateProductStock(v: String) = _uiState.update { it.copy(productStock = v) }
    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun addProduct() {
        val user = authRepository.getCurrentUser() ?: return
        if (OperationalContextProvider.active.value !is OperationalContext.Shop) return
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
        check(!com.comunidapp.app.domain.qa.ProfilePublishAuthPolicy.TRIGGERS_OAUTH)
        val user = authRepository.getCurrentUser() ?: return
        val category = serviceCategoryFromActiveContext() ?: run {
            _uiState.update { it.copy(message = CanonicalProviderWrite.INCOMPLETE_SETUP_MESSAGE) }
            return
        }
        val state = _uiState.value
        val missing = com.comunidapp.app.domain.validation.ProviderPublishRequirements.summary(
            name = state.name,
            location = state.location,
            phone = state.contactInfo,
            hours = state.weeklyHours,
            acceptsBookings = state.acceptsBookings,
            slotIntervalMinutes = state.slotIntervalMinutes
        )
        if (!missing.isEmpty) {
            _uiState.update { it.copy(missingRequirements = missing, message = missing.message()) }
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
                scheduleText = null,
                weeklyHours = state.weeklyHours,
                latitude = state.pinLat,
                longitude = state.pinLng,
                geoIsPublicPremises = state.geoIsPublicPremises,
                priceFrom = state.priceFrom.toDoubleOrNull(),
                acceptsBookings = state.acceptsBookings,
                slotIntervalMinutes = state.slotIntervalMinutes,
                active = true
            )
            val result = serviceRepository.upsertServiceProfile(profile)
            result.onSuccess { id ->
                formDirty = false
                val canonical = serviceRepository as? CanonicalServiceRepository
                if (canonical != null) {
                    val hours = state.weeklyHours.ifEmpty { ProviderWeeklySchedule.emptyTemplate().days }
                    runCatching { canonical.saveWeeklyHours(id, hours) }
                    val lat = state.pinLat
                    val lng = state.pinLng
                    if (lat != null && lng != null) {
                        runCatching {
                            canonical.savePublicGeo(id, lat, lng, state.geoIsPublicPremises, state.location)
                        }
                    }
                    runCatching { canonical.refreshDirectory() }
                }
            }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    profile = if (result.isSuccess) {
                        profile.copy(id = result.getOrDefault(profile.id))
                    } else {
                        it.profile
                    },
                    published = result.isSuccess,
                    message = result.fold(
                        onSuccess = { "Ficha publicada en Comunidad" },
                        onFailure = { e ->
                            CanonicalUiErrorMapper.userMessage(
                                e,
                                CanonicalProviderWrite.INCOMPLETE_SETUP_MESSAGE
                            )
                        }
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

    private fun serviceCategoryFromActiveContext(): ServiceCategory? =
        CanonicalProviderWrite.categoryFromContext(OperationalContextProvider.active.value)
}
