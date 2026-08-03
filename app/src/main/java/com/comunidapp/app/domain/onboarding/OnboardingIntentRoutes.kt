package com.comunidapp.app.domain.onboarding

import com.comunidapp.app.navigation.NavRoutes

/** Mapeo de intención de primer ingreso a rutas NavHost existentes. */
object OnboardingIntentRoutes {

    fun primaryRoute(intent: OnboardingIntent): String = when (intent) {
        OnboardingIntent.REGISTER_PET -> NavRoutes.ADD_PET
        OnboardingIntent.LOST_PET -> NavRoutes.PUBLISH_LOST_FOUND
        OnboardingIntent.FOUND_ANIMAL -> NavRoutes.PUBLISH_LOST_FOUND
        OnboardingIntent.ADOPT -> NavRoutes.SUMATE
        OnboardingIntent.OFFER_FOSTER -> NavRoutes.PUBLISH_FOSTER
        OnboardingIntent.ORGANIZATION -> NavRoutes.MY_ORGANIZATIONS
        OnboardingIntent.VOLUNTEER -> NavRoutes.M17_HUB
        OnboardingIntent.EXPLORE -> NavRoutes.HOME
    }

    fun primaryCtaLabel(intent: OnboardingIntent): String = when (intent) {
        OnboardingIntent.REGISTER_PET -> "Crear mi primera mascota"
        OnboardingIntent.LOST_PET -> "Publicar una alerta"
        OnboardingIntent.FOUND_ANIMAL -> "Informar un animal encontrado"
        OnboardingIntent.ADOPT -> "Explorar adopciones"
        OnboardingIntent.OFFER_FOSTER -> "Configurar tránsito"
        OnboardingIntent.ORGANIZATION -> "Administrar organización"
        OnboardingIntent.VOLUNTEER -> "Buscar formas de ayudar"
        OnboardingIntent.EXPLORE -> "Explorar LeoVer"
    }

    fun exploreFallbackRoute(): String = NavRoutes.HOME
}
