package com.comunidapp.app.domain.perf

import com.comunidapp.app.core.logging.AppLog
import com.comunidapp.app.domain.time.ElapsedRealtime

/**
 * Lightweight screen timing probe for PERF-02 QA.
 * Logs only; never blocks UI. No PII.
 */
object ScreenPerfProbe {
    private const val TAG = "PERF02"

    fun begin(screen: String): Session = Session(screen, ElapsedRealtime.nowMs())

    object Ledger {
        @Volatile private var requestCount: Int = 0
        @Volatile private var cacheHits: Int = 0
        @Volatile private var signedUrlCount: Int = 0

        fun rpc(name: String) {
            requestCount += 1
            if (!com.comunidapp.app.BuildConfig.DEBUG) return
            AppLog.info(TAG, "rpc=$name count=$requestCount")
        }

        fun signedUrl(cacheHit: Boolean) {
            signedUrlCount += 1
            if (cacheHit) cacheHits += 1
            if (!com.comunidapp.app.BuildConfig.DEBUG) return
            AppLog.info(
                TAG,
                "signed_url cache=${if (cacheHit) "hit" else "miss"} " +
                    "signed_count=$signedUrlCount cache_hits=$cacheHits"
            )
        }

        fun snapshot(): String =
            "requests=$requestCount signed_urls=$signedUrlCount cache_hits=$cacheHits"

        fun reset() {
            requestCount = 0
            cacheHits = 0
            signedUrlCount = 0
        }
    }

    class Session(
        private val screen: String,
        private val t0: Long
    ) {
        private var firstContentMs: Long? = null
        private var networkMs: Long = 0L
        private var mappingMs: Long = 0L
        private var secondaryMs: Long = 0L

        fun markFirstContent() {
            if (firstContentMs == null) {
                firstContentMs = ElapsedRealtime.nowMs() - t0
                AppLog.info(
                    TAG,
                    "screen=$screen time_to_first_content_ms=$firstContentMs"
                )
            }
        }

        suspend fun <T> network(block: suspend () -> T): T {
            val start = ElapsedRealtime.nowMs()
            return try {
                block()
            } finally {
                networkMs += ElapsedRealtime.nowMs() - start
            }
        }

        fun <T> mapping(block: () -> T): T {
            val start = ElapsedRealtime.nowMs()
            return try {
                block()
            } finally {
                mappingMs += ElapsedRealtime.nowMs() - start
            }
        }

        suspend fun <T> secondary(block: suspend () -> T): T {
            val start = ElapsedRealtime.nowMs()
            return try {
                block()
            } finally {
                secondaryMs += ElapsedRealtime.nowMs() - start
            }
        }

        fun finish(extra: String = "") {
            val total = ElapsedRealtime.nowMs() - t0
            AppLog.info(
                TAG,
                "screen=$screen total_ms=$total first_content_ms=${firstContentMs ?: -1} " +
                    "network_ms=$networkMs mapping_ms=$mappingMs secondary_hydration_ms=$secondaryMs" +
                    if (extra.isBlank()) "" else " $extra"
            )
        }
    }
}
