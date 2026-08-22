package com.comunidapp.app.domain.publish

import com.comunidapp.app.domain.context.OperationalContext
import com.comunidapp.app.domain.context.OperationalContextKind

/**
 * UX-only mapping of Publicar actions.
 *
 * ActiveContext is not security authority. Backend RPCs remain authorization.
 * A NavRoute existing is not enough to show an action.
 */
enum class PublishAction {
    SOCIAL_POST,
    REEL,
    STORY,
    UPDATE_PROVIDER_FICHA,
    ORGANIZATION_NEED,
    CAMPAIGN,
    DONATION,
    EVENT
}

data class PublishOptionSpec(
    val action: PublishAction,
    val title: String,
    val description: String
)

object ContextPublishMatrix {

    fun optionsFor(context: OperationalContext): List<PublishOptionSpec> {
        val social = listOf(
            PublishOptionSpec(PublishAction.SOCIAL_POST, "Publicación", "Foto, video o texto para el feed"),
            PublishOptionSpec(PublishAction.REEL, "Reel", "Video corto vertical"),
            PublishOptionSpec(PublishAction.STORY, "Historia", "Contenido efímero · 24 horas")
        )
        return when (context.kind) {
            OperationalContextKind.PERSONAL -> social
            OperationalContextKind.RESCUER -> social
            OperationalContextKind.FOSTER -> social
            OperationalContextKind.PROVIDER,
            OperationalContextKind.VETERINARY,
            OperationalContextKind.SHOP -> social + PublishOptionSpec(
                PublishAction.UPDATE_PROVIDER_FICHA,
                "Actualizar ficha",
                "Publicá o actualizá los datos de tu servicio"
            )
            OperationalContextKind.ORGANIZATION -> {
                val orgType = (context as? OperationalContext.Organization)?.organizationType.orEmpty()
                val refuge = orgType.contains("REFUGE", ignoreCase = true) ||
                    orgType.contains("ONG", ignoreCase = true) ||
                    orgType.contains("SHELTER", ignoreCase = true)
                social + listOf(
                    PublishOptionSpec(
                        PublishAction.ORGANIZATION_NEED,
                        "Necesidad de refugio",
                        "Insumos o apoyo para tu organización"
                    ),
                    PublishOptionSpec(PublishAction.EVENT, "Evento", "Organizá o anunciá un evento")
                ) + if (refuge) {
                    listOf(
                        PublishOptionSpec(PublishAction.CAMPAIGN, "Crear campaña", "Campaña solidaria de tu organización"),
                        PublishOptionSpec(PublishAction.DONATION, "Crear donación", "Pedido de ayuda autorizado")
                    )
                } else {
                    emptyList()
                }
            }
        }
    }

    fun personIsSocialOnly(): Boolean =
        optionsFor(OperationalContext.Personal).all {
            it.action in setOf(PublishAction.SOCIAL_POST, PublishAction.REEL, PublishAction.STORY)
        }
}
