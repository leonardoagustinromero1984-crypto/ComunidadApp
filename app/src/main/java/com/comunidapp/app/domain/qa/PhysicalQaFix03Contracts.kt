package com.comunidapp.app.domain.qa

/**
 * Physical QA FIX-03 contracts. Does not replace server RLS.
 */
object PhysicalQaFix03Contracts {
    const val PERSON_ALWAYS_SELECTED = true
    const val PERSON_SELECTION_EDITABLE = false
    const val PERSON_REMOVABLE = false

    const val ADD_FUNCTION_GROUPED = true
    const val PROFESSIONAL_CATEGORIES_FLAT = false
    const val BUSINESS_CATEGORIES_FLAT = false
    const val FOSTER_PRESENT = true
    const val PERSON_IN_ADD_FUNCTION = false
    const val EXISTING_CONTEXTS_FILTERED = true
    const val ADD_FUNCTION_TOP_LEVEL_COUNT = 5

    const val USER_VISIBLE_MEDIA_LIMIT_FOR_NORMAL_PHONE_MEDIA = false
    const val PROFILE_PHOTO_FAILURE_BLOCKS = true
    const val PROFILE_PHOTO_OPTIONAL_SKIP = true

    const val VETERINARY_CREATE_DESTINATION_SETTINGS = false
    const val RESCUER_CREATE_DESTINATION_SETTINGS = false
    const val FOSTER_CREATE_LANDING_ADMIN = false
    const val RAW_LOCATION_ID_VISIBLE = false

    const val BACKGROUND_DOES_NOT_RESET_APP = true
    const val ACTIVE_CONTEXT_RESTORED = true
    const val NAV_ROUTE_RESTORED = true

    const val PERSON_LOST_FOUND_MAP_OPENS = true
    const val MAP_ZERO_ALERTS_NO_CRASH = true

    const val AUTH_SESSION_VALID_DURING_PET_CREATE = true
    const val CUSTOM_AUTH_DOMAIN_ACTIVE = false
}
