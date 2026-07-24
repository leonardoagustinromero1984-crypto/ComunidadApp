package com.comunidapp.app.data.repository

import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundSighting
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.M13ActorAuthority
import com.comunidapp.app.data.model.M13AuditEvents
import com.comunidapp.app.data.model.M13MatchCandidate
import com.comunidapp.app.data.model.M13MatchDecision
import com.comunidapp.app.data.model.M13MatchDecisionType
import com.comunidapp.app.data.model.M13MatchStatus
import com.comunidapp.app.data.model.M13PermissionCodes
import com.comunidapp.app.data.model.M13Sighting
import com.comunidapp.app.data.model.M13SightingPublic
import com.comunidapp.app.data.model.M13SightingStatus
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.toPublic
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

data class CreateM13SightingInput(
    val lostFoundCaseId: String? = null,
    val species: PetSpecies,
    val breedText: String? = null,
    val primaryColor: String,
    val secondaryColor: String? = null,
    val sex: PetSex? = null,
    val size: PetSize? = null,
    val observedAt: Long,
    val zoneText: String,
    val latitudeApprox: Double? = null,
    val longitudeApprox: Double? = null,
    val accuracyMeters: Double? = null,
    val description: String,
    val mediaRefs: List<String> = emptyList(),
    val mirrorToLegacy: Boolean = true
)

/** Adaptador bidireccional con LostFoundSighting legacy (sin reemplazo destructivo). */
object M13LegacySightingAdapter {
    fun fromLegacy(
        legacy: LostFoundSighting,
        species: PetSpecies = PetSpecies.DOG,
        primaryColor: String = "desconocido"
    ): M13Sighting {
        val now = legacy.createdAt ?: System.currentTimeMillis()
        return M13Sighting(
            id = if (legacy.id.startsWith("m13_")) legacy.id else "m13_legacy_${legacy.id}",
            reporterUserId = legacy.reporterId,
            lostFoundCaseId = legacy.postId.ifBlank { null },
            species = species,
            primaryColor = primaryColor,
            observedAt = now,
            zoneText = legacy.locationText?.ifBlank { null } ?: "Zona no indicada",
            latitudeApprox = legacy.latitude,
            longitudeApprox = legacy.longitude,
            accuracyMeters = if (legacy.latitude != null) 500.0 else null,
            description = legacy.note.ifBlank { "Avistamiento legacy" },
            mediaRefs = emptyList(),
            status = M13SightingStatus.ACTIVE,
            createdAt = now,
            updatedAt = now
        )
    }

    fun toLegacy(sighting: M13Sighting, reporterName: String = "Reportante"): LostFoundSighting =
        LostFoundSighting(
            id = sighting.id.removePrefix("m13_legacy_").ifBlank { sighting.id },
            postId = sighting.lostFoundCaseId.orEmpty(),
            reporterId = sighting.reporterUserId,
            reporterName = reporterName,
            note = sighting.description,
            locationText = sighting.zoneText,
            latitude = sighting.latitudeApprox,
            longitude = sighting.longitudeApprox,
            createdAt = sighting.createdAt
        )
}

class M13MemoryStore {
    private val idSeq = AtomicLong(0)
    private val _sightings = MutableStateFlow<List<M13Sighting>>(emptyList())
    private val _candidates = MutableStateFlow<List<M13MatchCandidate>>(emptyList())
    private val _decisions = MutableStateFlow<List<M13MatchDecision>>(emptyList())
    private val _audit = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    val sightings: StateFlow<List<M13Sighting>> = _sightings.asStateFlow()
    val candidates: StateFlow<List<M13MatchCandidate>> = _candidates.asStateFlow()
    val decisions: StateFlow<List<M13MatchDecision>> = _decisions.asStateFlow()
    val auditTrail: StateFlow<List<Pair<String, String>>> = _audit.asStateFlow()

    var forceFailure: Boolean = false

    fun nextId(prefix: String): String = "${prefix}_${idSeq.incrementAndGet()}_${System.currentTimeMillis()}"

    fun replaceSightings(list: List<M13Sighting>) {
        _sightings.value = list
    }

    fun upsertSighting(sighting: M13Sighting) {
        _sightings.update { current ->
            val without = current.filterNot { it.id == sighting.id }
            listOf(sighting) + without
        }
    }

    fun putCandidate(candidate: M13MatchCandidate) {
        _candidates.update { current ->
            val existing = current.find {
                it.caseId == candidate.caseId && it.sightingId == candidate.sightingId
            }
            val stable = candidate.copy(
                id = "match_${candidate.caseId}_${candidate.sightingId}",
                createdAt = existing?.createdAt ?: candidate.createdAt,
                status = existing?.status?.takeIf { it.isTerminal } ?: candidate.status,
                updatedAt = candidate.updatedAt
            )
            listOf(stable) + current.filterNot {
                it.caseId == candidate.caseId && it.sightingId == candidate.sightingId
            }
        }
    }

    fun updateCandidate(candidate: M13MatchCandidate) {
        _candidates.update { list -> list.map { if (it.id == candidate.id) candidate else it } }
    }

    fun addDecision(decision: M13MatchDecision) {
        _decisions.update { listOf(decision) + it }
    }

    fun audit(event: String, entityId: String) {
        _audit.update { listOf(event to entityId) + it }
    }

    fun seedDemoData(cases: List<LostFoundPost> = emptyList()) {
        val now = System.currentTimeMillis()
        val activeCase = cases.firstOrNull { it.status == LostFoundStatus.ACTIVE }
            ?: LostFoundPost(
                id = "lf-demo-1",
                authorId = "user_1",
                authorName = "María",
                type = com.comunidapp.app.data.model.LostFoundType.LOST,
                petName = "Luna",
                species = PetSpecies.DOG,
                location = "Palermo, CABA",
                description = "Perra mediana color marrón, muy dócil",
                contactInfo = "REDACTED",
                status = LostFoundStatus.ACTIVE,
                latitude = -34.5875,
                longitude = -58.4250,
                date = "2026-07-20",
                createdAt = now - 2L * 24 * 60 * 60 * 1000
            )
        val sighting = M13Sighting(
            id = "m13_sighting_demo_1",
            reporterUserId = "user_3",
            lostFoundCaseId = activeCase.id,
            species = PetSpecies.DOG,
            breedText = null,
            primaryColor = "marrón",
            sex = PetSex.FEMALE,
            size = PetSize.MEDIUM,
            observedAt = now - 12L * 60 * 60 * 1000,
            zoneText = "Palermo",
            latitudeApprox = -34.5880,
            longitudeApprox = -58.4240,
            accuracyMeters = 400.0,
            description = "Vi una perra marrón cerca de Plaza Serrano",
            mediaRefs = listOf("m05:lostfound/demo/sighting1.jpg"),
            status = M13SightingStatus.ACTIVE,
            createdAt = now,
            updatedAt = now
        )
        _sightings.value = listOf(sighting)
        M13MatchingEngine.score(sighting, activeCase, now)?.let { putCandidate(it) }
        audit(M13AuditEvents.SIGHTING_CREATED, sighting.id)
    }
}

interface M13SightingRepository {
    fun observeMySightings(): Flow<List<M13Sighting>>
    fun observePublicSightings(): Flow<List<M13SightingPublic>>
    suspend fun getSighting(id: String, forPublic: Boolean = false): Result<Any>
    suspend fun createSighting(input: CreateM13SightingInput): Result<M13Sighting>
    suspend fun withdrawSighting(id: String): Result<M13Sighting>
}

interface M13MatchRepository {
    fun observeMatchesForCase(caseId: String): Flow<List<M13MatchCandidate>>
    fun observeMatch(candidateId: String): Flow<M13MatchCandidate?>
    suspend fun recalculateForSighting(sightingId: String): Result<List<M13MatchCandidate>>
    suspend fun openReview(candidateId: String): Result<M13MatchCandidate>
    suspend fun decide(
        candidateId: String,
        decision: M13MatchDecisionType,
        reasonCode: String,
        notePrivate: String? = null
    ): Result<M13MatchCandidate>
}

object M13Authority {
    fun hasDeclaredPermission(code: String): Boolean =
        M13PermissionCodes.all.contains(code)

    fun canManageOwn(actorId: String?, sighting: M13Sighting): Boolean =
        !actorId.isNullOrBlank() && actorId == sighting.reporterUserId

    fun canReviewCase(actorId: String?, casePost: LostFoundPost?): Boolean =
        !actorId.isNullOrBlank() && casePost != null && casePost.authorId == actorId
}

class MockM13SightingRepository(
    private val actorUserId: () -> String?,
    private val store: M13MemoryStore
) : M13SightingRepository {

    override fun observeMySightings(): Flow<List<M13Sighting>> =
        store.sightings.map { list ->
            val uid = actorUserId().orEmpty()
            list.filter { it.reporterUserId == uid }.sortedByDescending { it.createdAt }
        }

    override fun observePublicSightings(): Flow<List<M13SightingPublic>> =
        store.sightings.map { list ->
            list.filter { it.status == M13SightingStatus.ACTIVE }
                .map { it.toPublic() }
                .sortedByDescending { it.observedAtApproxDay }
        }

    override suspend fun getSighting(id: String, forPublic: Boolean): Result<Any> {
        if (store.forceFailure) return resultFailM13("M13_REPOSITORY_FAILURE")
        if (id.isBlank()) return resultFailM13("SIGHTING_NOT_FOUND")
        val found = store.sightings.value.find { it.id == id }
            ?: return resultFailM13("SIGHTING_NOT_FOUND")
        val actor = actorUserId()
        return if (forPublic || actor == null || actor != found.reporterUserId) {
            Result.success(found.toPublic())
        } else {
            Result.success(found)
        }
    }

    override suspend fun createSighting(input: CreateM13SightingInput): Result<M13Sighting> {
        if (store.forceFailure) return resultFailM13("M13_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: return resultFailM13("NOT_AUTHENTICATED")
        M13Validators.validateCreate(
            description = input.description,
            zoneText = input.zoneText,
            primaryColor = input.primaryColor,
            mediaRefs = input.mediaRefs,
            latitudeApprox = input.latitudeApprox,
            longitudeApprox = input.longitudeApprox,
            accuracyMeters = input.accuracyMeters
        )?.let { return resultFailM13(it) }

        val now = System.currentTimeMillis()
        val sighting = M13Sighting(
            id = store.nextId("m13_sighting"),
            reporterUserId = actor,
            lostFoundCaseId = input.lostFoundCaseId?.trim()?.ifBlank { null },
            species = input.species,
            breedText = input.breedText?.trim()?.ifBlank { null },
            primaryColor = input.primaryColor.trim(),
            secondaryColor = input.secondaryColor?.trim()?.ifBlank { null },
            sex = input.sex,
            size = input.size,
            observedAt = input.observedAt,
            zoneText = input.zoneText.trim(),
            latitudeApprox = input.latitudeApprox,
            longitudeApprox = input.longitudeApprox,
            accuracyMeters = input.accuracyMeters,
            description = input.description.trim(),
            mediaRefs = input.mediaRefs.map { it.trim() },
            status = M13SightingStatus.ACTIVE,
            createdAt = now,
            updatedAt = now
        )
        store.upsertSighting(sighting)
        store.audit(M13AuditEvents.SIGHTING_CREATED, sighting.id)

        if (input.mirrorToLegacy && !sighting.lostFoundCaseId.isNullOrBlank()) {
            InMemoryDataStore.addSighting(
                M13LegacySightingAdapter.toLegacy(sighting, reporterName = "Usuario")
            )
        }
        return Result.success(sighting)
    }

    override suspend fun withdrawSighting(id: String): Result<M13Sighting> {
        if (store.forceFailure) return resultFailM13("M13_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: return resultFailM13("NOT_AUTHENTICATED")
        val existing = store.sightings.value.find { it.id == id }
            ?: return resultFailM13("SIGHTING_NOT_FOUND")
        if (!M13Authority.canManageOwn(actor, existing)) return resultFailM13("SIGHTING_FORBIDDEN")
        if (existing.status == M13SightingStatus.WITHDRAWN) return Result.success(existing)
        if (existing.status.isTerminal) return resultFailM13("SIGHTING_INVALID_TRANSITION")
        val updated = existing.copy(
            status = M13SightingStatus.WITHDRAWN,
            updatedAt = System.currentTimeMillis()
        )
        store.upsertSighting(updated)
        store.audit(M13AuditEvents.SIGHTING_WITHDRAWN, updated.id)
        return Result.success(updated)
    }
}

class MockM13MatchRepository(
    private val actorUserId: () -> String?,
    private val store: M13MemoryStore,
    private val resolveCases: () -> List<LostFoundPost>
) : M13MatchRepository {

    override fun observeMatchesForCase(caseId: String): Flow<List<M13MatchCandidate>> =
        store.candidates.map { list ->
            list.filter { it.caseId == caseId }.sortedByDescending { it.score }
        }

    override fun observeMatch(candidateId: String): Flow<M13MatchCandidate?> =
        store.candidates.map { list -> list.find { it.id == candidateId } }

    override suspend fun recalculateForSighting(sightingId: String): Result<List<M13MatchCandidate>> {
        if (store.forceFailure) return resultFailM13("M13_REPOSITORY_FAILURE")
        val sighting = store.sightings.value.find { it.id == sightingId }
            ?: return resultFailM13("SIGHTING_NOT_FOUND")
        val cases = resolveCases().filter { it.status == LostFoundStatus.ACTIVE }
        val produced = cases.mapNotNull { case ->
            M13MatchingEngine.score(sighting, case)?.also { store.putCandidate(it) }
        }
        produced.forEach { store.audit(M13AuditEvents.MATCH_PROPOSED, it.id) }
        return Result.success(produced.sortedByDescending { it.score })
    }

    override suspend fun openReview(candidateId: String): Result<M13MatchCandidate> {
        if (store.forceFailure) return resultFailM13("M13_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: return resultFailM13("NOT_AUTHENTICATED")
        val candidate = store.candidates.value.find { it.id == candidateId }
            ?: return resultFailM13("MATCH_NOT_FOUND")
        val casePost = resolveCases().find { it.id == candidate.caseId }
        if (!M13Authority.canReviewCase(actor, casePost)) return resultFailM13("MATCH_FORBIDDEN")
        if (candidate.status.isTerminal) return resultFailM13("MATCH_INVALID_TRANSITION")
        if (candidate.status == M13MatchStatus.UNDER_REVIEW) return Result.success(candidate)
        val updated = candidate.copy(
            status = M13MatchStatus.UNDER_REVIEW,
            updatedAt = System.currentTimeMillis()
        )
        store.updateCandidate(updated)
        return Result.success(updated)
    }

    override suspend fun decide(
        candidateId: String,
        decision: M13MatchDecisionType,
        reasonCode: String,
        notePrivate: String?
    ): Result<M13MatchCandidate> {
        if (store.forceFailure) return resultFailM13("M13_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: return resultFailM13("NOT_AUTHENTICATED")
        val candidate = store.candidates.value.find { it.id == candidateId }
            ?: return resultFailM13("MATCH_NOT_FOUND")
        val casePost = resolveCases().find { it.id == candidate.caseId }
        if (!M13Authority.canReviewCase(actor, casePost)) return resultFailM13("MATCH_FORBIDDEN")
        if (candidate.status.isTerminal) return resultFailM13("MATCH_INVALID_TRANSITION")
        // Sin autoconfirmación: siempre requiere actor humano (ya autenticado + autoridad).
        val newStatus = when (decision) {
            M13MatchDecisionType.CONFIRMED -> M13MatchStatus.CONFIRMED
            M13MatchDecisionType.REJECTED -> M13MatchStatus.REJECTED
            M13MatchDecisionType.INCONCLUSIVE -> M13MatchStatus.INCONCLUSIVE
        }
        val now = System.currentTimeMillis()
        val updated = candidate.copy(status = newStatus, updatedAt = now)
        store.updateCandidate(updated)
        store.addDecision(
            M13MatchDecision(
                id = store.nextId("m13_decision"),
                candidateId = candidate.id,
                decision = decision,
                actorUserId = actor,
                actorAuthority = M13ActorAuthority.CASE_OWNER,
                reasonCode = reasonCode.ifBlank { decision.name },
                notePrivate = notePrivate?.trim()?.ifBlank { null },
                createdAt = now
            )
        )
        val event = when (decision) {
            M13MatchDecisionType.CONFIRMED -> M13AuditEvents.MATCH_CONFIRMED
            M13MatchDecisionType.REJECTED -> M13AuditEvents.MATCH_REJECTED
            M13MatchDecisionType.INCONCLUSIVE -> M13AuditEvents.MATCH_INCONCLUSIVE
        }
        store.audit(event, updated.id)
        if (decision == M13MatchDecisionType.CONFIRMED) {
            store.sightings.value.find { it.id == candidate.sightingId }?.let { s ->
                if (s.status == M13SightingStatus.ACTIVE) {
                    store.upsertSighting(
                        s.copy(status = M13SightingStatus.CONFIRMED, updatedAt = now)
                    )
                }
            }
        }
        return Result.success(updated)
    }
}
