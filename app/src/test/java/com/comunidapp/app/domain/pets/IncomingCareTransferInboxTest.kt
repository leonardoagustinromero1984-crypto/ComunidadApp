package com.comunidapp.app.domain.pets

import com.comunidapp.app.viewmodel.FakeStage5TransferRepository
import com.comunidapp.app.viewmodel.stage5Transfer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class IncomingCareTransferInboxTest {

    @Test
    fun failedRefreshDoesNotOverwriteVisibleInbox() = runTest {
        val repo = FakeStage5TransferRepository()
        repo.incomingItems = listOf(stage5Transfer(id = "ba8378f9-e970-4f13-855a-e2be2d77aac9"))
        val inbox = IncomingCareTransferInbox(
            repository = { repo },
            currentUserId = { "user_2" }
        )
        inbox.refresh("first")
        assertEquals(1, inbox.items.value.size)

        repo.incomingFailure = IllegalStateException("boom")
        inbox.refresh("second")
        assertEquals(1, inbox.items.value.size)
        assertEquals(
            "ba8378f9-e970-4f13-855a-e2be2d77aac9",
            inbox.items.value.single().id.value
        )
    }

    @Test
    fun nullSessionDoesNotClearLoadedInbox() = runTest {
        val repo = FakeStage5TransferRepository()
        repo.incomingItems = listOf(stage5Transfer(id = "ba8378f9-e970-4f13-855a-e2be2d77aac9"))
        var userId: String? = "user_2"
        val inbox = IncomingCareTransferInbox(
            repository = { repo },
            currentUserId = { userId }
        )
        inbox.refresh("first")
        assertEquals(1, inbox.items.value.size)

        userId = null
        inbox.refresh("auth_flicker")
        assertEquals(1, inbox.items.value.size)
    }

    @Test
    fun removeDropsPendingWithoutWaitingRefresh() = runTest {
        val repo = FakeStage5TransferRepository()
        repo.incomingItems = listOf(stage5Transfer(id = "ba8378f9-e970-4f13-855a-e2be2d77aac9"))
        val inbox = IncomingCareTransferInbox(
            repository = { repo },
            currentUserId = { "user_2" }
        )
        inbox.refresh("first")
        assertEquals(1, inbox.items.value.size)
        inbox.remove("ba8378f9-e970-4f13-855a-e2be2d77aac9")
        assertEquals(0, inbox.items.value.size)
    }
}
