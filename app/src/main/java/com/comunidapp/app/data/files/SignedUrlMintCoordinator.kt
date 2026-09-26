package com.comunidapp.app.data.files

import com.comunidapp.app.data.repository.mintMediaUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * Limited-concurrency signed URL mint with in-flight dedupe.
 * Does not bypass media-signed-url ACL / rate limits.
 */
object SignedUrlMintCoordinator {
    private const val MAX_PARALLEL = 5
    private val semaphore = Semaphore(MAX_PARALLEL)
    private val inFlight = ConcurrentHashMap<String, Mutex>()
    private val cache = ConcurrentHashMap<String, CachedUrl>()

    private data class CachedUrl(val url: String, val expiresAtEpochMs: Long)

    suspend fun mint(assetId: String, nowEpochMs: Long = System.currentTimeMillis()): String? {
        val id = assetId.trim()
        if (id.isEmpty()) return null
        cache[id]?.let { cached ->
            if (cached.expiresAtEpochMs - nowEpochMs > 15_000L) return cached.url
        }
        val gate = inFlight.getOrPut(id) { Mutex() }
        return gate.withLock {
            cache[id]?.let { cached ->
                if (cached.expiresAtEpochMs - nowEpochMs > 15_000L) return@withLock cached.url
            }
            semaphore.withPermit {
                val url = mintMediaUrl(id) ?: return@withPermit null
                // Edge TTL typically ~600s; keep conservative client cache.
                cache[id] = CachedUrl(url, nowEpochMs + 480_000L)
                url
            }
        }.also {
            inFlight.remove(id, gate)
        }
    }

    suspend fun mintMany(assetIds: List<String>): Map<String, String?> = coroutineScope {
        assetIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { id ->
                async { id to mint(id) }
            }
            .awaitAll()
            .toMap()
    }

    fun clear() {
        cache.clear()
        inFlight.clear()
    }
}
