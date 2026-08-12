package com.comunidapp.app.domain.organization

/**
 * Identificador tipado de organización (M03).
 * Extraído a commonMain para que el dominio M08 de mascotas sea multiplataforma
 * sin arrastrar el agregado Organization completo.
 */
@JvmInline
value class OrganizationId(val value: String) {
    init {
        require(value.isNotBlank()) { "organization id blank" }
    }
}
