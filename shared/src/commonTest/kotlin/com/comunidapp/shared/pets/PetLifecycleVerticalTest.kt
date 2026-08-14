package com.comunidapp.shared.pets

import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetLifecycleStatus
import com.comunidapp.shared.auth.FakeAuthSessionGateway
import com.comunidapp.shared.auth.GatewayAuthRepository
import com.comunidapp.shared.remote.FakePetsRemoteGateway
import com.comunidapp.shared.remote.RemoteAccessiblePetRow
import com.comunidapp.shared.remote.RemotePetRow
import com.comunidapp.shared.remote.VaccinationRecordDto
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.session.SessionUser
import com.comunidapp.shared.ui.VerticalLoadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class PetLifecycleVerticalTest {

    private fun auth(userId: String = "u1") =
        GatewayAuthRepository(
            FakeAuthSessionGateway(
                SessionState.Authenticated(SessionUser(userId, "a@leover.test", "Ana"))
            )
        )

    private fun activePet(
        id: String = "pet-1",
        ownerId: String = "u1",
        status: String = "ACTIVE"
    ) = RemotePetRow(
        id = id,
        name = "Luna",
        species = "DOG",
        status = status,
        ownerId = ownerId,
        vaccinations = listOf(
            VaccinationRecordDto(name = "Rabia", date = "2024-01-10")
        ),
        description = "Juguetona",
        breed = "Mestiza"
    )

    private fun remote(
        gateway: FakePetsRemoteGateway,
        authRepo: GatewayAuthRepository = auth()
    ) = RemoteSharedPetsRepository(
        gateway = gateway,
        sessionRepository = authRepo
    )

    @Test
    fun owner_archive_success_removes_from_active_list() = runTest {
        val pet = activePet()
        val gw = FakePetsRemoteGateway(
            detail = pet,
            list = listOf(
                RemoteAccessiblePetRow(
                    id = pet.id,
                    name = pet.name,
                    species = pet.species,
                    status = pet.status,
                    ownerId = pet.ownerId
                )
            )
        )
        val repo = remote(gw)
        val result = repo.archive(PetId("pet-1"), reason = "MOVED")
        assertIs<PetLifecycleResult.Success>(result)
        assertEquals(PetLifecycleStatus.ARCHIVED, result.status)
        assertEquals(1, gw.archiveCalls)
        assertEquals("MOVED", gw.lastArchiveReason)
        assertTrue(gw.list.none { it.id == "pet-1" })

        val listState = repo.observeMyPets("u1").first { it !is VerticalLoadState.Loading }
        assertTrue(
            listState is VerticalLoadState.Empty ||
                (listState is VerticalLoadState.Content && listState.data.none { it.id.value == "pet-1" })
        )
    }

    @Test
    fun non_owner_forbidden() = runTest {
        val gw = FakePetsRemoteGateway(
            detail = activePet(ownerId = "other"),
            archiveError = IllegalStateException("FORBIDDEN")
        )
        val result = remote(gw).archive(PetId("pet-1"))
        assertIs<PetLifecycleResult.Forbidden>(result)
        assertFalse(result.message.contains("FORBIDDEN"))
    }

    @Test
    fun already_archived_conflict() = runTest {
        val gw = FakePetsRemoteGateway(
            detail = activePet(status = "ARCHIVED")
        )
        val result = remote(gw).archive(PetId("pet-1"))
        assertIs<PetLifecycleResult.Conflict>(result)
        assertFalse(result.message.contains("PET_ALREADY_ARCHIVED"))
    }

    @Test
    fun restore_success() = runTest {
        val gw = FakePetsRemoteGateway(
            detail = activePet(status = "ARCHIVED"),
            list = emptyList()
        )
        val repo = remote(gw)
        val result = repo.restore(PetId("pet-1"))
        assertIs<PetLifecycleResult.Success>(result)
        assertEquals(PetLifecycleStatus.ACTIVE, result.status)
        assertEquals(1, gw.restoreCalls)
        assertTrue(gw.list.any { it.id == "pet-1" && it.status == "ACTIVE" })

        val detail = repo.observePetDetail(PetId("pet-1"))
            .first { it !is VerticalLoadState.Loading }
        val content = assertIs<VerticalLoadState.Content<PetDetailView>>(detail)
        assertEquals(PetLifecycleStatus.ACTIVE, content.data.status)
    }

    @Test
    fun mark_deceased_from_active() = runTest {
        val gw = FakePetsRemoteGateway(detail = activePet())
        val result = remote(gw).markDeceased(PetId("pet-1"), reason = "NATURAL")
        assertIs<PetLifecycleResult.Success>(result)
        assertEquals(PetLifecycleStatus.DECEASED, result.status)
        assertEquals("NATURAL", gw.lastDeceasedReason)
    }

    @Test
    fun health_and_profile_still_work_after_lifecycle_wiring() = runTest {
        val gw = FakePetsRemoteGateway(detail = activePet())
        val repo = remote(gw)
        val health = repo.updateHealth(
            PetId("pet-1"),
            PetHealthDraft(weightKg = 12.5f, sterilized = "YES")
        )
        assertIs<PetHealthWriteResult.Success>(health)
        val edit = repo.update(
            PetId("pet-1"),
            PetEditDraft(name = "Luna", species = "DOG", description = "Ok")
        )
        assertIs<PetEditResult.Success>(edit)
        assertEquals(1, gw.healthCalls)
        assertEquals(1, gw.updateCalls)
    }

    @Test
    fun unconfigured_no_fake_success() = runTest {
        assertIs<PetLifecycleResult.BackendError>(
            UnconfiguredSharedPetsRepository().archive(PetId("pet-1"))
        )
        assertIs<PetLifecycleResult.BackendError>(
            UnconfiguredSharedPetsRepository().restore(PetId("pet-1"))
        )
    }

    @Test
    fun unauthenticated() = runTest {
        val authRepo = GatewayAuthRepository(FakeAuthSessionGateway(SessionState.Unauthenticated))
        val result = remote(
            FakePetsRemoteGateway(detail = activePet()),
            authRepo
        ).archive(PetId("pet-1"))
        assertIs<PetLifecycleResult.Unauthenticated>(result)
    }
}
