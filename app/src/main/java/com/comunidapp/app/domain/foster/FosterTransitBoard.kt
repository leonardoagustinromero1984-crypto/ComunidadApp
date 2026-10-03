package com.comunidapp.app.domain.foster

import com.comunidapp.app.domain.user.SessionBoundState
import com.comunidapp.app.domain.user.SessionEpoch
import com.comunidapp.app.domain.user.SessionGeneration
import kotlinx.coroutines.flow.StateFlow

/**
 * One session-scoped signal per pet. VitaCora reads it for the transit CTA.
 * A response captured before [SessionEpoch.invalidate] cannot publish.
 */
data class FosterTransitSnapshot(
    val petId: String,
    val requestId: String?,
    val status: String?
)

class FosterTransitBoard(private val epoch: SessionEpoch) {
    private val bound = SessionBoundState(emptyMap<String, FosterTransitSnapshot>(), epoch)
    val state: StateFlow<Map<String, FosterTransitSnapshot>> = bound.state

    fun publish(token: Long, snapshot: FosterTransitSnapshot): Boolean {
        if (snapshot.petId.isBlank()) return false
        return bound.tryUpdate(token) { current ->
            current + (snapshot.petId to snapshot)
        }
    }

    fun current(petId: String): FosterTransitSnapshot? = bound.value[petId]

    suspend fun refresh(
        petId: String,
        load: suspend () -> FosterTransitSnapshot
    ): Boolean {
        val token = epoch.current()
        val snapshot = load()
        return publish(token, snapshot)
    }
}

object FosterTransitSignals {
    val live: FosterTransitBoard = FosterTransitBoard(SessionGeneration)
}
