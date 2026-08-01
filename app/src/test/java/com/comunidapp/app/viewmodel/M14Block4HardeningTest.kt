package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.CreateM14CredentialInput
import com.comunidapp.app.data.model.CreateM14PassportInput
import com.comunidapp.app.data.model.M14CredentialStatus
import com.comunidapp.app.data.model.M14CredentialType
import com.comunidapp.app.data.model.M14ExpirationPolicy
import com.comunidapp.app.data.model.M14M06Hooks
import com.comunidapp.app.data.model.M14PassportStatus
import com.comunidapp.app.data.model.M14RemoteFallback
import com.comunidapp.app.data.model.M14VerificationRequestStatus
import com.comunidapp.app.data.model.M14Visibility
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.nextStep
import com.comunidapp.app.data.remote.supabase.m14.M14ErrorMapper
import com.comunidapp.app.data.repository.M14MemoryStore
import com.comunidapp.app.data.repository.M14PublicProjectionService
import com.comunidapp.app.data.repository.M14PublicQrPayloadService
import com.comunidapp.app.data.repository.M14Validators
import com.comunidapp.app.data.repository.MockM14AuthorityPolicy
import com.comunidapp.app.data.repository.MockM14CredentialRepository
import com.comunidapp.app.data.repository.MockM14OperationsRepository
import com.comunidapp.app.data.repository.MockM14PassportRepository
import com.comunidapp.app.data.repository.MockM14VerificationRepository
import com.comunidapp.app.data.repository.SupabaseM14OperationsRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * LeoVer M14 Bloque 4 — expiraciones, privacidad, métricas sin PII, M06, fallback remoto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class M14Block4HardeningTest {

    private lateinit var store: M14MemoryStore
    private lateinit var pets: MutableMap<String, Pet>
    private lateinit var passports: MockM14PassportRepository
    private lateinit var credentials: MockM14CredentialRepository
    private lateinit var verifications: MockM14VerificationRepository
    private lateinit var ops: MockM14OperationsRepository

    private val ownerPet = Pet(
        id = "pet_owner_1",
        ownerId = "user_1",
        name = "Luna",
        species = PetSpecies.DOG,
        sex = PetSex.FEMALE,
        ageYears = 3,
        size = PetSize.MEDIUM,
        description = "demo",
        color = "marrón",
        breed = "mestiza",
        microchipId = "982000123456789"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = M14MemoryStore()
        pets = mutableMapOf(ownerPet.id to ownerPet)
        val authority = MockM14AuthorityPolicy(
            isModerator = { it == "mod_1" },
            isOrgVerifier = { it == "org_verifier_1" }
        )
        passports = MockM14PassportRepository(
            store = store,
            actorUserId = { "user_1" },
            resolvePet = { pets[it] },
            authority = authority
        )
        credentials = MockM14CredentialRepository(
            store = store,
            actorUserId = { "user_1" },
            resolvePet = { pets[it] },
            authority = authority
        )
        verifications = MockM14VerificationRepository(
            store = store,
            actorUserId = { "org_verifier_1" },
            authority = authority
        )
        ops = MockM14OperationsRepository(
            store = store,
            actorUserId = { "user_1" }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun seedPassportActive(): String {
        val p = passports.createPassport(
            CreateM14PassportInput(
                petId = ownerPet.id,
                displayName = "Luna",
                species = PetSpecies.DOG,
                microchipNumber = "982000123456789",
                visibility = M14Visibility.PUBLIC_REDACTED
            )
        ).getOrThrow()
        passports.activatePassport(p.id).getOrThrow()
        return p.id
    }

    @Test
    fun expiration_pending_request_by_policy() = runTest {
        val passportId = seedPassportActive()
        val cred = credentials.createCredential(
            CreateM14CredentialInput(
                passportId = passportId,
                type = M14CredentialType.IDENTITY,
                title = "Identidad"
            )
        ).getOrThrow()
        val ownerCreds = MockM14CredentialRepository(
            store = store,
            actorUserId = { "user_1" },
            resolvePet = { pets[it] },
            authority = MockM14AuthorityPolicy(isOrgVerifier = { it == "org_verifier_1" })
        )
        ownerCreds.requestVerification(cred.id).getOrThrow()
        val req = store.verificationRequests.value.first()
        assertEquals(M14VerificationRequestStatus.PENDING, req.status)
        store.upsertRequest(req.copy(requestedAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(20)))
        val policy = M14ExpirationPolicy(pendingRequestTtlDays = 14, underReviewRequestTtlDays = 7)
        val first = ops.applyExpirations(System.currentTimeMillis(), policy).getOrThrow()
        assertTrue(first.expiredRequests >= 1)
        assertEquals("REQUIERE_INFRA_EXTERNA", first.infrastructureNote)
        assertEquals(
            M14VerificationRequestStatus.EXPIRED,
            store.verificationRequests.value.first { it.id == req.id }.status
        )
    }

    @Test
    fun expiration_under_review_request_by_policy() = runTest {
        val passportId = seedPassportActive()
        val cred = credentials.createCredential(
            CreateM14CredentialInput(
                passportId = passportId,
                type = M14CredentialType.MICROCHIP,
                title = "Chip"
            )
        ).getOrThrow()
        MockM14CredentialRepository(
            store = store,
            actorUserId = { "user_1" },
            resolvePet = { pets[it] },
            authority = MockM14AuthorityPolicy(isOrgVerifier = { it == "org_verifier_1" })
        ).requestVerification(cred.id).getOrThrow()
        val reqId = store.verificationRequests.value.first().id
        verifications.openReview(reqId).getOrThrow()
        val under = store.verificationRequests.value.first { it.id == reqId }
        store.upsertRequest(under.copy(requestedAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10)))
        val policy = M14ExpirationPolicy(pendingRequestTtlDays = 14, underReviewRequestTtlDays = 7)
        val result = ops.applyExpirations(System.currentTimeMillis(), policy).getOrThrow()
        assertTrue(result.expiredRequests >= 1)
        assertEquals(
            M14VerificationRequestStatus.EXPIRED,
            store.verificationRequests.value.first { it.id == reqId }.status
        )
    }

    @Test
    fun expiration_verified_credential_by_expires_at() = runTest {
        val passportId = seedPassportActive()
        val issuer = MockM14CredentialRepository(
            store = store,
            actorUserId = { "org_verifier_1" },
            resolvePet = { pets[it] },
            authority = MockM14AuthorityPolicy(isOrgVerifier = { it == "org_verifier_1" })
        )
        val now = System.currentTimeMillis()
        val cred = issuer.issueVerified(
            com.comunidapp.app.data.model.IssueVerifiedM14CredentialInput(
                passportId = passportId,
                type = M14CredentialType.VACCINATION_ATTESTATION,
                title = "Vacuna",
                issuerOrganizationId = "org_1",
                issuedAt = now - TimeUnit.DAYS.toMillis(40),
                expiresAt = now - TimeUnit.DAYS.toMillis(1)
            )
        ).getOrThrow()
        assertEquals(M14CredentialStatus.VERIFIED, cred.status)
        val result = ops.applyExpirations(now).getOrThrow()
        assertTrue(result.expiredCredentials >= 1)
        assertEquals(
            M14CredentialStatus.EXPIRED,
            store.credentials.value.first { it.id == cred.id }.status
        )
    }

    @Test
    fun expiration_preserves_terminal_and_is_idempotent() = runTest {
        val passportId = seedPassportActive()
        val cred = credentials.createCredential(
            CreateM14CredentialInput(
                passportId = passportId,
                type = M14CredentialType.IDENTITY,
                title = "ID"
            )
        ).getOrThrow()
        MockM14CredentialRepository(
            store = store,
            actorUserId = { "user_1" },
            resolvePet = { pets[it] },
            authority = MockM14AuthorityPolicy(isOrgVerifier = { it == "org_verifier_1" })
        ).requestVerification(cred.id).getOrThrow()
        val reqId = store.verificationRequests.value.first().id
        verifications.openReview(reqId).getOrThrow()
        verifications.approve(reqId, "OK").getOrThrow()
        assertEquals(
            M14VerificationRequestStatus.APPROVED,
            store.verificationRequests.value.first { it.id == reqId }.status
        )
        val policy = M14ExpirationPolicy(pendingRequestTtlDays = 1, underReviewRequestTtlDays = 1)
        val farFuture = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(40)
        val first = ops.applyExpirations(farFuture, policy).getOrThrow()
        assertTrue(first.preservedTerminal >= 1)
        assertEquals(
            M14VerificationRequestStatus.APPROVED,
            store.verificationRequests.value.first { it.id == reqId }.status
        )
        val second = ops.applyExpirations(farFuture, policy).getOrThrow()
        assertEquals(0, second.expiredRequests)
        val expireAgain = verifications.expire(reqId)
        assertEquals(
            "EXPIRATION_NOT_ALLOWED",
            M14ErrorMapper.codeOf(expireAgain.exceptionOrNull()!!)
        )
    }

    @Test
    fun expiration_already_applied_on_explicit_retry() = runTest {
        val passportId = seedPassportActive()
        val cred = credentials.createCredential(
            CreateM14CredentialInput(
                passportId = passportId,
                type = M14CredentialType.IDENTITY,
                title = "ID"
            )
        ).getOrThrow()
        MockM14CredentialRepository(
            store = store,
            actorUserId = { "user_1" },
            resolvePet = { pets[it] },
            authority = MockM14AuthorityPolicy(isOrgVerifier = { it == "org_verifier_1" })
        ).requestVerification(cred.id).getOrThrow()
        val reqId = store.verificationRequests.value.first().id
        verifications.expire(reqId).getOrThrow()
        val again = verifications.expire(reqId)
        assertEquals(
            "EXPIRATION_ALREADY_APPLIED",
            M14ErrorMapper.codeOf(again.exceptionOrNull()!!)
        )
    }

    @Test
    fun privacy_public_projection_and_qr_without_pii() = runTest {
        val passportId = seedPassportActive()
        val p = store.passports.value.first { it.id == passportId }
        val code = p.publicCode
        assertNotNull(code)
        credentials.createCredential(
            CreateM14CredentialInput(
                passportId = passportId,
                type = M14CredentialType.IDENTITY,
                title = "ID pública",
                visibility = M14Visibility.PUBLIC_REDACTED,
                notePrivate = "secreto-privado-no-exponer"
            )
        ).getOrThrow()
        val projection = M14PublicProjectionService.project(p, store.credentials.value)!!
        val blob = projection.toString().lowercase()
        assertFalse(blob.contains("pet_owner"))
        assertFalse(blob.contains("user_1"))
        assertFalse(blob.contains("secreto-privado"))
        assertFalse(blob.contains(p.passportNumber.lowercase()))
        assertFalse(blob.contains("982000123456789"))
        assertTrue(M14Validators.publicProjectionHasNoSensitiveLeak(blob))
        val masked = projection.microchipMasked
        assertNotNull(masked)
        assertFalse(masked!!.contains("982000123456789"))
        val qr = M14PublicQrPayloadService.buildPayload(code!!).getOrThrow()
        assertTrue(qr.startsWith("leover://passport/"))
        assertTrue(qr.endsWith(code))
        assertFalse(qr.contains(p.passportNumber))
        assertFalse(qr.contains("user_"))
        assertEquals("OPEN_REVIEW", M14VerificationRequestStatus.PENDING.nextStep().name)
    }

    @Test
    fun metrics_aggregate_without_pii_and_invalid_range() = runTest {
        seedPassportActive()
        val now = System.currentTimeMillis()
        val metrics = ops.getOperationalMetrics(now - TimeUnit.DAYS.toMillis(7), now + 1).getOrThrow()
        assertTrue(metrics.passportsByStatus.isNotEmpty())
        assertTrue(metrics.credentialsByStatus.isNotEmpty() || metrics.passportsByStatus.isNotEmpty())
        assertEquals("America/Argentina/Buenos_Aires", metrics.zoneIdName)
        val blob = metrics.toString().lowercase()
        assertFalse(blob.contains("user_1"))
        assertFalse(blob.contains("@"))
        assertFalse(blob.contains("whatsapp"))
        assertFalse(blob.contains("note_private") || blob.contains("noteprivate"))
        assertFalse(blob.contains("982000"))
        val bad = ops.getOperationalMetrics(now, now - 1)
        assertEquals("METRICS_INVALID_RANGE", M14ErrorMapper.codeOf(bad.exceptionOrNull()!!))
    }

    @Test
    fun m06_hooks_prepared_without_push_claim() = runTest {
        val passportId = seedPassportActive()
        assertTrue(store.m06PreparedHooks.value.any { it.first == M14M06Hooks.PASSPORT_CREATED })
        assertTrue(store.m06PreparedHooks.value.any { it.first == M14M06Hooks.INFRASTRUCTURE })
        assertFalse(store.m06PreparedHooks.value.any { it.first.contains("PUSH_SENT") })
        val issuer = MockM14CredentialRepository(
            store = store,
            actorUserId = { "org_verifier_1" },
            resolvePet = { pets[it] },
            authority = MockM14AuthorityPolicy(isOrgVerifier = { it == "org_verifier_1" })
        )
        issuer.issueVerified(
            com.comunidapp.app.data.model.IssueVerifiedM14CredentialInput(
                passportId = passportId,
                type = M14CredentialType.IDENTITY,
                title = "Emitida",
                issuerOrganizationId = "org_1",
                expiresAt = System.currentTimeMillis() - 1000L
            )
        ).getOrThrow()
        ops.applyExpirations(System.currentTimeMillis()).getOrThrow()
        assertTrue(store.m06PreparedHooks.value.any { it.first == M14M06Hooks.CREDENTIAL_EXPIRED })
        assertTrue(M14M06Hooks.all.contains(M14M06Hooks.CREDENTIAL_EXPIRED))
    }

    @Test
    fun supabase_ops_reports_remote_validation_pending() = runTest {
        val remote = SupabaseM14OperationsRepository()
        val exp = remote.applyExpirations()
        assertEquals(
            "REMOTE_VALIDATION_PENDING",
            M14ErrorMapper.codeOf(exp.exceptionOrNull()!!)
        )
        assertEquals(M14RemoteFallback.CODE, "REMOTE_VALIDATION_PENDING")
        val met = remote.getOperationalMetrics(0L, 1L)
        assertEquals(
            "REMOTE_VALIDATION_PENDING",
            M14ErrorMapper.codeOf(met.exceptionOrNull()!!)
        )
        val badRange = remote.getOperationalMetrics(10L, 1L)
        assertEquals(
            "METRICS_INVALID_RANGE",
            M14ErrorMapper.codeOf(badRange.exceptionOrNull()!!)
        )
    }

    @Test
    fun error_codes_block4_mapped() {
        listOf(
            "EXPIRATION_NOT_ALLOWED",
            "EXPIRATION_ALREADY_APPLIED",
            "METRICS_INVALID_RANGE",
            "PUBLIC_CODE_UNAVAILABLE",
            "HISTORY_UNAVAILABLE",
            "REMOTE_VALIDATION_PENDING",
            "CONFLICT"
        ).forEach { code ->
            val msg = M14ErrorMapper.userMessage(code)
            assertFalse(msg.contains("Ocurrió un error en el pasaporte"))
            assertTrue(msg.isNotBlank())
        }
    }

    @Test
    fun terminal_passport_status_preserved() = runTest {
        val passportId = seedPassportActive()
        passports.transitionPassport(passportId, M14PassportStatus.REVOKED, "TEST").getOrThrow()
        assertEquals(M14PassportStatus.REVOKED, store.passports.value.first { it.id == passportId }.status)
        ops.applyExpirations(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(100)).getOrThrow()
        assertEquals(M14PassportStatus.REVOKED, store.passports.value.first { it.id == passportId }.status)
    }
}
