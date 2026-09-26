package com.comunidapp.app.domain.pets

object SpeciesLifecycle {
    const val PREPARATION = "PREPARATION"
    const val ACTIVE = "ACTIVE"
    const val INACTIVE = "INACTIVE"

    fun label(status: String?): String = when (status?.uppercase()) {
        PREPARATION -> "En preparación"
        ACTIVE -> "Activa"
        INACTIVE -> "Inactiva"
        else -> status.orEmpty()
    }

    fun isSelectableForNewPet(status: String?): Boolean = status?.uppercase() == ACTIVE
}

object SecondaryClassificationKind {
    const val NONE = "NONE"
    const val BREED = "BREED"
    const val TYPE = "TYPE"
    const val VARIETY = "VARIETY"
    const val CUSTOM = "CUSTOM"

    fun defaultLabels(kind: String?): Pair<String, String> = when (kind?.uppercase()) {
        TYPE -> "Tipo" to "Tipos"
        VARIETY -> "Variedad" to "Variedades"
        CUSTOM -> "Clasificación" to "Clasificaciones"
        BREED -> "Raza" to "Razas"
        else -> "" to ""
    }

    fun kindLabel(kind: String?): String = when (kind?.uppercase()) {
        TYPE -> "Tipo"
        VARIETY -> "Variedad"
        CUSTOM -> "Personalizado"
        BREED -> "Raza"
        else -> "Sin segundo nivel"
    }
}
