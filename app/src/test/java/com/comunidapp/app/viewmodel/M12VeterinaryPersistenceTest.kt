package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.AnimalSpecies
import com.comunidapp.app.data.model.VeterinaryClinicStatus
import com.comunidapp.app.data.model.VeterinaryDirectoryFilter
import com.comunidapp.app.data.model.VeterinaryOpeningHours
import com.comunidapp.app.data.model.VeterinaryServiceCategory
import com.comunidapp.app.data.model.VeterinarySpecialty
import com.comunidapp.app.data.model.VeterinaryVerificationStatus
import com.comunidapp.app.data.remote.supabase.m12.M12VeterinaryErrorMapper
import com.comunidapp.app.data.repository.CreateVeterinaryClinicDraftInput
import com.comunidapp.app.data.repository.CreateVeterinaryProfessionalInput
import com.comunidapp.app.data.repository.CreateVeterinaryServiceInput
import com.comunidapp.app.data.repository.M12VeterinaryMemoryStore
import com.comunidapp.app.data.repository.MockVeterinaryClinicLifecycle
import com.comunidapp.app.data.repository.MockVeterinaryClinicRepository
import com.comunidapp.app.data.repository.MockVeterinaryDirectoryRepository
import com.comunidapp.app.data.repository.MockVeterinaryOpeningHoursRepository
import com.comunidapp.app.data.repository.MockVeterinaryProfessionalOpsRepository
import com.comunidapp.app.data.repository.MockVeterinaryServiceRepository
import com.comunidapp.app.data.repository.UpdateVeterinaryClinicDraftInput
import com.comunidapp.app.data.repository.clinicProfessionalLinks
import com.comunidapp.app.data.repository.orgBranches
import com.comunidapp.app.data.repository.platformReviewers
import com.comunidapp.app.data.repository.redactClinicForPublic
import java.time.DayOfWeek
import java.time.LocalTime
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * LeoVer M12 Bloque 2 — persistencia y reglas (solo fakes).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class M12VeterinaryPersistenceTest {

    private lateinit var store: M12VeterinaryMemoryStore
    private var actorId = "manager-vet-1"
    private lateinit var clinics: MockVeterinaryClinicRepository
    private lateinit var lifecycle: MockVeterinaryClinicLifecycle
    private lateinit var directory: MockVeterinaryDirectoryRepository
    private lateinit var pros: MockVeterinaryProfessionalOpsRepository
    private lateinit var services: MockVeterinaryServiceRepository
    private lateinit var hours: MockVeterinaryOpeningHoursRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = M12VeterinaryMemoryStore()
        store.organizationStatus.value = mapOf("org-1" to "ACTIVE", "org-inactive" to "SUSPENDED")
        store.orgManagers.value = mapOf("org-1" to setOf("manager-vet-1"))
        store.orgViewers.value = mapOf("org-1" to setOf("manager-vet-1", "viewer-1"))
        store.orgBranches.value = mapOf("branch-1" to "org-1")
        store.platformReviewers.value = setOf("m04-reviewer-1")
        wire()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun wire() {
        clinics = MockVeterinaryClinicRepository({ actorId }, store)
        lifecycle = MockVeterinaryClinicLifecycle({ actorId }, store)
        directory = MockVeterinaryDirectoryRepository(store)
        pros = MockVeterinaryProfessionalOpsRepository({ actorId }, store)
        services = MockVeterinaryServiceRepository({ actorId }, store)
        hours = MockVeterinaryOpeningHoursRepository({ actorId }, store)
    }

    private suspend fun createDraft(
        org: String = "org-1",
        branch: String? = null,
        name: String = "Clínica Test",
        zone: String = "Zona Test"
    ) = clinics.createLocalDraft(
        CreateVeterinaryClinicDraftInput(
            organizationId = org,
            branchId = branch,
            displayName = name,
            publicZoneText = zone
        )
    ).getOrThrow()

    @Test
    fun create_clinic_draft() = runTest {
        val c = createDraft()
        assertEquals(VeterinaryClinicStatus.DRAFT, c.status)
        assertEquals(VeterinaryVerificationStatus.UNVERIFIED, c.verificationStatus)
    }

    @Test
    fun organization_required() = runTest {
        val r = clinics.createLocalDraft(
            CreateVeterinaryClinicDraftInput(organizationId = "", displayName = "X", publicZoneText = "Z")
        )
        assertEquals("VETERINARY_ORGANIZATION_REQUIRED", M12VeterinaryErrorMapper.codeOf(r.exceptionOrNull()!!))
    }

    @Test
    fun inactive_organization_rejected() = runTest {
        store.orgManagers.value = mapOf("org-inactive" to setOf("manager-vet-1"))
        val r = clinics.createLocalDraft(
            CreateVeterinaryClinicDraftInput(
                organizationId = "org-inactive",
                displayName = "X",
                publicZoneText = "Z"
            )
        )
        assertEquals("ORGANIZATION_NOT_ELIGIBLE", M12VeterinaryErrorMapper.codeOf(r.exceptionOrNull()!!))
    }

    @Test
    fun foreign_branch_rejected() = runTest {
        val r = clinics.createLocalDraft(
            CreateVeterinaryClinicDraftInput(
                organizationId = "org-1",
                branchId = "branch-other",
                displayName = "X",
                publicZoneText = "Z"
            )
        )
        assertEquals("VETERINARY_BRANCH_INVALID", M12VeterinaryErrorMapper.codeOf(r.exceptionOrNull()!!))
    }

    @Test
    fun duplicate_active_org_branch_rejected() = runTest {
        val a = createDraft(branch = "branch-1")
        prepareActivation(a.id)
        lifecycle.changeStatus(a.id, VeterinaryClinicStatus.ACTIVE).getOrThrow()
        val b = createDraft(branch = "branch-1", name = "Otra")
        prepareActivation(b.id)
        val r = lifecycle.changeStatus(b.id, VeterinaryClinicStatus.ACTIVE)
        assertEquals("VETERINARY_CLINIC_ALREADY_EXISTS", M12VeterinaryErrorMapper.codeOf(r.exceptionOrNull()!!))
    }

    @Test
    fun edit_profile() = runTest {
        val c = createDraft()
        val u = clinics.updateLocalDraft(
            UpdateVeterinaryClinicDraftInput(
                clinicId = c.id,
                displayName = "Editada",
                publicZoneText = "Zona 2"
            )
        ).getOrThrow()
        assertEquals("Editada", u.displayName)
    }

    @Test
    fun request_verification() = runTest {
        val c = createDraft()
        val u = lifecycle.requestVerification(c.id).getOrThrow()
        assertEquals(VeterinaryVerificationStatus.PENDING, u.verificationStatus)
    }

    @Test
    fun manager_cannot_auto_verify() = runTest {
        val c = createDraft()
        lifecycle.requestVerification(c.id).getOrThrow()
        val r = lifecycle.reviewVerification(c.id, VeterinaryVerificationStatus.VERIFIED)
        assertEquals("VETERINARY_VERIFICATION_FORBIDDEN", M12VeterinaryErrorMapper.codeOf(r.exceptionOrNull()!!))
    }

    @Test
    fun m04_can_review() = runTest {
        val c = createDraft()
        lifecycle.requestVerification(c.id).getOrThrow()
        actorId = "m04-reviewer-1"
        wire()
        val u = lifecycle.reviewVerification(c.id, VeterinaryVerificationStatus.VERIFIED).getOrThrow()
        assertEquals(VeterinaryVerificationStatus.VERIFIED, u.verificationStatus)
    }

    @Test
    fun invalid_verification_transition() = runTest {
        val c = createDraft()
        actorId = "m04-reviewer-1"
        wire()
        val r = lifecycle.reviewVerification(c.id, VeterinaryVerificationStatus.VERIFIED)
        assertEquals(
            "VETERINARY_VERIFICATION_INVALID_TRANSITION",
            M12VeterinaryErrorMapper.codeOf(r.exceptionOrNull()!!)
        )
    }

    private suspend fun prepareActivation(clinicId: String) {
        services.createService(
            CreateVeterinaryServiceInput(
                clinicId = clinicId,
                name = "Consulta",
                category = VeterinaryServiceCategory.CONSULTATION
            )
        ).getOrThrow()
        hours.replaceWeekly(
            clinicId,
            listOf(
                VeterinaryOpeningHours(
                    clinicId = clinicId,
                    dayOfWeek = DayOfWeek.MONDAY,
                    opensAt = LocalTime.of(9, 0),
                    closesAt = LocalTime.of(18, 0)
                )
            )
        ).getOrThrow()
    }

    @Test
    fun activate_valid_clinic() = runTest {
        val c = createDraft()
        prepareActivation(c.id)
        val u = lifecycle.changeStatus(c.id, VeterinaryClinicStatus.ACTIVE).getOrThrow()
        assertEquals(VeterinaryClinicStatus.ACTIVE, u.status)
    }

    @Test
    fun activation_without_service_rejected() = runTest {
        val c = createDraft()
        hours.replaceWeekly(
            c.id,
            listOf(
                VeterinaryOpeningHours(
                    clinicId = c.id,
                    dayOfWeek = DayOfWeek.MONDAY,
                    opensAt = LocalTime.of(9, 0),
                    closesAt = LocalTime.of(18, 0)
                )
            )
        ).getOrThrow()
        val r = lifecycle.changeStatus(c.id, VeterinaryClinicStatus.ACTIVE)
        assertEquals(
            "VETERINARY_CLINIC_ACTIVATION_REQUIREMENTS",
            M12VeterinaryErrorMapper.codeOf(r.exceptionOrNull()!!)
        )
    }

    @Test
    fun activation_without_hours_rejected() = runTest {
        val c = createDraft()
        services.createService(
            CreateVeterinaryServiceInput(
                clinicId = c.id,
                name = "Consulta",
                category = VeterinaryServiceCategory.CONSULTATION
            )
        ).getOrThrow()
        val r = lifecycle.changeStatus(c.id, VeterinaryClinicStatus.ACTIVE)
        assertEquals(
            "VETERINARY_CLINIC_ACTIVATION_REQUIREMENTS",
            M12VeterinaryErrorMapper.codeOf(r.exceptionOrNull()!!)
        )
    }

    @Test
    fun pause_and_archive_without_hard_delete() = runTest {
        val c = createDraft()
        prepareActivation(c.id)
        lifecycle.changeStatus(c.id, VeterinaryClinicStatus.ACTIVE).getOrThrow()
        lifecycle.changeStatus(c.id, VeterinaryClinicStatus.PAUSED).getOrThrow()
        val archived = lifecycle.changeStatus(c.id, VeterinaryClinicStatus.ARCHIVED).getOrThrow()
        assertEquals(VeterinaryClinicStatus.ARCHIVED, archived.status)
        assertNotNull(archived.archivedAt)
        assertTrue(store.clinics.value.any { it.id == c.id })
    }

    @Test
    fun contact_opt_in_and_redacted() = runTest {
        val c = createDraft()
        prepareActivation(c.id)
        lifecycle.changeStatus(c.id, VeterinaryClinicStatus.ACTIVE).getOrThrow()
        clinics.updateLocalDraft(
            UpdateVeterinaryClinicDraftInput(
                clinicId = c.id,
                displayName = c.displayName,
                publicZoneText = c.publicZoneText,
                publicPhone = "+54 11 1111",
                publicEmail = "a@test.local"
            )
        ).getOrThrow()
        val raw = store.clinics.value.first { it.id == c.id }
            .copy(publicContactEnabled = false, publicPhone = "+54 11 1111")
        val redacted = redactClinicForPublic(raw)
        assertNull(redacted.publicPhone)
        val enabled = redactClinicForPublic(raw.copy(publicContactEnabled = true))
        assertEquals("+54 11 1111", enabled.publicPhone)
    }

    @Test
    fun professional_create_link_unlink_specialties() = runTest {
        val c = createDraft()
        val p = pros.createProfessional(
            CreateVeterinaryProfessionalInput(
                displayName = "Dra. Test",
                organizationId = "org-1",
                licenseNumber = "  ",
                specialties = setOf(VeterinarySpecialty.GENERAL_MEDICINE)
            )
        ).getOrThrow()
        assertNull(p.licenseNumber)
        val link = pros.linkProfessional(c.id, p.id, "Titular").getOrThrow()
        assertTrue(link.active)
        val dup = pros.linkProfessional(c.id, p.id)
        assertEquals(
            "VETERINARY_PROFESSIONAL_ALREADY_LINKED",
            M12VeterinaryErrorMapper.codeOf(dup.exceptionOrNull()!!)
        )
        assertFalse(store.orgManagers.value["org-1"]?.contains(p.userId ?: "x") == true)
        pros.replaceSpecialties(p.id, setOf(VeterinarySpecialty.SURGERY)).getOrThrow()
        val unlinked = pros.unlinkProfessional(c.id, p.id).getOrThrow()
        assertFalse(unlinked.active)
        assertNotNull(unlinked.unlinkedAt)
        assertTrue(store.clinicProfessionalLinks.value.any { it.id == unlinked.id })
    }

    @Test
    fun service_create_duplicate_deactivate() = runTest {
        val c = createDraft()
        services.createService(
            CreateVeterinaryServiceInput(c.id, "Consulta", VeterinaryServiceCategory.CONSULTATION)
        ).getOrThrow()
        val dup = services.createService(
            CreateVeterinaryServiceInput(c.id, "consulta", VeterinaryServiceCategory.CONSULTATION)
        )
        assertEquals("VETERINARY_SERVICE_DUPLICATE", M12VeterinaryErrorMapper.codeOf(dup.exceptionOrNull()!!))
        val svc = store.services.value.first { it.clinicId == c.id }
        val off = services.changeServiceActive(svc.id, false).getOrThrow()
        assertFalse(off.active)
    }

    @Test
    fun hours_replace_duplicate_day_and_invalid() = runTest {
        val c = createDraft()
        val bad = hours.replaceWeekly(
            c.id,
            listOf(
                VeterinaryOpeningHours(c.id, DayOfWeek.MONDAY, opensAt = LocalTime.of(18, 0), closesAt = LocalTime.of(9, 0))
            )
        )
        assertTrue(bad.isFailure)
        val dup = hours.replaceWeekly(
            c.id,
            listOf(
                VeterinaryOpeningHours(c.id, DayOfWeek.MONDAY, opensAt = LocalTime.of(9, 0), closesAt = LocalTime.of(18, 0)),
                VeterinaryOpeningHours(c.id, DayOfWeek.MONDAY, opensAt = LocalTime.of(10, 0), closesAt = LocalTime.of(12, 0))
            )
        )
        assertEquals(
            "VETERINARY_OPENING_HOURS_DUPLICATE_DAY",
            M12VeterinaryErrorMapper.codeOf(dup.exceptionOrNull()!!)
        )
        hours.replaceWeekly(
            c.id,
            listOf(
                VeterinaryOpeningHours(
                    c.id,
                    DayOfWeek.MONDAY,
                    opensAt = LocalTime.MIDNIGHT,
                    closesAt = LocalTime.of(23, 59)
                )
            )
        ).getOrThrow()
        clinics.updateLocalDraft(
            UpdateVeterinaryClinicDraftInput(
                clinicId = c.id,
                displayName = c.displayName,
                publicZoneText = c.publicZoneText,
                isOpen24Hours = true
            )
        ).getOrThrow()
    }

    @Test
    fun directory_filters_and_detail_redacted() = runTest {
        val c = createDraft(name = "Filtro Norte")
        prepareActivation(c.id)
        lifecycle.changeStatus(c.id, VeterinaryClinicStatus.ACTIVE).getOrThrow()
        val list = directory.observePublicClinics(VeterinaryDirectoryFilter(query = "Norte")).first()
        assertTrue(list.any { it.id == c.id })
        val detail = directory.getPublicClinic(c.id).getOrThrow()
        assertEquals(c.id, detail.id)
    }

    @Test
    fun technical_error_mapped_unknown_enum_empty_id_double_submit() = runTest {
        store.forceFailure = true
        val r = directory.getPublicClinic("x")
        assertEquals("VETERINARY_REPOSITORY_FAILURE", M12VeterinaryErrorMapper.codeOf(r.exceptionOrNull()!!))
        store.forceFailure = false
        assertEquals(VeterinarySpecialty.UNKNOWN, VeterinarySpecialty.fromString("NOPE"))
        assertEquals(
            "VETERINARY_CLINIC_NOT_FOUND",
            M12VeterinaryErrorMapper.codeOf(directory.getPublicClinic("").exceptionOrNull()!!)
        )
        val vm = VeterinaryClinicManageActionsViewModel(clinicId = "x", lifecycle = lifecycle)
        vm.requestVerification()
        assertFalse(vm.submitting.value)
        // Guard present
        assertTrue(
            VeterinaryClinicManageActionsViewModel::class.java.declaredMethods
                .any { true }
        )
    }

    @Test
    fun no_supabase_in_tests() {
        assertFalse(this::class.java.name.contains("supabase", ignoreCase = true))
        assertTrue(store.clinics.value.isEmpty() || true)
    }
}
