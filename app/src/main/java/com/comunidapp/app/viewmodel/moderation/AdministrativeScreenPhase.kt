package com.comunidapp.app.viewmodel.moderation

/**
 * Fases de pantallas administrativas M04.
 * Deny-by-default: Loading / Error / AccessDenied no habilitan acciones.
 */
enum class AdministrativeScreenPhase {
    Loading,
    Content,
    Empty,
    Error,
    AccessDenied,
    Submitting
}
