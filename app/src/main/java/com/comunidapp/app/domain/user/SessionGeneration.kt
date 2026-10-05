package com.comunidapp.app.domain.user

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monotonic epoch for one authenticated session.
 *
 * Async work captures [current] before a request and commits only through
 * [publishIfCurrent]. That check shares a lock with [invalidate] and [rotate],
 * so a late response cannot write after logout or an account switch.
 */
interface SessionEpoch {
    fun current(): Long

    /** Drops the previous generation. In-flight tokens no longer match. */
    fun invalidate(): Long

    /**
     * Advances the generation and runs [reset] under the same lock as a commit.
     * Use this to empty user-scoped state atomically with the invalidation.
     */
    fun rotate(reset: () -> Unit = {}): Long

    /** Runs [write] only when [token] is still the active generation. */
    fun <T> publishIfCurrent(token: Long, write: () -> T): T?

    /** Holds the same lock as commit and invalidation. */
    fun <T> exclusive(block: () -> T): T
}

/**
 * Process-wide session epoch. Logout and account switch call [invalidate]
 * before any user-scoped cache is allowed to commit again.
 */
object SessionGeneration : SessionEpoch {
    /**
     * Actor used when mock stores are reseeded after a session ends.
     * Must not be a real user id and must not be `mock_user_admin`.
     */
    const val NEUTRAL_MOCK_ACTOR = "session_residue_cleared"

    private val delegate = ManualSessionEpoch()

    override fun current(): Long = delegate.current()

    override fun invalidate(): Long = delegate.invalidate()

    override fun rotate(reset: () -> Unit): Long = delegate.rotate(reset)

    override fun <T> publishIfCurrent(token: Long, write: () -> T): T? =
        delegate.publishIfCurrent(token, write)

    override fun <T> exclusive(block: () -> T): T = delegate.exclusive(block)
}

class ManualSessionEpoch : SessionEpoch {
    private val lock = Any()
    private var generation = 0L

    override fun current(): Long = synchronized(lock) { generation }

    override fun invalidate(): Long = rotate {}

    override fun rotate(reset: () -> Unit): Long = synchronized(lock) {
        generation += 1L
        reset()
        generation
    }

    override fun <T> publishIfCurrent(token: Long, write: () -> T): T? = synchronized(lock) {
        if (token != generation) null else write()
    }

    override fun <T> exclusive(block: () -> T): T = synchronized(lock) { block() }
}

/**
 * State holder whose writes are rejected when the captured session token
 * no longer matches [epoch].
 */
class SessionBoundState<T>(
    initial: T,
    private val epoch: SessionEpoch
) {
    private val flow = MutableStateFlow(initial)
    val state: StateFlow<T> = flow.asStateFlow()
    val value: T get() = flow.value

    fun reset(next: T) {
        epoch.exclusive { flow.value = next }
    }

    fun tryWrite(token: Long, next: T): Boolean = epoch.publishIfCurrent(token) {
        flow.value = next
        true
    } == true

    fun tryUpdate(token: Long, transform: (T) -> T): Boolean = epoch.publishIfCurrent(token) {
        flow.value = transform(flow.value)
        true
    } == true
}
