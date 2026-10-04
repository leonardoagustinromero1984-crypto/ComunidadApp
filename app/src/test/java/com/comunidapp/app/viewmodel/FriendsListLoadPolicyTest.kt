package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.FriendConnection
import com.comunidapp.app.data.model.FriendConnectionStatus
import com.comunidapp.app.data.model.User
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendsListLoadPolicyTest {

    @Test
    fun identicalFriendshipEmissionDoesNotReload() = runTest {
        val snapshot = listOf(connection("c1"))
        val ticks = flow {
            emit(snapshot)
            emit(snapshot)
            emit(snapshot.map { it.copy() })
        }
        val started = ticks.onlyWhenFriendshipChanges().toList()
        assertEquals(1, started.size)
        assertFalse(FriendsListLoadPolicy.shouldStartLoad(snapshot, snapshot))
        val visible = FriendsListLoadPolicy.commit(
            friends = listOf(item("c1")),
            incoming = emptyList(),
            outgoing = emptyList(),
            incomingCount = 0,
            actionInProgressId = null,
            actionMessage = null
        )
        val duringRepeat = FriendsListLoadPolicy.stateWhileResolving(visible)
        assertFalse(duringRepeat.isLoading)
        assertEquals(visible.friends, duringRepeat.friends)
    }

    @Test
    fun laterUpdateKeepsVisibleContent() {
        val visible = FriendsListLoadPolicy.commit(
            friends = listOf(item("c1")),
            incoming = emptyList(),
            outgoing = emptyList(),
            incomingCount = 1,
            actionInProgressId = null,
            actionMessage = null
        )
        val duringRefresh = FriendsListLoadPolicy.stateWhileResolving(visible)
        assertFalse(duringRefresh.isLoading)
        assertEquals(visible.friends, duringRefresh.friends)
        assertEquals(1, duringRefresh.incomingCount)
        val committed = FriendsListLoadPolicy.commit(
            friends = listOf(item("c1"), item("c2")),
            incoming = emptyList(),
            outgoing = emptyList(),
            incomingCount = 0,
            actionInProgressId = null,
            actionMessage = null
        )
        assertFalse(committed.isLoading)
        assertEquals(listOf("c1", "c2"), committed.friends.map { it.connection.id })
    }

    @Test
    fun slowPollDoesNotCycleLoadingEveryFourSeconds() = runTest {
        val first = listOf(connection("c1"))
        val updated = listOf(connection("c1"), connection("c2"))
        val visible = FriendsListLoadPolicy.commit(
            friends = listOf(item("c1")),
            incoming = emptyList(),
            outgoing = emptyList(),
            incomingCount = 0,
            actionInProgressId = null,
            actionMessage = null
        )
        val ticks = flow {
            emit(first)
            kotlinx.coroutines.delay(4_000)
            emit(first)
            kotlinx.coroutines.delay(4_000)
            emit(updated)
        }
        val started = ticks.onlyWhenFriendshipChanges().toList()
        assertEquals(listOf(1, 2), started.map { it.size })
        val whileSlow = FriendsListLoadPolicy.stateWhileResolving(visible)
        assertFalse(whileSlow.isLoading)
        assertEquals(visible.friends, whileSlow.friends)
        assertTrue(FriendsListLoadPolicy.hasResolvedContent(whileSlow))
        val initial = FriendsListLoadPolicy.stateWhileResolving(FriendsListUiState())
        assertTrue(initial.isLoading)
        assertTrue(initial.friends.isEmpty())
    }

    private fun connection(id: String) = FriendConnection(
        id = id,
        requesterId = "me",
        addresseeId = "other-$id",
        status = FriendConnectionStatus.ACCEPTED
    )

    private fun item(id: String) = FriendListItem(
        user = User(id = id, name = "Ada", email = "ada@example.com", accountType = AccountType.PERSON),
        connection = connection(id)
    )
}
