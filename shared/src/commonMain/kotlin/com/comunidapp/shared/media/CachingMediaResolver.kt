package com.comunidapp.shared.media

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * MediaResolver con cache en memoria + dedupe de resolves concurrentes.
 * No persiste signed URLs. clearCache() en logout.
 */
internal class CachingMediaResolver(
    private val gateway: M05MediaReadGateway,
    private val clock: () -> Long,
    private val checkAuthenticated: suspend () -> Boolean,
    private val maxEntries: Int = 64,
    private val skewMs: Long = 15_000L
) : MediaResolver {

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, MediaResource>()
    private val accessOrder = mutableListOf<String>()
    private val inflight = HashMap<String, CompletableDeferred<MediaResolveResult>>()

    override suspend fun resolve(ref: MediaRef): MediaResolveResult {
        // Public HTTPS RemoteUrl may resolve without session; private refs still require auth.
        if (ref !is MediaRef.RemoteUrl && !checkAuthenticated()) {
            clearCache()
            return MediaResolveResult.Unauthenticated
        }
        val key = cacheKey(ref)
        val now = clock()

        mutex.withLock {
            cache[key]?.let { cached ->
                val exp = cached.expiresAtEpochMs
                if (exp == null || exp - skewMs > now) {
                    touch(key)
                    return MediaResolveResult.Success(cached)
                }
                cache.remove(key)
                accessOrder.remove(key)
            }
        }

        val (deferred, isOwner) = mutex.withLock {
            val existing = inflight[key]
            if (existing != null) {
                existing to false
            } else {
                val created = CompletableDeferred<MediaResolveResult>()
                inflight[key] = created
                created to true
            }
        }

        if (!isOwner) return deferred.await()

        val result = try {
            resolveFresh(ref, clock())
        } catch (t: Throwable) {
            mapReadThrowable(t)
        }
        deferred.complete(result)
        mutex.withLock { inflight.remove(key) }
        return result
    }

    private suspend fun resolveFresh(ref: MediaRef, now: Long): MediaResolveResult {
        val result = when (ref) {
            is MediaRef.Asset -> gateway.resolveAsset(ref.assetId, now)
            is MediaRef.RemoteUrl -> gateway.resolveRemoteUrl(ref.url, now)
            is MediaRef.ProfileAvatarPath -> gateway.resolveProfileAvatarPath(ref.path, now)
        }
        if (result is MediaResolveResult.Success) {
            mutex.withLock {
                val key = cacheKey(ref)
                cache[key] = result.resource
                touch(key)
                while (cache.size > maxEntries && accessOrder.isNotEmpty()) {
                    val oldest = accessOrder.removeAt(0)
                    cache.remove(oldest)
                }
            }
        }
        return result
    }

    override fun clearCache() {
        cache.clear()
        accessOrder.clear()
        inflight.values.forEach { it.cancel() }
        inflight.clear()
    }

    fun invalidate(ref: MediaRef) {
        val key = cacheKey(ref)
        cache.remove(key)
        accessOrder.remove(key)
    }

    fun invalidateProfileAvatarPaths() {
        val keys = cache.keys.filter { it.startsWith("avatar:") }
        keys.forEach { cache.remove(it) }
        accessOrder.removeAll { it.startsWith("avatar:") }
    }

    override fun invalidateProfileAvatars() = invalidateProfileAvatarPaths()

    internal fun cachedCountForTests(): Int = cache.size

    private fun touch(key: String) {
        accessOrder.remove(key)
        accessOrder.add(key)
    }

    private fun cacheKey(ref: MediaRef): String =
        when (ref) {
            is MediaRef.Asset -> "asset:${ref.assetId}"
            is MediaRef.RemoteUrl -> "url:${ref.url}"
            is MediaRef.ProfileAvatarPath -> "avatar:${ref.path}"
        }
}
