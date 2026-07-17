package com.comunidapp.app.viewmodel.observability

import com.comunidapp.app.domain.observability.ObservabilityErrorCode
import com.comunidapp.app.domain.observability.ObservabilityModule
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase

data class ObservabilityFilterState(
    val module: ObservabilityModule? = null,
    val metricKey: String = "",
    val unit: String = "",
    val scope: String = "PLATFORM",
    val incidentStatus: String = "",
    val timeRangeHours: Int = 24
) {
    fun clear(): ObservabilityFilterState = ObservabilityFilterState()
}

object ObservabilityUiErrorMapper {
    fun userMessage(code: String?): String = when (ObservabilityErrorCode.fromString(code)) {
        ObservabilityErrorCode.OBS_PERMISSION_DENIED,
        ObservabilityErrorCode.OBS_DASHBOARD_PERMISSION_DENIED,
        ObservabilityErrorCode.OBS_HEALTH_EXECUTION_DENIED,
        ObservabilityErrorCode.OBS_INCIDENT_TRANSITION_DENIED,
        ObservabilityErrorCode.OBS_RETENTION_EXECUTION_DENIED,
        ObservabilityErrorCode.OBS_EXPORT_SCOPE_DENIED,
        ObservabilityErrorCode.OBS_PERMISSION_MAPPING_INVALID,
        ObservabilityErrorCode.OBS_READ_DENIED,
        ObservabilityErrorCode.OBS_WRITE_DENIED ->
            "No tenés permiso para esta operación de observabilidad."
        ObservabilityErrorCode.OBS_DATA_UNAVAILABLE,
        ObservabilityErrorCode.OBS_REPOSITORY_UNAVAILABLE ->
            "Los datos de observabilidad no están disponibles."
        ObservabilityErrorCode.OBS_METRIC_UNKNOWN,
        ObservabilityErrorCode.OBS_METRIC_DIMENSION_DENIED ->
            "La métrica o sus dimensiones no son válidas."
        ObservabilityErrorCode.OBS_HEALTH_CHECK_UNKNOWN ->
            "El health check indicado no existe."
        ObservabilityErrorCode.OBS_INCIDENT_NOT_FOUND ->
            "No encontramos el incidente."
        ObservabilityErrorCode.OBS_FILTER_INVALID ->
            "El filtro no es válido."
        ObservabilityErrorCode.OBS_ALERT_RULE_INVALID,
        ObservabilityErrorCode.OBS_ALERT_EVALUATION_FAILED ->
            "No se pudo evaluar la regla de alerta."
        ObservabilityErrorCode.OBS_RETENTION_POLICY_UNKNOWN ->
            "La política de retención no es válida."
        ObservabilityErrorCode.OBS_RETENTION_PREVIEW_REQUIRED ->
            "Primero generá un preview válido."
        ObservabilityErrorCode.OBS_RETENTION_PREVIEW_EXPIRED ->
            "El preview expiró. Generá uno nuevo."
        ObservabilityErrorCode.OBS_RETENTION_LEGAL_HOLD ->
            "Legal hold o revisión legal bloquea la operación."
        ObservabilityErrorCode.OBS_RETENTION_RUN_FAILED ->
            "La ejecución de retención falló."
        ObservabilityErrorCode.OBS_EXPORT_NOT_READY,
        ObservabilityErrorCode.OBS_EXPORT_EXPIRED,
        ObservabilityErrorCode.OBS_EXPORT_DENIED ->
            "La exportación no está disponible."
        ObservabilityErrorCode.OBS_M06_NOTIFICATION_PENDING ->
            "Integración M06 pendiente."
        ObservabilityErrorCode.OBS_CI_QUALITY_CHECK_FAILED ->
            "Falló un control de calidad CI."
        else -> "No pudimos completar la operación de observabilidad."
    }

    fun phaseForEmptyOrContent(isEmpty: Boolean): AdministrativeScreenPhase =
        if (isEmpty) AdministrativeScreenPhase.Empty else AdministrativeScreenPhase.Content
}
