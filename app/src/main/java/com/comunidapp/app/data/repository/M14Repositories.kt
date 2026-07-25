package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM14CredentialInput
import com.comunidapp.app.data.model.CreateM14PassportInput
import com.comunidapp.app.data.model.M14AuditEvents
import com.comunidapp.app.data.model.M14Credential
import com.comunidapp.app.data.model.M14CredentialStatus
import com.comunidapp.app.data.model.M14M06Hooks
import com.comunidapp.app.data.model.M14PassportHistory
import com.comunidapp.app.data.model.M14PassportStatus
import com.comunidapp.app.data.model.M14PetPassport
import com.comunidapp.app.data.model.M14PublicPassportProjection
import com.comunidapp.app.data.model.M14VerificationDecision
import com.comunidapp.app.data.model.M14VerificationRequest
import com.comunidapp.app.data.model.M14VerificationRequestStatus
import com.comunidapp.app.data.model.M14Visibility
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.UpdateM14PassportInput
import com.comunidapp.app.data.remote.supabase.m14.M14Exception
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * LeoVer M14 — store + contratos + fakes (Bloque 1, sin red).
 */

class M14MemoryStore(
    val numberGenerator: M14PassportNumberGenerator = M14PassportNumberGenerator()
) {
    private val idSeq = AtomicLong(0)
    private val mutex = Mutex()
    private val _passports = MutableStateFlow<List<M14PetPassport>>(emptyList())
    private val _credentials = MutableStateFlow<List<M14Credential>>(emptyList())
    private val _requests = MutableStateFlow<List<M14VerificationRequest>>(emptyList())
    private val _decisions = MutableStateFlow<List<M14VerificationDecision>>(emptyList())
    private val _history = MutableStateFlow<List<M14PassportHistory>>(emptyList())
    private val _audit = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    private val _m06 = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    var forceFailure: Boolean = false
    var m06InfrastructureAvailable: Boolean = false

    val passports: StateFlow<List<M14PetPassport>> = _passports.asStateFlow()
    val credentials: StateFlow<List<M14Credential>> = _credentials.asStateFlow()
    val verificationRequests: StateFlow<List<M14VerificationRequest>> = _requests.asStateFlow()
    val history: StateFlow<List<M14PassportHistory>> = _history.asStateFlow()
    val auditLog: StateFlow<List<Pair<String, String>>> = _audit.asStateFlow()
    val m06PreparedHooks: StateFlow<List<Pair<String, String>>> = _m06.asStateFlow()

    fun nextId(prefix: String): String = "${prefix}_${idSeq.incrementAndGet()}"

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    fun upsertPassport(p: M14PetPassport) {
        _passports.update { list ->
            val without = list.filterNot { it.id == p.id }
            (without + p).sortedByDescending { it.updatedAt }
        }
    }

    fun upsertCredential(c: M14Credential) {
        _credentials.update { list ->
            val without = list.filterNot { it.id == c.id }
            (without + c).sortedByDescending { it.updatedAt }
        }
    }

    fun upsertRequest(r: M14VerificationRequest) {
        _requests.update { list ->
            val without = list.filterNot { it.id == r.id }
            (without + r).sortedByDescending { it.requestedAt }
        }
    }

    fun appendDecision(d: M14VerificationDecision) {
        _decisions.update { listOf(d) + it }
    }

    fun appendHistory(h: M14PassportHistory) {
        _history.update { listOf(h) + it }
    }

    fun audit(event: String, entityId: String) {
        _audit.update { listOf(event to entityId) + it }
    }

    fun recordM06(eventKey: String, entityId: String) {
        val key = eventKey to entityId
        _m06.update { list ->
            if (list.any { it == key }) list else listOf(key) + list
        }
        if (!m06InfrastructureAvailable) {
            val infra = M14M06Hooks.INFRASTRUCTURE to "infra"
            _m06.update { list ->
                if (list.any { it.first == M14M06Hooks.INFRASTRUCTURE }) list else listOf(infra) + list
            }
        }
    }

    fun nonFinalForPet(petId: String): M14PetPassport? =
        _passports.value.firstOrNull { it.petId == petId && it.status.isNonFinal }
}

interface M14AuthorityPolicy {
    fun canCreateOrManage(actorUserId: String, pet: Pet): Boolean
    fun canModerate(actorUserId: String): Boolean
    fun canVerifyAsIssuer(actorUserId: String, credentialCreatedBy: String): Boolean
}

/** Autoridad local B1: responsable M08 vía ownerId; sin autoverificación. */
class MockM14AuthorityPolicy(
    private val isModerator: (String) -> Boolean = { false },
    private val isOrgVerifier: (String) -> Boolean = { false }
) : M14AuthorityPolicy {
    override fun canCreateOrManage(actorUserId: String, pet: Pet): Boolean =
        !pet.ownerId.isNullOrBlank() && pet.ownerId == actorUserId

    override fun canModerate(actorUserId: String): Boolean = isModerator(actorUserId)

    override fun canVerifyAsIssuer(actorUserId: String, credentialCreatedBy: String): Boolean =
        isOrgVerifier(actorUserId) && actorUserId != credentialCreatedBy
}

interface M14PassportRepository {
    fun observeMyPassports(): Flow<List<M14PetPassport>>
    fun observePassport(passportId: String): Flow<M14PetPassport?>
    fun observePassportForPet(petId: String): Flow<M14PetPassport?>
    suspend fun getPassport(passportId: String): Result<M14PetPassport>
    suspend fun createPassport(input: CreateM14PassportInput): Result<M14PetPassport>
    suspend fun updatePassport(passportId: String, input: UpdateM14PassportInput): Result<M14PetPassport>
    suspend fun activatePassport(passportId: String): Result<M14PetPassport>
    suspend fun transitionPassport(
        passportId: String,
        to: M14PassportStatus,
        reason: String?
    ): Result<M14PetPassport>
    fun observeHistory(passportId: String): Flow<List<M14PassportHistory>>
    suspend fun getPublicProjection(publicCode: String): Result<M14PublicPassportProjection>
}

interface M14CredentialRepository {
    fun observeCredentials(passportId: String): Flow<List<M14Credential>>
    fun observeCredential(credentialId: String): Flow<M14Credential?>
    suspend fun getCredential(credentialId: String): Result<M14Credential>
    suspend fun createCredential(input: CreateM14CredentialInput): Result<M14Credential>
    suspend fun requestVerification(
        credentialId: String,
        targetOrganizationId: String? = null
    ): Result<M14VerificationRequest>
}

interface M14VerificationRepository {
    fun observeRequests(passportId: String): Flow<List<M14VerificationRequest>>
    suspend fun resolveLocal(
        requestId: String,
        approve: Boolean,
        reasonCode: String,
        notePrivate: String? = null
    ): Result<M14VerificationRequest>
}

class MockM14PassportRepository(
    private val store: M14MemoryStore,
    private val actorUserId: () -> String?,
    private val resolvePet: (String) -> Pet?,
    private val authority: M14AuthorityPolicy = MockM14AuthorityPolicy()
) : M14PassportRepository {

    override fun observeMyPassports(): Flow<List<M14PetPassport>> =
        store.passports.map { list ->
            val uid = actorUserId().orEmpty()
            list.filter { it.createdBy == uid || manageable(it, uid) }
                .sortedByDescending { it.updatedAt }
        }

    private fun manageable(p: M14PetPassport, uid: String): Boolean {
        val pet = resolvePet(p.petId) ?: return false
        return authority.canCreateOrManage(uid, pet)
    }

    override fun observePassport(passportId: String): Flow<M14PetPassport?> =
        store.passports.map { list -> list.find { it.id == passportId } }

    override fun observePassportForPet(petId: String): Flow<M14PetPassport?> =
        store.passports.map { list ->
            list.firstOrNull { it.petId == petId && it.status.isNonFinal }
                ?: list.filter { it.petId == petId }.maxByOrNull { it.updatedAt }
        }

    override suspend fun getPassport(passportId: String): Result<M14PetPassport> {
        if (store.forceFailure) return resultFailM14("M14_REPOSITORY_FAILURE")
        val p = store.passports.value.find { it.id == passportId }
            ?: return resultFailM14("PASSPORT_NOT_FOUND")
        return Result.success(p)
    }

    override suspend fun createPassport(input: CreateM14PassportInput): Result<M14PetPassport> =
        store.withLock {
            if (store.forceFailure) return@withLock resultFailM14("M14_REPOSITORY_FAILURE")
            val uid = actorUserId() ?: return@withLock resultFailM14("NOT_AUTHENTICATED")
            M14Validators.validateCreatePassport(input)?.let { return@withLock resultFailM14(it) }
            val pet = resolvePet(input.petId) ?: return@withLock resultFailM14("PET_NOT_FOUND")
            if (!authority.canCreateOrManage(uid, pet)) return@withLock resultFailM14("UNAUTHORIZED")
            if (store.nonFinalForPet(input.petId) != null) {
                return@withLock resultFailM14("PASSPORT_ALREADY_EXISTS")
            }
            val now = System.currentTimeMillis()
            val number = store.numberGenerator.nextPassportNumber()
            val publicCode = store.numberGenerator.nextPublicCode()
            if (M14Validators.publicCodeLooksLikePii(publicCode)) {
                return@withLock resultFailM14("CONFLICT")
            }
            val passport = M14PetPassport(
                id = store.nextId("m14_pp"),
                petId = input.petId,
                passportNumber = number,
                publicCode = publicCode,
                status = M14PassportStatus.DRAFT,
                displayName = input.displayName.trim(),
                species = input.species,
                breedText = input.breedText?.trim()?.takeIf { it.isNotEmpty() },
                sex = input.sex,
                birthDateEpochMs = input.birthDateEpochMs,
                primaryColor = input.primaryColor?.trim()?.takeIf { it.isNotEmpty() },
                distinctiveMarks = input.distinctiveMarks?.trim()?.takeIf { it.isNotEmpty() },
                microchipNumber = M14Validators.normalizeMicrochip(input.microchipNumber),
                visibility = input.visibility,
                createdBy = uid,
                createdAt = now,
                updatedAt = now
            )
            store.upsertPassport(passport)
            store.appendHistory(
                M14PassportHistory(
                    id = store.nextId("m14_hist"),
                    passportId = passport.id,
                    fromStatus = null,
                    toStatus = M14PassportStatus.DRAFT,
                    actorUserId = uid,
                    reason = "CREATED",
                    createdAt = now
                )
            )
            store.audit(M14AuditEvents.PASSPORT_CREATED, passport.id)
            store.recordM06(M14M06Hooks.PASSPORT_CREATED, passport.id)
            Result.success(passport)
        }

    override suspend fun updatePassport(
        passportId: String,
        input: UpdateM14PassportInput
    ): Result<M14PetPassport> =
        store.withLock {
            if (store.forceFailure) return@withLock resultFailM14("M14_REPOSITORY_FAILURE")
            val uid = actorUserId() ?: return@withLock resultFailM14("NOT_AUTHENTICATED")
            M14Validators.validateUpdatePassport(input)?.let { return@withLock resultFailM14(it) }
            val current = store.passports.value.find { it.id == passportId }
                ?: return@withLock resultFailM14("PASSPORT_NOT_FOUND")
            if (current.status.isTerminal) return@withLock resultFailM14("INVALID_PASSPORT_STATUS")
            val pet = resolvePet(current.petId) ?: return@withLock resultFailM14("PET_NOT_FOUND")
            if (!authority.canCreateOrManage(uid, pet) && !authority.canModerate(uid)) {
                return@withLock resultFailM14("UNAUTHORIZED")
            }
            val updated = current.copy(
                displayName = input.displayName?.trim() ?: current.displayName,
                breedText = input.breedText?.trim() ?: current.breedText,
                sex = input.sex ?: current.sex,
                birthDateEpochMs = input.birthDateEpochMs ?: current.birthDateEpochMs,
                primaryColor = input.primaryColor?.trim() ?: current.primaryColor,
                distinctiveMarks = input.distinctiveMarks?.trim() ?: current.distinctiveMarks,
                microchipNumber = input.microchipNumber?.let { M14Validators.normalizeMicrochip(it) }
                    ?: current.microchipNumber,
                visibility = input.visibility ?: current.visibility,
                updatedAt = System.currentTimeMillis()
            )
            store.upsertPassport(updated)
            Result.success(updated)
        }

    override suspend fun activatePassport(passportId: String): Result<M14PetPassport> {
        val result = transitionPassport(passportId, M14PassportStatus.ACTIVE, "ACTIVATED")
        result.onSuccess {
            store.audit(M14AuditEvents.PASSPORT_ACTIVATED, passportId)
            store.recordM06(M14M06Hooks.PASSPORT_ACTIVATED, passportId)
        }
        return result
    }

    override suspend fun transitionPassport(
        passportId: String,
        to: M14PassportStatus,
        reason: String?
    ): Result<M14PetPassport> =
        store.withLock {
            if (store.forceFailure) return@withLock resultFailM14("M14_REPOSITORY_FAILURE")
            val uid = actorUserId() ?: return@withLock resultFailM14("NOT_AUTHENTICATED")
            val current = store.passports.value.find { it.id == passportId }
                ?: return@withLock resultFailM14("PASSPORT_NOT_FOUND")
            if (!M14Validators.canTransitionPassport(current.status, to)) {
                return@withLock resultFailM14("INVALID_TRANSITION")
            }
            val pet = resolvePet(current.petId) ?: return@withLock resultFailM14("PET_NOT_FOUND")
            val okManage = authority.canCreateOrManage(uid, pet)
            val okMod = authority.canModerate(uid)
            if (to == M14PassportStatus.SUSPENDED && !okMod && !okManage) {
                return@withLock resultFailM14("UNAUTHORIZED")
            }
            if (!okManage && !okMod) return@withLock resultFailM14("UNAUTHORIZED")
            val now = System.currentTimeMillis()
            val updated = current.copy(status = to, updatedAt = now)
            store.upsertPassport(updated)
            store.appendHistory(
                M14PassportHistory(
                    id = store.nextId("m14_hist"),
                    passportId = passportId,
                    fromStatus = current.status,
                    toStatus = to,
                    actorUserId = uid,
                    reason = reason,
                    createdAt = now
                )
            )
            store.audit(M14AuditEvents.PASSPORT_STATUS_CHANGED, passportId)
            Result.success(updated)
        }

    override fun observeHistory(passportId: String): Flow<List<M14PassportHistory>> =
        store.history.map { list ->
            list.filter { it.passportId == passportId }.sortedByDescending { it.createdAt }
        }

    override suspend fun getPublicProjection(publicCode: String): Result<M14PublicPassportProjection> {
        if (store.forceFailure) return resultFailM14("M14_REPOSITORY_FAILURE")
        val passport = store.passports.value.find { it.publicCode == publicCode }
            ?: return resultFailM14("PASSPORT_NOT_FOUND")
        if (passport.visibility != M14Visibility.PUBLIC_REDACTED &&
            passport.status != M14PassportStatus.ACTIVE
        ) {
            // Vista pública solo si hay código y proyección redactada disponible.
        }
        val creds = store.credentials.value.filter { it.passportId == passport.id }
        val projection = M14PublicProjectionService.project(passport, creds)
            ?: return resultFailM14("PUBLIC_PROJECTION_REDACTED")
        return Result.success(projection)
    }
}

class MockM14CredentialRepository(
    private val store: M14MemoryStore,
    private val actorUserId: () -> String?,
    private val resolvePet: (String) -> Pet?,
    private val authority: M14AuthorityPolicy = MockM14AuthorityPolicy()
) : M14CredentialRepository {

    override fun observeCredentials(passportId: String): Flow<List<M14Credential>> =
        store.credentials.map { list ->
            list.filter { it.passportId == passportId }.sortedByDescending { it.updatedAt }
        }

    override fun observeCredential(credentialId: String): Flow<M14Credential?> =
        store.credentials.map { list -> list.find { it.id == credentialId } }

    override suspend fun getCredential(credentialId: String): Result<M14Credential> {
        val c = store.credentials.value.find { it.id == credentialId }
            ?: return resultFailM14("CREDENTIAL_NOT_FOUND")
        return Result.success(c)
    }

    override suspend fun createCredential(input: CreateM14CredentialInput): Result<M14Credential> =
        store.withLock {
            if (store.forceFailure) return@withLock resultFailM14("M14_REPOSITORY_FAILURE")
            val uid = actorUserId() ?: return@withLock resultFailM14("NOT_AUTHENTICATED")
            M14Validators.validateCredential(input)?.let { return@withLock resultFailM14(it) }
            val passport = store.passports.value.find { it.id == input.passportId }
                ?: return@withLock resultFailM14("PASSPORT_NOT_FOUND")
            if (passport.status.isTerminal) return@withLock resultFailM14("INVALID_PASSPORT_STATUS")
            val pet = resolvePet(passport.petId) ?: return@withLock resultFailM14("PET_NOT_FOUND")
            if (!authority.canCreateOrManage(uid, pet)) return@withLock resultFailM14("UNAUTHORIZED")
            val now = System.currentTimeMillis()
            val cred = M14Credential(
                id = store.nextId("m14_cred"),
                passportId = input.passportId,
                type = input.type,
                title = input.title.trim(),
                issuedAt = input.issuedAt,
                expiresAt = input.expiresAt,
                status = M14CredentialStatus.DRAFT,
                visibility = input.visibility,
                mediaRefs = input.mediaRefs,
                externalReferenceMasked = input.externalReferenceMasked,
                notePrivate = input.notePrivate,
                createdBy = uid,
                createdAt = now,
                updatedAt = now
            )
            store.upsertCredential(cred)
            store.audit(M14AuditEvents.CREDENTIAL_ADDED, cred.id)
            store.recordM06(M14M06Hooks.CREDENTIAL_ADDED, cred.id)
            Result.success(cred)
        }

    override suspend fun requestVerification(
        credentialId: String,
        targetOrganizationId: String?
    ): Result<M14VerificationRequest> =
        store.withLock {
            if (store.forceFailure) return@withLock resultFailM14("M14_REPOSITORY_FAILURE")
            val uid = actorUserId() ?: return@withLock resultFailM14("NOT_AUTHENTICATED")
            val cred = store.credentials.value.find { it.id == credentialId }
                ?: return@withLock resultFailM14("CREDENTIAL_NOT_FOUND")
            // Sin autoverificación: el creador no puede aprobar; solo solicitar.
            if (cred.status != M14CredentialStatus.DRAFT &&
                cred.status != M14CredentialStatus.PENDING_VERIFICATION
            ) {
                return@withLock resultFailM14("INVALID_TRANSITION")
            }
            if (!M14Validators.canTransitionCredential(
                    cred.status,
                    M14CredentialStatus.PENDING_VERIFICATION
                ) && cred.status != M14CredentialStatus.PENDING_VERIFICATION
            ) {
                return@withLock resultFailM14("INVALID_TRANSITION")
            }
            val passport = store.passports.value.find { it.id == cred.passportId }
                ?: return@withLock resultFailM14("PASSPORT_NOT_FOUND")
            val pet = resolvePet(passport.petId) ?: return@withLock resultFailM14("PET_NOT_FOUND")
            if (!authority.canCreateOrManage(uid, pet)) return@withLock resultFailM14("UNAUTHORIZED")
            val now = System.currentTimeMillis()
            val updated = cred.copy(
                status = M14CredentialStatus.PENDING_VERIFICATION,
                updatedAt = now
            )
            store.upsertCredential(updated)
            val req = M14VerificationRequest(
                id = store.nextId("m14_vreq"),
                credentialId = credentialId,
                requestedBy = uid,
                targetOrganizationId = targetOrganizationId,
                status = M14VerificationRequestStatus.PENDING,
                requestedAt = now
            )
            store.upsertRequest(req)
            store.audit(M14AuditEvents.VERIFICATION_REQUESTED, req.id)
            store.recordM06(M14M06Hooks.VERIFICATION_REQUESTED, req.id)
            Result.success(req)
        }
}

class MockM14VerificationRepository(
    private val store: M14MemoryStore,
    private val actorUserId: () -> String?,
    private val authority: M14AuthorityPolicy = MockM14AuthorityPolicy()
) : M14VerificationRepository {

    override fun observeRequests(passportId: String): Flow<List<M14VerificationRequest>> =
        store.verificationRequests.map { reqs ->
            val credIds = store.credentials.value
                .filter { it.passportId == passportId }
                .map { it.id }
                .toSet()
            reqs.filter { it.credentialId in credIds }.sortedByDescending { it.requestedAt }
        }

    override suspend fun resolveLocal(
        requestId: String,
        approve: Boolean,
        reasonCode: String,
        notePrivate: String?
    ): Result<M14VerificationRequest> =
        store.withLock {
            if (store.forceFailure) return@withLock resultFailM14("M14_REPOSITORY_FAILURE")
            val uid = actorUserId() ?: return@withLock resultFailM14("NOT_AUTHENTICATED")
            val req = store.verificationRequests.value.find { it.id == requestId }
                ?: return@withLock resultFailM14("CREDENTIAL_NOT_FOUND")
            if (req.status != M14VerificationRequestStatus.PENDING) {
                return@withLock resultFailM14("VERIFICATION_ALREADY_FINAL")
            }
            val cred = store.credentials.value.find { it.id == req.credentialId }
                ?: return@withLock resultFailM14("CREDENTIAL_NOT_FOUND")
            // Autoverificación prohibida.
            if (uid == req.requestedBy || uid == cred.createdBy) {
                return@withLock resultFailM14("VERIFICATION_NOT_ALLOWED")
            }
            if (!authority.canVerifyAsIssuer(uid, cred.createdBy) && !authority.canModerate(uid)) {
                return@withLock resultFailM14("UNAUTHORIZED")
            }
            val now = System.currentTimeMillis()
            val decisionStatus = if (approve) {
                M14VerificationRequestStatus.APPROVED
            } else {
                M14VerificationRequestStatus.REJECTED
            }
            val updatedReq = req.copy(
                status = decisionStatus,
                resolvedAt = now,
                resolutionReason = reasonCode
            )
            store.upsertRequest(updatedReq)
            store.appendDecision(
                M14VerificationDecision(
                    id = store.nextId("m14_vdec"),
                    requestId = requestId,
                    decision = decisionStatus,
                    actorUserId = uid,
                    actorAuthority = "ISSUER_OR_MODERATOR",
                    reasonCode = reasonCode,
                    notePrivate = notePrivate,
                    createdAt = now
                )
            )
            val newCredStatus =
                if (approve) M14CredentialStatus.VERIFIED else M14CredentialStatus.REJECTED
            store.upsertCredential(cred.copy(status = newCredStatus, updatedAt = now))
            store.audit(M14AuditEvents.VERIFICATION_RESOLVED, requestId)
            Result.success(updatedReq)
        }
}

/** Stub remoto B1: sin SQL / sin Supabase real. */
class SupabaseM14PassportRepository : M14PassportRepository {
    private fun fail(): Result<Nothing> = resultFailM14("INFRASTRUCTURE_UNAVAILABLE")
    override fun observeMyPassports(): Flow<List<M14PetPassport>> =
        MutableStateFlow(emptyList())
    override fun observePassport(passportId: String): Flow<M14PetPassport?> =
        MutableStateFlow(null)
    override fun observePassportForPet(petId: String): Flow<M14PetPassport?> =
        MutableStateFlow(null)
    override suspend fun getPassport(passportId: String) = fail()
    override suspend fun createPassport(input: CreateM14PassportInput) = fail()
    override suspend fun updatePassport(passportId: String, input: UpdateM14PassportInput) = fail()
    override suspend fun activatePassport(passportId: String) = fail()
    override suspend fun transitionPassport(
        passportId: String,
        to: M14PassportStatus,
        reason: String?
    ) = fail()
    override fun observeHistory(passportId: String): Flow<List<M14PassportHistory>> =
        MutableStateFlow(emptyList())
    override suspend fun getPublicProjection(publicCode: String) = fail()
}

class SupabaseM14CredentialRepository : M14CredentialRepository {
    private fun fail(): Result<Nothing> = resultFailM14("INFRASTRUCTURE_UNAVAILABLE")
    override fun observeCredentials(passportId: String): Flow<List<M14Credential>> =
        MutableStateFlow(emptyList())
    override fun observeCredential(credentialId: String): Flow<M14Credential?> =
        MutableStateFlow(null)
    override suspend fun getCredential(credentialId: String) = fail()
    override suspend fun createCredential(input: CreateM14CredentialInput) = fail()
    override suspend fun requestVerification(credentialId: String, targetOrganizationId: String?) =
        fail()
}

class SupabaseM14VerificationRepository : M14VerificationRepository {
    private fun fail(): Result<Nothing> = resultFailM14("INFRASTRUCTURE_UNAVAILABLE")
    override fun observeRequests(passportId: String): Flow<List<M14VerificationRequest>> =
        MutableStateFlow(emptyList())
    override suspend fun resolveLocal(
        requestId: String,
        approve: Boolean,
        reasonCode: String,
        notePrivate: String?
    ) = fail()
}
