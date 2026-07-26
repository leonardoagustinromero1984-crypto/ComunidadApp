package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.CreateM14CredentialInput
import com.comunidapp.app.data.model.CreateM14PassportInput
import com.comunidapp.app.data.model.M14CredentialStatus
import com.comunidapp.app.data.model.M14CredentialType
import com.comunidapp.app.data.model.M14M06Hooks
import com.comunidapp.app.data.model.M14PassportStatus
import com.comunidapp.app.data.model.M14PermissionCodes
import com.comunidapp.app.data.model.M14Visibility
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m14.M14ErrorMapper
import com.comunidapp.app.data.repository.M14MemoryStore
import com.comunidapp.app.data.repository.M14PassportNumberGenerator
import com.comunidapp.app.data.repository.M14Validators
import com.comunidapp.app.data.repository.MockM14AuthorityPolicy
import com.comunidapp.app.data.repository.MockM14CredentialRepository
import com.comunidapp.app.data.repository.MockM14PassportRepository
import com.comunidapp.app.data.repository.MockM14VerificationRepository
import com.comunidapp.app.navigation.NavRoutes
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * LeoVer M14 Bloque 1 — fundación local de pasaporte.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class M14FoundationTest {

    private lateinit var store: M14MemoryStore
    private lateinit var pets: MutableMap<String, Pet>
    private lateinit var passports: MockM14PassportRepository
    private lateinit var credentials: MockM14CredentialRepository
    private lateinit var verifications: MockM14VerificationRepository

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

    private val otherPet = Pet(
        id = "pet_other",
        ownerId = "user_2",
        name = "Michi",
        species = PetSpecies.CAT,
        sex = PetSex.MALE,
        ageYears = 2,
        size = PetSize.SMALL,
        description = "demo"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = M14MemoryStore()
        pets = mutableMapOf(ownerPet.id to ownerPet, otherPet.id to otherPet)
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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun create_passport_from_responsible() = runTest {
        val p = passports.createPassport(
            CreateM14PassportInput(
                petId = ownerPet.id,
                displayName = "Luna",
                species = PetSpecies.DOG
            )
        ).getOrThrow()
        assertEquals(M14PassportStatus.DRAFT, p.status)
        assertTrue(p.passportNumber.startsWith("LV-AR-"))
        assertNotNull(p.publicCode)
        assertNotEquals(p.passportNumber, p.publicCode)
    }

    @Test
    fun duplicate_passport_rejected() = runTest {
        passports.createPassport(
            CreateM14PassportInput(ownerPet.id, "Luna", PetSpecies.DOG)
        ).getOrThrow()
        val second = passports.createPassport(
            CreateM14PassportInput(ownerPet.id, "Luna", PetSpecies.DOG)
        )
        assertEquals("PASSPORT_ALREADY_EXISTS", M14ErrorMapper.codeOf(second.exceptionOrNull()!!))
    }

    @Test
    fun transitions_and_terminal_lock() = runTest {
        val p = passports.createPassport(
            CreateM14PassportInput(ownerPet.id, "Luna", PetSpecies.DOG)
        ).getOrThrow()
        passports.activatePassport(p.id).getOrThrow()
        assertEquals(M14PassportStatus.ACTIVE, store.passports.value.first().status)
        val hist = store.history.value.filter { it.passportId == p.id }
        assertTrue(hist.size >= 2)
        passports.transitionPassport(p.id, M14PassportStatus.ARCHIVED, "DONE").getOrThrow()
        val bad = passports.updatePassport(
            p.id,
            com.comunidapp.app.data.model.UpdateM14PassportInput(displayName = "X")
        )
        assertEquals("INVALID_PASSPORT_STATUS", M14ErrorMapper.codeOf(bad.exceptionOrNull()!!))
    }

    @Test
    fun number_generator_no_collision_and_deterministic() {
        val seq = AtomicInteger(0)
        val gen = M14PassportNumberGenerator(clockYear = { 2026 }, sequence = seq)
        val a = gen.nextPassportNumber()
        val b = gen.nextPassportNumber()
        assertEquals("LV-AR-2026-00000001", a)
        assertEquals("LV-AR-2026-00000002", b)
        assertNotEquals(gen.nextPublicCode(), a)
    }

    @Test
    fun credential_dates_and_no_autoverify() = runTest {
        val p = passports.createPassport(
            CreateM14PassportInput(ownerPet.id, "Luna", PetSpecies.DOG)
        ).getOrThrow()
        val badDates = credentials.createCredential(
            CreateM14CredentialInput(
                passportId = p.id,
                type = M14CredentialType.IDENTITY,
                title = "ID",
                issuedAt = 2000L,
                expiresAt = 1000L
            )
        )
        assertEquals(
            "INVALID_CREDENTIAL_DATES",
            M14ErrorMapper.codeOf(badDates.exceptionOrNull()!!)
        )
        val c = credentials.createCredential(
            CreateM14CredentialInput(
                passportId = p.id,
                type = M14CredentialType.MICROCHIP,
                title = "Chip",
                mediaRefs = listOf("m05://passport/chip.pdf")
            )
        ).getOrThrow()
        assertEquals(M14CredentialStatus.DRAFT, c.status)
        val req = credentials.requestVerification(c.id).getOrThrow()
        // Mismo actor (responsable) no puede resolver.
        val selfVerify = MockM14VerificationRepository(
            store = store,
            actorUserId = { "user_1" },
            authority = MockM14AuthorityPolicy(isOrgVerifier = { it == "user_1" })
        ).resolveLocal(req.id, true, "SELF")
        assertEquals(
            "VERIFICATION_NOT_ALLOWED",
            M14ErrorMapper.codeOf(selfVerify.exceptionOrNull()!!)
        )
        verifications.resolveLocal(req.id, true, "ORG_OK").getOrThrow()
        assertEquals(
            M14CredentialStatus.VERIFIED,
            store.credentials.value.first { it.id == c.id }.status
        )
    }

    @Test
    fun foreign_pet_unauthorized() = runTest {
        val r = passports.createPassport(
            CreateM14PassportInput(otherPet.id, "Michi", PetSpecies.CAT)
        )
        assertEquals("UNAUTHORIZED", M14ErrorMapper.codeOf(r.exceptionOrNull()!!))
    }

    @Test
    fun public_projection_redacts_and_masks() = runTest {
        val p = passports.createPassport(
            CreateM14PassportInput(
                petId = ownerPet.id,
                displayName = "Luna",
                species = PetSpecies.DOG,
                microchipNumber = "982000123456789",
                visibility = M14Visibility.PUBLIC_REDACTED
            )
        ).getOrThrow()
        credentials.createCredential(
            CreateM14CredentialInput(
                passportId = p.id,
                type = M14CredentialType.IDENTITY,
                title = "Doc privado",
                visibility = M14Visibility.PRIVATE,
                notePrivate = "secreto",
                mediaRefs = listOf("m05://passport/private.pdf")
            )
        ).getOrThrow()
        val pubCred = credentials.createCredential(
            CreateM14CredentialInput(
                passportId = p.id,
                type = M14CredentialType.OWNERSHIP,
                title = "Titularidad",
                visibility = M14Visibility.PUBLIC_REDACTED
            )
        ).getOrThrow()
        store.upsertCredential(pubCred.copy(status = M14CredentialStatus.VERIFIED))
        val proj = passports.getPublicProjection(p.publicCode!!).getOrThrow()
        assertFalse(proj.toString().contains("user_1"))
        assertFalse(proj.toString().contains(ownerPet.id))
        assertFalse(proj.microchipMasked!!.contains("982000123456789"))
        assertTrue(proj.microchipMasked!!.endsWith("6789"))
        assertTrue(proj.credentialsPublic.none { it.title == "Doc privado" })
        assertTrue(proj.credentialsPublic.any { it.title == "Titularidad" })
        assertEquals(
            "Verificado por una organización",
            proj.credentialsPublic.first().statusLabel
        )
    }

    @Test
    fun media_m05_rejects_http() {
        assertTrue(M14Validators.isSafeMediaRef("m05://passport/a.jpg"))
        assertTrue(M14Validators.isSafeMediaRef("file_asset:abc"))
        assertFalse(M14Validators.isSafeMediaRef("https://cdn.example/x.jpg"))
        assertFalse(M14Validators.isSafeMediaRef("/object/public/leover/x"))
    }

    @Test
    fun m06_m07_prepared_hooks() = runTest {
        val p = passports.createPassport(
            CreateM14PassportInput(ownerPet.id, "Luna", PetSpecies.DOG)
        ).getOrThrow()
        passports.activatePassport(p.id).getOrThrow()
        val hooks = store.m06PreparedHooks.value.map { it.first }.toSet()
        assertTrue(hooks.contains(M14M06Hooks.PASSPORT_CREATED))
        assertTrue(hooks.contains(M14M06Hooks.PASSPORT_ACTIVATED))
        assertTrue(hooks.contains(M14M06Hooks.INFRASTRUCTURE))
        assertTrue(store.auditLog.value.any { it.first.contains("m14.passport") })
    }

    @Test
    fun permissions_constants_and_dataprovider() {
        assertEquals(9, M14PermissionCodes.all.size)
        assertNotNull(DataProvider.m14PassportRepository)
        assertNotNull(DataProvider.m14CredentialRepository)
        assertNotNull(DataProvider.m14VerificationRepository)
    }

    @Test
    fun navigation_routes_present() {
        assertTrue(NavRoutes.M14_PASSPORTS.startsWith("m14/"))
        assertTrue(NavRoutes.m14PetPassport("p1").contains("passport"))
        assertTrue(NavRoutes.m14Public("PUB-1").contains("public"))
    }

    @Test
    fun supabase_repository_is_wired_not_stub() {
        assertNotNull(DataProvider.m14PassportRepository)
        assertTrue(
            DataProvider.m14PassportRepository::class.java.simpleName
                .contains("M14Passport")
        )
        assertTrue(M14ErrorMapper.userMessage("PUBLIC_PASSPORT_NOT_AVAILABLE").isNotBlank())
        assertTrue(M14ErrorMapper.userMessage("PET_NOT_ELIGIBLE").isNotBlank())
    }

    @Test
    fun no_clinical_fields_in_public_projection_type() {
        val names = com.comunidapp.app.data.model.M14PublicPassportProjection::class.java
            .declaredFields
            .map { it.name.lowercase() }
        assertFalse(names.any { it.contains("diagnos") || it.contains("clinic") || it.contains("userid") })
    }

    @Test
    fun isolation_between_pets() = runTest {
        passports.createPassport(
            CreateM14PassportInput(ownerPet.id, "Luna", PetSpecies.DOG)
        ).getOrThrow()
        val otherRepo = MockM14PassportRepository(
            store = store,
            actorUserId = { "user_2" },
            resolvePet = { pets[it] }
        )
        otherRepo.createPassport(
            CreateM14PassportInput(otherPet.id, "Michi", PetSpecies.CAT)
        ).getOrThrow()
        assertEquals(2, store.passports.value.size)
        assertEquals(1, passports.observeMyPassports().first().size)
    }

    @Test
    fun migrations_include_050_and_051() {
        val dir = listOf(
            File("supabase/migrations"),
            File("../supabase/migrations"),
            File("../../supabase/migrations")
        ).first { it.isDirectory }
        val names = dir.listFiles()?.map { it.name }.orEmpty()
        assertTrue(names.any { it.startsWith("049_") })
        assertTrue(names.any { it.startsWith("050_") })
        assertTrue(names.any { it.startsWith("051_") })
        assertTrue(names.any { it.startsWith("052_") }); assertFalse(names.any { it.startsWith("053_") })
    }
}

class M14StaticGuardsTest {
    private fun repoRoot(): File = listOf(File("."), File(".."), File("../.."))
        .first { File(it, "supabase/migrations").isDirectory }

    @Test
    fun migration_050_and_m14_sources_have_no_service_role() {
        val names = File(repoRoot(), "supabase/migrations").listFiles()?.map { it.name }.orEmpty()
        assertTrue(names.any { it.startsWith("050_") })
        val files = listOf(
            "app/src/main/java/com/comunidapp/app/data/repository/M14Repositories.kt",
            "app/src/main/java/com/comunidapp/app/data/model/M14PassportModels.kt"
        )
        files.forEach { rel ->
            val text = File(repoRoot(), rel).readText().lowercase()
            assertFalse("$rel service_role", text.contains("service_role"))
            assertFalse("$rel push enviado", text.contains("push enviado"))
        }
    }

    @Test
    fun dataprovider_wires_m14() {
        val text = File(
            repoRoot(),
            "app/src/main/java/com/comunidapp/app/data/provider/DataProvider.kt"
        ).readText()
        assertTrue(text.contains("m14PassportRepository"))
        assertTrue(text.contains("MockM14PassportRepository"))
        assertTrue(text.contains("SupabaseM14PassportRepository"))
    }

    @Test
    fun no_secrets_in_quality_docs() {
        val docs = listOf(
            "docs/04-calidad/M13-Aplicacion-y-Validacion-Migracion-048-v1.0.md",
            "docs/04-calidad/M13-Aplicacion-y-Validacion-Migracion-049-v1.0.md"
        )
        docs.forEach { rel ->
            val f = File(repoRoot(), rel)
            if (!f.exists()) return@forEach
            val t = f.readText()
            assertFalse(t.contains("eyJ"))
            assertFalse(t.contains("service_role_key"))
            assertFalse(Regex("sk_live_[A-Za-z0-9]+").containsMatchIn(t))
        }
    }
}
