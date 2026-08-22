package com.comunidapp.app.domain.qa

/**
 * Physical QA FIX-01 contracts. Source of truth for UI + tests.
 * Does not replace server RLS; documents the product rules enforced in this block.
 */
object PhysicalQaFix01Contracts {
    const val GOOGLE_AUTO_SELECT = false
    const val GOOGLE_OAUTH_PROMPT = "select_account"
    const val GOOGLE_PASSWORD_OPTIONAL = true
    const val ONE_EMAIL_ONE_PERSON = true
    const val INCOMPLETE_ONBOARDING_RESUMES = true
    const val PHOTO_OPTIONAL_ON_ONBOARDING = true

    const val PUBLIC_ADOPTION_ALLOWED_PERSON = false
    const val PERSON_CAMPAIGN_CREATE = false
    const val PERSON_CAMPAIGN_ADMIN = false
    const val PERSON_CAMPAIGN_VIEW = true
    const val PERSON_CAMPAIGN_PARTICIPATE = true
    const val PERSON_EVENT_CREATE = false
    const val PERSON_EVENT_ADMIN = false
    const val PERSON_EVENT_VIEW = true
    const val PERSON_EVENT_REGISTER = true

    const val CONTACT_PHONE_VISIBLE = true
    const val CONTACT_INSTAGRAM_VISIBLE = false
    const val CONTACT_EMAIL_VISIBLE = false

    const val GLOBAL_SIGHTING_ACTION = false
    const val SIGHTING_REQUIRES_CASE = true
    const val MATCHES_REQUIRE_CASE = true

    const val VERIFICATION_SELF_DECLARED = false
    const val VERIFICATION_SOURCE_OF_TRUTH = "actor_verifications + canon_admin_set_org_verification"
    const val VERIFICATION_UI_VISIBLE = false

    const val FIRST_LEVEL_ACTOR_COUNT = 6
    const val PET_FRIENDLY_IS_FIRST_LEVEL = false
    const val PET_FRIENDLY_IS_BUSINESS_KIND = true

    const val MIS_POSTULACIONES_LABEL = "Mis postulaciones"
    const val PERSON_ADOPTIONS_COPY =
        "Encontrá mascotas en adopción y seguí tus postulaciones."

    const val CREATE_PASSWORD_LABEL = "Crear contraseña"
    const val OAUTH_LOGIN_RECOVERY_COPY =
        "Si te registraste con Google, continuá con Google o usá Recuperar contraseña para crear una."

    const val STORY_ORANGE_TOKEN = "BrandOrange"
    const val MAP_ZONE_SELECTOR_WHEN_GPS_GRANTED = false
    const val TIME_FIELDS_FREE_TEXT = false
    const val OPEN_24_HOURS_SUPPORTED = true
}

object PublicContactPolicy {
    const val PHONE_VISIBLE = true
    const val INSTAGRAM_VISIBLE = false
    const val EMAIL_VISIBLE = false
    const val EMAIL_INTERNAL_AUTH_ONLY = true
}
