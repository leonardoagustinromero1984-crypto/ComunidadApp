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
        else -> "No pudimos completar la operación de observabilidad."
    }

    fun phaseForEmptyOrContent(isEmpty: Boolean): AdministrativeScreenPhase =
        if (isEmpty) AdministrativeScreenPhase.Empty else AdministrativeScreenPhase.Content
}
