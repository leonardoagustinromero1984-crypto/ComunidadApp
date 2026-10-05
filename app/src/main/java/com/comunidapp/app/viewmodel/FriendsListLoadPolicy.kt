package com.comunidapp.app.viewmodel

import com.comunidapp.app.data.model.FriendConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Friendship polling may keep running. The list must not return to the
 * full-screen loader on every tick, and an identical snapshot must not
 * start another profile fetch.
 */
object FriendsListLoadPolicy {

    fun shouldStartLoad(
        previous: List<FriendConnection>?,
        next: List<FriendConnection>
    ): Boolean {
        if (previous == null) return true
        return previous != next
    }

    /** Initial state, before the first resolved snapshot. */
    fun hasResolvedContent(state: FriendsListUiState): Boolean = !state.isLoading

    /**
     * While a fetch is in flight, keep the rows already on screen.
     * Loading replaces the screen only when nothing has been resolved yet.
     */
    fun stateWhileResolving(current: FriendsListUiState): FriendsListUiState {
        if (!hasResolvedContent(current)) {
            return FriendsListUiState(isLoading = true)
        }
        return current.copy(isLoading = false)
    }

    fun commit(
        friends: List<FriendListItem>,
        incoming: List<FriendListItem>,
        outgoing: List<FriendListItem>,
        incomingCount: Int,
        actionInProgressId: String?,
        actionMessage: String?
    ): FriendsListUiState = FriendsListUiState(
        isLoading = false,
        friends = friends,
        incoming = incoming,
        incomingCount = incomingCount,
        outgoing = outgoing,
        actionInProgressId = actionInProgressId,
        actionMessage = actionMessage
    )
}

fun Flow<List<FriendConnection>>.onlyWhenFriendshipChanges(): Flow<List<FriendConnection>> = flow {
    var previous: List<FriendConnection>? = null
    collect { next ->
        if (FriendsListLoadPolicy.shouldStartLoad(previous, next)) {
            previous = next
            emit(next)
        }
    }
}
