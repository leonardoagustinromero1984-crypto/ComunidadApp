package com.comunidapp.shared.lostfound

import com.comunidapp.shared.auth.FakeAuthSessionGateway
import com.comunidapp.shared.auth.GatewayAuthRepository
import com.comunidapp.shared.domain.lostfound.LostFoundCaseStatus
import com.comunidapp.shared.remote.FakeLostFoundRemoteGateway
import com.comunidapp.shared.remote.FakeLostFoundWriteGateway
import com.comunidapp.shared.remote.RemoteLostFoundRow
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

class LostFoundManageVerticalTest {

    private fun auth(userId: String = "owner-1") =
        GatewayAuthRepository(
            FakeAuthSessionGateway(
                SessionState.Authenticated(SessionUser(userId, "a@leover.test", "Ana"))
            )
        )

    private fun activeRow(
        id: String = "lf-1",
        authorId: String = "owner-1",
        status: String = "ACTIVE"
    ) = RemoteLostFoundRow(
        id = id,
        authorId = authorId,
        authorName = "Ana",
        type = "LOST",
        petName = "Luna",
        species = "DOG",
        location = "Palermo",
        description = "Se perdió cerca de la plaza",
        status = status,
        publicCode = "PUB-LF1"
    )

    private fun remote(
        row: RemoteLostFoundRow = activeRow(),
        write: FakeLostFoundWriteGateway = FakeLostFoundWriteGateway(),
        authRepo: GatewayAuthRepository = auth()
    ): Pair<RemoteLostFoundRepository, FakeLostFoundRemoteGateway> {
        val read = FakeLostFoundRemoteGateway(detail = row, list = listOf(row))
        val repo = RemoteLostFoundRepository(
            gateway = read,
            writeGateway = write,
            sessionRepository = authRepo
        )
        return repo to read
    }

    @Test
    fun resolve_success() = runTest {
        val write = FakeLostFoundWriteGateway()
        val (repo, read) = remote(write = write)
        val result = repo.markResolved(LostFoundId("lf-1"))
        assertIs<LostFoundManageResult.Success>(result)
        assertEquals(listOf("lf-1" to "RESOLVED"), write.statusUpdates)
        // Simulate backend status for refresh
        read.detail = activeRow(status = "RESOLVED")
        val detail = repo.observeDetail(LostFoundId("lf-1"))
            .first { it !is VerticalLoadState.Loading }
        val content = assertIs<VerticalLoadState.Content<LostFoundDetail>>(detail)
        assertEquals(LostFoundCaseStatus.RESOLVED, content.data.status)
        assertTrue(content.data.viewerCanManage)
    }

    @Test
    fun non_owner_forbidden() = runTest {
        val (repo, _) = remote(row = activeRow(authorId = "other-user"))
        val result = repo.markResolved(LostFoundId("lf-1"))
        assertIs<LostFoundManageResult.Forbidden>(result)
        val detail = repo.observeDetail(LostFoundId("lf-1"))
            .first { it !is VerticalLoadState.Loading }
        val content = assertIs<VerticalLoadState.Content<LostFoundDetail>>(detail)
        assertFalse(content.data.viewerCanManage)
    }

    @Test
    fun invalid_transition_conflict() = runTest {
        val (repo, _) = remote(row = activeRow(status = "RESOLVED"))
        val result = repo.markResolved(LostFoundId("lf-1"))
        assertIs<LostFoundManageResult.Conflict>(result)
    }

    @Test
    fun unauthenticated() = runTest {
        val authRepo = GatewayAuthRepository(FakeAuthSessionGateway(SessionState.Unauthenticated))
        val (repo, _) = remote(authRepo = authRepo)
        val result = repo.markResolved(LostFoundId("lf-1"))
        assertIs<LostFoundManageResult.Unauthenticated>(result)
    }

    @Test
    fun fake_mark_resolved() = runTest {
        val fake = FakeLostFoundRepository()
        val id = LostFoundId("demo-lost-luna")
        assertIs<LostFoundManageResult.Success>(fake.markResolved(id))
        assertEquals(1, fake.markResolvedCalls)
        val detail = fake.observeDetail(id).first { it !is VerticalLoadState.Loading }
        val content = assertIs<VerticalLoadState.Content<LostFoundDetail>>(detail)
        assertEquals(LostFoundCaseStatus.RESOLVED, content.data.status)
    }

    @Test
    fun unconfigured_no_fake_success() = runTest {
        val result = UnconfiguredLostFoundRepository().markResolved(LostFoundId("x"))
        assertIs<LostFoundManageResult.BackendError>(result)
    }
}
