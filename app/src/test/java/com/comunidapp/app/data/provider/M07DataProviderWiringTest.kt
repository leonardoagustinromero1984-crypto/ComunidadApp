package com.comunidapp.app.data.provider

import com.comunidapp.app.data.repository.AlertRepository
import com.comunidapp.app.data.repository.AnalyticsEventRepository
import com.comunidapp.app.data.repository.ApplicationErrorRepository
import com.comunidapp.app.data.repository.AuditEventRepository
import com.comunidapp.app.data.repository.ClientDeniedAuditEventRepository
import com.comunidapp.app.data.repository.CorrelationContextRepository
import com.comunidapp.app.data.repository.EventCatalogRepository
import com.comunidapp.app.data.repository.HealthCheckRepository
import com.comunidapp.app.data.repository.MockObservabilityRepositories
import com.comunidapp.app.data.repository.ObservabilityExportRepository
import com.comunidapp.app.data.repository.PerformanceMetricRepository
import com.comunidapp.app.data.repository.SecurityEventRepository
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M07DataProviderWiringTest {

    @Test
    fun mockObservabilityBundle_implementsAllContracts() {
        val mocks = MockObservabilityRepositories.create()
        assertTrue(mocks.audit is AuditEventRepository)
        assertTrue(mocks.security is SecurityEventRepository)
        assertTrue(mocks.errors is ApplicationErrorRepository)
        assertTrue(mocks.metrics is PerformanceMetricRepository)
        assertTrue(mocks.health is HealthCheckRepository)
        assertTrue(mocks.analytics is AnalyticsEventRepository)
        assertTrue(mocks.alerts is AlertRepository)
        assertTrue(mocks.exports is ObservabilityExportRepository)
        assertTrue(mocks.catalog is EventCatalogRepository)
        assertTrue(mocks.correlation is CorrelationContextRepository)
        assertNotNull(mocks.store)
        assertTrue(mocks.catalog.all().size >= 90)
    }

    @Test
    fun clientDeniedAudit_implementsContract() {
        assertTrue(
            AuditEventRepository::class.java.isAssignableFrom(ClientDeniedAuditEventRepository::class.java)
        )
    }
}
