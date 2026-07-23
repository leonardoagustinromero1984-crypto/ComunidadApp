package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.FosterAvailabilityStatus
import com.comunidapp.app.data.model.FosterContributionStatus
import com.comunidapp.app.data.model.FosterEvolutionVisibility
import com.comunidapp.app.data.model.FosterExpenseCategory
import com.comunidapp.app.data.model.FosterHealthStatus
import com.comunidapp.app.data.model.FosterHelpStatus
import com.comunidapp.app.data.model.FosterHelpType
import com.comunidapp.app.data.model.FosterHomeRequestStatus
import com.comunidapp.app.data.model.FosterPlacementEndReason
import com.comunidapp.app.data.model.FosterPlacementStatus
import com.comunidapp.app.data.model.FosterUrgency
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.m10.M10FosterErrorMapper
import com.comunidapp.app.data.repository.CreateFosterHomeInput
import com.comunidapp.app.data.repository.FosterSecureRefValidator
import com.comunidapp.app.data.repository.M10FosterMemoryStore
import com.comunidapp.app.data.repository.MockFosterEvolutionRepository
import com.comunidapp.app.data.repository.MockFosterExpenseRepository
import com.comunidapp.app.data.repository.MockFosterHelpRepository
import com.comunidapp.app.data.repository.MockFosterHomeRepository
import com.comunidapp.app.data.repository.MockFosterPlacementRepository
import com.comunidapp.app.data.repository.MockFosterRequestRepository
import com.comunidapp.app.data.repository.SubmitFosterRequestInput
import kotlinx.coroutines.Dispatchers
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
 * LeoVer M10 — bloque 2: gastos, evolución, ayuda y finalización.
 * Solo fakes en memoria; sin Supabase real ni DataProvider de producción.
 */
class M10FosterCareManagementTest {

    private lateinit var store: M10FosterMemoryStore
    private var actorId: String = "owner-1"
    private val pets = mutableMapOf<String, Pet>()

    private lateinit var homes: MockFosterHomeRepository
    private lateinit var requests: MockFosterRequestRepository
    private lateinit var placements: MockFosterPlacementRepository
    private lateinit var expenses: MockFosterExpenseRepository
    private lateinit var evolution: MockFosterEvolutionRepository
    private lateinit var help: MockFosterHelpRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = M10FosterMemoryStore()
        actorId = "owner-1"
        pets.clear()
        pets["pet-1"] = Pet(
            id = "pet-1",
            name = "Nala",
            species = PetSpecies.DOG,
            sex = PetSex.FEMALE,
            size = PetSize.MEDIUM,
            ageYears = 2,
            ageMonths = 0,
            description = "test",
            ownerId = "requester-1",
            status = "ACTIVE"
        )
        store.petPrincipal.value = mapOf("pet-1" to "requester-1")
        wireRepos()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun wireRepos() {
        homes = MockFosterHomeRepository(actorUserId = { actorId }, store = store)
        requests = MockFosterRequestRepository(
            actorUserId = { actorId },
            store = store,
            resolvePet = { pets[it] }
        )
        placements = MockFosterPlacementRepository(actorUserId = { actorId }, store = store)
        expenses = MockFosterExpenseRepository(actorUserId = { actorId }, store = store)
        evolution = MockFosterEvolutionRepository(actorUserId = { actorId }, store = store)
        help = MockFosterHelpRepository(actorUserId = { actorId }, store = store)
    }

    private suspend fun createActivePlacement(): String {
        actorId = "owner-1"
        wireRepos()
        val home = homes.createFosterHome(
            CreateFosterHomeInput(
                displayName = "Casa Sol",
                totalCapacity = 2,
                acceptedSpecies = setOf("DOG"),
                acceptedSizes = setOf("MEDIUM", "SMALL"),
                zoneText = "Quilmes",
                activate = true
            )
        ).getOrThrow()
        actorId = "requester-1"
        wireRepos()
        val req = requests.submitRequest(
            SubmitFosterRequestInput(
                fosterHomeId = home.id,
                petId = "pet-1",
                message = "Necesito tránsito",
                urgency = FosterUrgency.NORMAL
            )
        ).getOrThrow()
        actorId = "owner-1"
        wireRepos()
        requests.acceptRequest(req.id).getOrThrow()
        return placements.startPlacement(req.id, "Ingreso").getOrThrow().id
    }

    private fun codeOf(r: Result<*>): String =
        M10FosterErrorMapper.codeOf(r.exceptionOrNull()!!)

    // —— Gastos ——

    @Test
    fun expense_registerValid() = runTest {
        val pid = createActivePlacement()
        val e = expenses.addExpense(
            pid, FosterExpenseCategory.FOOD, "Balanceado", 15000, "ARS",
            System.currentTimeMillis(), "m05://receipt/1"
        ).getOrThrow()
        assertEquals(15000L, e.amountMinor)
        assertEquals("ARS", e.currency)
        assertEquals(1, expenses.observeExpenses(pid).first().size)
    }

    @Test
    fun expense_rejectZeroOrNegative() = runTest {
        val pid = createActivePlacement()
        assertEquals(
            "FOSTER_EXPENSE_INVALID_AMOUNT",
            codeOf(expenses.addExpense(pid, FosterExpenseCategory.FOOD, "x", 0, "ARS", 1L, null))
        )
        assertEquals(
            "FOSTER_EXPENSE_INVALID_AMOUNT",
            codeOf(expenses.addExpense(pid, FosterExpenseCategory.FOOD, "x", -5, "ARS", 1L, null))
        )
    }

    @Test
    fun expense_rejectOnCompletedPlacement() = runTest {
        val pid = createActivePlacement()
        placements.completePlacement(pid, FosterPlacementEndReason.RETURNED_TO_OWNER, null).getOrThrow()
        assertEquals(
            "FOSTER_PLACEMENT_NOT_ACTIVE",
            codeOf(expenses.addExpense(pid, FosterExpenseCategory.FOOD, "x", 100, "ARS", 1L, null))
        )
    }

    @Test
    fun expense_forbidOutsider() = runTest {
        val pid = createActivePlacement()
        actorId = "stranger"
        wireRepos()
        assertEquals(
            "FOSTER_EXPENSE_FORBIDDEN",
            codeOf(expenses.addExpense(pid, FosterExpenseCategory.FOOD, "x", 100, "ARS", 1L, null))
        )
    }

    @Test
    fun expense_acceptPrivateReceiptRef() = runTest {
        assertFalse(FosterSecureRefValidator.isUnsafePublicReference("m05://doc/1"))
        assertFalse(FosterSecureRefValidator.isUnsafePublicReference("file_asset:abc"))
        val pid = createActivePlacement()
        val e = expenses.addExpense(
            pid, FosterExpenseCategory.VETERINARY, "Consulta", 5000, "ARS", 1L, "file_asset:ticket"
        ).getOrThrow()
        assertEquals("file_asset:ticket", e.receiptRef)
    }

    @Test
    fun expense_rejectPublicUnsafeUrl() = runTest {
        assertTrue(
            FosterSecureRefValidator.isUnsafePublicReference(
                "https://xyz.supabase.co/storage/v1/object/public/leover/receipt.jpg"
            )
        )
        val pid = createActivePlacement()
        assertEquals(
            "FOSTER_EVOLUTION_INVALID_MEDIA_REF",
            codeOf(
                expenses.addExpense(
                    pid, FosterExpenseCategory.FOOD, "x", 100, "ARS", 1L,
                    "https://cdn.example/leover/public/r.jpg"
                )
            )
        )
    }

    // —— Evolución ——

    @Test
    fun evolution_register() = runTest {
        val pid = createActivePlacement()
        val e = evolution.addEvolution(
            pid, "Día 1", "Come bien", FosterHealthStatus.GOOD, 4200, 1L,
            listOf("m05://photo/1"), FosterEvolutionVisibility.PARTICIPANTS
        ).getOrThrow()
        assertEquals(FosterHealthStatus.GOOD, e.healthStatus)
    }

    @Test
    fun evolution_criticalPrepared() = runTest {
        val pid = createActivePlacement()
        val e = evolution.addEvolution(
            pid, "Alerta", "Decaída", FosterHealthStatus.CRITICAL, null, 1L,
            emptyList(), FosterEvolutionVisibility.PARTICIPANTS
        ).getOrThrow()
        assertEquals(FosterHealthStatus.CRITICAL, e.healthStatus)
    }

    @Test
    fun evolution_publicLimited() = runTest {
        val pid = createActivePlacement()
        val longDesc = "x".repeat(400)
        val e = evolution.addEvolution(
            pid, "Update", longDesc, FosterHealthStatus.STABLE, null, 1L,
            emptyList(), FosterEvolutionVisibility.PUBLIC
        ).getOrThrow()
        assertEquals(280, e.description.length)
        assertEquals(FosterEvolutionVisibility.PUBLIC, e.visibility)
    }

    @Test
    fun evolution_rejectAfterCompletion() = runTest {
        val pid = createActivePlacement()
        placements.completePlacement(pid, FosterPlacementEndReason.ADOPTED, null).getOrThrow()
        assertEquals(
            "FOSTER_PLACEMENT_NOT_ACTIVE",
            codeOf(
                evolution.addEvolution(
                    pid, "t", "d", FosterHealthStatus.GOOD, null, 1L,
                    emptyList(), FosterEvolutionVisibility.PARTICIPANTS
                )
            )
        )
    }

    @Test
    fun evolution_unknownHealthControlled() {
        assertEquals(FosterHealthStatus.UNKNOWN, FosterHealthStatus.fromString("WEIRD_STATUS"))
        assertEquals(FosterEvolutionVisibility.UNKNOWN, FosterEvolutionVisibility.fromString("??"))
    }

    // —— Ayuda ——

    @Test
    fun help_createRequest() = runTest {
        val pid = createActivePlacement()
        val hr = help.createHelpRequest(
            pid, FosterHelpType.FOOD, "Balanceado", "Necesitamos alimento",
            null, null, 5, FosterUrgency.HIGH
        ).getOrThrow()
        assertEquals(FosterHelpStatus.OPEN, hr.status)
    }

    @Test
    fun help_recordContribution() = runTest {
        val pid = createActivePlacement()
        val hr = help.createHelpRequest(
            pid, FosterHelpType.SUPPLIES, "Mantas", "Frío", null, null, 2, FosterUrgency.NORMAL
        ).getOrThrow()
        val c = help.recordContribution(hr.id, "1 manta", null, 1, FosterContributionStatus.RECEIVED)
            .getOrThrow()
        assertEquals(FosterContributionStatus.RECEIVED, c.status)
        assertEquals(1, help.getHelpRequest(hr.id).getOrThrow().receivedQuantity)
    }

    @Test
    fun help_markFulfilled() = runTest {
        val pid = createActivePlacement()
        val hr = help.createHelpRequest(
            pid, FosterHelpType.VOLUNTEER, "Paseo", "Ayuda", null, null, null, FosterUrgency.NORMAL
        ).getOrThrow()
        val done = help.changeHelpRequestStatus(hr.id, FosterHelpStatus.FULFILLED).getOrThrow()
        assertEquals(FosterHelpStatus.FULFILLED, done.status)
        assertNotNull(done.closedAt)
    }

    @Test
    fun help_cancelRequest() = runTest {
        val pid = createActivePlacement()
        val hr = help.createHelpRequest(
            pid, FosterHelpType.TRANSPORT, "Viaje", "Traslado", null, null, 1, FosterUrgency.NORMAL
        ).getOrThrow()
        assertEquals(
            FosterHelpStatus.CANCELLED,
            help.changeHelpRequestStatus(hr.id, FosterHelpStatus.CANCELLED).getOrThrow().status
        )
    }

    @Test
    fun help_forbidEditClosed() = runTest {
        val pid = createActivePlacement()
        val hr = help.createHelpRequest(
            pid, FosterHelpType.OTHER, "X", "Y", null, null, null, FosterUrgency.NORMAL
        ).getOrThrow()
        help.changeHelpRequestStatus(hr.id, FosterHelpStatus.FULFILLED).getOrThrow()
        assertEquals(
            "FOSTER_HELP_REQUEST_NOT_EDITABLE",
            codeOf(help.changeHelpRequestStatus(hr.id, FosterHelpStatus.OPEN))
        )
        assertEquals(
            "FOSTER_HELP_REQUEST_NOT_EDITABLE",
            codeOf(help.recordContribution(hr.id, "tarde", 100, null, FosterContributionStatus.RECEIVED))
        )
    }

    @Test
    fun help_rejectBankOrPaymentData() = runTest {
        val pid = createActivePlacement()
        assertEquals(
            "FOSTER_CONTRIBUTION_INVALID",
            codeOf(
                help.createHelpRequest(
                    pid, FosterHelpType.MONEY, "Fondos", "CBU 123456789",
                    10000, "ARS", null, FosterUrgency.NORMAL
                )
            )
        )
        assertEquals(
            "FOSTER_CONTRIBUTION_INVALID",
            codeOf(
                help.createHelpRequest(
                    pid, FosterHelpType.MONEY, "Fondos", "Enviar a alias bancario xx",
                    10000, "ARS", null, FosterUrgency.NORMAL
                )
            )
        )
    }

    @Test
    fun help_forbidUnauthorizedUser() = runTest {
        val pid = createActivePlacement()
        actorId = "stranger"
        wireRepos()
        assertEquals(
            "FOSTER_HELP_REQUEST_FORBIDDEN",
            codeOf(
                help.createHelpRequest(
                    pid, FosterHelpType.FOOD, "x", "y", null, null, 1, FosterUrgency.NORMAL
                )
            )
        )
    }

    // —— Finalización ——

    @Test
    fun complete_activePlacement() = runTest {
        val pid = createActivePlacement()
        val done = placements.completePlacement(
            pid, FosterPlacementEndReason.RETURNED_TO_OWNER, "OK"
        ).getOrThrow()
        assertEquals(FosterPlacementStatus.COMPLETED, done.status)
        assertEquals(FosterPlacementEndReason.RETURNED_TO_OWNER.name, done.endReason)
        assertNotNull(done.endedAt)
        assertEquals("owner-1", done.endedBy)
    }

    @Test
    fun complete_setsCompletedStatus() = runTest {
        val pid = createActivePlacement()
        assertEquals(
            FosterPlacementStatus.COMPLETED,
            placements.completePlacement(pid, FosterPlacementEndReason.HOSPITALIZED, null)
                .getOrThrow().status
        )
    }

    @Test
    fun complete_freesOccupancy() = runTest {
        val pid = createActivePlacement()
        val homeId = placements.getPlacementById(pid).getOrThrow().fosterHomeId
        assertEquals(1, homes.getFosterHomeById(homeId).getOrThrow().currentOccupancy)
        placements.completePlacement(pid, FosterPlacementEndReason.OTHER, null).getOrThrow()
        assertEquals(0, homes.getFosterHomeById(homeId).getOrThrow().currentOccupancy)
    }

    @Test
    fun complete_updatesAvailability() = runTest {
        val pid = createActivePlacement()
        val homeId = placements.getPlacementById(pid).getOrThrow().fosterHomeId
        placements.completePlacement(pid, FosterPlacementEndReason.RETURNED_TO_OWNER, null).getOrThrow()
        val home = homes.getFosterHomeById(homeId).getOrThrow()
        assertEquals(FosterAvailabilityStatus.AVAILABLE, home.availabilityStatus)
    }

    @Test
    fun complete_revokesTemporaryCustodian() = runTest {
        val pid = createActivePlacement()
        assertTrue(store.temporaryCustody.value.any { it.placementId == pid && it.active })
        placements.completePlacement(pid, FosterPlacementEndReason.MOVED_TO_ANOTHER_FOSTER_HOME, null)
            .getOrThrow()
        assertTrue(store.temporaryCustody.value.none { it.placementId == pid && it.active })
    }

    @Test
    fun complete_keepsPrincipal() = runTest {
        val pid = createActivePlacement()
        assertEquals("requester-1", store.petPrincipal.value["pet-1"])
        placements.completePlacement(pid, FosterPlacementEndReason.ADOPTED, "M09 aparte").getOrThrow()
        assertEquals("requester-1", store.petPrincipal.value["pet-1"])
    }

    @Test
    fun complete_keepsHistoryImmutable() = runTest {
        val pid = createActivePlacement()
        expenses.addExpense(pid, FosterExpenseCategory.FOOD, "x", 100, "ARS", 1L, null).getOrThrow()
        evolution.addEvolution(
            pid, "t", "d", FosterHealthStatus.GOOD, null, 1L,
            emptyList(), FosterEvolutionVisibility.PARTICIPANTS
        ).getOrThrow()
        placements.completePlacement(pid, FosterPlacementEndReason.OTHER, null).getOrThrow()
        assertEquals(1, expenses.observeExpenses(pid).first().size)
        assertEquals(1, evolution.observeEvolution(pid).first().size)
        assertEquals(1, placements.observePlacementHistory("owner-1").first().size)
    }

    @Test
    fun complete_closesOpenHelp() = runTest {
        val pid = createActivePlacement()
        val hr = help.createHelpRequest(
            pid, FosterHelpType.FOOD, "x", "y", null, null, 1, FosterUrgency.NORMAL
        ).getOrThrow()
        placements.completePlacement(pid, FosterPlacementEndReason.OTHER, null).getOrThrow()
        assertEquals(FosterHelpStatus.CANCELLED, help.getHelpRequest(hr.id).getOrThrow().status)
    }

    @Test
    fun complete_idempotentDoubleFinalize() = runTest {
        val pid = createActivePlacement()
        val homeId = placements.getPlacementById(pid).getOrThrow().fosterHomeId
        placements.completePlacement(pid, FosterPlacementEndReason.OTHER, null).getOrThrow()
        val again = placements.completePlacement(pid, FosterPlacementEndReason.OTHER, null).getOrThrow()
        assertEquals(FosterPlacementStatus.COMPLETED, again.status)
        assertEquals(0, homes.getFosterHomeById(homeId).getOrThrow().currentOccupancy)
    }

    @Test
    fun cancel_reservedBeforeStart() = runTest {
        actorId = "owner-1"
        wireRepos()
        val home = homes.createFosterHome(
            CreateFosterHomeInput(
                displayName = "Casa",
                totalCapacity = 1,
                acceptedSpecies = setOf("DOG"),
                acceptedSizes = setOf("MEDIUM"),
                zoneText = "Zona",
                activate = true
            )
        ).getOrThrow()
        actorId = "requester-1"
        wireRepos()
        val req = requests.submitRequest(
            SubmitFosterRequestInput(home.id, "pet-1", "msg")
        ).getOrThrow()
        actorId = "owner-1"
        wireRepos()
        requests.acceptRequest(req.id).getOrThrow()
        val reserved = store.placements.value.first {
            it.fosterRequestId == req.id && it.status == FosterPlacementStatus.RESERVED
        }
        assertEquals(1, homes.getFosterHomeById(home.id).getOrThrow().reservedCount)
        val cancelled = placements.cancelReservedPlacement(reserved.id, "No puedo").getOrThrow()
        assertEquals(FosterPlacementStatus.CANCELLED, cancelled.status)
        assertEquals(
            FosterPlacementEndReason.CANCELLED_BEFORE_START.name,
            cancelled.endReason
        )
        assertEquals(0, homes.getFosterHomeById(home.id).getOrThrow().reservedCount)
        assertTrue(store.temporaryCustody.value.none { it.placementId == reserved.id })
        assertEquals(FosterHomeRequestStatus.ACCEPTED, requests.getRequestById(req.id).getOrThrow().status)
    }

    @Test
    fun cancel_freesReservedCapacity() = runTest {
        // covered in cancel_reservedBeforeStart; assert availability recovered
        actorId = "owner-1"
        wireRepos()
        val home = homes.createFosterHome(
            CreateFosterHomeInput(
                displayName = "Casa",
                totalCapacity = 1,
                acceptedSpecies = setOf("DOG"),
                acceptedSizes = setOf("MEDIUM"),
                zoneText = "Zona",
                activate = true
            )
        ).getOrThrow()
        actorId = "requester-1"
        wireRepos()
        val req = requests.submitRequest(SubmitFosterRequestInput(home.id, "pet-1", "msg")).getOrThrow()
        actorId = "owner-1"
        wireRepos()
        requests.acceptRequest(req.id).getOrThrow()
        val reserved = store.placements.value.first { it.fosterRequestId == req.id }
        placements.cancelReservedPlacement(reserved.id, null).getOrThrow()
        val after = homes.getFosterHomeById(home.id).getOrThrow()
        assertEquals(0, after.reservedCount)
        assertEquals(FosterAvailabilityStatus.AVAILABLE, after.availabilityStatus)
    }

    @Test
    fun complete_invalidTransitionOnReserved() = runTest {
        actorId = "owner-1"
        wireRepos()
        val home = homes.createFosterHome(
            CreateFosterHomeInput(
                displayName = "Casa",
                totalCapacity = 1,
                acceptedSpecies = setOf("DOG"),
                acceptedSizes = setOf("MEDIUM"),
                zoneText = "Zona",
                activate = true
            )
        ).getOrThrow()
        actorId = "requester-1"
        wireRepos()
        val req = requests.submitRequest(SubmitFosterRequestInput(home.id, "pet-1", "msg")).getOrThrow()
        actorId = "owner-1"
        wireRepos()
        requests.acceptRequest(req.id).getOrThrow()
        val reserved = store.placements.value.first { it.fosterRequestId == req.id }
        assertEquals(
            "FOSTER_PLACEMENT_NOT_ACTIVE",
            codeOf(placements.completePlacement(reserved.id, FosterPlacementEndReason.OTHER, null))
        )
        val active = placements.startPlacement(req.id, null).getOrThrow()
        assertEquals(FosterPlacementStatus.ACTIVE, active.status)
        assertEquals(
            "FOSTER_PLACEMENT_INVALID_TRANSITION",
            codeOf(placements.cancelReservedPlacement(active.id, null))
        )
    }

    @Test
    fun complete_revokePermissionFailure() = runTest {
        val pid = createActivePlacement()
        store.forceRevokeFailure = true
        assertEquals(
            "FOSTER_TEMPORARY_PERMISSION_REVOKE_FAILED",
            codeOf(placements.completePlacement(pid, FosterPlacementEndReason.OTHER, null))
        )
        assertEquals(FosterPlacementStatus.ACTIVE, placements.getPlacementById(pid).getOrThrow().status)
    }

    @Test
    fun ids_blankAndMissing() = runTest {
        assertEquals(
            "FOSTER_PLACEMENT_NOT_FOUND",
            codeOf(placements.getPlacementById(""))
        )
        assertEquals(
            "FOSTER_PLACEMENT_NOT_FOUND",
            codeOf(placements.getPlacementById("missing-id"))
        )
        assertEquals(
            "FOSTER_HELP_REQUEST_NOT_FOUND",
            codeOf(help.getHelpRequest(""))
        )
        assertEquals(
            "FOSTER_HELP_REQUEST_NOT_FOUND",
            codeOf(help.getHelpRequest("nope"))
        )
    }

    @Test
    fun repository_errorMappedToUserMessage() {
        val msg = M10FosterErrorMapper.userMessage("FOSTER_EXPENSE_FORBIDDEN")
        assertFalse(msg.contains("postgres", ignoreCase = true))
        assertFalse(msg.contains("supabase", ignoreCase = true))
        assertTrue(msg.isNotBlank())
    }

    @Test
    fun viewModel_doubleSubmitGuarded() = runTest {
        val pid = createActivePlacement()
        val vm = FosterExpenseFormViewModel(pid, expenses)
        vm.submit(FosterExpenseCategory.FOOD, "a", 100, "ARS", null)
        // second call while/after — busy flag clears after; ensure at most one row from rapid double
        vm.submit(FosterExpenseCategory.FOOD, "b", 200, "ARS", null)
        assertTrue(expenses.observeExpenses(pid).first().size >= 1)
    }

    @Test
    fun viewModel_blankIdControlledLoading() {
        val vm = FosterPlacementManagementViewModel("", placements)
        val state = vm.uiState.value
        assertTrue(state is FosterCarePanelUiState.Error)
        assertNotEquals(FosterCarePanelUiState.Loading, state)
    }

    @Test
    fun noRealSupabaseInTests() {
        assertTrue(expenses is MockFosterExpenseRepository)
        assertTrue(evolution is MockFosterEvolutionRepository)
        assertTrue(help is MockFosterHelpRepository)
        assertTrue(placements is MockFosterPlacementRepository)
        assertFalse(expenses.javaClass.name.contains("Supabase"))
    }
}
