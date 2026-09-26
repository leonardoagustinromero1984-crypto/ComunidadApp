package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.BookingStatus
import com.comunidapp.app.data.model.FosterRequest
import com.comunidapp.app.data.model.PaymentStatus
import com.comunidapp.app.data.model.ServiceBooking
import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.data.model.ServiceProfile
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.canonical.CanonicalProviderHolder
import com.comunidapp.app.domain.canonical.CanonicalProviderWrite
import com.comunidapp.app.domain.context.OperationalContextProvider
import com.comunidapp.app.domain.ux.CanonicalUiErrorMapper
import com.comunidapp.app.domain.schedule.ProviderWeeklySchedule
import com.comunidapp.app.domain.schedule.WeeklyHoursDay
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class CanonicalHoursRow(
    val weekday: Int,
    val closed: Boolean = true,
    @SerialName("opens_at") val opensAt: String? = null,
    @SerialName("closes_at") val closesAt: String? = null
)

@Serializable
private data class CanonicalProviderListRow(
    val id: String,
    @SerialName("holder_kind") val holderKind: String? = null,
    @SerialName("holder_person_id") val holderPersonId: String? = null,
    @SerialName("holder_organization_id") val holderOrganizationId: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val categories: List<String> = emptyList(),
    @SerialName("locality_ids") val localityIds: List<String> = emptyList(),
    @SerialName("province_ids") val provinceIds: List<String> = emptyList(),
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("geo_is_public_premises") val geoIsPublicPremises: Boolean = false,
    @SerialName("distance_m") val distanceM: Double? = null,
    val hours: List<CanonicalHoursRow> = emptyList()
)

class CanonicalServiceRepository : ServiceRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val all = MutableStateFlow<List<ServiceProfile>>(emptyList())

    init {
        scope.launch { refreshDirectory() }
    }

    override fun observeServices(category: ServiceCategory?): StateFlow<List<ServiceProfile>> {
        if (category == null) return all
        return all
            .map { list -> list.filter { it.category == category } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    override fun getServiceById(id: String): ServiceProfile? = all.value.firstOrNull { it.id == id }

    override suspend fun fetchMyServiceProfile(ownerId: String): ServiceProfile? {
        runCatching { refreshDirectory() }
        val orgIds = runCatching {
            DataProvider.organizationRepository.getMyOrganizations().map { it.id.value }.toSet()
        }.getOrDefault(emptySet())
        val category = CanonicalProviderWrite.categoryFromContext(OperationalContextProvider.active.value)
            ?: return null
        val holder = CanonicalProviderWrite.resolveHolder(
            userId = ownerId,
            context = OperationalContextProvider.active.value,
            myOrganizationIds = orgIds
        )
        val mine = all.value.filter { profile ->
            profile.category == category && when (holder.kind) {
                CanonicalProviderHolder.ORGANIZATION ->
                    profile.ownerId == holder.organizationId
                else ->
                    profile.ownerId == ownerId
            }
        }
        return mine.firstOrNull()
    }

    override suspend fun upsertServiceProfile(profile: ServiceProfile): Result<String> =
        runCatching {
            if (profile.name.isBlank()) {
                error(CanonicalProviderWrite.INCOMPLETE_SETUP_MESSAGE)
            }
            val userId = AuthProvider.repository.getCurrentUser()?.id.orEmpty()
            if (userId.isBlank()) {
                error(CanonicalProviderWrite.INCOMPLETE_SETUP_MESSAGE)
            }
            val myOrgIds = runCatching {
                DataProvider.organizationRepository.getMyOrganizations().map { it.id.value }.toSet()
            }.getOrDefault(emptySet())
            val holder = CanonicalProviderWrite.resolveHolder(
                userId = userId,
                context = OperationalContextProvider.active.value,
                myOrganizationIds = myOrgIds
            )
            val category = CanonicalProviderWrite.storageCategory(profile.category)
            val localityId = profile.localityId?.takeIf { it.isNotBlank() && !it.contains(' ') }
            val id: String = supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_UPSERT_PROVIDER,
                parameters = buildJsonObject {
                    put("p_kind", holder.kind)
                    if (holder.personId != null) put("p_person", holder.personId) else put("p_person", JsonNull)
                    if (holder.organizationId != null) {
                        put("p_org", holder.organizationId)
                    } else {
                        put("p_org", JsonNull)
                    }
                    put("p_name", profile.name.trim())
                    put("p_category", category)
                    if (localityId != null) put("p_locality_id", localityId) else put("p_locality_id", JsonNull)
                }
            ).decodeAs()
            runCatching { refreshDirectory() }
            val uid = AuthProvider.repository.getCurrentUser()?.id
            if (!uid.isNullOrBlank()) {
                runCatching { OperationalContextProvider.refresh(uid) }
                val active = OperationalContextProvider.active.value
                val created = when (active) {
                    is com.comunidapp.app.domain.context.OperationalContext.Provider ->
                        active.copy(entityId = id, displayName = profile.name)
                    is com.comunidapp.app.domain.context.OperationalContext.Veterinary ->
                        active.copy(displayName = profile.name.ifBlank { active.displayName })
                    is com.comunidapp.app.domain.context.OperationalContext.Shop ->
                        active.copy(displayName = profile.name.ifBlank { active.displayName })
                    is com.comunidapp.app.domain.context.OperationalContext.Organization ->
                        active.copy(displayName = profile.name.ifBlank { active.displayName })
                    else -> com.comunidapp.app.domain.context.OperationalContext.Provider(
                        entityId = id,
                        displayName = profile.name,
                        category = CanonicalProviderWrite.storageCategory(profile.category)
                    )
                }
                runCatching { OperationalContextProvider.activateNewlyCreated(created) }
            }
            id
        }.recoverCatching { error ->
            throw IllegalStateException(
                CanonicalUiErrorMapper.userMessage(
                    error,
                    CanonicalProviderWrite.INCOMPLETE_SETUP_MESSAGE
                )
            )
        }

    override suspend fun createBooking(client: User, booking: ServiceBooking): Result<String> =
        Result.failure(IllegalStateException("CANONICAL_BOOKING_NOT_FROM_DIRECTORY"))

    override fun observeProviderBookings(providerId: String): StateFlow<List<ServiceBooking>> =
        MutableStateFlow(emptyList())

    override suspend fun fetchClientBookings(clientId: String): List<ServiceBooking> = emptyList()

    override suspend fun updateBookingStatus(
        bookingId: String,
        status: BookingStatus,
        paymentStatus: PaymentStatus?
    ): Result<Unit> = Result.failure(IllegalStateException("CANONICAL_BOOKING_NOT_FROM_DIRECTORY"))

    override suspend fun createFosterRequest(request: FosterRequest): Result<String> =
        Result.failure(IllegalStateException("CANONICAL_FOSTER_NOT_FROM_DIRECTORY"))

    override suspend fun expressEventInterest(eventId: String, userId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun refreshDirectory(
        nearLat: Double?,
        nearLng: Double?
    ): Result<Unit> = runCatching {
        val element: JsonElement = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_LIST_PROVIDERS,
            parameters = buildJsonObject {
                if (nearLat != null) put("p_lat", nearLat) else put("p_lat", JsonNull)
                if (nearLng != null) put("p_lng", nearLng) else put("p_lng", JsonNull)
            }
        ).decodeAs()
        val rows = M08RpcDecoding.decodeRows<CanonicalProviderListRow>(element)
        all.value = rows.flatMap { row ->
            val categories = row.categories.mapNotNull { CanonicalProviderWrite.fromStorageCategory(it) }
            if (categories.isEmpty()) return@flatMap emptyList()
            categories.map { category ->
                ServiceProfile(
                    id = if (categories.size == 1) row.id else "${row.id}:$category",
                    ownerId = row.holderPersonId ?: row.holderOrganizationId.orEmpty(),
                    category = category,
                    name = row.displayName.orEmpty(),
                    location = "",
                    provinceId = row.provinceIds.firstOrNull(),
                    localityId = row.localityIds.firstOrNull(),
                    localityIds = row.localityIds,
                    latitude = row.lat,
                    longitude = row.lng,
                    geoIsPublicPremises = row.geoIsPublicPremises,
                    distanceKm = row.distanceM?.let { it / 1000.0 },
                    weeklyHours = row.hours.map {
                        WeeklyHoursDay(
                            weekday = ProviderWeeklySchedule.normalizeIsoWeekday(it.weekday),
                            closed = it.closed,
                            opensAt = it.opensAt.toHm(),
                            closesAt = it.closesAt.toHm()
                        )
                    }
                )
            }
        }
    }

    suspend fun saveWeeklyHours(providerId: String, days: List<WeeklyHoursDay>): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_SET_PROVIDER_WEEKLY_HOURS,
            parameters = buildJsonObject {
                put("p_provider", providerId)
                put("p_days", kotlinx.serialization.json.buildJsonArray {
                    days.forEach { day ->
                        add(
                            buildJsonObject {
                                put("weekday", day.weekday)
                                put("closed", day.closed)
                                if (!day.closed && !day.opensAt.isNullOrBlank()) put("opens_at", day.opensAt)
                                else put("opens_at", JsonNull)
                                if (!day.closed && !day.closesAt.isNullOrBlank()) put("closes_at", day.closesAt)
                                else put("closes_at", JsonNull)
                            }
                        )
                    }
                })
            }
        )
        Unit
    }

    suspend fun savePublicGeo(
        providerId: String,
        lat: Double,
        lng: Double,
        publicPremises: Boolean,
        address: String? = null
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_SET_PROVIDER_PUBLIC_GEO,
            parameters = buildJsonObject {
                put("p_provider", providerId)
                put("p_lat", lat)
                put("p_lng", lng)
                put("p_public_premises", publicPremises)
                if (address.isNullOrBlank()) put("p_address", JsonNull) else put("p_address", address)
            }
        )
        Unit
    }
}

private fun String?.toHm(): String? {
    val raw = this?.trim().orEmpty()
    if (raw.isBlank()) return null
    return raw.take(5)
}

fun ServiceCategory.Companion.fromCanonicalCode(code: String): ServiceCategory? =
    CanonicalProviderWrite.fromStorageCategory(code)
