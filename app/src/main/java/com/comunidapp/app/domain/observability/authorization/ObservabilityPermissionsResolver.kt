package com.comunidapp.app.domain.observability.authorization

import com.comunidapp.app.domain.authorization.PermissionCode
import com.comunidapp.app.domain.observability.ObservabilityPermissionSectionInfo

/**
 * Mapeo sección UI M07 → permiso dedicado.
 * AccountType / active_modules / deep links nunca otorgan autoridad.
 * No duplica administración de roles M02.
 */
object ObservabilityPermissionsResolver {

    data class Section(val key: String, val required: PermissionCode)

    val sections: List<Section> = listOf(
        Section("overview", PermissionCode.OBSERVABILITY_VIEW),
        Section("metrics", PermissionCode.OBSERVABILITY_VIEW),
        Section("health", PermissionCode.OBSERVABILITY_VIEW),
        Section("incidents_view", PermissionCode.OBSERVABILITY_VIEW),
        Section("incidents_manage", PermissionCode.ALERT_MANAGE),
        Section("rules_manage", PermissionCode.OBSERVABILITY_MANAGE),
        Section("audit_sensitive", PermissionCode.AUDIT_VIEW_SENSITIVE),
        Section("security_events", PermissionCode.SECURITY_EVENTS_VIEW),
        Section("exports", PermissionCode.EXPORT_AUDIT_DATA),
        Section("retention", PermissionCode.RETENTION_MANAGE),
        Section("health_manual", PermissionCode.HEALTH_CHECK_EXECUTE)
    )

    fun requiredFor(sectionKey: String): PermissionCode? =
        sections.firstOrNull { it.key.equals(sectionKey, ignoreCase = true) }?.required

    fun resolve(
        granted: Set<PermissionCode>,
        accountTypeClaim: String? = null,
        activeModulesClaim: Set<String> = emptySet()
    ): List<ObservabilityPermissionSectionInfo> {
        // Claims are accepted only to document they never flip deny → allow.
        @Suppress("UNUSED_VARIABLE")
        val ignoredClaims = accountTypeClaim to activeModulesClaim
        return sections.map { section ->
            ObservabilityPermissionSectionInfo(
                sectionKey = section.key,
                requiredPermission = section.required.code,
                allowed = section.required in granted
            )
        }
    }

    fun deepLinkGrantsAccess(): Boolean = false
}
