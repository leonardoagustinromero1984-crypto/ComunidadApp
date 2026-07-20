package com.comunidapp.app.data.remote.supabase.m08

import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.repository.LegacyPetRepositoryAdapter
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.pets.PetCapability
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.app.domain.pets.PetResponsibility
import com.comunidapp.app.domain.pets.PetResponsibilityId
import com.comunidapp.app.domain.pets.PetResponsibilityRole
import com.comunidapp.app.domain.pets.PetLinkStatus
import com.comunidapp.app.domain.pets.PetTransfer
import com.comunidapp.app.domain.pets.PetTransferId
import com.comunidapp.app.domain.pets.PetTransferStatus
import com.comunidapp.app.data.repository.SupabasePetAuthorizationRepository
import com.comunidapp.app.data.repository.SupabasePetDomainRepository
import com.comunidapp.app.data.repository.SupabasePetResponsibilityRepository
import com.comunidapp.app.data.repository.SupabasePetTransferRepository
import com.comunidapp.app.domain.pets.PetAuthorization
import com.comunidapp.app.domain.pets.PetAuthorizationId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * LeoVer M08 Etapa 4B — adapter / mapper / error scenarios (FakePetM08RemoteDataSource).
 */
class LegacyPetRepositoryAdapterTest {

    private lateinit var fake: FakePetM08RemoteDataSource
    private lateinit var adapter: LegacyPetRepositoryAdapter

    @Before
    fun setUp() {
        fake = FakePetM08RemoteDataSource()
        adapter = LegacyPetRepositoryAdapter(
            remote = fake,
            authUidProvider = { "user-1" },
            scope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined
            )
        )
    }

    private fun samplePet(id: String = "", ownerId: String? = "user-1") = Pet(
        id = id,
        ownerId = ownerId,
        name = "Luna",
        species = PetSpecies.DOG,
        sex = PetSex.FEMALE,
        ageYears = 2,
        size = PetSize.MEDIUM,
        description = "Amigable"
    )

    // --- S01–S05 list / privacy ---

    @Test
    fun s01_listAccessiblePets_returnsSeeded() = runTest {
        fake.seedAccessible(
            AccessiblePetM08Row(
                id = "p1",
                ownerId = "user-1",
                name = "Luna",
                species = "DOG",
                sex = "FEMALE",
                size = "MEDIUM",
                canUpdate = true
            )
        )
        val list = fake.listAccessiblePets("ACTIVE")
        assertEquals(1, list.size)
        assertEquals("Luna", list.first().name)
    }

    @Test
    fun s02_observePetsForOwner_otherUser_empty() = runTest {
        fake.seedAccessible(
            AccessiblePetM08Row(
                id = "p1", ownerId = "user-1", name = "Luna",
                species = "DOG", sex = "FEMALE", size = "MEDIUM"
            )
        )
        val other = LegacyPetRepositoryAdapter(
            remote = fake,
            authUidProvider = { "user-1" },
            scope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined
            )
        )
        val pets = other.getPetsByOwner("user-2")
        assertTrue(pets.isEmpty())
    }

    @Test
    fun s03_getPetsByOwner_self_usesAccessibleCache() = runTest {
        fake.seedAccessible(
            AccessiblePetM08Row(
                id = "p1", ownerId = "user-1", name = "Luna",
                species = "DOG", sex = "FEMALE", size = "MEDIUM", status = "ACTIVE"
            )
        )
        // refresh via create path side effect: call list through get after manual cache
        adapter.createPet(samplePet())
        val pets = adapter.getPetsByOwner("user-1")
        assertTrue(pets.isNotEmpty())
    }

    @Test
    fun s04_orgPrincipal_ownerIdNull_neverEmptyString() = runTest {
        fake.forceCreateFailCode = null
        val created = fake.createPetWithPrincipal(
            CreatePetWithPrincipalParams(
                name = "OrgPet",
                species = "DOG",
                sex = "UNKNOWN",
                size = "MEDIUM",
                description = "x",
                organizationId = "org-1"
            )
        )
        assertNull(created.ownerId)
        assertFalse(created.ownerId == "")
    }

    @Test
    fun s05_mapper_nullableOwnerId() {
        val pet = PetM08Row(
            id = "p1",
            ownerId = null,
            name = "X",
            species = "DOG",
            sex = "UNKNOWN",
            size = "MEDIUM"
        ).let { PetM08Mappers.run { it.toPet() } }
        assertNull(pet.ownerId)
    }

    // --- S06–S12 create / partial ---

    @Test
    fun s06_createPet_success_callsProfileAndHealth() = runTest {
        val result = adapter.createPet(samplePet())
        assertTrue(result.isSuccess)
        assertEquals(1, fake.createCalls)
        assertEquals(1, fake.profileCalls)
        assertEquals(1, fake.healthCalls)
    }

    @Test
    fun s07_createPet_profileFail_partialException_keepsPet() = runTest {
        fake.forceProfileFail = true
        val result = adapter.createPet(samplePet())
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as PetCreatePartialException
        assertEquals("profile", ex.failedStage)
        assertTrue(fake.pets.containsKey(ex.petId))
    }

    @Test
    fun s08_createPet_healthFail_partialException_noDelete() = runTest {
        fake.forceHealthFail = true
        val result = adapter.createPet(samplePet())
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull() as PetCreatePartialException
        assertEquals("health", ex.failedStage)
        assertTrue(fake.pets.containsKey(ex.petId))
    }

    @Test
    fun s09_createPet_nameRequired() = runTest {
        fake.forceCreateFailCode = "PET_NAME_REQUIRED"
        val result = adapter.createPet(samplePet().copy(name = " "))
        // create params still send trimmed name from mapper — force code on remote
        assertTrue(result.isFailure)
        assertEquals("PET_NAME_REQUIRED", M08PetErrorMapper.codeOf(result.exceptionOrNull()!!))
    }

    @Test
    fun s10_createPet_notAuthenticated() = runTest {
        fake.authRequired = true
        val result = adapter.createPet(samplePet())
        assertTrue(result.isFailure)
        assertEquals("NOT_AUTHENTICATED", M08PetErrorMapper.codeOf(result.exceptionOrNull()!!))
    }

    @Test
    fun s11_noDirectInsertFlag() {
        assertFalse(fake.insertAttempted)
    }

    @Test
    fun s12_create_returnsPetId() = runTest {
        val id = adapter.createPet(samplePet()).getOrThrow()
        assertTrue(id.startsWith("pet-"))
    }

    // --- S13–S18 update / archive / avatar ---

    @Test
    fun s13_updatePet_callsProfileAndHealth() = runTest {
        val id = adapter.createPet(samplePet()).getOrThrow()
        fake.profileCalls = 0
        fake.healthCalls = 0
        val result = adapter.updatePet(samplePet(id = id))
        assertTrue(result.isSuccess)
        assertEquals(1, fake.profileCalls)
        assertEquals(1, fake.healthCalls)
    }

    @Test
    fun s14_updatePet_microchipConflict() = runTest {
        val id = adapter.createPet(samplePet()).getOrThrow()
        val result = adapter.updatePet(samplePet(id = id).copy(microchipId = "CONFLICT"))
        assertTrue(result.isFailure)
        assertEquals(
            "PET_MICROCHIP_ACTIVE_CONFLICT",
            M08PetErrorMapper.codeOf(result.exceptionOrNull()!!)
        )
    }

    @Test
    fun s15_deletePet_archives() = runTest {
        val id = adapter.createPet(samplePet()).getOrThrow()
        val result = adapter.deletePet(id)
        assertTrue(result.isSuccess)
        assertEquals(1, fake.archiveCalls)
        assertEquals("ARCHIVED", fake.pets[id]?.status)
    }

    @Test
    fun s16_setPetAvatarAsset_doesNotWritePhotoUrl() = runTest {
        val id = adapter.createPet(samplePet().copy(photoUrl = "https://legacy")).getOrThrow()
        fake.pets[id] = fake.pets[id]!!.copy(photoUrl = "https://legacy")
        val updated = adapter.setPetAvatarAsset(id, "asset-1").getOrThrow()
        assertEquals("asset-1", updated.avatarFileAssetId)
        assertEquals("https://legacy", fake.pets[id]?.photoUrl)
        assertEquals(1, fake.avatarCalls)
    }

    @Test
    fun s17_getPetAccessContext_mapsFlags() = runTest {
        fake.seedAccessible(
            AccessiblePetM08Row(
                id = "p1",
                ownerId = "user-1",
                name = "Luna",
                species = "DOG",
                sex = "FEMALE",
                size = "MEDIUM",
                canUpdate = true,
                canArchive = true,
                capabilities = listOf("pet.update", "pet.archive")
            )
        )
        val ctx = adapter.getPetAccessContext("p1").getOrThrow()
        assertTrue(ctx.canUpdate)
        assertTrue(ctx.canArchive)
        assertEquals("p1", ctx.petId)
    }

    @Test
    fun s18_getPetAccessContext_notFound() = runTest {
        val result = adapter.getPetAccessContext("missing")
        assertTrue(result.isFailure)
        assertEquals("PET_NOT_FOUND", M08PetErrorMapper.codeOf(result.exceptionOrNull()!!))
    }

    // --- S19–S24 error mapper ---

    @Test
    fun s19_errorMapper_forbidden() {
        assertEquals("FORBIDDEN", M08PetErrorMapper.codeOf(Exception("FORBIDDEN")))
    }

    @Test
    fun s20_errorMapper_petNotActive() {
        assertEquals("PET_NOT_ACTIVE", M08PetErrorMapper.codeOf(Exception("PET_NOT_ACTIVE")))
    }

    @Test
    fun s21_errorMapper_network() {
        assertEquals("NETWORK", M08PetErrorMapper.codeOf(Exception("Unable to resolve host")))
    }

    @Test
    fun s22_errorMapper_timeout() {
        assertEquals("TIMEOUT", M08PetErrorMapper.codeOf(Exception("timeout")))
    }

    @Test
    fun s23_errorMapper_serialization() {
        assertEquals("SERIALIZATION", M08PetErrorMapper.codeOf(Exception("Json decode error")))
    }

    @Test
    fun s24_errorMapper_userMessage_spanish() {
        val msg = M08PetErrorMapper.userMessage("PET_NOT_FOUND")
        assertTrue(msg.contains("mascota", ignoreCase = true))
    }

    // --- S25–S28 fetch / observe ---

    @Test
    fun s25_fetchPetById() = runTest {
        val id = adapter.createPet(samplePet()).getOrThrow()
        val pet = adapter.fetchPetById(id)
        assertNotNull(pet)
        assertEquals("Luna", pet!!.name)
    }

    @Test
    fun s26_observePetsForOwner_self_emits() = runTest {
        adapter.createPet(samplePet())
        val first = adapter.observePetsForOwner("user-1").first()
        assertTrue(first.isNotEmpty())
    }

    @Test
    fun s27_observePetsForOwner_other_empty() = runTest {
        adapter.createPet(samplePet())
        val first = adapter.observePetsForOwner("user-2").first()
        assertTrue(first.isEmpty())
    }

    @Test
    fun s28_accessibleRow_toPetAndContext() {
        val row = AccessiblePetM08Row(
            id = "p1",
            ownerId = "user-1",
            name = "Luna",
            species = "DOG",
            sex = "FEMALE",
            size = "MEDIUM",
            canUpdate = true,
            relationCode = "PRINCIPAL"
        )
        val (pet, ctx) = PetM08Mappers.run { row.toPetAndContext() }
        assertEquals("Luna", pet.name)
        assertTrue(ctx.canUpdate)
        assertEquals("PRINCIPAL", ctx.relationCode)
    }

    // --- S29–S34 domain repos ---

    @Test
    fun s29_domainRepo_listAccessible() = runTest {
        fake.seedAccessible(
            AccessiblePetM08Row(
                id = "p1", ownerId = "user-1", name = "Luna",
                species = "DOG", sex = "FEMALE", size = "MEDIUM"
            )
        )
        val repo = SupabasePetDomainRepository(fake)
        val list = repo.listAccessibleForActor("user-1", 0L)
        assertEquals(1, list.size)
    }

    @Test
    fun s30_responsibility_assignAndList() = runTest {
        val repo = SupabasePetResponsibilityRepository(fake)
        val result = repo.assignCoResponsible(
            PetResponsibility(
                id = PetResponsibilityId("tmp"),
                petId = PetId("p1"),
                role = PetResponsibilityRole.CO_RESPONSIBLE,
                status = PetLinkStatus.ACTIVE,
                holder = PetPrincipalHolder.Person("user-2"),
                validFromEpochMs = 0L,
                grantedByUserId = "user-1",
                createdAtEpochMs = 0L
            )
        )
        assertTrue(result.isSuccess)
        assertEquals(1, repo.listForPet(PetId("p1")).size)
    }

    @Test
    fun s31_authorization_create() = runTest {
        val repo = SupabasePetAuthorizationRepository(fake)
        val result = repo.create(
            PetAuthorization(
                id = PetAuthorizationId("tmp"),
                petId = PetId("p1"),
                granteeUserId = "user-2",
                capabilities = setOf(PetCapability.UPDATE, PetCapability.MANAGE_HEALTH),
                status = PetLinkStatus.ACTIVE,
                validFromEpochMs = 0L,
                grantedByUserId = "user-1",
                createdAtEpochMs = 0L
            )
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun s32_transfer_initiateAndAccept() = runTest {
        val repo = SupabasePetTransferRepository(fake)
        val create = repo.create(
            PetTransfer(
                id = PetTransferId("tmp"),
                petId = PetId("p1"),
                fromPrincipal = PetPrincipalHolder.Person("user-1"),
                toPrincipal = PetPrincipalHolder.Person("user-2"),
                status = PetTransferStatus.PENDING,
                requestedAtEpochMs = 0L,
                expiresAtEpochMs = System.currentTimeMillis() + 86_400_000,
                requestedByUserId = "user-1"
            )
        )
        assertTrue(create.isSuccess)
        val accept = repo.accept(create.getOrThrow(), 0L)
        assertTrue(accept.isSuccess)
    }

    @Test
    fun s33_transfer_expire_clientForbidden() = runTest {
        val repo = SupabasePetTransferRepository(fake)
        val result = repo.expire(PetTransferId("x"), 0L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("EXPIRE"))
    }

    @Test
    fun s34_domain_changeLifecycle_archive() = runTest {
        val id = adapter.createPet(samplePet()).getOrThrow()
        val repo = SupabasePetDomainRepository(fake)
        val result = repo.changeLifecycleStatus(
            petId = PetId(id),
            to = com.comunidapp.app.domain.pets.PetLifecycleStatus.ARCHIVED,
            actorUserId = "user-1",
            atEpochMs = 0L,
            reasonCode = "TEST"
        )
        assertTrue(result.isSuccess)
        assertEquals("ARCHIVED", fake.pets[id]?.status)
    }
}
