package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM14CredentialInput
import com.comunidapp.app.data.model.CreateM14PassportInput
import com.comunidapp.app.data.model.IssueVerifiedM14CredentialInput
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
    val decisions: StateFlow<List<M14VerificationDecision>> = _decisions.asStateFlow()
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
    suspend fun rotatePublicCode(passportId: String): Result<M14PetPassport>
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
    suspend fun issueVerified(input: IssueVerifiedM14CredentialInput): Result<M14Credential>
    suspend fun revokeVerified(
        credentialId: String,
        reasonCode: String,
        notePrivate: String? = null
    ): Result<M14Credential>
}

interface M14VerificationRepository {
    fun observeRequests(passportId: String): Flow<List<M14VerificationRequest>>
    suspend fun listManaged(): Result<List<M14VerificationRequest>>
    suspend fun getRequest(requestId: String): Result<M14VerificationRequest>
    suspend fun openReview(requestId: String): Result<M14VerificationRequest>
    suspend fun approve(
        requestId: String,
        reasonCode: String,
        notePrivate: String? = null
    ): Result<M14VerificationRequest>
    suspend fun reject(
        requestId: String,
        reasonCode: String,
        notePrivate: String? = null
    ): Result<M14VerificationRequest>
    suspend fun expire(requestId: String): Result<M14VerificationRequest>
    suspend fun getDecision(requestId: String): Result<M14VerificationDecision>
    suspend fun listDecisions(requestId: String): Result<List<M14VerificationDecision>>
    /** Maps to approve/reject (opens review first when still PENDING — mock/UI parity). */
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

    override suspend fun rotatePublicCode(passportId: String): Result<M14PetPassport> =
        store.withLock {
            if (store.forceFailure) return@withLock resultFailM14("M14_REPOSITORY_FAILURE")
            val uid = actorUserId() ?: return@withLock resultFailM14("NOT_AUTHENTICATED")
            val current = store.passports.value.find { it.id == passportId }
                ?: return@withLock resultFailM14("PASSPORT_NOT_FOUND")
            if (current.status.isTerminal) {
                return@withLock resultFailM14("PUBLIC_CODE_ROTATION_NOT_ALLOWED")
            }
            val pet = resolvePet(current.petId) ?: return@withLock resultFailM14("PET_NOT_FOUND")
            if (!authority.canCreateOrManage(uid, pet) && !authority.canModerate(uid)) {
                return@withLock resultFailM14("PUBLIC_CODE_ROTATION_NOT_ALLOWED")
            }
            val now = System.currentTimeMillis()
            var next: String
            var attempts = 0
            do {
                next = store.numberGenerator.nextPublicCode()
                attempts++
                if (attempts > 5) return@withLock resultFailM14("CONFLICT")
            } while (M14Validators.publicCodeLooksLikePii(next))
            val updated = current.copy(publicCode = next, updatedAt = now)
            store.upsertPassport(updated)
            store.appendHistory(
                M14PassportHistory(
                    id = store.nextId("m14_hist"),
                    passportId = passportId,
                    fromStatus = current.status,
                    toStatus = current.status,
                    actorUserId = uid,
                    reason = "PUBLIC_CODE_ROTATED",
                    createdAt = now,
                    metadataEvent = "PUBLIC_CODE_ROTATED"
                )
            )
            store.recordM06(M14M06Hooks.PUBLIC_CODE_ROTATED, passportId)
            Result.success(updated)
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

    override suspend fun issueVerified(input: IssueVerifiedM14CredentialInput): Result<M14Credential> =
        store.withLock {
            if (store.forceFailure) return@withLock resultFailM14("M14_REPOSITORY_FAILURE")
            val uid = actorUserId() ?: return@withLock resultFailM14("NOT_AUTHENTICATED")
            M14Validators.validateIssueVerified(input)?.let { return@withLock resultFailM14(it) }
            val passport = store.passports.value.find { it.id == input.passportId }
                ?: return@withLock resultFailM14("PASSPORT_NOT_FOUND")
            if (passport.status.isTerminal) return@withLock resultFailM14("INVALID_PASSPORT_STATUS")
            val pet = resolvePet(passport.petId) ?: return@withLock resultFailM14("PET_NOT_FOUND")
            if (authority.canCreateOrManage(uid, pet) && !authority.canModerate(uid)) {
                return@withLock resultFailM14("SELF_VERIFICATION_NOT_ALLOWED")
            }
            if (!authority.canVerifyAsIssuer(uid, passport.createdBy) && !authority.canModerate(uid)) {
                return@withLock resultFailM14("ISSUER_NOT_AUTHORIZED")
            }
            val now = System.currentTimeMillis()
            val cred = M14Credential(
                id = store.nextId("m14_cred"),
                passportId = input.passportId,
                type = input.type,
                title = input.title.trim(),
                issuerOrganizationId = input.issuerOrganizationId,
                issuerProfessionalId = input.issuerProfessionalId,
                issuedAt = input.issuedAt ?: now,
                expiresAt = input.expiresAt,
                status = M14CredentialStatus.VERIFIED,
                visibility = input.visibility,
                mediaRefs = input.mediaRefs,
                externalReferenceMasked = input.externalReferenceMasked,
                notePrivate = input.notePrivate,
                createdBy = uid,
                createdAt = now,
                updatedAt = now
            )
            store.upsertCredential(cred)
            store.appendHistory(
                M14PassportHistory(
                    id = store.nextId("m14_hist"),
                    passportId = passport.id,
                    fromStatus = passport.status,
                    toStatus = passport.status,
                    actorUserId = uid,
                    reason = "ISSUED",
                    createdAt = now,
                    metadataEvent = "CREDENTIAL_ISSUED"
                )
            )
            store.recordM06(M14M06Hooks.CREDENTIAL_ISSUED, cred.id)
            Result.success(cred)
        }

    override suspend fun revokeVerified(
        credentialId: String,
        reasonCode: String,
        notePrivate: String?
    ): Result<M14Credential> =
        store.withLock {
            if (store.forceFailure) return@withLock resultFailM14("M14_REPOSITORY_FAILURE")
            val uid = actorUserId() ?: return@withLock resultFailM14("NOT_AUTHENTICATED")
            val cred = store.credentials.value.find { it.id == credentialId }
                ?: return@withLock resultFailM14("CREDENTIAL_NOT_FOUND")
            if (cred.status == M14CredentialStatus.REVOKED) return@withLock Result.success(cred)
            if (cred.status != M14CredentialStatus.VERIFIED) {
                return@withLock resultFailM14("CREDENTIAL_REVOCATION_NOT_ALLOWED")
            }
            val passport = store.passports.value.find { it.id == cred.passportId }
                ?: return@withLock resultFailM14("PASSPORT_NOT_FOUND")
            val canIssuer = uid == cred.createdBy ||
                authority.canVerifyAsIssuer(uid, cred.createdBy) ||
                authority.canModerate(uid)
            if (!canIssuer) {
                return@withLock resultFailM14("CREDENTIAL_REVOCATION_NOT_ALLOWED")
            }
            val now = System.currentTimeMillis()
            val updated = cred.copy(
                status = M14CredentialStatus.REVOKED,
                notePrivate = notePrivate ?: cred.notePrivate,
                updatedAt = now
            )
            store.upsertCredential(updated)
            store.appendHistory(
                M14PassportHistory(
                    id = store.nextId("m14_hist"),
                    passportId = passport.id,
                    fromStatus = passport.status,
                    toStatus = passport.status,
                    actorUserId = uid,
                    reason = reasonCode.ifBlank { "REVOKED" },
                    createdAt = now,
                    metadataEvent = "CREDENTIAL_REVOKED"
                )
            )
            store.recordM06(M14M06Hooks.CREDENTIAL_REVOKED, updated.id)
            Result.success(updated)
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

    override suspend fun listManaged(): Result<List<M14VerificationRequest>> {
        if (store.forceFailure) return resultFailM14("M14_REPOSITORY_FAILURE")
        val uid = actorUserId() ?: return resultFailM14("NOT_AUTHENTICATED")
        val items = store.verificationRequests.value.filter { req ->
            req.status == M14VerificationRequestStatus.PENDING ||
                req.status == M14VerificationRequestStatus.UNDER_REVIEW
        }.filter { req ->
            val cred = store.credentials.value.find { it.id == req.credentialId } ?: return@filter false
            canDecide(uid, req, cred)
        }.sortedByDescending { it.requestedAt }
        return Result.success(items)
    }

    override suspend fun getRequest(requestId: String): Result<M14VerificationRequest> {
        if (store.forceFailure) return resultFailM14("M14_REPOSITORY_FAILURE")
        val req = store.verificationRequests.value.find { it.id == requestId }
            ?: return resultFailM14("VERIFICATION_NOT_FOUND")
        return Result.success(req)
    }

    override suspend fun openReview(requestId: String): Result<M14VerificationRequest> =
        store.withLock {
            if (store.forceFailure) return@withLock resultFailM14("M14_REPOSITORY_FAILURE")
            val uid = actorUserId() ?: return@withLock resultFailM14("NOT_AUTHENTICATED")
            val req = store.verificationRequests.value.find { it.id == requestId }
                ?: return@withLock resultFailM14("VERIFICATION_NOT_FOUND")
            val cred = store.credentials.value.find { it.id == req.credentialId }
                ?: return@withLock resultFailM14("CREDENTIAL_NOT_FOUND")
            if (!canDecide(uid, req, cred)) {
                return@withLock resultFailM14("VERIFICATION_REVIEW_NOT_ALLOWED")
            }
            if (req.status == M14VerificationRequestStatus.UNDER_REVIEW) {
                return@withLock Result.success(req)
            }
            if (req.status != M14VerificationRequestStatus.PENDING) {
                return@withLock resultFailM14("VERIFICATION_ALREADY_FINAL")
            }
            val now = System.currentTimeMillis()
            val updated = req.copy(status = M14VerificationRequestStatus.UNDER_REVIEW)
            store.upsertRequest(updated)
            appendCredentialEvent(cred.passportId, uid, "REVIEW_OPENED", "VERIFICATION_REVIEW_OPENED", now)
            store.recordM06(M14M06Hooks.VERIFICATION_REVIEW_OPENED, requestId)
            Result.success(updated)
        }

    override suspend fun approve(
        requestId: String,
        reasonCode: String,
        notePrivate: String?
    ): Result<M14VerificationRequest> =
        decideFinal(requestId, approve = true, reasonCode = reasonCode, notePrivate = notePrivate)

    override suspend fun reject(
        requestId: String,
        reasonCode: String,
        notePrivate: String?
    ): Result<M14VerificationRequest> =
        decideFinal(requestId, approve = false, reasonCode = reasonCode, notePrivate = notePrivate)

    override suspend fun expire(requestId: String): Result<M14VerificationRequest> =
        store.withLock {
            if (store.forceFailure) return@withLock resultFailM14("M14_REPOSITORY_FAILURE")
            val uid = actorUserId() ?: return@withLock resultFailM14("NOT_AUTHENTICATED")
            val req = store.verificationRequests.value.find { it.id == requestId }
                ?: return@withLock resultFailM14("VERIFICATION_NOT_FOUND")
            val cred = store.credentials.value.find { it.id == req.credentialId }
                ?: return@withLock resultFailM14("CREDENTIAL_NOT_FOUND")
            val allowed = canDecide(uid, req, cred) ||
                uid == req.requestedBy ||
                authority.canModerate(uid)
            if (!allowed) return@withLock resultFailM14("VERIFICATION_REVIEW_NOT_ALLOWED")
            if (req.status == M14VerificationRequestStatus.EXPIRED) {
                return@withLock Result.success(req)
            }
            if (req.status == M14VerificationRequestStatus.APPROVED ||
                req.status == M14VerificationRequestStatus.REJECTED ||
                req.status == M14VerificationRequestStatus.CANCELLED
            ) {
                return@withLock resultFailM14("VERIFICATION_ALREADY_FINAL")
            }
            if (req.status != M14VerificationRequestStatus.PENDING &&
                req.status != M14VerificationRequestStatus.UNDER_REVIEW
            ) {
                return@withLock resultFailM14("INVALID_TRANSITION")
            }
            val now = System.currentTimeMillis()
            val updated = req.copy(
                status = M14VerificationRequestStatus.EXPIRED,
                resolvedAt = now,
                resolutionReason = "EXPIRED"
            )
            store.upsertRequest(updated)
            if (cred.status == M14CredentialStatus.PENDING_VERIFICATION) {
                val nextStatus = if (cred.expiresAt != null && cred.expiresAt <= now) {
                    M14CredentialStatus.EXPIRED
                } else {
                    M14CredentialStatus.DRAFT
                }
                store.upsertCredential(cred.copy(status = nextStatus, updatedAt = now))
            }
            appendCredentialEvent(cred.passportId, uid, "EXPIRED", "VERIFICATION_EXPIRED", now)
            store.recordM06(M14M06Hooks.VERIFICATION_EXPIRED, requestId)
            Result.success(updated)
        }

    override suspend fun getDecision(requestId: String): Result<M14VerificationDecision> {
        if (store.forceFailure) return resultFailM14("M14_REPOSITORY_FAILURE")
        val decision = store.decisions.value.firstOrNull { it.requestId == requestId }
            ?: return resultFailM14("DECISION_NOT_FOUND")
        return Result.success(decision)
    }

    override suspend fun listDecisions(requestId: String): Result<List<M14VerificationDecision>> {
        if (store.forceFailure) return resultFailM14("M14_REPOSITORY_FAILURE")
        return Result.success(
            store.decisions.value.filter { it.requestId == requestId }.sortedByDescending { it.createdAt }
        )
    }

    override suspend fun resolveLocal(
        requestId: String,
        approve: Boolean,
        reasonCode: String,
        notePrivate: String?
    ): Result<M14VerificationRequest> {
        val current = getRequest(requestId).getOrElse { return Result.failure(it) }
        if (current.status == M14VerificationRequestStatus.PENDING) {
            openReview(requestId).getOrElse { return Result.failure(it) }
        }
        return if (approve) {
            approve(requestId, reasonCode, notePrivate)
        } else {
            reject(requestId, reasonCode, notePrivate)
        }
    }

    private fun canDecide(
        uid: String,
        req: M14VerificationRequest,
        cred: M14Credential
    ): Boolean {
        if (uid == req.requestedBy || uid == cred.createdBy) return false
        return authority.canVerifyAsIssuer(uid, cred.createdBy) || authority.canModerate(uid)
    }

    private fun appendCredentialEvent(
        passportId: String,
        actorUserId: String,
        reason: String,
        event: String,
        now: Long
    ) {
        val passport = store.passports.value.find { it.id == passportId } ?: return
        store.appendHistory(
            M14PassportHistory(
                id = store.nextId("m14_hist"),
                passportId = passportId,
                fromStatus = passport.status,
                toStatus = passport.status,
                actorUserId = actorUserId,
                reason = reason,
                createdAt = now,
                metadataEvent = event
            )
        )
    }

    private suspend fun decideFinal(
        requestId: String,
        approve: Boolean,
        reasonCode: String,
        notePrivate: String?
    ): Result<M14VerificationRequest> =
        store.withLock {
            if (store.forceFailure) return@withLock resultFailM14("M14_REPOSITORY_FAILURE")
            val uid = actorUserId() ?: return@withLock resultFailM14("NOT_AUTHENTICATED")
            val req = store.verificationRequests.value.find { it.id == requestId }
                ?: return@withLock resultFailM14("VERIFICATION_NOT_FOUND")
            val cred = store.credentials.value.find { it.id == req.credentialId }
                ?: return@withLock resultFailM14("CREDENTIAL_NOT_FOUND")
            if (uid == req.requestedBy || uid == cred.createdBy) {
                return@withLock resultFailM14("VERIFICATION_NOT_ALLOWED")
            }
            if (!authority.canVerifyAsIssuer(uid, cred.createdBy) && !authority.canModerate(uid)) {
                return@withLock resultFailM14("VERIFICATION_REVIEW_NOT_ALLOWED")
            }
            if (req.status == M14VerificationRequestStatus.PENDING) {
                return@withLock resultFailM14("INVALID_TRANSITION")
            }
            val decisionStatus = if (approve) {
                M14VerificationRequestStatus.APPROVED
            } else {
                M14VerificationRequestStatus.REJECTED
            }
            val existing = store.decisions.value.firstOrNull { it.requestId == requestId }
            if (req.status == decisionStatus && existing != null && existing.decision == decisionStatus) {
                return@withLock Result.success(req)
            }
            if (req.status == M14VerificationRequestStatus.APPROVED ||
                req.status == M14VerificationRequestStatus.REJECTED ||
                req.status == M14VerificationRequestStatus.CANCELLED ||
                req.status == M14VerificationRequestStatus.EXPIRED
            ) {
                return@withLock resultFailM14("VERIFICATION_ALREADY_FINAL")
            }
            if (req.status != M14VerificationRequestStatus.UNDER_REVIEW) {
                return@withLock resultFailM14("INVALID_TRANSITION")
            }
            if (existing != null) {
                return@withLock resultFailM14(
                    if (existing.decision == decisionStatus) "CONFLICT" else "DECISION_ALREADY_EXISTS"
                )
            }
            val now = System.currentTimeMillis()
            val updatedReq = req.copy(
                status = decisionStatus,
                resolvedAt = now,
                resolutionReason = reasonCode.ifBlank { decisionStatus.name }
            )
            store.upsertRequest(updatedReq)
            store.appendDecision(
                M14VerificationDecision(
                    id = store.nextId("m14_vdec"),
                    requestId = requestId,
                    decision = decisionStatus,
                    actorUserId = uid,
                    actorAuthority = "ISSUER_OR_MODERATOR",
                    reasonCode = reasonCode.ifBlank { decisionStatus.name },
                    notePrivate = notePrivate,
                    createdAt = now
                )
            )
            val newCredStatus =
                if (approve) M14CredentialStatus.VERIFIED else M14CredentialStatus.REJECTED
            when {
                cred.status == M14CredentialStatus.PENDING_VERIFICATION -> {
                    store.upsertCredential(cred.copy(status = newCredStatus, updatedAt = now))
                }
                approve && cred.status == M14CredentialStatus.VERIFIED -> Unit
                approve -> return@withLock resultFailM14("CREDENTIAL_ALREADY_FINAL")
                else -> Unit
            }
            appendCredentialEvent(
                cred.passportId,
                uid,
                reasonCode.ifBlank { decisionStatus.name },
                if (approve) "VERIFICATION_APPROVED" else "VERIFICATION_REJECTED",
                now
            )
            store.audit(M14AuditEvents.VERIFICATION_RESOLVED, requestId)
            store.recordM06(
                if (approve) M14M06Hooks.VERIFICATION_APPROVED else M14M06Hooks.VERIFICATION_REJECTED,
                requestId
            )
            Result.success(updatedReq)
        }
}
