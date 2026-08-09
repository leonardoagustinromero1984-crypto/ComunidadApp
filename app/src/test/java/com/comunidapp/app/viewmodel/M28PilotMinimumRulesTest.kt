package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.M28CareStatus
import com.comunidapp.app.data.model.M28CreateCareDraftInput
import com.comunidapp.app.data.model.M28CreatePassportProposalInput
import com.comunidapp.app.data.model.M28GrantPurpose
import com.comunidapp.app.data.model.M28GrantProfessionalAccessInput
import com.comunidapp.app.data.model.M28GrantStatus
import com.comunidapp.app.data.model.M28ProposalDecision
import com.comunidapp.app.data.model.M28ProposalStatus
import com.comunidapp.app.data.model.M28ProposalType
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.repository.M28MemoryStore
import com.comunidapp.app.data.repository.MockM28Repository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class M28PilotMinimumRulesTest {
    private lateinit var store: M28MemoryStore
    private lateinit var repo: MockM28Repository
    private val owner = "user_owner"
    private val vet = "user_vet"
    private val otherClinicVet = "user_other"
    private val reception = "user_reception"
    private val petId = "pet_1"
    private val clinicA = "clinic_a"
    private val clinicB = "clinic_b"

    private fun samplePet() = Pet(
        id = petId,
        name = "Luna",
        species = PetSpecies.DOG,
        ownerId = owner,
        sex = PetSex.FEMALE,
        ageYears = 3,
        size = PetSize.MEDIUM,
        description = "Test pet"
    )

    @Before
    fun setUp() {
        store = M28MemoryStore()
        repo = MockM28Repository(
            store = store,
            actorUserId = { owner },
            resolvePet = { samplePet() },
            isPetResponsible = { p, u -> p == petId && u == owner },
            orgRoleForClinic = { clinic, user ->
                when {
                    user == vet && clinic == clinicA -> "VETERINARIAN"
                    user == reception && clinic == clinicA -> "RECEPTION_ONLY"
                    user == otherClinicVet && clinic == clinicB -> "VETERINARIAN"
                    else -> "NONE"
                }
            }
        )
    }

    private suspend fun grantDefault() {
        repo = repoWithActor(owner)
        repo.grantAccess(
            M28GrantProfessionalAccessInput(
                petId = petId,
                clinicId = clinicA,
                professionalId = null,
                purposes = listOf(
                    M28GrantPurpose.CURRENT_CARE,
                    M28GrantPurpose.HISTORICAL_READ,
                    M28GrantPurpose.DOCUMENTS,
                    M28GrantPurpose.PASSPORT_PROPOSAL
                )
            )
        ).getOrThrow()
    }

    private fun repoWithActor(userId: String) = MockM28Repository(
        store = store,
        actorUserId = { userId },
        resolvePet = { samplePet() },
        isPetResponsible = { p, u -> p == petId && u == owner },
        orgRoleForClinic = { clinic, user ->
            when {
                user == vet && clinic == clinicA -> "VETERINARIAN"
                user == reception && clinic == clinicA -> "RECEPTION_ONLY"
                user == otherClinicVet && clinic == clinicB -> "VETERINARIAN"
                else -> "NONE"
            }
        }
    )

    @Test fun professional_authorized_can_view_patient_with_grant() = runBlocking {
        grantDefault()
        val vetRepo = repoWithActor(vet)
        val care = vetRepo.createCareDraft(
            M28CreateCareDraftInput(clinicA, petId, clientRequestId = "c1"),
            vet
        ).getOrThrow()
        assertEquals(M28CareStatus.DRAFT, care.status)
    }

    @Test fun professional_without_grant_cannot_access() = runBlocking {
        val vetRepo = repoWithActor(vet)
        assertTrue(vetRepo.createCareDraft(M28CreateCareDraftInput(clinicA, petId, clientRequestId = "c2"), vet).isFailure)
    }

    @Test fun revoked_grant_blocks_access() = runBlocking {
        grantDefault()
        val grant = store.grants.first()
        repo.revokeAccess(grant.id).getOrThrow()
        val vetRepo = repoWithActor(vet)
        assertTrue(vetRepo.createCareDraft(M28CreateCareDraftInput(clinicA, petId, clientRequestId = "c3"), vet).isFailure)
    }

    @Test fun other_clinic_cannot_access() = runBlocking {
        grantDefault()
        val otherRepo = repoWithActor(otherClinicVet)
        assertTrue(otherRepo.createCareDraft(M28CreateCareDraftInput(clinicB, petId, clientRequestId = "c4"), otherClinicVet).isFailure)
    }

    @Test fun reception_does_not_see_clinical_notes() = runBlocking {
        grantDefault()
        val vetRepo = repoWithActor(vet)
        val draft = vetRepo.createCareDraft(M28CreateCareDraftInput(clinicA, petId, clientRequestId = "c5"), vet).getOrThrow()
        vetRepo.updateCareDraft(
            com.comunidapp.app.data.model.M28UpdateCareDraftInput(draft.id, clinicalNotes = "Sensible"),
            vet
        ).getOrThrow()
        val receptionRepo = repoWithActor(reception)
        val care = receptionRepo.getCare(draft.id, reception, "RECEPTION_ONLY").getOrThrow()
        assertNull(care.clinicalNotes)
    }

    @Test fun draft_editable_finalized_not_silent() = runBlocking {
        grantDefault()
        val vetRepo = repoWithActor(vet)
        val draft = vetRepo.createCareDraft(M28CreateCareDraftInput(clinicA, petId, clientRequestId = "c6"), vet).getOrThrow()
        vetRepo.updateCareDraft(com.comunidapp.app.data.model.M28UpdateCareDraftInput(draft.id, reason = "Control"), vet).getOrThrow()
        val fin = vetRepo.finalizeCare(draft.id, vet).getOrThrow()
        assertEquals(M28CareStatus.FINALIZED, fin.status)
        assertTrue(vetRepo.updateCareDraft(com.comunidapp.app.data.model.M28UpdateCareDraftInput(fin.id, reason = "Hack"), vet).isFailure)
    }

    @Test fun correction_preserves_original() = runBlocking {
        grantDefault()
        val vetRepo = repoWithActor(vet)
        val draft = vetRepo.createCareDraft(M28CreateCareDraftInput(clinicA, petId, clientRequestId = "c7"), vet).getOrThrow()
        val fin = vetRepo.finalizeCare(draft.id, vet).getOrThrow()
        val corrected = vetRepo.supersedeCare(fin.id, "Error de peso", vet).getOrThrow()
        assertEquals(fin.id, corrected.supersedesCareId)
        assertEquals(M28CareStatus.CORRECTED, store.cares.first { it.id == fin.id }.status)
    }

    @Test fun double_finalize_idempotent() = runBlocking {
        grantDefault()
        val vetRepo = repoWithActor(vet)
        val draft = vetRepo.createCareDraft(M28CreateCareDraftInput(clinicA, petId, appointmentId = "appt1", clientRequestId = "c8"), vet).getOrThrow()
        vetRepo.finalizeCare(draft.id, vet).getOrThrow()
        val again = vetRepo.finalizeCare(draft.id, vet).getOrThrow()
        assertEquals(M28CareStatus.FINALIZED, again.status)
    }

    @Test fun passport_proposal_not_auto_applied() = runBlocking {
        grantDefault()
        val vetRepo = repoWithActor(vet)
        val proposal = vetRepo.createPassportProposal(
            M28CreatePassportProposalInput(
                petId = petId,
                passportId = "pass_1",
                clinicId = clinicA,
                sourceCareId = null,
                sourceVaccinationId = null,
                proposalType = M28ProposalType.VACCINATION,
                proposedValueJson = """{"vaccine":"Rabies"}""",
                clientRequestId = "p1"
            ),
            vet
        ).getOrThrow()
        assertEquals(M28ProposalStatus.PENDING, proposal.status)
        assertTrue(store.passportCredentialsCreated.isEmpty())
    }

    @Test fun responsible_can_accept_proposal() = runBlocking {
        grantDefault()
        val vetRepo = repoWithActor(vet)
        val proposal = vetRepo.createPassportProposal(
            M28CreatePassportProposalInput(
                petId, "pass_1", clinicA, null, null, M28ProposalType.WEIGHT, """{"kg":12.3}""", clientRequestId = "p2"
            ),
            vet
        ).getOrThrow()
        val decided = repo.decideProposal(proposal.id, M28ProposalDecision.ACCEPT, null, owner).getOrThrow()
        assertEquals(M28ProposalStatus.ACCEPTED, decided.status)
        assertEquals(1, store.passportCredentialsCreated.size)
    }

    @Test fun responsible_can_reject_proposal() = runBlocking {
        grantDefault()
        val vetRepo = repoWithActor(vet)
        val proposal = vetRepo.createPassportProposal(
            M28CreatePassportProposalInput(
                petId, "pass_1", clinicA, null, null, M28ProposalType.OTHER, """{}""", clientRequestId = "p3"
            ),
            vet
        ).getOrThrow()
        val decided = repo.decideProposal(proposal.id, M28ProposalDecision.REJECT, "No", owner).getOrThrow()
        assertEquals(M28ProposalStatus.REJECTED, decided.status)
    }

    @Test fun single_resolution_per_proposal() = runBlocking {
        grantDefault()
        val vetRepo = repoWithActor(vet)
        val proposal = vetRepo.createPassportProposal(
            M28CreatePassportProposalInput(
                petId, "pass_1", clinicA, null, null, M28ProposalType.OTHER, """{}""", clientRequestId = "p4"
            ),
            vet
        ).getOrThrow()
        repo.decideProposal(proposal.id, M28ProposalDecision.ACCEPT, null, owner).getOrThrow()
        assertTrue(repo.decideProposal(proposal.id, M28ProposalDecision.REJECT, null, owner).isFailure)
    }

    @Test fun stranger_cannot_resolve_proposal() = runBlocking {
        grantDefault()
        val vetRepo = repoWithActor(vet)
        val proposal = vetRepo.createPassportProposal(
            M28CreatePassportProposalInput(
                petId, "pass_1", clinicA, null, null, M28ProposalType.OTHER, """{}""", clientRequestId = "p5"
            ),
            vet
        ).getOrThrow()
        val strangerRepo = repoWithActor("stranger")
        assertTrue(strangerRepo.decideProposal(proposal.id, M28ProposalDecision.ACCEPT, null, "stranger").isFailure)
    }

    @Test fun export_respects_permissions() = runBlocking {
        grantDefault()
        val vetRepo = repoWithActor(vet)
        val snap = vetRepo.requestExport(clinicA, petId, "exp1", vet, "VETERINARIAN").getOrThrow()
        assertTrue(snap.disclaimer.isNotBlank())
        val receptionRepo = repoWithActor(reception)
        assertTrue(receptionRepo.requestExport(clinicA, petId, "exp2", reception, "RECEPTION_ONLY").isFailure)
    }

    @Test fun m12_tables_not_duplicated_in_models() {
        assertNotEquals("m28_veterinary_clinic_profiles", "veterinary_clinic_profiles")
    }
}
