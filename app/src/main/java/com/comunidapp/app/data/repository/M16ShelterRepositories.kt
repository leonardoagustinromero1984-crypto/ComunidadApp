package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM16ShelterProfileInput
import com.comunidapp.app.data.model.M16OpeningHours
import com.comunidapp.app.data.model.M16OpeningPeriod
import com.comunidapp.app.data.model.M16PublicContactChannel
import com.comunidapp.app.data.model.M16PublicShelter
import com.comunidapp.app.data.model.M16ShelterNeed
import com.comunidapp.app.data.model.M16ShelterOperationalStatus
import com.comunidapp.app.data.model.M16ShelterProfile
import com.comunidapp.app.data.model.M16ShelterPublicationStatus
import com.comunidapp.app.data.model.M16ShelterSearchFilter
import com.comunidapp.app.data.model.M16ShelterService
import com.comunidapp.app.data.model.M16ShelterVerificationStatus
import com.comunidapp.app.data.model.M16ShelterCapacity
import com.comunidapp.app.data.model.M16MockOrganizations
import com.comunidapp.app.data.model.M16ShelterVerificationFilter
import com.comunidapp.app.data.model.M16PrivacySanitizer
import com.comunidapp.app.data.model.M16_ELIGIBLE_ORGANIZATION_TYPES
import com.comunidapp.app.data.model.UpdateM16ShelterPublicInput
import com.comunidapp.app.data.remote.supabase.m16.M16Exception
import com.comunidapp.app.data.remote.supabase.m16.M16ShelterErrorMapper
import com.comunidapp.app.domain.organization.OrganizationType
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** LeoVer M16 — store + contratos + mock (Bloque 1, sin red). */

class M16MemoryStore {
    private val idSeq = AtomicLong(0)
    private val mutex = Mutex()
    private val _profiles = MutableStateFlow<List<M16ShelterProfile>>(emptyList())
    private val idempotentRetries = AtomicInteger(0)
    private val conflicts = AtomicInteger(0)

    /** orgId → OrganizationType (mock M03). */
    val organizationTypes = MutableStateFlow<Map<String, OrganizationType>>(emptyMap())
    /** orgId → manager userIds. */
    val organizationManagers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    var m06InfrastructureAvailable: Boolean = false
    var seeded: Boolean = false

    val profiles: StateFlow<List<M16ShelterProfile>> = _profiles.asStateFlow()

    fun nextId(prefix: String): String = "${prefix}_${idSeq.incrementAndGet()}"

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    fun upsert(profile: M16ShelterProfile) {
        _profiles.update { list ->
            val without = list.filterNot { it.id == profile.id }
            (without + profile).sortedByDescending { it.updatedAt }
        }
    }

    fun recordIdempotentRetry() {
        idempotentRetries.incrementAndGet()
    }

    fun recordConflict() {
        conflicts.incrementAndGet()
    }

    fun idempotentRetryCount(): Int = idempotentRetries.get()

    fun seedDefaults(actorUserId: String = "mock_user_admin") {
        if (seeded) return
        seeded = true
        val now = System.currentTimeMillis()
        organizationTypes.value = mapOf(
            M16MockOrganizations.ORG_NORTE to OrganizationType.SHELTER,
            M16MockOrganizations.ORG_SUR to OrganizationType.RESCUE_GROUP,
            M16MockOrganizations.ORG_OESTE to OrganizationType.NGO,
            "org_clinica_demo" to OrganizationType.VETERINARY_CLINIC
        )
        organizationManagers.value = mapOf(
            M16MockOrganizations.ORG_NORTE to setOf(actorUserId),
            M16MockOrganizations.ORG_SUR to setOf(actorUserId),
            M16MockOrganizations.ORG_OESTE to setOf(actorUserId)
        )
        val defaultHours = M16OpeningHours(
            periods = (1..5).map { day ->
                M16OpeningPeriod(dayOfWeek = day, openTime = "09:00", closeTime = "18:00")
            } + listOf(
                M16OpeningPeriod(dayOfWeek = 6, openTime = "10:00", closeTime = "14:00"),
                M16OpeningPeriod(dayOfWeek = 7, closed = true)
            )
        )
        val samples = listOf(
            M16ShelterProfile(
                id = nextId("m16_shelter"),
                organizationId = M16MockOrganizations.ORG_NORTE,
                displayName = "Refugio Comunitario Norte",
                description = "Adopciones responsables y tránsito coordinado.",
                operationalStatus = M16ShelterOperationalStatus.ACTIVE,
                publicationStatus = M16ShelterPublicationStatus.PUBLISHED,
                verificationStatus = M16ShelterVerificationStatus.VERIFIED,
                publicZoneText = "Zona norte · CABA",
                coverageAreas = setOf("CABA", "Zona norte GBA"),
                openingHours = defaultHours,
                acceptedSpecies = setOf("DOG", "CAT"),
                services = setOf(
                    M16ShelterService.ADOPTIONS,
                    M16ShelterService.TEMPORARY_SHELTER,
                    M16ShelterService.VOLUNTEERING
                ),
                publicContacts = listOf(
                    M16PublicContactChannel(
                        type = com.comunidapp.app.data.model.M16PublicContactChannelType.INSTITUTIONAL_EMAIL,
                        value = "contacto@refugio-demo.local"
                    ),
                    M16PublicContactChannel(
                        type = com.comunidapp.app.data.model.M16PublicContactChannelType.WEBSITE,
                        value = "https://refugio-demo.local"
                    )
                ),
                capacity = M16ShelterCapacity(totalCapacity = 40, currentOccupancy = 28, reservedCount = 2),
                needs = listOf(
                    M16ShelterNeed(category = "FOOD", description = "Alimento balanceado perros adultos"),
                    M16ShelterNeed(category = "HYGIENE", description = "Mantitas y toallas")
                ),
                createdAt = now - 86_400_000L,
                updatedAt = now
            ),
            M16ShelterProfile(
                id = nextId("m16_shelter"),
                organizationId = M16MockOrganizations.ORG_SUR,
                displayName = "Rescate Sur",
                description = "Rescate y rehabilitación.",
                operationalStatus = M16ShelterOperationalStatus.PAUSED,
                publicationStatus = M16ShelterPublicationStatus.PUBLISHED,
                verificationStatus = M16ShelterVerificationStatus.UNVERIFIED,
                publicZoneText = "Zona sur · GBA",
                openingHours = defaultHours,
                acceptedSpecies = setOf("DOG"),
                services = setOf(M16ShelterService.RESCUE, M16ShelterService.REHABILITATION),
                capacity = M16ShelterCapacity(totalCapacity = 20, currentOccupancy = 5),
                createdAt = now - 172_800_000L,
                updatedAt = now
            ),
            M16ShelterProfile(
                id = nextId("m16_shelter"),
                organizationId = M16MockOrganizations.ORG_LEGACY,
                displayName = "Refugio Histórico",
                description = "Perfil cerrado permanentemente.",
                operationalStatus = M16ShelterOperationalStatus.PERMANENTLY_CLOSED,
                publicationStatus = M16ShelterPublicationStatus.PUBLISHED,
                verificationStatus = M16ShelterVerificationStatus.SUSPENDED,
                publicZoneText = "Zona oeste",
                capacity = M16ShelterCapacity(totalCapacity = 10, currentOccupancy = 0),
                createdAt = now - 500_000_000L,
                updatedAt = now - 100_000_000L
            )
        )
        _profiles.value = samples
    }
}

interface M16ShelterRepository {
    fun observePublicShelters(): Flow<List<M16PublicShelter>>
    fun observeProfile(shelterId: String): Flow<M16ShelterProfile?>
    fun observeProfileByOrganization(organizationId: String): Flow<M16ShelterProfile?>
    suspend fun getProfileById(id: String): Result<M16ShelterProfile>
    suspend fun getPublicById(id: String): Result<M16PublicShelter>
    suspend fun createProfile(input: CreateM16ShelterProfileInput): Result<M16ShelterProfile>
    suspend fun updatePublicData(input: UpdateM16ShelterPublicInput): Result<M16ShelterProfile>
    suspend fun updateOperationalStatus(
        shelterId: String,
        status: M16ShelterOperationalStatus
    ): Result<M16ShelterProfile>
    suspend fun updatePublicationStatus(
        shelterId: String,
        status: M16ShelterPublicationStatus
    ): Result<M16ShelterProfile>
    suspend fun requestVerification(shelterId: String): Result<M16ShelterProfile>
    suspend fun updateOpeningHours(shelterId: String, hours: M16OpeningHours): Result<M16ShelterProfile>
    suspend fun updateServices(
        shelterId: String,
        services: Set<M16ShelterService>
    ): Result<M16ShelterProfile>
    suspend fun updateNeeds(shelterId: String, needs: List<M16ShelterNeed>): Result<M16ShelterProfile>
    suspend fun updateCapacity(shelterId: String, capacity: M16ShelterCapacity): Result<M16ShelterProfile>
    suspend fun updatePublicContacts(
        shelterId: String,
        contacts: List<M16PublicContactChannel>
    ): Result<M16ShelterProfile>
    suspend fun searchPublic(filter: M16ShelterSearchFilter): Result<List<M16PublicShelter>>
    suspend fun canManageOrganization(organizationId: String): Boolean
    suspend fun isOrganizationEligible(organizationId: String): Boolean
}

interface M16ShelterAuthorityPolicy {
    fun canManageShelter(actorUserId: String, organizationId: String, store: M16MemoryStore): Boolean
    fun isOrganizationEligible(organizationId: String, store: M16MemoryStore): Boolean
}

class MockM16ShelterAuthorityPolicy : M16ShelterAuthorityPolicy {
    override fun canManageShelter(
        actorUserId: String,
        organizationId: String,
        store: M16MemoryStore
    ): Boolean = store.organizationManagers.value[organizationId]?.contains(actorUserId) == true

    override fun isOrganizationEligible(organizationId: String, store: M16MemoryStore): Boolean {
        val type = store.organizationTypes.value[organizationId] ?: return false
        return type in M16_ELIGIBLE_ORGANIZATION_TYPES
    }
}

private fun failM16(code: String): Nothing =
    throw M16Exception(code, M16ShelterErrorMapper.userMessage(code))

private fun matchesPublicVisibility(
    profile: M16ShelterProfile,
    filter: M16ShelterSearchFilter
): Boolean {
    if (profile.publicationStatus != M16ShelterPublicationStatus.PUBLISHED) return false
    val statusFilter = filter.operationalStatus
    return when {
        statusFilter != null -> profile.operationalStatus == statusFilter
        else -> profile.operationalStatus != M16ShelterOperationalStatus.PERMANENTLY_CLOSED
    }
}

private fun matchesVerificationFilter(
    profile: M16ShelterProfile,
    filter: M16ShelterSearchFilter
): Boolean {
    val effective = when {
        filter.verificationFilter != M16ShelterVerificationFilter.ALL ->
            filter.verificationFilter
        filter.verifiedOnly -> M16ShelterVerificationFilter.VERIFIED_ONLY
        else -> M16ShelterVerificationFilter.ALL
    }
    return when (effective) {
        M16ShelterVerificationFilter.ALL -> true
        M16ShelterVerificationFilter.VERIFIED_ONLY ->
            profile.verificationStatus == M16ShelterVerificationStatus.VERIFIED
        M16ShelterVerificationFilter.UNVERIFIED_OR_PENDING ->
            profile.verificationStatus == M16ShelterVerificationStatus.UNVERIFIED ||
                profile.verificationStatus == M16ShelterVerificationStatus.PENDING
    }
}

private fun applyPublicSearchFilters(
    profiles: List<M16ShelterProfile>,
    filter: M16ShelterSearchFilter
): List<M16ShelterProfile> {
    val q = filter.query.trim().lowercase()
    return profiles
        .filter { matchesPublicVisibility(it, filter) }
        .filter { matchesVerificationFilter(it, filter) }
        .filter { profile ->
            filter.species?.let { sp ->
                profile.acceptedSpecies.isEmpty() || sp.uppercase() in profile.acceptedSpecies
            } ?: true
        }
        .filter { profile ->
            filter.service?.let { profile.services.contains(it) } ?: true
        }
        .filter { profile ->
            if (q.isEmpty()) true
            else {
                profile.displayName.lowercase().contains(q) ||
                    profile.publicZoneText.lowercase().contains(q) ||
                    profile.description.orEmpty().lowercase().contains(q)
            }
        }
}

class MockM16ShelterRepository(
    private val actorUserId: () -> String?,
    private val store: M16MemoryStore,
    private val authority: M16ShelterAuthorityPolicy = MockM16ShelterAuthorityPolicy()
) : M16ShelterRepository {

    init {
        store.seedDefaults(actorUserId().orEmpty().ifBlank { "mock_user_admin" })
    }

    override fun observePublicShelters(): Flow<List<M16PublicShelter>> =
        store.profiles.map { list ->
            applyPublicSearchFilters(list, M16ShelterSearchFilter())
                .map { it.toPublicShelter() }
        }

    override fun observeProfile(shelterId: String): Flow<M16ShelterProfile?> =
        store.profiles.map { list -> list.find { it.id == shelterId } }

    override fun observeProfileByOrganization(organizationId: String): Flow<M16ShelterProfile?> =
        store.profiles.map { list -> list.find { it.organizationId == organizationId } }

    override suspend fun getProfileById(id: String): Result<M16ShelterProfile> = runCatching {
        if (id.isBlank()) failM16("M16_SHELTER_NOT_FOUND")
        store.profiles.value.find { it.id == id } ?: failM16("M16_SHELTER_NOT_FOUND")
    }.fold({ Result.success(it) }, { M16ShelterErrorMapper.failure(it) })

    override suspend fun getPublicById(id: String): Result<M16PublicShelter> =
        getProfileById(id).fold(
            onSuccess = { profile ->
                if (profile.publicationStatus != M16ShelterPublicationStatus.PUBLISHED) {
                    M16ShelterErrorMapper.fail("M16_SHELTER_NOT_FOUND")
                } else {
                    Result.success(profile.toPublicShelter())
                }
            },
            onFailure = { M16ShelterErrorMapper.failure(it) }
        )

    override suspend fun createProfile(input: CreateM16ShelterProfileInput): Result<M16ShelterProfile> =
        store.withLock {
            runCatching {
                val actor = actorUserId() ?: failM16("NOT_AUTHENTICATED")
                M16ShelterValidators.validateCreate(input)?.let { failM16(it) }
                if (!authority.isOrganizationEligible(input.organizationId, store)) {
                    failM16("M16_ORGANIZATION_NOT_ELIGIBLE")
                }
                if (!authority.canManageShelter(actor, input.organizationId, store)) {
                    failM16("M16_PERMISSION_DENIED")
                }
                val exists = store.profiles.value.any { it.organizationId == input.organizationId }
                if (exists) {
                    store.recordIdempotentRetry()
                    return@runCatching store.profiles.value.first { it.organizationId == input.organizationId }
                }
                val now = System.currentTimeMillis()
                val publication = if (input.publish) {
                    M16ShelterPublicationStatus.PUBLISHED
                } else {
                    M16ShelterPublicationStatus.DRAFT
                }
                val profile = M16ShelterProfile(
                    id = store.nextId("m16_shelter"),
                    organizationId = input.organizationId,
                    displayName = input.displayName.trim(),
                    description = input.description?.trim(),
                    publicationStatus = publication,
                    publicZoneText = input.publicZoneText.trim(),
                    acceptedSpecies = input.acceptedSpecies.map { it.uppercase() }.toSet(),
                    services = input.services,
                    capacity = M16ShelterCapacity(totalCapacity = input.totalCapacity),
                    createdAt = now,
                    updatedAt = now
                )
                store.upsert(profile)
                profile
            }.fold({ Result.success(it) }, { M16ShelterErrorMapper.failure(it) })
        }

    override suspend fun updatePublicData(input: UpdateM16ShelterPublicInput): Result<M16ShelterProfile> =
        mutateProfile(input.shelterId) { profile ->
            M16ShelterValidators.validateUpdatePublic(input)?.let { failM16(it) }
            profile.copy(
                displayName = input.displayName.trim(),
                description = input.description?.trim(),
                publicZoneText = input.publicZoneText.trim(),
                coverageAreas = input.coverageAreas,
                acceptedSpecies = input.acceptedSpecies.map { it.uppercase() }.toSet(),
                publicImageRef = input.publicImageRef
            )
        }

    override suspend fun updateOperationalStatus(
        shelterId: String,
        status: M16ShelterOperationalStatus
    ): Result<M16ShelterProfile> = mutateProfile(shelterId) { profile ->
        if (profile.operationalStatus == status) {
            store.recordIdempotentRetry()
            return@mutateProfile profile
        }
        if (profile.operationalStatus.isTerminal) {
            store.recordConflict()
            failM16("M16_STATE_ALREADY_FINAL")
        }
        if (!M16ShelterValidators.canTransitionOperational(profile.operationalStatus, status)) {
            failM16("M16_INVALID_STATE_TRANSITION")
        }
        profile.copy(operationalStatus = status)
    }

    override suspend fun updatePublicationStatus(
        shelterId: String,
        status: M16ShelterPublicationStatus
    ): Result<M16ShelterProfile> = mutateProfile(shelterId) { profile ->
        if (profile.publicationStatus == status) {
            store.recordIdempotentRetry()
            return@mutateProfile profile
        }
        if (profile.operationalStatus.isTerminal) failM16("M16_STATE_ALREADY_FINAL")
        profile.copy(publicationStatus = status)
    }

    override suspend fun requestVerification(shelterId: String): Result<M16ShelterProfile> =
        mutateProfile(shelterId) { profile ->
            when (profile.verificationStatus) {
                M16ShelterVerificationStatus.PENDING,
                M16ShelterVerificationStatus.VERIFIED -> {
                    store.recordIdempotentRetry()
                    profile
                }
                M16ShelterVerificationStatus.UNVERIFIED,
                M16ShelterVerificationStatus.REJECTED -> profile.copy(
                    verificationStatus = M16ShelterVerificationStatus.PENDING
                )
                M16ShelterVerificationStatus.SUSPENDED -> failM16("M16_INVALID_STATE_TRANSITION")
            }
        }

    override suspend fun updateOpeningHours(
        shelterId: String,
        hours: M16OpeningHours
    ): Result<M16ShelterProfile> = mutateProfile(shelterId) { profile ->
        M16ShelterValidators.validateOpeningHours(hours)?.let { failM16(it) }
        profile.copy(openingHours = hours)
    }

    override suspend fun updateServices(
        shelterId: String,
        services: Set<M16ShelterService>
    ): Result<M16ShelterProfile> = mutateProfile(shelterId) { profile ->
        profile.copy(services = services)
    }

    override suspend fun updateNeeds(
        shelterId: String,
        needs: List<M16ShelterNeed>
    ): Result<M16ShelterProfile> = mutateProfile(shelterId) { profile ->
        profile.copy(needs = needs)
    }

    override suspend fun updateCapacity(
        shelterId: String,
        capacity: M16ShelterCapacity
    ): Result<M16ShelterProfile> = mutateProfile(shelterId) { profile ->
        M16ShelterValidators.validateCapacityModel(capacity)?.let { failM16(it) }
        profile.copy(capacity = capacity)
    }

    override suspend fun updatePublicContacts(
        shelterId: String,
        contacts: List<M16PublicContactChannel>
    ): Result<M16ShelterProfile> = mutateProfile(shelterId) { profile ->
        M16ShelterValidators.validatePublicContacts(contacts)?.let { failM16(it) }
        profile.copy(publicContacts = contacts)
    }

    override suspend fun searchPublic(filter: M16ShelterSearchFilter): Result<List<M16PublicShelter>> =
        runCatching {
            applyPublicSearchFilters(store.profiles.value, filter).map { it.toPublicShelter() }
        }.fold({ Result.success(it) }, { M16ShelterErrorMapper.failure(it) })

    override suspend fun canManageOrganization(organizationId: String): Boolean {
        val actor = actorUserId() ?: return false
        return authority.canManageShelter(actor, organizationId, store)
    }

    override suspend fun isOrganizationEligible(organizationId: String): Boolean =
        authority.isOrganizationEligible(organizationId, store)

    private suspend fun mutateProfile(
        shelterId: String,
        transform: (M16ShelterProfile) -> M16ShelterProfile
    ): Result<M16ShelterProfile> = store.withLock {
        runCatching {
            val actor = actorUserId() ?: failM16("NOT_AUTHENTICATED")
            val existing = store.profiles.value.find { it.id == shelterId }
                ?: failM16("M16_SHELTER_NOT_FOUND")
            if (!authority.canManageShelter(actor, existing.organizationId, store)) {
                failM16("M16_PERMISSION_DENIED")
            }
            if (existing.operationalStatus.isTerminal &&
                transform(existing).operationalStatus != existing.operationalStatus
            ) {
                store.recordConflict()
                failM16("M16_STATE_ALREADY_FINAL")
            }
            val updated = transform(existing).copy(updatedAt = System.currentTimeMillis())
            if (updated == existing) {
                store.recordIdempotentRetry()
                return@runCatching existing
            }
            store.upsert(updated)
            updated
        }.fold({ Result.success(it) }, { M16ShelterErrorMapper.failure(it) })
    }
}

class SupabaseM16ShelterRepository : M16ShelterRepository {
    override fun observePublicShelters(): Flow<List<M16PublicShelter>> =
        MutableStateFlow(emptyList())

    override fun observeProfile(shelterId: String): Flow<M16ShelterProfile?> =
        MutableStateFlow(null)

    override fun observeProfileByOrganization(organizationId: String): Flow<M16ShelterProfile?> =
        MutableStateFlow(null)

    override suspend fun getProfileById(id: String): Result<M16ShelterProfile> =
        M16ShelterErrorMapper.fail("M16_REMOTE_VALIDATION_PENDING")

    override suspend fun getPublicById(id: String): Result<M16PublicShelter> =
        M16ShelterErrorMapper.fail("M16_REMOTE_VALIDATION_PENDING")

    override suspend fun createProfile(input: CreateM16ShelterProfileInput): Result<M16ShelterProfile> =
        M16ShelterErrorMapper.fail("M16_REMOTE_VALIDATION_PENDING")

    override suspend fun updatePublicData(input: UpdateM16ShelterPublicInput): Result<M16ShelterProfile> =
        M16ShelterErrorMapper.fail("M16_REMOTE_VALIDATION_PENDING")

    override suspend fun updateOperationalStatus(
        shelterId: String,
        status: M16ShelterOperationalStatus
    ): Result<M16ShelterProfile> = M16ShelterErrorMapper.fail("M16_REMOTE_VALIDATION_PENDING")

    override suspend fun updatePublicationStatus(
        shelterId: String,
        status: M16ShelterPublicationStatus
    ): Result<M16ShelterProfile> = M16ShelterErrorMapper.fail("M16_REMOTE_VALIDATION_PENDING")

    override suspend fun requestVerification(shelterId: String): Result<M16ShelterProfile> =
        M16ShelterErrorMapper.fail("M16_VERIFICATION_MANAGED_EXTERNALLY")

    override suspend fun updateOpeningHours(
        shelterId: String,
        hours: M16OpeningHours
    ): Result<M16ShelterProfile> = M16ShelterErrorMapper.fail("M16_REMOTE_VALIDATION_PENDING")

    override suspend fun updateServices(
        shelterId: String,
        services: Set<M16ShelterService>
    ): Result<M16ShelterProfile> = M16ShelterErrorMapper.fail("M16_REMOTE_VALIDATION_PENDING")

    override suspend fun updateNeeds(
        shelterId: String,
        needs: List<M16ShelterNeed>
    ): Result<M16ShelterProfile> = M16ShelterErrorMapper.fail("M16_REMOTE_VALIDATION_PENDING")

    override suspend fun updateCapacity(
        shelterId: String,
        capacity: M16ShelterCapacity
    ): Result<M16ShelterProfile> = M16ShelterErrorMapper.fail("M16_REMOTE_VALIDATION_PENDING")

    override suspend fun updatePublicContacts(
        shelterId: String,
        contacts: List<M16PublicContactChannel>
    ): Result<M16ShelterProfile> = M16ShelterErrorMapper.fail("M16_REMOTE_VALIDATION_PENDING")

    override suspend fun searchPublic(filter: M16ShelterSearchFilter): Result<List<M16PublicShelter>> =
        M16ShelterErrorMapper.fail("M16_REMOTE_VALIDATION_PENDING")

    override suspend fun canManageOrganization(organizationId: String): Boolean = false

    override suspend fun isOrganizationEligible(organizationId: String): Boolean = false
}
