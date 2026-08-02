package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM22ProviderInput
import com.comunidapp.app.data.model.M22BranchStatus
import com.comunidapp.app.data.model.M22CoverageArea
import com.comunidapp.app.data.model.M22CoverageType
import com.comunidapp.app.data.model.M22MockProviderIds
import com.comunidapp.app.data.model.M22MockUsers
import com.comunidapp.app.data.model.M22ProviderBranch
import com.comunidapp.app.data.model.M22ProviderCategory
import com.comunidapp.app.data.model.M22ProviderProfile
import com.comunidapp.app.data.model.M22ProviderStatus
import com.comunidapp.app.data.model.M22PriceType
import com.comunidapp.app.data.model.M22PublicProviderDetail
import com.comunidapp.app.data.model.M22PublicProviderListing
import com.comunidapp.app.data.model.M22ServiceOffering
import com.comunidapp.app.data.model.UpdateM22ProviderInput
import com.comunidapp.app.data.model.UpsertM22BranchInput
import com.comunidapp.app.data.model.UpsertM22OfferingInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface M22ProviderRepository {
    fun observeCatalog(category: M22ProviderCategory? = null): Flow<List<M22PublicProviderListing>>
    fun observeProviderDetail(providerId: String): Flow<M22PublicProviderDetail?>
    fun observeMyProviders(): Flow<List<M22ProviderProfile>>
    suspend fun createProvider(input: CreateM22ProviderInput): Result<M22ProviderProfile>
    suspend fun updateProvider(input: UpdateM22ProviderInput): Result<M22ProviderProfile>
    suspend fun upsertBranch(input: UpsertM22BranchInput): Result<M22ProviderBranch>
    suspend fun upsertOffering(input: UpsertM22OfferingInput): Result<M22ServiceOffering>
    suspend fun archiveProvider(providerId: String): Result<Unit>
}

class M22ProviderMemoryStore {
    private val mutex = Mutex()
    private var sequence = 0
    val providers = MutableStateFlow<List<M22ProviderProfile>>(emptyList())
    val branches = MutableStateFlow<List<M22ProviderBranch>>(emptyList())
    val offerings = MutableStateFlow<List<M22ServiceOffering>>(emptyList())

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
    fun nextId(prefix: String): String = "${prefix}_${++sequence}"

    fun seedDefaults() {
        if (providers.value.isNotEmpty()) return
        val stamp = 1_700_000_000_000L
        providers.value = listOf(
            profile(M22MockProviderIds.ACTIVE_MULTI_BRANCH, M22MockUsers.PROVIDER, "Patitas Centro", M22ProviderCategory.GROOMING, "Baño y peluquería para perros y gatos.", "CABA", M22ProviderStatus.ACTIVE, stamp),
            profile(M22MockProviderIds.DRAFT, M22MockUsers.PROVIDER, "Paseos Norte", M22ProviderCategory.WALKING, "Paseos personalizados para tu mascota.", "Vicente López", M22ProviderStatus.DRAFT, stamp),
            profile(M22MockProviderIds.SUSPENDED, M22MockUsers.OTHER_PROVIDER, "Traslados Mascota", M22ProviderCategory.TRANSPORT, "Traslados coordinados en la ciudad.", "La Plata", M22ProviderStatus.SUSPENDED, stamp),
            profile(M22MockProviderIds.EMPTY_OFFERINGS, M22MockUsers.PROVIDER, "Entrená Juntos", M22ProviderCategory.TRAINING, "Educación canina con refuerzo positivo.", "CABA", M22ProviderStatus.ACTIVE, stamp),
            profile("m22_provider_vet", M22MockUsers.OTHER_PROVIDER, "Clínica Animal Sur", M22ProviderCategory.VET, "Atención clínica general.", "Avellaneda", M22ProviderStatus.ACTIVE, stamp),
            profile("m22_provider_boarding", M22MockUsers.OTHER_PROVIDER, "Hotel Huellitas", M22ProviderCategory.BOARDING, "Hospedaje con cuidado diario.", "CABA", M22ProviderStatus.ACTIVE, stamp)
        )
        branches.value = listOf(
            branch("m22_branch_centro", M22MockProviderIds.ACTIVE_MULTI_BRANCH, "Sede Centro", "CABA", "Balvanera", M22CoverageArea(M22CoverageType.NEIGHBORHOOD, "CABA", "Balvanera")),
            branch("m22_branch_norte", M22MockProviderIds.ACTIVE_MULTI_BRANCH, "Sede Norte", "CABA", null, M22CoverageArea(M22CoverageType.RADIUS, "CABA", radiusKm = 8)),
            branch("m22_branch_draft", M22MockProviderIds.DRAFT, "Paseos Norte", "Vicente López", null, M22CoverageArea(M22CoverageType.CITY, "Vicente López")),
            branch("m22_branch_vet", "m22_provider_vet", "Consultorio Sur", "Avellaneda", null, M22CoverageArea(M22CoverageType.CITY, "Avellaneda")),
            branch("m22_branch_board", "m22_provider_boarding", "Hotel principal", "CABA", "Villa Urquiza", M22CoverageArea(M22CoverageType.NEIGHBORHOOD, "CABA", "Villa Urquiza"))
        )
        offerings.value = listOf(
            offering("m22_offer_fixed", M22MockProviderIds.ACTIVE_MULTI_BRANCH, "m22_branch_centro", "Baño completo", "Baño, secado y cuidado básico.", M22PriceType.FIXED, 18000),
            offering("m22_offer_from", M22MockProviderIds.ACTIVE_MULTI_BRANCH, "m22_branch_norte", "Peluquería", "Servicio según tamaño y tipo de manto.", M22PriceType.FROM, 22000),
            offering("m22_offer_quote", M22MockProviderIds.ACTIVE_MULTI_BRANCH, null, "Spa personalizado", "Propuesta según necesidades de la mascota.", M22PriceType.QUOTE, null),
            offering("m22_offer_walk", M22MockProviderIds.DRAFT, null, "Paseo individual", "Paseo de una hora con seguimiento.", M22PriceType.FIXED, 12000),
            offering("m22_offer_vet", "m22_provider_vet", "m22_branch_vet", "Consulta clínica", "Consulta general para perros y gatos.", M22PriceType.FIXED, 25000),
            offering("m22_offer_board", "m22_provider_boarding", "m22_branch_board", "Hospedaje diario", "Cuidado y alojamiento por día.", M22PriceType.FROM, 30000)
        )
    }

    private fun profile(id: String, owner: String, name: String, category: M22ProviderCategory, description: String, city: String, status: M22ProviderStatus, stamp: Long) =
        M22ProviderProfile(id, owner, if (id == M22MockProviderIds.ACTIVE_MULTI_BRANCH) "mock_org_m03" else null, name, category, description, city, status, stamp, stamp)
    private fun branch(id: String, providerId: String, name: String, city: String, neighborhood: String?, coverage: M22CoverageArea) =
        M22ProviderBranch(id, providerId, name, city, neighborhood, coverage)
    private fun offering(id: String, providerId: String, branchId: String?, name: String, description: String, priceType: M22PriceType, amount: Long?) =
        M22ServiceOffering(id, providerId, branchId, name, description, priceType, amount)
}

class MockM22ProviderRepository(
    private val actorUserId: () -> String?,
    private val store: M22ProviderMemoryStore = M22ProviderMemoryStore()
) : M22ProviderRepository {
    init { store.seedDefaults() }

    override fun observeCatalog(category: M22ProviderCategory?): Flow<List<M22PublicProviderListing>> =
        store.providers.map { providers ->
            providers.filter { it.status == M22ProviderStatus.ACTIVE && (category == null || it.category == category) }
                .map { it.toPublicListing(branchesFor(it.id), offeringsFor(it.id)) }
        }

    override fun observeProviderDetail(providerId: String): Flow<M22PublicProviderDetail?> =
        store.providers.map { providers ->
            providers.firstOrNull {
                (it.id == providerId || it.displayName == providerId) && it.status == M22ProviderStatus.ACTIVE
            }?.let { provider ->
                provider.toPublicDetail(branchesFor(provider.id), offeringsFor(provider.id))
            }
        }

    override fun observeMyProviders(): Flow<List<M22ProviderProfile>> = store.providers.map { providers ->
        val actor = actorUserId() ?: return@map emptyList()
        providers.filter { it.ownerUserId == actor }
    }

    override suspend fun createProvider(input: CreateM22ProviderInput): Result<M22ProviderProfile> = mutate {
        val actor = requireActor()
        M22ProviderValidators.validateProvider(input.displayName, input.description, input.city)?.let(::fail)
        val now = System.currentTimeMillis()
        M22ProviderProfile(store.nextId("m22_provider"), actor, input.organizationId, input.displayName.trim(), input.category, input.description.trim(), input.city.trim(), M22ProviderStatus.DRAFT, now, now)
            .also { store.providers.value += it }
    }

    override suspend fun updateProvider(input: UpdateM22ProviderInput): Result<M22ProviderProfile> = mutate {
        val provider = owned(input.providerId)
        val updated = provider.copy(
            displayName = input.displayName?.trim() ?: provider.displayName,
            description = input.description?.trim() ?: provider.description,
            city = input.city?.trim() ?: provider.city,
            status = input.status ?: provider.status,
            updatedAt = System.currentTimeMillis()
        )
        M22ProviderValidators.validateProvider(updated.displayName, updated.description, updated.city)?.let(::fail)
        store.providers.value = store.providers.value.map { if (it.id == updated.id) updated else it }
        updated
    }

    override suspend fun upsertBranch(input: UpsertM22BranchInput): Result<M22ProviderBranch> = mutate {
        owned(input.providerId)
        M22ProviderValidators.validateBranch(input.name, input.city, input.coverage)?.let(::fail)
        input.branchId?.let { id ->
            val old = store.branches.value.firstOrNull { it.id == id && it.providerId == input.providerId } ?: fail("M22_BRANCH_NOT_FOUND")
            old.copy(name = input.name.trim(), city = input.city.trim(), neighborhood = input.neighborhood?.trim(), coverage = input.coverage, status = input.status)
        } ?: M22ProviderBranch(store.nextId("m22_branch"), input.providerId, input.name.trim(), input.city.trim(), input.neighborhood?.trim(), input.coverage, input.status)
    }.also { result ->
        result.getOrNull()?.let { branch ->
            store.branches.value = store.branches.value.filterNot { it.id == branch.id } + branch
        }
    }

    override suspend fun upsertOffering(input: UpsertM22OfferingInput): Result<M22ServiceOffering> = mutate {
        owned(input.providerId)
        if (input.branchId != null && store.branches.value.none { it.id == input.branchId && it.providerId == input.providerId }) fail("M22_BRANCH_NOT_FOUND")
        M22ProviderValidators.validateOffering(input.name, input.description, input.priceType, input.priceAmount)?.let(::fail)
        input.offeringId?.let { id ->
            val old = store.offerings.value.firstOrNull { it.id == id && it.providerId == input.providerId } ?: fail("M22_OFFERING_NOT_FOUND")
            old.copy(branchId = input.branchId, name = input.name.trim(), description = input.description.trim(), priceType = input.priceType, priceAmount = input.priceAmount, currency = input.currency, active = input.active)
        } ?: M22ServiceOffering(store.nextId("m22_offering"), input.providerId, input.branchId, input.name.trim(), input.description.trim(), input.priceType, input.priceAmount, input.currency, input.active)
    }.also { result ->
        result.getOrNull()?.let { offering ->
            store.offerings.value = store.offerings.value.filterNot { it.id == offering.id } + offering
        }
    }

    override suspend fun archiveProvider(providerId: String): Result<Unit> = mutate {
        val provider = owned(providerId)
        if (provider.status != M22ProviderStatus.ARCHIVED) {
            store.providers.value = store.providers.value.map {
                if (it.id == providerId) it.copy(status = M22ProviderStatus.ARCHIVED, updatedAt = System.currentTimeMillis()) else it
            }
        }
        Unit
    }

    private fun branchesFor(providerId: String) = store.branches.value.filter { it.providerId == providerId }
    private fun offeringsFor(providerId: String) = store.offerings.value.filter { it.providerId == providerId }
    private fun requireActor(): String = actorUserId() ?: fail("NOT_AUTHENTICATED")
    private fun owned(providerId: String): M22ProviderProfile {
        val actor = requireActor()
        val provider = store.providers.value.firstOrNull { it.id == providerId } ?: fail("M22_PROVIDER_NOT_FOUND")
        if (provider.ownerUserId != actor) fail("M22_PERMISSION_DENIED")
        return provider
    }
    private suspend fun <T> mutate(block: suspend () -> T): Result<T> = store.withLock {
        try {
            Result.success(block())
        } catch (error: Throwable) {
            M22ProviderErrors.failure(error)
        }
    }
    private fun fail(code: String): Nothing = throw M22ProviderException(code, M22ProviderErrors.userMessage(code))
}
