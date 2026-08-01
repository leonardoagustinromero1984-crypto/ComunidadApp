package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M17CampaignTransparencyReport
import com.comunidapp.app.data.model.M17FundUsageItem
import com.comunidapp.app.data.model.M17InKindCategory
import com.comunidapp.app.data.model.M17InKindDonationNeed
import com.comunidapp.app.data.model.M17InKindNeedStatus
import com.comunidapp.app.data.model.M17InKindPledge
import com.comunidapp.app.data.model.M17InKindPledgeStatus
import com.comunidapp.app.data.model.M17InKindSearchFilter
import com.comunidapp.app.data.model.M17ExtendedPrivacySanitizer
import com.comunidapp.app.data.model.M17MockOrganizations
import com.comunidapp.app.data.model.M17PublicInKindNeed
import com.comunidapp.app.data.model.M17PublicVolunteerOpportunity
import com.comunidapp.app.data.model.M17TransparencyMilestone
import com.comunidapp.app.data.model.M17VolunteerApplication
import com.comunidapp.app.data.model.M17VolunteerApplicationStatus
import com.comunidapp.app.data.model.M17VolunteerOpportunity
import com.comunidapp.app.data.model.M17VolunteerOpportunityStatus
import com.comunidapp.app.data.model.M17VolunteerOpportunityType
import com.comunidapp.app.data.model.M17VolunteerSearchFilter
import com.comunidapp.app.data.remote.supabase.m17.M17DonationErrorMapper
import com.comunidapp.app.data.remote.supabase.m17.M17Exception
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class M17ExtendedMemoryStore {
    private val idSeq = AtomicLong(0)
    private val mutex = Mutex()
    private val _needs = MutableStateFlow<List<M17InKindDonationNeed>>(emptyList())
    private val _pledges = MutableStateFlow<List<M17InKindPledge>>(emptyList())
    private val _opportunities = MutableStateFlow<List<M17VolunteerOpportunity>>(emptyList())
    private val _applications = MutableStateFlow<List<M17VolunteerApplication>>(emptyList())
    private val _transparency = MutableStateFlow<Map<String, M17CampaignTransparencyReport>>(emptyMap())
    var seeded = false

    val needs = _needs.asStateFlow()
    val pledges = _pledges.asStateFlow()
    val opportunities = _opportunities.asStateFlow()
    val applications = _applications.asStateFlow()
    val transparency = _transparency.asStateFlow()

    fun nextId(prefix: String) = "${prefix}_${idSeq.incrementAndGet()}"

    suspend fun <T> withLock(block: suspend () -> T) = mutex.withLock { block() }

    fun upsertPledge(pledge: M17InKindPledge) {
        _pledges.update { list -> list.filterNot { it.id == pledge.id } + pledge }
    }

    fun upsertApplication(app: M17VolunteerApplication) {
        _applications.update { list -> list.filterNot { it.id == app.id } + app }
    }

    fun updateOpportunity(opportunity: M17VolunteerOpportunity) {
        _opportunities.update { list -> list.filterNot { it.id == opportunity.id } + opportunity }
    }

    fun seedDefaults() {
        if (seeded) return
        seeded = true
        val now = System.currentTimeMillis()
        val needs = listOf(
            need("Alimento balanceado", M17InKindCategory.FOOD, M17InKindNeedStatus.PUBLISHED, 50, "kg", now),
            need("Antiparasitarios", M17InKindCategory.MEDICATION, M17InKindNeedStatus.PUBLISHED, 20, "unidades", now),
            need("Shampoo y toallas", M17InKindCategory.HYGIENE, M17InKindNeedStatus.PUBLISHED, 30, "kits", now),
            need("Madera y herramientas", M17InKindCategory.CONSTRUCTION_MATERIALS, M17InKindNeedStatus.PUBLISHED, 10, "lotes", now),
            need("Cumplida — camas", M17InKindCategory.BEDDING, M17InKindNeedStatus.FULFILLED, 15, "camas", now - 100000),
            need("Cancelada — transporte", M17InKindCategory.TRANSPORT_SUPPLIES, M17InKindNeedStatus.CANCELLED, 5, "cajas", now - 200000),
            need("Borrador insumos", M17InKindCategory.OTHER, M17InKindNeedStatus.DRAFT, 8, "items", now),
            need("Parcial — medicación", M17InKindCategory.MEDICATION, M17InKindNeedStatus.PUBLISHED, 40, "dosis", now)
        )
        _needs.value = needs
        val food = needs[0].id
        val partial = needs[7].id
        _pledges.value = listOf(
            pledge(food, 20, M17InKindPledgeStatus.DELIVERED, "Ana", "user1", now),
            pledge(food, 10, M17InKindPledgeStatus.ACCEPTED, "Carlos", "user2", now),
            pledge(partial, 15, M17InKindPledgeStatus.PLEDGED, null, "user3", now)
        )
        _opportunities.value = listOf(
            opp("Apoyo diario en el refugio", M17VolunteerOpportunityType.ANIMAL_CARE, M17VolunteerOpportunityStatus.PUBLISHED, 4, 2, now),
            opp("Traslados solidarios", M17VolunteerOpportunityType.TRANSPORT, M17VolunteerOpportunityStatus.PUBLISHED, 3, 1, now),
            opp("Evento adopción", M17VolunteerOpportunityType.EVENTS, M17VolunteerOpportunityStatus.PUBLISHED, 6, 0, now),
            opp("Fotografía institucional", M17VolunteerOpportunityType.PHOTOGRAPHY, M17VolunteerOpportunityStatus.PUBLISHED, 2, 2, now),
            opp("Construcción de corral", M17VolunteerOpportunityType.CONSTRUCTION, M17VolunteerOpportunityStatus.COMPLETED, 5, 5, now - 50000),
            opp("Pausada — administración", M17VolunteerOpportunityType.ADMINISTRATIVE, M17VolunteerOpportunityStatus.PAUSED, 1, 0, now),
            opp("Sin postulantes", M17VolunteerOpportunityType.FUNDRAISING, M17VolunteerOpportunityStatus.PUBLISHED, 2, 0, now)
        )
        val transport = _opportunities.value[1].id
        _applications.value = listOf(
            application(transport, "user1", M17VolunteerApplicationStatus.SUBMITTED, now),
            application(transport, "user2", M17VolunteerApplicationStatus.REVIEWING, now)
        )
        _transparency.value = mapOf(
            "m17_campaign_1" to M17CampaignTransparencyReport(
                campaignId = "m17_campaign_1",
                summaryText = "Uso de fondos mock — sin PII.",
                usageItems = listOf(
                    M17FundUsageItem("u1", "Medicamentos", 30_000_00, "ARS", "mock://receipt/1"),
                    M17FundUsageItem("u2", "Alimento", 15_000_00, "ARS", "mock://receipt/2")
                ),
                milestones = listOf(
                    M17TransparencyMilestone("m1", "Primera entrega", "Compra autorizada", now - 10000)
                ),
                finalOutcome = null,
                updatedAt = now
            )
        )
    }

    private fun need(
        title: String,
        cat: M17InKindCategory,
        status: M17InKindNeedStatus,
        qty: Int,
        unit: String,
        now: Long
    ) = M17InKindDonationNeed(
        id = nextId("m17_need"),
        organizationId = M17MockOrganizations.ORG_NORTE,
        organizationDisplayName = "Refugio Comunitario Norte",
        title = title,
        description = "Necesidad de bienes mock M17 Bloque 3.",
        category = cat,
        status = status,
        quantityRequested = qty,
        quantityUnit = unit,
        shelterProfileId = "m16_shelter_1",
        publicLocationText = "Zona norte · aproximada",
        createdAt = now,
        updatedAt = now
    )

    private fun pledge(
        needId: String,
        qty: Int,
        status: M17InKindPledgeStatus,
        name: String?,
        userId: String,
        now: Long
    ) = M17InKindPledge(
        id = nextId("m17_pledge"),
        needId = needId,
        quantity = qty,
        status = status,
        donorDisplayName = name,
        userId = userId,
        createdAt = now
    )

    private fun opp(
        title: String,
        type: M17VolunteerOpportunityType,
        status: M17VolunteerOpportunityStatus,
        needed: Int,
        filled: Int,
        now: Long
    ) = M17VolunteerOpportunity(
        id = nextId("m17_vol"),
        organizationId = M17MockOrganizations.ORG_NORTE,
        organizationDisplayName = "Refugio Comunitario Norte",
        title = title,
        description = "Oportunidad de voluntariado mock — no crea membresía M03 ni tránsito M15.",
        type = type,
        status = status,
        slotsNeeded = needed,
        slotsFilled = filled,
        publicLocationText = "Zona norte · aproximada",
        scheduleHint = "Fines de semana",
        createdAt = now,
        updatedAt = now
    )

    private fun application(
        oppId: String,
        userId: String,
        status: M17VolunteerApplicationStatus,
        now: Long
    ) = M17VolunteerApplication(
        id = nextId("m17_app"),
        opportunityId = oppId,
        userId = userId,
        status = status,
        message = "Quiero colaborar",
        createdAt = now
    )
}

interface M17InKindRepository {
    suspend fun searchPublicNeeds(filter: M17InKindSearchFilter): Result<List<M17PublicInKindNeed>>
    suspend fun getPublicNeed(id: String): Result<M17PublicInKindNeed>
    fun observeNeedsForOrganization(orgId: String): Flow<List<M17InKindDonationNeed>>
    suspend fun createPledge(needId: String, quantity: Int, message: String?): Result<M17InKindPledge>
    suspend fun markDelivered(pledgeId: String): Result<M17InKindPledge>
}

interface M17VolunteerRepository {
    suspend fun searchPublicOpportunities(filter: M17VolunteerSearchFilter): Result<List<M17PublicVolunteerOpportunity>>
    suspend fun getPublicOpportunity(id: String): Result<M17PublicVolunteerOpportunity>
    fun observeOpportunitiesForOrganization(orgId: String): Flow<List<M17VolunteerOpportunity>>
    suspend fun submitApplication(opportunityId: String, message: String?): Result<M17VolunteerApplication>
    suspend fun acceptApplication(applicationId: String): Result<M17VolunteerApplication>
}

interface M17TransparencyRepository {
    suspend fun getTransparencyReport(campaignId: String): Result<M17CampaignTransparencyReport>
}

class MockM17InKindRepository(
    private val actorUserId: () -> String?,
    private val store: M17ExtendedMemoryStore = M17ExtendedMemoryStore(),
    private val canManage: (String) -> Boolean = { true }
) : M17InKindRepository {

    init { store.seedDefaults() }

    private fun pledgesFor(needId: String) = store.pledges.value.filter { it.needId == needId }

    private fun pledgedQty(needId: String) = pledgesFor(needId)
        .filter { it.status != M17InKindPledgeStatus.CANCELLED && it.status != M17InKindPledgeStatus.REJECTED }
        .sumOf { it.quantity }

    private fun deliveredQty(needId: String) = pledgesFor(needId)
        .filter { it.status == M17InKindPledgeStatus.DELIVERED }
        .sumOf { it.quantity }

    override suspend fun searchPublicNeeds(filter: M17InKindSearchFilter): Result<List<M17PublicInKindNeed>> =
        runCatching {
            store.needs.value
                .filter { if (filter.activeOnly) it.status == M17InKindNeedStatus.PUBLISHED else it.status.isPublic }
                .filter { filter.category == null || it.category == filter.category }
                .filter { filter.organizationId == null || it.organizationId == filter.organizationId }
                .filter {
                    filter.query.isBlank() ||
                        it.title.contains(filter.query, true) ||
                        it.description.contains(filter.query, true)
                }
                .map { M17ExtendedPrivacySanitizer.toPublicNeed(it, pledgedQty(it.id), deliveredQty(it.id)) }
        }

    override suspend fun getPublicNeed(id: String): Result<M17PublicInKindNeed> = runCatching {
        val need = store.needs.value.firstOrNull { it.id == id } ?: fail("M17_NEED_NOT_FOUND")
        if (!need.status.isPublic || need.status == M17InKindNeedStatus.DRAFT) fail("M17_NEED_NOT_PUBLIC")
        M17ExtendedPrivacySanitizer.toPublicNeed(need, pledgedQty(id), deliveredQty(id))
    }

    override fun observeNeedsForOrganization(orgId: String): Flow<List<M17InKindDonationNeed>> =
        store.needs.map { it.filter { n -> n.organizationId == orgId } }

    override suspend fun createPledge(needId: String, quantity: Int, message: String?): Result<M17InKindPledge> =
        store.withLock {
            runCatching {
                val user = actorUserId() ?: fail("NOT_AUTHENTICATED")
                M17ExtendedValidators.validateQuantity(quantity)?.let { fail(it) }
                val need = store.needs.value.firstOrNull { it.id == needId } ?: fail("M17_NEED_NOT_FOUND")
                if (need.status != M17InKindNeedStatus.PUBLISHED) fail("M17_NEED_NOT_PUBLIC")
                val pledge = M17InKindPledge(
                    id = store.nextId("m17_pledge"),
                    needId = needId,
                    quantity = quantity,
                    status = M17InKindPledgeStatus.PLEDGED,
                    message = message,
                    userId = user,
                    createdAt = System.currentTimeMillis()
                )
                store.upsertPledge(pledge)
                pledge
            }
        }

    override suspend fun markDelivered(pledgeId: String): Result<M17InKindPledge> = store.withLock {
        runCatching {
            val pledge = store.pledges.value.firstOrNull { it.id == pledgeId } ?: fail("M17_PLEDGE_NOT_FOUND")
            val need = store.needs.value.firstOrNull { it.id == pledge.needId } ?: fail("M17_NEED_NOT_FOUND")
            if (!canManage(need.organizationId)) fail("M17_PERMISSION_DENIED")
            if (pledge.status == M17InKindPledgeStatus.DELIVERED) return@runCatching pledge
            val updated = pledge.copy(status = M17InKindPledgeStatus.DELIVERED)
            store.upsertPledge(updated)
            updated
        }
    }

    private fun fail(code: String): Nothing = throw M17Exception(code, M17DonationErrorMapper.userMessage(code))
}

class MockM17VolunteerRepository(
    private val actorUserId: () -> String?,
    private val store: M17ExtendedMemoryStore = M17ExtendedMemoryStore(),
    private val canManage: (String) -> Boolean = { true }
) : M17VolunteerRepository {

    init { store.seedDefaults() }

    override suspend fun searchPublicOpportunities(
        filter: M17VolunteerSearchFilter
    ): Result<List<M17PublicVolunteerOpportunity>> = runCatching {
        store.opportunities.value
            .filter { if (filter.activeOnly) it.status == M17VolunteerOpportunityStatus.PUBLISHED else it.status.isPublic }
            .filter { filter.type == null || it.type == filter.type }
            .filter { filter.organizationId == null || it.organizationId == filter.organizationId }
            .filter {
                filter.query.isBlank() ||
                    it.title.contains(filter.query, true) ||
                    it.description.contains(filter.query, true)
            }
            .map { M17ExtendedPrivacySanitizer.toPublicOpportunity(it) }
    }

    override suspend fun getPublicOpportunity(id: String): Result<M17PublicVolunteerOpportunity> = runCatching {
        val opp = store.opportunities.value.firstOrNull { it.id == id } ?: fail("M17_OPPORTUNITY_NOT_FOUND")
        if (opp.status == M17VolunteerOpportunityStatus.DRAFT) fail("M17_OPPORTUNITY_NOT_PUBLIC")
        M17ExtendedPrivacySanitizer.toPublicOpportunity(opp)
    }

    override fun observeOpportunitiesForOrganization(orgId: String): Flow<List<M17VolunteerOpportunity>> =
        store.opportunities.map { it.filter { o -> o.organizationId == orgId } }

    override suspend fun submitApplication(opportunityId: String, message: String?): Result<M17VolunteerApplication> =
        store.withLock {
            runCatching {
                val user = actorUserId() ?: fail("NOT_AUTHENTICATED")
                val opp = store.opportunities.value.firstOrNull { it.id == opportunityId }
                    ?: fail("M17_OPPORTUNITY_NOT_FOUND")
                if (opp.status != M17VolunteerOpportunityStatus.PUBLISHED) fail("M17_OPPORTUNITY_NOT_PUBLIC")
                if (store.applications.value.any {
                        it.opportunityId == opportunityId && it.userId == user &&
                            it.status !in setOf(
                                M17VolunteerApplicationStatus.REJECTED,
                                M17VolunteerApplicationStatus.WITHDRAWN
                            )
                    }
                ) {
                    fail("M17_DUPLICATE_APPLICATION")
                }
                val app = M17VolunteerApplication(
                    id = store.nextId("m17_app"),
                    opportunityId = opportunityId,
                    userId = user,
                    status = M17VolunteerApplicationStatus.SUBMITTED,
                    message = message,
                    createdAt = System.currentTimeMillis()
                )
                store.upsertApplication(app)
                app
            }
        }

    override suspend fun acceptApplication(applicationId: String): Result<M17VolunteerApplication> =
        store.withLock {
            runCatching {
                val app = store.applications.value.firstOrNull { it.id == applicationId }
                    ?: fail("M17_APPLICATION_NOT_FOUND")
                val opp = store.opportunities.value.firstOrNull { it.id == app.opportunityId }
                    ?: fail("M17_OPPORTUNITY_NOT_FOUND")
                if (!canManage(opp.organizationId)) fail("M17_PERMISSION_DENIED")
                if (opp.status.isTerminal) fail("M17_OPPORTUNITY_TERMINAL")
                val updated = app.copy(status = M17VolunteerApplicationStatus.ACCEPTED)
                store.upsertApplication(updated)
                store.updateOpportunity(
                    opp.copy(
                        slotsFilled = (opp.slotsFilled + 1).coerceAtMost(opp.slotsNeeded),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                updated
            }
        }

    private fun fail(code: String): Nothing = throw M17Exception(code, M17DonationErrorMapper.userMessage(code))
}

class MockM17TransparencyRepository(
    private val store: M17ExtendedMemoryStore = M17ExtendedMemoryStore()
) : M17TransparencyRepository {

    init { store.seedDefaults() }

    override suspend fun getTransparencyReport(campaignId: String): Result<M17CampaignTransparencyReport> =
        runCatching {
            store.transparency.value[campaignId] ?: throw M17Exception(
                "M17_CAMPAIGN_NOT_FOUND",
                M17DonationErrorMapper.userMessage("M17_CAMPAIGN_NOT_FOUND")
            )
        }
}
