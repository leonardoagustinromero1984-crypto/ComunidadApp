package com.comunidapp.app.domain.pets

import com.comunidapp.app.core.logging.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Last-good-value inbox for PENDING care transfers.
 * Independent of Mis mascotas / accessible pets. A failed refresh never
 * replaces a previously visible list with empty.
 */
class IncomingCareTransferInbox(
    private val repository: () -> PetTransferRepository?,
    private val currentUserId: () -> String?
) {
    private val mutex = Mutex()
    private val _items = MutableStateFlow<List<PetTransfer>>(emptyList())
    val items: StateFlow<List<PetTransfer>> = _items.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var lastUserId: String? = null
    private var refreshSeq: Int = 0
    private var lastRefreshAtMs: Long = 0L
    private var lastRefreshReason: String? = null

    suspend fun refresh(reason: String) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (reason == lastRefreshReason && now - lastRefreshAtMs < 1_200L) {
                inboxLog("INCOMING_DEDUPE reason=$reason keepCount=${_items.value.size}")
                return
            }
            val seq = ++refreshSeq
            val uid = currentUserId()
            inboxLog("INCOMING_LOAD_START seq=$seq reason=$reason userPresent=${uid != null}")
            if (uid == null) {
                inboxLog("INCOMING_SKIP seq=$seq no_session keepCount=${_items.value.size}")
                return
            }
            if (lastUserId != null && lastUserId != uid) {
                inboxLog("INCOMING_USER_CHANGED seq=$seq clear")
                _items.value = emptyList()
            }
            lastUserId = uid
            val repo = repository()
            if (repo == null) {
                inboxLog("INCOMING_SKIP seq=$seq no_repo")
                return
            }
            val before = _items.value
            inboxLog("UI_STATE_BEFORE_SET seq=$seq count=${before.size} ids=${idsOf(before)}")
            val mapped = try {
                val rows = repo.listIncoming()
                inboxLog(
                    "INCOMING_RPC_RESULT seq=$seq count=${rows.size} ids=${idsOf(rows)} " +
                        "statuses=${rows.joinToString { it.status.name }} " +
                        "targets=${rows.joinToString { targetSummary(it) }}"
                )
                inboxLog("MAPPED_INCOMING seq=$seq count=${rows.size}")
                inboxLog("FILTER_BEFORE seq=$seq count=${rows.size}")
                inboxLog("FILTER_AFTER seq=$seq count=${rows.size} petAccessFilter=NO")
                rows
            } catch (error: Exception) {
                inboxLog(
                    "INCOMING_RPC_FAIL seq=$seq keepPrevious=${before.size} " +
                        "type=${error::class.java.simpleName}"
                )
                inboxLog("UI_STATE_AFTER_SET seq=$seq overwritten=NO visible=${before.size}")
                return
            }
            _items.value = mapped
            lastRefreshAtMs = now
            lastRefreshReason = reason
            inboxLog(
                "UI_STATE_AFTER_SET seq=$seq count=${mapped.size} ids=${idsOf(mapped)} " +
                    "visible=${mapped.size}"
            )
        }
    }

    fun remove(transferId: String) {
        val id = transferId.trim()
        if (id.isEmpty()) return
        _items.value = _items.value.filter { it.id.value != id }
    }

    fun publishAcceptedNotice(petName: String) {
        _notice.value = PetCareTransferCopy.nowUnderYourCare(petName)
    }

    fun consumeNotice() {
        _notice.value = null
    }

    suspend fun accept(transfer: PetTransfer): Result<Unit> {
        val repo = repository() ?: return Result.failure(IllegalStateException("UNAVAILABLE"))
        val result = repo.accept(transfer.id, System.currentTimeMillis())
        if (result.isSuccess) {
            remove(transfer.id.value)
            publishAcceptedNotice(transfer.petDisplayName.orEmpty())
            runCatching { refresh("post_accept") }
        }
        return result
    }

    suspend fun reject(transfer: PetTransfer): Result<Unit> {
        val repo = repository() ?: return Result.failure(IllegalStateException("UNAVAILABLE"))
        val result = repo.reject(transfer.id, System.currentTimeMillis())
        if (result.isSuccess) {
            remove(transfer.id.value)
            runCatching { refresh("post_reject") }
        }
        return result
    }

    fun clear() {
        lastUserId = null
        _items.value = emptyList()
    }

    companion object {
        const val TAG = "CareInbox"

        private fun idsOf(rows: List<PetTransfer>): String =
            rows.joinToString(prefix = "[", postfix = "]") { it.id.value }

        private fun inboxLog(message: String) {
            runCatching { AppLog.info(TAG, message) }
        }

        private fun targetSummary(transfer: PetTransfer): String = when (val t = transfer.toPrincipal) {
            is PetPrincipalHolder.Person -> "PERSON:${t.userId}"
            is PetPrincipalHolder.Organization -> "ORGANIZATION:${t.organizationId.value}"
        }
    }
}
