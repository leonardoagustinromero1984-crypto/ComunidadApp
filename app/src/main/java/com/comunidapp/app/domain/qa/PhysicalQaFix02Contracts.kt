package com.comunidapp.app.domain.qa

/**
 * Physical QA FIX-02 contracts. Does not replace server RLS.
 * Staging Google may still show the technical Supabase host until Custom Domain
 * DNS exists. Official apex is leover.com.ar; auth-staging / auth are NXDOMAIN.
 */
object PhysicalQaFix02Contracts {
    const val STAGING_SUPABASE_AUTH_DOMAIN_ALLOWED = true
    const val PROD_AUTH_BRANDING_PENDING = true
    const val STAGING_AUTH_HOST_MAY_SHOW_PROJECT_REF = true
    const val STAGING_CUSTOM_AUTH_DOMAIN_ACTIVE = false
    const val EXTERNAL_DNS_OR_GOOGLE_ACTION_REQUIRED = true
    const val PROD_MUST_SHOW_LEOVER_NOT_PROJECT_REF = true

    const val DEFAULT_START_ACTOR_PERSON = true
    const val FIRST_LEVEL_ACTOR_COUNT = 6
    const val FOSTER_IS_FIRST_LEVEL_ACTOR = true
    const val FOSTER_IN_ADD_FUNCTION = true
    const val PERSON_IN_ADD_FUNCTION = false
    const val ALREADY_ACTIVE_CAPABILITIES_IN_ADD_FUNCTION = false

    const val ONBOARDING_BACK_USES_FLOW_STACK = true
    const val ONBOARDING_BACK_EXITS_TUTORIAL = false

    const val NEW_CONTEXT_AUTO_ACTIVATED = true
    const val NEW_CONTEXT_NAVIGATES_TO_OWN_DASHBOARD = true
    const val BACK_DOES_NOT_CHANGE_ACTIVE_CONTEXT = true
    const val ACTIVE_CONTEXT_VISIBLE_ON_HOME = true

    const val PROFILE_DATA_ISOLATED_BETWEEN_CONTEXTS = true
    const val FIRST_ORG_FALLBACK_FORBIDDEN = true

    const val VETERINARY_PROFILE_REOPEN_CRASH = false
    const val WALKER_PROFILE_SAVE_CRASH = false
    const val PROFILE_PUBLISH_TRIGGERS_OAUTH = false
    const val PROFILE_PUBLISH_PRESERVES_SESSION = true

    const val RAW_ENUM_VISIBLE_TO_USER = false
    const val OPTIONAL_BOOKING_COPY = true
    const val APPOINTMENT_INTERVAL_SINGLE_LINE = true

    const val RESCUER_PERSON_DASHBOARD = true
    const val RESCUER_PERSON_SAME_AS_PERSON = false
    const val REFUGE_PROFESSIONAL_MIX = false
    const val REFUGE_MANUAL_PET_ADD = true
    const val REFUGE_VITACORA_IMPORT = true
    const val RESCUER_MANUAL_PET_ADD = true
    const val RESCUER_VITACORA_IMPORT = true

    const val VERIFICATION_UI_VISIBLE = false
    const val PET_FRIENDLY_PRESERVED = true
}

object ProfilePublishAuthPolicy {
    const val TRIGGERS_OAUTH = false
    const val PRESERVES_SESSION = true
    const val UNAUTHORIZED_WRITE_LOGS_OUT = false

    fun mayStartGoogleOAuth(fromProfilePublish: Boolean): Boolean =
        !fromProfilePublish && !TRIGGERS_OAUTH
}
