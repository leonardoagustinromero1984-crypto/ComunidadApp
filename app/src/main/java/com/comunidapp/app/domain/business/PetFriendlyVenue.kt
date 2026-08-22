package com.comunidapp.app.domain.business

enum class PetFriendlyVenueSubtype(
    val storageCode: String,
    val visibleLabel: String
) {
    HOTEL("HOTEL", "Hotel / Alojamiento"),
    CABIN("CABIN", "Cabaña / Complejo"),
    RESTAURANT("RESTAURANT", "Restaurante"),
    CAFE_BAR("CAFE_BAR", "Café / Bar"),
    CAMPING("CAMPING", "Camping"),
    OTHER("OTHER", "Otro");

    companion object {
        fun fromStorage(raw: String?): PetFriendlyVenueSubtype? =
            entries.firstOrNull { it.storageCode.equals(raw?.trim(), ignoreCase = true) }

        fun required(raw: String?): Boolean = fromStorage(raw) != null
    }
}

object PetFriendlyVenuePolicy {
    const val FIRST_LEVEL_ACTOR = false
    const val VISIBLE_LABEL = "Lugar pet friendly"
    const val STORAGE_CATEGORY = "PET_FRIENDLY"
    const val SUBTYPE_REQUIRED = true
    const val DISTINCT_FROM_DAYCARE = true
}
