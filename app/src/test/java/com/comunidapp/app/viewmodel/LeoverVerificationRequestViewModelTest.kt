package com.comunidapp.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.comunidapp.app.data.repository.LeoverVerificationRequests
import com.comunidapp.app.data.repository.LeoverVerificationRow
import com.comunidapp.app.data.repository.ResponderBaseSnapshot
import com.comunidapp.app.domain.verification.VerificationDisplayPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Submit success writes local PENDING, then refresh keeps the backend PENDING row visible.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LeoverVerificationRequestViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun submitSuccess_setsPending_andRefreshKeepsBackendPending() = runTest(dispatcher) {
        val requests = FakeVerificationRequests()
        val base = FakeResponderBase(hasBase = true)
        lateinit var vm: LeoverVerificationRequestViewModel
        requests.statusDuringRefresh = { vm.ui.value.status }
        vm = LeoverVerificationRequestViewModel(
            SavedStateHandle(
                mapOf(
                    "functionCode" to "SHELTER",
                    "organizationId" to ORG_ID
                )
            ),
            requests,
            base
        )
        advanceUntilIdle()

        assertNull(vm.ui.value.status)
        assertEquals(VerificationDisplayPolicy.NOT_REQUESTED_COPY, VerificationDisplayPolicy.statusLabel(vm.ui.value.status))
        assertTrue(vm.ui.value.storedLocation)

        vm.updateTerms(true)
        vm.submit(needsLocation = true, needsOrg = true)
        advanceUntilIdle()

        assertEquals(1, requests.requestCount)
        assertTrue(requests.listCount >= 2)
        assertEquals("PENDING", requests.statusAtPostSubmitRefresh)
        assertEquals("PENDING", vm.ui.value.status)
        assertEquals(REQUEST_ID, vm.ui.value.requestId)
        assertEquals(VerificationDisplayPolicy.PENDING_COPY, vm.ui.value.message)
        assertEquals(false, vm.ui.value.submitting)
        assertEquals(1, requests.rowsAfterSubmit.size)
        assertEquals("PENDING", requests.rowsAfterSubmit.single().status)
    }

    private class FakeVerificationRequests : LeoverVerificationRequests {
        var requestCount = 0
        var listCount = 0
        var rowsAfterSubmit: List<LeoverVerificationRow> = emptyList()
        var statusAtPostSubmitRefresh: String? = null
        var statusDuringRefresh: () -> String? = { null }
        private var submitted = false

        override suspend fun request(
            functionCode: String,
            termsAccepted: Boolean,
            evidenceNote: String?,
            organizationId: String?
        ): Result<String> {
            requestCount += 1
            submitted = true
            return Result.success(REQUEST_ID)
        }

        override suspend fun listMine(): Result<List<LeoverVerificationRow>> {
            listCount += 1
            if (submitted) statusAtPostSubmitRefresh = statusDuringRefresh()
            val rows = if (!submitted) {
                emptyList()
            } else {
                listOf(
                    LeoverVerificationRow(
                        id = REQUEST_ID,
                        functionCode = "SHELTER",
                        status = "PENDING",
                        reviewNote = null,
                        createdAt = "2026-09-27T03:53:59Z",
                        organizationId = ORG_ID
                    )
                )
            }
            if (submitted) rowsAfterSubmit = rows
            return Result.success(rows)
        }

        override suspend fun resubmit(id: String, evidenceNote: String?): Result<Unit> =
            Result.success(Unit)
    }

    private class FakeResponderBase(private val hasBase: Boolean) : ResponderBaseGateway {
        override suspend fun get(organizationId: String?): Result<ResponderBaseSnapshot> =
            Result.success(ResponderBaseSnapshot(hasBaseLocation = hasBase))

        override suspend fun upsert(
            latitude: Double,
            longitude: Double,
            organizationId: String?,
            address: String?
        ): Result<Unit> = Result.success(Unit)
    }

    private companion object {
        const val ORG_ID = "085a9890-813d-40e7-bc98-417e892f91f1"
        const val REQUEST_ID = "d8f507c1-9431-4bb0-9f67-3ec89b467624"
    }
}
