package com.comunidapp.app.domain.observability

import com.comunidapp.app.core.logging.sanitizeLogMessage
import com.comunidapp.app.core.logging.sanitizeThrowableForLog
import com.comunidapp.app.data.repository.MockObservabilityRepositories
import com.comunidapp.app.data.repository.staffAuth
import com.comunidapp.app.data.repository.userAuth
import com.comunidapp.app.domain.observability.authorization.ObservabilityAccessDecision
import com.comunidapp.app.domain.observability.authorization.ObservabilityAuthorization
import com.comunidapp.app.domain.observability.authorization.ObservabilityAuthorizationContext
import com.comunidapp.app.domain.observability.authorization.ObservabilityPermission
import com.comunidapp.app.domain.observability.authorization.ObservabilityRequestedAction
import com.comunidapp.app.domain.observability.catalog.ObservabilityEventCatalog
import com.comunidapp.app.domain.observability.correlation.CorrelationId
import com.comunidapp.app.domain.observability.correlation.CorrelationPropagationPolicy
import com.comunidapp.app.domain.observability.correlation.DefaultCorrelationContextProvider
import com.comunidapp.app.domain.observability.correlation.SequentialCorrelationIdGenerator
import com.comunidapp.app.domain.observability.retention.RetentionDecision
import com.comunidapp.app.domain.observability.retention.RetentionPolicies
import com.comunidapp.app.domain.observability.retention.RetentionPolicyKey
import com.comunidapp.app.domain.observability.retention.SamplingEvaluator
import com.comunidapp.app.domain.observability.retention.SamplingPolicy
import com.comunidapp.app.domain.observability.retention.SamplingPolicyKind
import com.comunidapp.app.domain.observability.sanitization.SensitiveDataSanitizer
import com.comunidapp.app.domain.observability.sanitization.ThrowableSanitizer
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.domain.observability.AlertIncidentState
import com.comunidapp.app.domain.observability.AlertRule
import com.comunidapp.app.domain.observability.HealthStatus
import com.comunidapp.app.domain.observability.ObservabilityExport
import com.comunidapp.app.domain.observability.ObservabilityExportState
import com.comunidapp.app.domain.observability.sanitization.SanitizedThrowable
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class M07Stage2ObservabilityFoundationTest {

    private lateinit var mocks: MockObservabilityRepositories
    private val fixed = Instant.parse("2026-07-17T12:00:00Z")

    @Before
    fun setUp() {
        mocks = MockObservabilityRepositories.create(
            clock = { fixed },
            randomPerThousand = { 0 }
        )
    }

    // ── Catalog ─────────────────────────────────────────────────────────────

    @Test
    fun catalog_eventKeysAreUniqueAndFollowConvention() {
        val keys = ObservabilityEventCatalog.all().map { it.eventKey }
        assertEquals(keys.size, keys.toSet().size)
        keys.forEach { key ->
            assertTrue("bad key $key", ObservabilityEventCatalog.isValidKeyConvention(key))
        }
        assertTrue(ObservabilityEventCatalog.size() >= 90)
        assertNotNull(ObservabilityEventCatalog.get("m00.config.loaded"))
        assertNotNull(ObservabilityEventCatalog.get("m06.dead_letter.recorded"))
        assertNotNull(ObservabilityEventCatalog.get("m07.event.accepted"))
    }

    @Test
    fun catalog_unknownEventRejected() {
        val r = ObservabilityEventCatalog.accept("m99.unknown.thing", emptyMap())
        assertTrue(r is ObservabilityEventCatalog.EventAcceptance.Rejected)
        val local = ObservabilityEventCatalog.accept(
            "m99.unknown.thing",
            emptyMap(),
            allowUnknownAsLocalOnly = true
        )
        assertTrue(local is ObservabilityEventCatalog.EventAcceptance.UnknownLocalOnly)
    }

    @Test
    fun catalog_requiredAndExtraMetadata() {
        val missing = ObservabilityEventCatalog.accept(
            "m02.permission.denied",
            mapOf("result" to "DENIED")
        )
        assertTrue(missing is ObservabilityEventCatalog.EventAcceptance.Rejected)

        val extra = ObservabilityEventCatalog.accept(
            "m07.health.checked",
            mapOf("result" to "SUCCESS", "password" to "x")
        )
        assertTrue(extra is ObservabilityEventCatalog.EventAcceptance.Rejected)

        val ok = ObservabilityEventCatalog.accept(
            "m07.health.checked",
            mapOf("result" to "SUCCESS")
        )
        assertTrue(ok is ObservabilityEventCatalog.EventAcceptance.Accepted)
    }

    // ── Sanitization ────────────────────────────────────────────────────────

    @Test
    fun sanitizer_redactsSensitivePatterns() {
        val samples = listOf(
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.aaa.bbb",
            "Bearer abcdef123456",
            "token=supersecret",
            "service_role=secretkey",
            "https://x.com/file?token=abc signed_url=https://x",
            "user maria@email.com",
            "call +54 11 1234-5678",
            "punto -34.603722, -58.381592",
            "SELECT * FROM users WHERE id=1",
            "at com.app.Foo.bar(Foo.kt:12)",
            "C:\\Users\\secret\\file.txt",
            "AAAA" + "B".repeat(80) + "==",
            "chat_body=hola mundo privado",
            "INTERNAL note about case"
        )
        samples.forEach { raw ->
            val clean = SensitiveDataSanitizer.sanitize(raw)
            assertFalse("leaked in: $raw -> $clean", clean.contains("supersecret"))
            assertFalse(clean.contains("eyJhbGci"))
            assertFalse(clean.contains("maria@email.com"))
            assertFalse(clean.contains("-34.603722"))
            assertFalse(clean.contains("SELECT * FROM"))
        }
        val log = sanitizeLogMessage("user maria@email.com password=secreto123")
        assertTrue(log.contains("REDACTED"))
    }

    @Test
    fun throwable_deepAndCyclic_fingerprintStable_noRawStack() {
        var deep: Throwable = RuntimeException("level0 token=abc")
        for (i in 1..8) {
            deep = IllegalStateException("level$i email=a@b.com", deep)
        }
        val a = ThrowableSanitizer.sanitize(deep)
        val b = ThrowableSanitizer.sanitize(deep)
        assertEquals(a.fingerprint, b.fingerprint)
        assertTrue(a.causeDepth <= ThrowableSanitizer.MAX_DEPTH)
        assertFalse(a.safeMessage.contains("a@b.com"))

        val child = IllegalArgumentException("child")
        val cyclic = RuntimeException("cycle", child)
        val s = ThrowableSanitizer.sanitize(cyclic)
        assertTrue(s.causeDepth >= 1)
        assertTrue(s.fingerprint.startsWith("fp_"))

        val forLog = sanitizeThrowableForLog(RuntimeException("Bearer xyz"))
        assertFalse(forLog.safeMessage.contains("xyz"))
    }

    // ── Correlation ─────────────────────────────────────────────────────────

    @Test
    fun correlation_rootChild_invalid_logout_noPii() {
        val provider = DefaultCorrelationContextProvider(SequentialCorrelationIdGenerator())
        val root = provider.startRoot(sessionId = "sess01")
        val child = provider.startChild(CorrelationPropagationPolicy.CHILD_WITH_NEW_ID)
        assertEquals(root.rootCorrelationId, child.rootCorrelationId)
        assertNotEquals(root.correlationId, child.correlationId)
        assertNull(CorrelationId.parseOrNull(""))
        assertNull(CorrelationId.parseOrNull("user-12345"))
        assertNull(CorrelationId.parseOrNull("a@b.com"))
        assertNull(CorrelationId.parseOrNull("ab")) // too short
        provider.onLogout()
        assertNull(provider.current())
        provider.startRoot()
        provider.onAccountChanged()
        assertNull(provider.current())
    }

    // ── Retention / sampling ────────────────────────────────────────────────

    @Test
    fun retention_and_sampling_rules() {
        assertEquals(
            RetentionDecision.LOCAL_ONLY,
            RetentionPolicies.decide(RetentionPolicyKey.NO_REMOTE, severityIsDebug = true, sensitivityIsSecurity = false)
        )
        assertEquals(
            RetentionDecision.ALLOW_REMOTE,
            RetentionPolicies.decide(RetentionPolicyKey.SECURITY_24_MONTHS, false, true)
        )
        assertEquals(
            RetentionDecision.REJECTED_UNDEFINED,
            RetentionPolicies.decide(null, false, false)
        )
        val windows = mutableSetOf<Long>()
        val always = SamplingEvaluator.evaluate(
            SamplingPolicy(SamplingPolicyKind.ALWAYS),
            isErrorOrDenied = false,
            isCriticalAuditOrSecurity = true,
            randomPerThousand = { 999 },
            nowMs = { 0L },
            windowSeen = windows
        )
        assertTrue(always.accepted)
        val neverButDenied = SamplingEvaluator.evaluate(
            SamplingPolicy(SamplingPolicyKind.NEVER),
            isErrorOrDenied = true,
            isCriticalAuditOrSecurity = false,
            randomPerThousand = { 999 },
            nowMs = { 0L },
            windowSeen = windows
        )
        assertTrue(neverButDenied.accepted)
    }

    // ── Authorization ───────────────────────────────────────────────────────

    @Test
    fun authorization_denyByDefault_accountTypeNoAuthority_export() {
        val base = ObservabilityAuthorizationContext(
            actorId = "u1",
            permissions = emptySet(),
            organizationIds = setOf("org-a"),
            isPlatformActor = false,
            requestedSensitivity = ObservabilitySensitivity.RESTRICTED,
            requestedAction = ObservabilityRequestedAction.VIEW,
            targetOrganizationId = "org-b"
        )
        assertEquals(ObservabilityAccessDecision.DENIED_PERMISSION, ObservabilityAuthorization.authorize(base))

        val wrongOrg = base.copy(
            permissions = setOf(ObservabilityPermission.OBSERVABILITY_VIEW),
            requestedAction = ObservabilityRequestedAction.VIEW
        )
        assertEquals(
            ObservabilityAccessDecision.DENIED_ORGANIZATION,
            ObservabilityAuthorization.authorize(wrongOrg)
        )

        val withAccountType = ObservabilityAuthorization.accountTypeGrantsAuthority(
            accountType = "SHELTER",
            modules = setOf("ADMIN", "SOCIAL"),
            base = base
        )
        assertEquals(ObservabilityAccessDecision.DENIED_PERMISSION, withAccountType)

        val exportDenied = ObservabilityAuthorization.authorize(
            userAuth().copy(
                requestedAction = ObservabilityRequestedAction.EXPORT,
                requestedSensitivity = ObservabilitySensitivity.RESTRICTED
            )
        )
        assertEquals(ObservabilityAccessDecision.DENIED_EXPORT, exportDenied)

        val unknown = ObservabilityAuthorization.authorize(
            userAuth().copy(requestedAction = ObservabilityRequestedAction.UNKNOWN)
        )
        assertEquals(ObservabilityAccessDecision.DENIED_UNKNOWN, unknown)
    }

    // ── Repositories / mocks ────────────────────────────────────────────────

    @Test
    fun mocks_auditDedup_filters_pagination_health_alerts_export() = runBlocking {
        val auth = staffAuth()
        val event = AuditEvent(
            id = "audit-1",
            eventKey = "m07.health.checked",
            module = ObservabilityModule.M07,
            severity = ObservabilitySeverity.INFO,
            sensitivity = ObservabilitySensitivity.INTERNAL,
            actorType = ObservabilityActorType.SYSTEM,
            result = ObservabilityResult.SUCCESS,
            metadata = mapOf("result" to "SUCCESS"),
            occurredAt = fixed,
            retentionPolicyKey = RetentionPolicyKey.TECHNICAL_30_DAYS
        )
        val first = mocks.audit.append(event, auth) as AppResult.Success
        val second = mocks.audit.append(event, auth) as AppResult.Success
        assertEquals(first.data.id, second.data.id)

        val listed = mocks.audit.list(auth, module = ObservabilityModule.M07, limit = 10) as AppResult.Success
        assertTrue(listed.data.isNotEmpty())

        val health = mocks.health.record(
            HealthCheck(
                id = "",
                component = "local-mock",
                status = HealthStatus.UP,
                checkedAt = fixed
            )
        ) as AppResult.Success
        assertEquals(HealthStatus.UP, health.data.status)
        assertEquals(HealthStatus.UP, (mocks.health.latest("local-mock") as AppResult.Success).data?.status)

        mocks.alerts.upsertRule(
            AlertRule(
                id = "rule-1",
                name = "health",
                eventKeyPattern = "m07.health.checked",
                severityThreshold = ObservabilitySeverity.INFO
            )
        )
        val incident = mocks.alerts.evaluate("m07.health.checked", ObservabilitySeverity.ERROR, fixed) as AppResult.Success
        assertNotNull(incident.data)
        assertEquals(
            1,
            (mocks.alerts.listIncidents(AlertIncidentState.OPEN) as AppResult.Success).data.size
        )

        val deniedExport = mocks.exports.request(
            ObservabilityExport(
                id = "",
                requestedBy = "u1",
                sensitivity = ObservabilitySensitivity.RESTRICTED,
                state = ObservabilityExportState.REQUESTED,
                requestedAt = fixed
            ),
            userAuth()
        )
        assertTrue(deniedExport is AppResult.Failure)

        val okExport = mocks.exports.request(
            ObservabilityExport(
                id = "",
                requestedBy = "staff-1",
                sensitivity = ObservabilitySensitivity.RESTRICTED,
                state = ObservabilityExportState.REQUESTED,
                requestedAt = fixed
            ),
            staffAuth()
        ) as AppResult.Success
        assertEquals(ObservabilityExportState.AUTHORIZED, okExport.data.state)
        assertTrue(okExport.data.filePending)
        assertTrue(okExport.data.simulatedArtifactLabel == null)
        assertTrue(okExport.data.note?.contains("PENDIENTE") == true)
    }

    @Test
    fun mocks_errorFingerprintAndCorrelation() = runBlocking {
        val sanitized = SanitizedThrowable(
            errorClass = "IllegalStateException",
            safeMessage = "boom",
            fingerprint = "fp_test001",
            causeDepth = 1,
            isRetryable = false
        )
        mocks.errors.capture(
            ApplicationError(
                id = "",
                module = ObservabilityModule.M07,
                errorCode = ObservabilityErrorCode.OBS_UNKNOWN,
                sanitized = sanitized,
                occurredAt = fixed
            ),
            staffAuth()
        )
        val found = mocks.errors.findByFingerprint("fp_test001") as AppResult.Success
        assertEquals(1, found.data.size)

        val root = mocks.correlation.startRoot("sess01")
        assertTrue(root.correlationId.value.startsWith("corr"))
        mocks.correlation.onLogout()
        assertNull(mocks.correlation.current())
    }

    @Test
    fun dataProvider_m07ContractsAssignable_andMocksNonNull() {
        val bundle = MockObservabilityRepositories.create(clock = { fixed })
        assertNotNull(bundle.audit)
        assertNotNull(bundle.security)
        assertNotNull(bundle.errors)
        assertNotNull(bundle.metrics)
        assertNotNull(bundle.health)
        assertNotNull(bundle.analytics)
        assertNotNull(bundle.alerts)
        assertNotNull(bundle.exports)
        assertNotNull(bundle.catalog)
        assertNotNull(bundle.correlation)
        assertTrue(bundle.catalog.all().isNotEmpty())
        assertTrue(
            com.comunidapp.app.data.repository.AuditEventRepository::class.java
                .isAssignableFrom(bundle.audit::class.java)
        )
        assertTrue(
            com.comunidapp.app.data.repository.EventCatalogRepository::class.java
                .isAssignableFrom(bundle.catalog::class.java)
        )
    }

    @Test
    fun appLogger_compatibleSanitization() {
        val clean = sanitizeLogMessage("Bearer tok123 eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.a.b")
        assertFalse(clean.contains("tok123"))
        assertTrue(clean.contains("REDACTED"))
    }
}
