package com.comunidapp.app.ui.components

import com.comunidapp.app.data.model.SterilizationStatus

fun SterilizationStatus.toDisplayName(): String = when (this) {
    SterilizationStatus.YES -> "Castrado"
    SterilizationStatus.NO -> "No castrado"
    SterilizationStatus.UNKNOWN -> "Desconocido"
}
