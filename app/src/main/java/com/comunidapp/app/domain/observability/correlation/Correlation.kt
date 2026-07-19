package com.comunidapp.app.domain.observability.correlation

/**
 * Correlation ID tipado. Sin userId, email, timestamp legible ni PII.
 */
@JvmInline
value class CorrelationId(val value: String) {
    init {
        require(isValid(value)) { "OBS_CORRELATION_INVALID" }
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 64
        const val MIN_LENGTH = 8
        private val pattern = Regex("^[A-Za-z0-9_-]{$MIN_LENGTH,$MAX_LENGTH}$")

        fun isValid(raw: String?): Boolean {
            val v = raw?.trim().orEmpty()
            if (v.isBlank()) return false
            if (!pattern.matches(v)) return false
            if (looksLikePii(v)) return false
            return true
        }

        fun parseOrNull(raw: String?): CorrelationId? =
            if (isValid(raw)) CorrelationId(raw!!.trim()) else null

        /** Reject embedded email-like, phone-like, or obvious user-id prefixes. */
        private fun looksLikePii(v: String): Boolean {
            if (v.contains('@')) return true
            if (v.contains('.')) return true
            if (v.startsWith("user-", ignoreCase = true)) return true
            if (v.startsWith("email-", ignoreCase = true)) return true
            if (v.any { it.isWhitespace() }) return true
            return false
        }
    }
}

data class CorrelationContext(
    val correlationId: CorrelationId,
    val rootCorrelationId: CorrelationId,
    val requestId: String? = null,
    val sessionId: String? = null,
    val jobId: String? = null,
    val eventId: String? = null,
    val parentCorrelationId: CorrelationId? = null
) {
    init {
        requestId?.let { requireOpaqueId(it, "requestId") }
        sessionId?.let { requireOpaqueId(it, "sessionId") }
        jobId?.let { requireOpaqueId(it, "jobId") }
        eventId?.let { requireOpaqueId(it, "eventId") }
    }

    fun child(childId: CorrelationId): CorrelationContext = copy(
        correlationId = childId,
        parentCorrelationId = correlationId,
        rootCorrelationId = rootCorrelationId
    )

    companion object {
        private val opaque = Regex("^[A-Za-z0-9_-]{1,128}$")

        private fun requireOpaqueId(value: String, field: String) {
            require(opaque.matches(value.trim()) && !value.contains('@') && !value.contains('.')) {
                "OBS_CORRELATION_INVALID:$field"
            }
        }
    }
}

fun interface CorrelationIdGenerator {
    fun next(): CorrelationId
}

/** UUID-like without dashes for allowlisted charset (A-Za-z0-9_-). */
class UuidCorrelationIdGenerator(
    private val uuidSupplier: () -> String = { java.util.UUID.randomUUID().toString() }
) : CorrelationIdGenerator {
    override fun next(): CorrelationId {
        val raw = uuidSupplier().replace("-", "").take(CorrelationId.MAX_LENGTH)
        return CorrelationId(raw.ifBlank { "corr00000001" })
    }
}

/** Deterministic generator for tests. */
class SequentialCorrelationIdGenerator(
    private val prefix: String = "corr",
    start: Int = 1
) : CorrelationIdGenerator {
    private var counter = start
    override fun next(): CorrelationId {
        val n = counter++
        return CorrelationId("%s%08d".format(prefix.take(4).padEnd(4, 'x'), n))
    }
}

enum class CorrelationPropagationPolicy {
    /** Keep root; mint new child id. */
    CHILD_WITH_NEW_ID,
    /** Reuse current id (same operation). */
    REUSE_CURRENT,
    /** Reject blank/invalid; do not silently accept PII. */
    REJECT_INVALID
}

interface CorrelationContextProvider {
    fun current(): CorrelationContext?
    fun startRoot(
        requestId: String? = null,
        sessionId: String? = null,
        jobId: String? = null,
        eventId: String? = null
    ): CorrelationContext

    fun startChild(policy: CorrelationPropagationPolicy = CorrelationPropagationPolicy.CHILD_WITH_NEW_ID): CorrelationContext
    fun clearSession()
    fun onLogout()
    fun onAccountChanged()
    fun adoptOrReject(raw: String?): CorrelationContext?
}

class DefaultCorrelationContextProvider(
    private val generator: CorrelationIdGenerator = UuidCorrelationIdGenerator(),
    private val onInvalid: (String?) -> CorrelationContext? = { null }
) : CorrelationContextProvider {

    @Volatile
    private var current: CorrelationContext? = null

    override fun current(): CorrelationContext? = current

    override fun startRoot(
        requestId: String?,
        sessionId: String?,
        jobId: String?,
        eventId: String?
    ): CorrelationContext {
        val id = generator.next()
        val ctx = CorrelationContext(
            correlationId = id,
            rootCorrelationId = id,
            requestId = requestId,
            sessionId = sessionId,
            jobId = jobId,
            eventId = eventId
        )
        current = ctx
        return ctx
    }

    override fun startChild(policy: CorrelationPropagationPolicy): CorrelationContext {
        val parent = current ?: return startRoot()
        return when (policy) {
            CorrelationPropagationPolicy.REUSE_CURRENT -> parent
            CorrelationPropagationPolicy.CHILD_WITH_NEW_ID,
            CorrelationPropagationPolicy.REJECT_INVALID -> {
                val child = parent.child(generator.next())
                current = child
                child
            }
        }
    }

    override fun clearSession() {
        current = null
    }

    override fun onLogout() = clearSession()

    override fun onAccountChanged() = clearSession()

    override fun adoptOrReject(raw: String?): CorrelationContext? {
        val parsed = CorrelationId.parseOrNull(raw)
        if (parsed == null) return onInvalid(raw)
        val root = current?.rootCorrelationId ?: parsed
        val ctx = CorrelationContext(
            correlationId = parsed,
            rootCorrelationId = root,
            parentCorrelationId = current?.correlationId
        )
        current = ctx
        return ctx
    }
}
