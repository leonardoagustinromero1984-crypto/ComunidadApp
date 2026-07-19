package com.comunidapp.app.domain.observability.catalog

import com.comunidapp.app.domain.observability.CatalogEventDefinition
import com.comunidapp.app.domain.observability.ObservabilityActorType
import com.comunidapp.app.domain.observability.ObservabilityCategory
import com.comunidapp.app.domain.observability.ObservabilityErrorCode
import com.comunidapp.app.domain.observability.ObservabilityModule
import com.comunidapp.app.domain.observability.ObservabilitySensitivity
import com.comunidapp.app.domain.observability.ObservabilitySeverity
import com.comunidapp.app.domain.observability.defaultSamplingFor
import com.comunidapp.app.domain.observability.retention.RetentionPolicyKey

/**
 * Catálogo central tipado M00–M07. Event keys únicos; sin instrumentación de call sites.
 */
object ObservabilityEventCatalog {

    private val EVENT_KEY_PATTERN = Regex("^m0[0-7]\\.[a-z0-9_]+\\.[a-z0-9_]+$")

    private val BASE_META = MetadataAllowlist.GLOBAL_ALLOWED_KEYS

    private fun e(
        key: String,
        module: ObservabilityModule,
        category: ObservabilityCategory,
        severity: ObservabilitySeverity,
        sensitivity: ObservabilitySensitivity,
        orgScoped: Boolean,
        retention: RetentionPolicyKey,
        remote: Boolean,
        analytics: Boolean = false,
        actors: Set<ObservabilityActorType> = setOf(
            ObservabilityActorType.AUTHENTICATED_USER,
            ObservabilityActorType.SYSTEM
        ),
        resources: Set<String> = emptySet(),
        required: Set<String> = emptySet(),
        allowed: Set<String> = BASE_META
    ): CatalogEventDefinition = CatalogEventDefinition(
        eventKey = key,
        module = module,
        category = category,
        defaultSeverity = severity,
        sensitivity = sensitivity,
        actorTypes = actors,
        resourceTypes = resources,
        organizationScoped = orgScoped,
        allowedMetadataKeys = allowed,
        requiredMetadataKeys = required,
        retentionPolicyKey = retention,
        remotePersistenceAllowed = remote && severity != ObservabilitySeverity.DEBUG,
        analyticsAllowed = analytics,
        samplingPolicy = defaultSamplingFor(severity, category)
    )

    private val definitions: List<CatalogEventDefinition> = listOf(
        // M00
        e("m00.config.loaded", ObservabilityModule.M00, ObservabilityCategory.SYSTEM, ObservabilitySeverity.INFO, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.NO_REMOTE, false, actors = setOf(ObservabilityActorType.SYSTEM)),
        e("m00.config.missing", ObservabilityModule.M00, ObservabilityCategory.ERROR, ObservabilitySeverity.ERROR, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.NO_REMOTE, false, actors = setOf(ObservabilityActorType.SYSTEM)),
        e("m00.feature_flag.evaluated", ObservabilityModule.M00, ObservabilityCategory.SYSTEM, ObservabilitySeverity.DEBUG, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.NO_REMOTE, false, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("feature_flag")),
        e("m00.log.sanitized", ObservabilityModule.M00, ObservabilityCategory.SECURITY, ObservabilitySeverity.DEBUG, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.NO_REMOTE, false, actors = setOf(ObservabilityActorType.SYSTEM)),
        e("m00.log.level_gate", ObservabilityModule.M00, ObservabilityCategory.SYSTEM, ObservabilitySeverity.INFO, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.NO_REMOTE, false, actors = setOf(ObservabilityActorType.SYSTEM)),
        e("m00.build.debug_assemble", ObservabilityModule.M00, ObservabilityCategory.JOB, ObservabilitySeverity.INFO, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.TECHNICAL_30_DAYS, true, actors = setOf(ObservabilityActorType.CI), required = setOf("job_name", "result")),
        e("m00.ci.unit_tests", ObservabilityModule.M00, ObservabilityCategory.JOB, ObservabilitySeverity.INFO, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.TECHNICAL_30_DAYS, true, actors = setOf(ObservabilityActorType.CI), required = setOf("job_name", "result")),
        e("m00.ci.lint", ObservabilityModule.M00, ObservabilityCategory.JOB, ObservabilitySeverity.WARNING, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.TECHNICAL_30_DAYS, true, actors = setOf(ObservabilityActorType.CI), required = setOf("job_name", "result")),
        e("m00.error.app_result_failure", ObservabilityModule.M00, ObservabilityCategory.ERROR, ObservabilitySeverity.ERROR, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("error_code")),
        // M01
        e("m01.auth.login_success", ObservabilityModule.M01, ObservabilityCategory.SECURITY, ObservabilitySeverity.INFO, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.SECURITY_24_MONTHS, true, actors = setOf(ObservabilityActorType.AUTHENTICATED_USER, ObservabilityActorType.ANONYMOUS), required = setOf("result")),
        e("m01.auth.login_failure", ObservabilityModule.M01, ObservabilityCategory.SECURITY, ObservabilitySeverity.WARNING, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.SECURITY_24_MONTHS, true, actors = setOf(ObservabilityActorType.ANONYMOUS, ObservabilityActorType.AUTHENTICATED_USER), required = setOf("result")),
        e("m01.auth.logout", ObservabilityModule.M01, ObservabilityCategory.SECURITY, ObservabilitySeverity.INFO, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.SECURITY_24_MONTHS, true, actors = setOf(ObservabilityActorType.AUTHENTICATED_USER, ObservabilityActorType.SYSTEM), required = setOf("result")),
        e("m01.auth.verify_email", ObservabilityModule.M01, ObservabilityCategory.SECURITY, ObservabilitySeverity.INFO, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.SECURITY_24_MONTHS, true, required = setOf("result")),
        e("m01.auth.password_recovery", ObservabilityModule.M01, ObservabilityCategory.SECURITY, ObservabilitySeverity.WARNING, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.SECURITY_24_MONTHS, true, actors = setOf(ObservabilityActorType.ANONYMOUS, ObservabilityActorType.AUTHENTICATED_USER), required = setOf("result")),
        e("m01.auth.password_changed", ObservabilityModule.M01, ObservabilityCategory.SECURITY, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.SECURITY_24_MONTHS, true, required = setOf("result")),
        e("m01.consent.recorded", ObservabilityModule.M01, ObservabilityCategory.AUDIT, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, required = setOf("result")),
        e("m01.consent.gate_unavailable", ObservabilityModule.M01, ObservabilityCategory.ERROR, ObservabilitySeverity.WARNING, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("error_code")),
        e("m01.account.deletion_requested", ObservabilityModule.M01, ObservabilityCategory.AUDIT, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, required = setOf("result", "correlation_id")),
        e("m01.account.deletion_completed", ObservabilityModule.M01, ObservabilityCategory.AUDIT, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.AUTHENTICATED_USER, ObservabilityActorType.EDGE_FUNCTION, ObservabilityActorType.SYSTEM), required = setOf("result")),
        e("m01.account.deletion_failed", ObservabilityModule.M01, ObservabilityCategory.ERROR, ObservabilitySeverity.ERROR, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.UNTIL_RESOLUTION, true, actors = setOf(ObservabilityActorType.EDGE_FUNCTION, ObservabilityActorType.SYSTEM), required = setOf("error_code", "result")),
        // M02
        e("m02.profile.onboarding_completed", ObservabilityModule.M02, ObservabilityCategory.AUDIT, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, required = setOf("result")),
        e("m02.profile.updated", ObservabilityModule.M02, ObservabilityCategory.AUDIT, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, required = setOf("result")),
        e("m02.privacy.settings_changed", ObservabilityModule.M02, ObservabilityCategory.AUDIT, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, required = setOf("result")),
        e("m02.role.assigned", ObservabilityModule.M02, ObservabilityCategory.AUDIT, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF, ObservabilityActorType.SYSTEM), required = setOf("result", "reason_code")),
        e("m02.role.revoked", ObservabilityModule.M02, ObservabilityCategory.AUDIT, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF, ObservabilityActorType.SYSTEM), required = setOf("result", "reason_code")),
        e("m02.role.expired", ObservabilityModule.M02, ObservabilityCategory.AUDIT, ObservabilitySeverity.INFO, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("result")),
        e("m02.status.changed", ObservabilityModule.M02, ObservabilityCategory.AUDIT, ObservabilitySeverity.WARNING, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF, ObservabilityActorType.SYSTEM), required = setOf("result", "reason_code")),
        e("m02.permission.denied", ObservabilityModule.M02, ObservabilityCategory.AUTHORIZATION, ObservabilitySeverity.WARNING, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.SECURITY_24_MONTHS, true, actors = setOf(ObservabilityActorType.AUTHENTICATED_USER, ObservabilityActorType.PLATFORM_STAFF, ObservabilityActorType.ORGANIZATION_MEMBER), required = setOf("permission_code", "result")),
        e("m02.admin.audit_read", ObservabilityModule.M02, ObservabilityCategory.DATA_ACCESS, ObservabilitySeverity.INFO, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        // M03
        e("m03.org.created", ObservabilityModule.M03, ObservabilityCategory.AUDIT, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, true, RetentionPolicyKey.AUDIT_12_MONTHS, true, required = setOf("organization_id", "result")),
        e("m03.invitation.created", ObservabilityModule.M03, ObservabilityCategory.AUDIT, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, true, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.ORGANIZATION_MEMBER, ObservabilityActorType.AUTHENTICATED_USER), required = setOf("organization_id", "result")),
        e("m03.invitation.accepted", ObservabilityModule.M03, ObservabilityCategory.AUDIT, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, true, RetentionPolicyKey.AUDIT_12_MONTHS, true, required = setOf("organization_id", "result")),
        e("m03.invitation.declined", ObservabilityModule.M03, ObservabilityCategory.AUDIT, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, true, RetentionPolicyKey.AUDIT_12_MONTHS, true, required = setOf("organization_id", "result")),
        e("m03.invitation.expired", ObservabilityModule.M03, ObservabilityCategory.AUDIT, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, true, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("organization_id", "result")),
        e("m03.member.role_changed", ObservabilityModule.M03, ObservabilityCategory.AUDIT, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.CONFIDENTIAL, true, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.ORGANIZATION_MEMBER), required = setOf("organization_id", "result")),
        e("m03.member.removed", ObservabilityModule.M03, ObservabilityCategory.AUDIT, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.CONFIDENTIAL, true, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.ORGANIZATION_MEMBER), required = setOf("organization_id", "result")),
        e("m03.ownership.transferred", ObservabilityModule.M03, ObservabilityCategory.AUDIT, ObservabilitySeverity.WARNING, ObservabilitySensitivity.RESTRICTED, true, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.ORGANIZATION_MEMBER), required = setOf("organization_id", "result")),
        e("m03.branch.changed", ObservabilityModule.M03, ObservabilityCategory.AUDIT, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, true, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.ORGANIZATION_MEMBER), required = setOf("organization_id", "result")),
        // M04
        e("m04.report.created", ObservabilityModule.M04, ObservabilityCategory.MODERATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, required = setOf("result", "resource_type")),
        e("m04.report.triaged", ObservabilityModule.M04, ObservabilityCategory.MODERATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m04.report.marked_duplicate", ObservabilityModule.M04, ObservabilityCategory.MODERATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m04.case.created", ObservabilityModule.M04, ObservabilityCategory.MODERATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m04.case.report_attached", ObservabilityModule.M04, ObservabilityCategory.MODERATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m04.case.assigned", ObservabilityModule.M04, ObservabilityCategory.MODERATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m04.case.status_changed", ObservabilityModule.M04, ObservabilityCategory.MODERATION, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m04.case.internal_note_added", ObservabilityModule.M04, ObservabilityCategory.MODERATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m04.action.applied", ObservabilityModule.M04, ObservabilityCategory.MODERATION, ObservabilitySeverity.WARNING, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m04.moderation.action_applied", ObservabilityModule.M04, ObservabilityCategory.MODERATION, ObservabilitySeverity.WARNING, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.DATABASE_TRIGGER, ObservabilityActorType.SYSTEM), required = setOf("result")),
        e("m04.appeal.submitted", ObservabilityModule.M04, ObservabilityCategory.MODERATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, required = setOf("result")),
        e("m04.appeal.assigned", ObservabilityModule.M04, ObservabilityCategory.MODERATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m04.appeal.reviewed", ObservabilityModule.M04, ObservabilityCategory.MODERATION, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m04.appeal.resolved", ObservabilityModule.M04, ObservabilityCategory.MODERATION, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.DATABASE_TRIGGER, ObservabilityActorType.SYSTEM), required = setOf("result")),
        e("m04.verification.assigned", ObservabilityModule.M04, ObservabilityCategory.MODERATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.RESTRICTED, true, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("organization_id", "result")),
        e("m04.verification.decided", ObservabilityModule.M04, ObservabilityCategory.MODERATION, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.RESTRICTED, true, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("organization_id", "result")),
        e("m04.verification.notify", ObservabilityModule.M04, ObservabilityCategory.MODERATION, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.SECURITY_SENSITIVE, true, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.DATABASE_TRIGGER, ObservabilityActorType.SYSTEM), required = setOf("organization_id", "result")),
        e("m04.support.ticket_created", ObservabilityModule.M04, ObservabilityCategory.SUPPORT, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, required = setOf("result")),
        e("m04.support.assigned", ObservabilityModule.M04, ObservabilityCategory.SUPPORT, ObservabilitySeverity.INFO, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m04.support.status_changed", ObservabilityModule.M04, ObservabilityCategory.SUPPORT, ObservabilitySeverity.INFO, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m04.support.internal_message", ObservabilityModule.M04, ObservabilityCategory.SUPPORT, ObservabilitySeverity.INFO, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m04.support.visible_reply", ObservabilityModule.M04, ObservabilityCategory.SUPPORT, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF, ObservabilityActorType.DATABASE_TRIGGER), required = setOf("result")),
        e("m04.support.internal_update", ObservabilityModule.M04, ObservabilityCategory.SUPPORT, ObservabilitySeverity.INFO, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF, ObservabilityActorType.DATABASE_TRIGGER), required = setOf("result")),
        e("m04.sensitive.access_projection", ObservabilityModule.M04, ObservabilityCategory.DATA_ACCESS, ObservabilitySeverity.INFO, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.SECURITY_24_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result", "permission_code")),
        e("m04.audit.helper_write", ObservabilityModule.M04, ObservabilityCategory.AUDIT, ObservabilitySeverity.INFO, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.SYSTEM, ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        // M05
        e("m05.upload.session_started", ObservabilityModule.M05, ObservabilityCategory.FILE, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, required = setOf("result", "file_type")),
        e("m05.upload.completed", ObservabilityModule.M05, ObservabilityCategory.FILE, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.DATABASE_TRIGGER, ObservabilityActorType.SYSTEM, ObservabilityActorType.AUTHENTICATED_USER), required = setOf("result")),
        e("m05.upload.failed", ObservabilityModule.M05, ObservabilityCategory.ERROR, ObservabilitySeverity.WARNING, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.DATABASE_TRIGGER, ObservabilityActorType.SYSTEM), required = setOf("result", "error_code")),
        e("m05.upload.cancelled", ObservabilityModule.M05, ObservabilityCategory.FILE, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.TECHNICAL_30_DAYS, true, required = setOf("result")),
        e("m05.verification_document.ready", ObservabilityModule.M05, ObservabilityCategory.FILE, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.SECURITY_SENSITIVE, true, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.DATABASE_TRIGGER, ObservabilityActorType.SYSTEM), required = setOf("organization_id", "result")),
        e("m05.signed_url.issued", ObservabilityModule.M05, ObservabilityCategory.DATA_ACCESS, ObservabilitySeverity.INFO, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.SECURITY_24_MONTHS, true, required = setOf("result", "resource_id")),
        e("m05.download.performed", ObservabilityModule.M05, ObservabilityCategory.DATA_ACCESS, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, required = setOf("result", "resource_id")),
        e("m05.file.deleted", ObservabilityModule.M05, ObservabilityCategory.FILE, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.AUTHENTICATED_USER, ObservabilityActorType.EDGE_FUNCTION, ObservabilityActorType.SYSTEM), required = setOf("result")),
        e("m05.retention.expiry", ObservabilityModule.M05, ObservabilityCategory.JOB, ObservabilitySeverity.INFO, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("result")),
        e("m05.storage.error", ObservabilityModule.M05, ObservabilityCategory.ERROR, ObservabilitySeverity.ERROR, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.EDGE_FUNCTION, ObservabilityActorType.SYSTEM), required = setOf("error_code", "result")),
        // M06
        e("m06.event.enqueued", ObservabilityModule.M06, ObservabilityCategory.NOTIFICATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.SYSTEM, ObservabilityActorType.EDGE_FUNCTION), required = setOf("event_key", "result")),
        e("m06.recipient.materialized", ObservabilityModule.M06, ObservabilityCategory.NOTIFICATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("result")),
        e("m06.inbox.read", ObservabilityModule.M06, ObservabilityCategory.NOTIFICATION, ObservabilitySeverity.DEBUG, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.NO_REMOTE, false, required = setOf("result")),
        e("m06.inbox.archived", ObservabilityModule.M06, ObservabilityCategory.NOTIFICATION, ObservabilitySeverity.DEBUG, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.NO_REMOTE, false, required = setOf("result")),
        e("m06.inbox.deleted_logical", ObservabilityModule.M06, ObservabilityCategory.NOTIFICATION, ObservabilitySeverity.DEBUG, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.NO_REMOTE, false, required = setOf("result")),
        e("m06.delivery.in_app", ObservabilityModule.M06, ObservabilityCategory.NOTIFICATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("channel", "result")),
        e("m06.delivery.push_planned", ObservabilityModule.M06, ObservabilityCategory.NOTIFICATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("channel", "result")),
        e("m06.delivery.push_claimed", ObservabilityModule.M06, ObservabilityCategory.NOTIFICATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.EDGE_FUNCTION, ObservabilityActorType.SYSTEM), required = setOf("result")),
        e("m06.delivery.push_result", ObservabilityModule.M06, ObservabilityCategory.NOTIFICATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.EDGE_FUNCTION, ObservabilityActorType.SYSTEM), required = setOf("result")),
        e("m06.delivery.token_invalidated", ObservabilityModule.M06, ObservabilityCategory.SECURITY, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.SECURITY_24_MONTHS, true, actors = setOf(ObservabilityActorType.EDGE_FUNCTION, ObservabilityActorType.SYSTEM), required = setOf("result", "installation_fingerprint")),
        e("m06.outbox.enqueued", ObservabilityModule.M06, ObservabilityCategory.JOB, ObservabilitySeverity.INFO, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("result")),
        e("m06.outbox.claimed", ObservabilityModule.M06, ObservabilityCategory.JOB, ObservabilitySeverity.INFO, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("result")),
        e("m06.outbox.processed", ObservabilityModule.M06, ObservabilityCategory.JOB, ObservabilitySeverity.INFO, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("result")),
        e("m06.outbox.failed", ObservabilityModule.M06, ObservabilityCategory.JOB, ObservabilitySeverity.WARNING, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.UNTIL_RESOLUTION, true, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("result", "error_code", "attempt_count")),
        e("m06.dead_letter.recorded", ObservabilityModule.M06, ObservabilityCategory.ERROR, ObservabilitySeverity.ERROR, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.UNTIL_RESOLUTION, true, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("result", "error_code")),
        e("m06.emit.failed_swallowed", ObservabilityModule.M06, ObservabilityCategory.ERROR, ObservabilitySeverity.WARNING, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.UNTIL_RESOLUTION, true, actors = setOf(ObservabilityActorType.SYSTEM, ObservabilityActorType.DATABASE_TRIGGER), required = setOf("result", "error_code")),
        e("m06.installation.registered", ObservabilityModule.M06, ObservabilityCategory.SECURITY, ObservabilitySeverity.INFO, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.SECURITY_24_MONTHS, true, required = setOf("result", "installation_fingerprint", "platform")),
        e("m06.installation.token_rotated", ObservabilityModule.M06, ObservabilityCategory.SECURITY, ObservabilitySeverity.INFO, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.SECURITY_24_MONTHS, true, required = setOf("result", "installation_fingerprint")),
        e("m06.installation.revoked", ObservabilityModule.M06, ObservabilityCategory.SECURITY, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.SECURITY_24_MONTHS, true, required = setOf("result")),
        e("m06.preference.updated", ObservabilityModule.M06, ObservabilityCategory.AUDIT, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, required = setOf("result")),
        e("m06.deep_link.resolved", ObservabilityModule.M06, ObservabilityCategory.NOTIFICATION, ObservabilitySeverity.DEBUG, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.NO_REMOTE, false, required = setOf("result")),
        e("m06.deep_link.permission_denied", ObservabilityModule.M06, ObservabilityCategory.AUTHORIZATION, ObservabilitySeverity.WARNING, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.SECURITY_24_MONTHS, true, required = setOf("result", "permission_code")),
        e("m06.access_audit.decision", ObservabilityModule.M06, ObservabilityCategory.AUDIT, ObservabilitySeverity.INFO, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.AUTHENTICATED_USER, ObservabilityActorType.PLATFORM_STAFF, ObservabilityActorType.SYSTEM), required = setOf("result", "reason_code")),
        e("m06.legacy.create_notification", ObservabilityModule.M06, ObservabilityCategory.NOTIFICATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.NO_REMOTE, false, required = setOf("result")),
        e("m06.edge.push_invoked", ObservabilityModule.M06, ObservabilityCategory.INTEGRATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.EDGE_FUNCTION, ObservabilityActorType.SYSTEM), required = setOf("result")),
        // M07
        e("m07.event.accepted", ObservabilityModule.M07, ObservabilityCategory.SYSTEM, ObservabilitySeverity.INFO, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.TECHNICAL_30_DAYS, true, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("event_key", "result")),
        e("m07.event.rejected", ObservabilityModule.M07, ObservabilityCategory.ERROR, ObservabilitySeverity.WARNING, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("event_key", "error_code", "result")),
        e("m07.event.sanitized", ObservabilityModule.M07, ObservabilityCategory.SECURITY, ObservabilitySeverity.DEBUG, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.NO_REMOTE, false, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("result")),
        e("m07.correlation.created", ObservabilityModule.M07, ObservabilityCategory.SYSTEM, ObservabilitySeverity.DEBUG, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.NO_REMOTE, false, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("correlation_id")),
        e("m07.correlation.propagated", ObservabilityModule.M07, ObservabilityCategory.SYSTEM, ObservabilitySeverity.DEBUG, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.NO_REMOTE, false, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("correlation_id")),
        e("m07.error.captured", ObservabilityModule.M07, ObservabilityCategory.ERROR, ObservabilitySeverity.ERROR, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("error_code", "result")),
        e("m07.health.checked", ObservabilityModule.M07, ObservabilityCategory.HEALTH, ObservabilitySeverity.INFO, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.TECHNICAL_30_DAYS, true, actors = setOf(ObservabilityActorType.SYSTEM, ObservabilityActorType.CI), required = setOf("result")),
        e("m07.export.requested", ObservabilityModule.M07, ObservabilityCategory.EXPORT, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m07.export.denied", ObservabilityModule.M07, ObservabilityCategory.AUTHORIZATION, ObservabilitySeverity.WARNING, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.SECURITY_24_MONTHS, true, actors = setOf(ObservabilityActorType.AUTHENTICATED_USER, ObservabilityActorType.PLATFORM_STAFF), required = setOf("result", "reason_code")),
        e("m07.alert.rule_evaluated", ObservabilityModule.M07, ObservabilityCategory.SYSTEM, ObservabilitySeverity.INFO, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.TECHNICAL_30_DAYS, true, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("result")),
        e("m07.audit.read", ObservabilityModule.M07, ObservabilityCategory.DATA_ACCESS, ObservabilitySeverity.INFO, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m07.security.read", ObservabilityModule.M07, ObservabilityCategory.DATA_ACCESS, ObservabilitySeverity.INFO, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.SECURITY_24_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m07.error.read", ObservabilityModule.M07, ObservabilityCategory.DATA_ACCESS, ObservabilitySeverity.INFO, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m07.retention.previewed", ObservabilityModule.M07, ObservabilityCategory.AUDIT, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m07.retention.executed", ObservabilityModule.M07, ObservabilityCategory.AUDIT, ObservabilitySeverity.WARNING, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m07.retention.legal_hold_changed", ObservabilityModule.M07, ObservabilityCategory.AUDIT, ObservabilitySeverity.WARNING, ObservabilitySensitivity.SECURITY_SENSITIVE, false, RetentionPolicyKey.SECURITY_24_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m07.health.manual_check", ObservabilityModule.M07, ObservabilityCategory.HEALTH, ObservabilitySeverity.INFO, ObservabilitySensitivity.INTERNAL, false, RetentionPolicyKey.TECHNICAL_30_DAYS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m07.incident.acknowledged", ObservabilityModule.M07, ObservabilityCategory.AUDIT, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m07.incident.resolved", ObservabilityModule.M07, ObservabilityCategory.AUDIT, ObservabilitySeverity.NOTICE, ObservabilitySensitivity.RESTRICTED, false, RetentionPolicyKey.AUDIT_12_MONTHS, true, actors = setOf(ObservabilityActorType.PLATFORM_STAFF), required = setOf("result")),
        e("m07.incident.staff_notification", ObservabilityModule.M07, ObservabilityCategory.NOTIFICATION, ObservabilitySeverity.INFO, ObservabilitySensitivity.CONFIDENTIAL, false, RetentionPolicyKey.TECHNICAL_90_DAYS, true, actors = setOf(ObservabilityActorType.SYSTEM), required = setOf("result"))
    )

    private val byKey: Map<String, CatalogEventDefinition> = definitions.associateBy { it.eventKey }

    init {
        require(definitions.size == byKey.size) { "Duplicate event keys in ObservabilityEventCatalog" }
        definitions.forEach { def ->
            require(EVENT_KEY_PATTERN.matches(def.eventKey)) {
                "Invalid event key convention: ${def.eventKey}"
            }
            require(def.module.name.lowercase() == def.eventKey.substring(0, 3)) {
                "Module mismatch for ${def.eventKey}"
            }
            require(def.requiredMetadataKeys.all { it in def.allowedMetadataKeys }) {
                "Required metadata not allowlisted for ${def.eventKey}"
            }
        }
    }

    fun all(): List<CatalogEventDefinition> = definitions

    fun size(): Int = definitions.size

    fun get(eventKey: String): CatalogEventDefinition? = byKey[eventKey.trim()]

    fun contains(eventKey: String): Boolean = byKey.containsKey(eventKey.trim())

    fun isValidKeyConvention(eventKey: String): Boolean = EVENT_KEY_PATTERN.matches(eventKey.trim())

    sealed class EventAcceptance {
        data class Accepted(val definition: CatalogEventDefinition, val metadata: Map<String, String>) : EventAcceptance()
        data class Rejected(val code: ObservabilityErrorCode, val detail: String) : EventAcceptance()
        /** Unknown key — explicit local-only / non-persistent fallback. */
        data class UnknownLocalOnly(val eventKey: String) : EventAcceptance()
    }

    fun accept(
        eventKey: String,
        metadata: Map<String, String>,
        allowUnknownAsLocalOnly: Boolean = false
    ): EventAcceptance {
        val key = eventKey.trim()
        val def = byKey[key]
        if (def == null) {
            return if (allowUnknownAsLocalOnly) {
                EventAcceptance.UnknownLocalOnly(key)
            } else {
                EventAcceptance.Rejected(ObservabilityErrorCode.OBS_EVENT_UNKNOWN, key)
            }
        }
        return when (val meta = MetadataAllowlist.validate(metadata, def.allowedMetadataKeys, def.requiredMetadataKeys)) {
            is MetadataAllowlist.MetadataValidation.Accepted ->
                EventAcceptance.Accepted(def, meta.metadata)
            is MetadataAllowlist.MetadataValidation.Rejected ->
                EventAcceptance.Rejected(meta.code, meta.detail)
        }
    }
}
