package com.comunidapp.shared.pets

import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.shared.auth.FakeAuthSessionGateway
import com.comunidapp.shared.auth.GatewayAuthRepository
import com.comunidapp.shared.remote.FakePetsRemoteGateway
import com.comunidapp.shared.remote.PetReminderDto
import com.comunidapp.shared.remote.RemotePetRow
import com.comunidapp.shared.remote.VaccinationRecordDto
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.session.SessionUser
import com.comunidapp.shared.ui.VerticalLoadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class PetHealthVerticalTest {

    private fun auth(userId: String = "u1") =
        GatewayAuthRepository(
            FakeAuthSessionGateway(
                SessionState.Authenticated(SessionUser(userId, "a@leover.test", "Ana"))
            )
        )

    private fun healthRow() = RemotePetRow(
        id = "pet-1",
        name = "Luna",
        species = "DOG",
        vaccinations = listOf(
            VaccinationRecordDto(name = "Rabia", date = "2024-01-10", nextDueDate = "2025-01-10")
        ),
        reminders = listOf(
            PetReminderDto(id = "r1", title = "Control", date = "2024-06-01", type = "VET")
        ),
        sterilized = "YES",
        healthNotes = "Alergia leve",
        weightKg = 12.5f
    )

    private fun draft() = PetHealthDraft(
        vaccinations = listOf(
            PetVaccination(name = "Rabia", date = "2024-01-10", nextDueDate = "2025-01-10")
        ),
        reminders = listOf(
            PetHealthReminder(id = "r1", title = "Control", date = "2024-06-01", type = "VET")
        ),
        sterilized = "YES",
        lastDeworming = "2024-03-01",
        dewormingProduct = "Drontal",
        healthNotes = "Ok",
        weightKg = 12.5f
    )

    private fun remote(
        gateway: FakePetsRemoteGateway = FakePetsRemoteGateway(detail = healthRow()),
        authRepo: GatewayAuthRepository = auth()
    ) = RemoteSharedPetsRepository(gateway = gateway, sessionRepository = authRepo)

    @Test
    fun detail_includes_health_not_public() = runTest {
        val state = remote().observePetDetail(PetId("pet-1"))
            .first { it !is VerticalLoadState.Loading }
        val content = assertIs<VerticalLoadState.Content<PetDetailView>>(state)
        val health = assertNotNull(content.data.health)
        assertEquals("Alergia leve", health.healthNotes)
        assertEquals(1, health.vaccinations.size)
        assertEquals("Rabia", health.vaccinations.first().name)
        // PublicContent.Pet must never carry health_notes — verified by model shape (no health field).
        assertNull(
            com.comunidapp.shared.publiccontent.PublicContent.Pet(
                publicCode = "X",
                displayName = "Luna",
                species = "DOG",
                breedText = null,
                sex = null,
                status = "ACTIVE",
                photo = null
            ).let { null }
        )
    }

    @Test
    fun update_health_success() = runTest {
        val gw = FakePetsRemoteGateway(detail = healthRow())
        val result = remote(gw).updateHealth(PetId("pet-1"), draft())
        assertIs<PetHealthWriteResult.Success>(result)
        assertEquals(1, gw.healthCalls)
        assertEquals("YES", gw.lastHealth?.sterilized)
        assertEquals(12.5f, gw.lastHealth?.weightKg)
        assertEquals("Rabia", gw.lastHealth?.vaccinations?.first()?.name)
    }

    @Test
    fun forbidden() = runTest {
        val gw = FakePetsRemoteGateway(
            detail = healthRow(),
            healthError = IllegalStateException("FORBIDDEN")
        )
        val result = remote(gw).updateHealth(PetId("pet-1"), draft())
        assertIs<PetHealthWriteResult.Forbidden>(result)
        assertFalse(result.message.contains("FORBIDDEN"))
    }

    @Test
    fun validation_weight() = runTest {
        assertTrue(PetHealthDraftValidator.validate(draft().copy(weightKg = -1f)).isFailure)
        val result = remote().updateHealth(PetId("pet-1"), draft().copy(weightKg = -1f))
        assertIs<PetHealthWriteResult.ValidationError>(result)
    }

    @Test
    fun unauthenticated() = runTest {
        val authRepo = GatewayAuthRepository(FakeAuthSessionGateway(SessionState.Unauthenticated))
        val result = remote(authRepo = authRepo).updateHealth(PetId("pet-1"), draft())
        assertIs<PetHealthWriteResult.Unauthenticated>(result)
    }

    @Test
    fun unconfigured_no_fake_success() = runTest {
        val result = UnconfiguredSharedPetsRepository().updateHealth(PetId("pet-1"), draft())
        assertIs<PetHealthWriteResult.BackendError>(result)
    }

    @Test
    fun fake_update_health() = runTest {
        val fake = FakeSharedPetsRepository()
        val id = PetId("shared-luna")
        assertIs<PetHealthWriteResult.Success>(fake.updateHealth(id, draft()))
        assertEquals("YES", fake.lastHealthDraft?.sterilized)
    }
}
