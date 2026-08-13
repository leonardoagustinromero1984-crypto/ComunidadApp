package com.comunidapp.app.domain.organization

/**
 * Identificador tipado de organización (M03).
 * Extraído a commonMain para que el dominio M08 de mascotas sea multiplataforma
 * sin arrastrar el agregado Organization completo.
 *
 * Kotlin 2.3 Native: `value class` + `@JvmInline` no es portable en commonMain
 * (JvmInline no resuelve en Native; expect/actual no satisface el checker JVM).
 * `data class` mantiene `.value` y semántica de igualdad para IDs.
 */
data class OrganizationId(val value: String) {
    init {
        require(value.isNotBlank()) { "organization id blank" }
    }
}
