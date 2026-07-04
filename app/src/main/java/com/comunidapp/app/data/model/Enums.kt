package com.comunidapp.app.data.model

enum class PetSpecies {
    DOG,
    CAT,
    OTHER
}

enum class PetSex {
    MALE,
    FEMALE,
    UNKNOWN
}

enum class PetSize {
    SMALL,
    MEDIUM,
    LARGE
}

enum class AdoptionStatus {
    AVAILABLE,
    IN_PROCESS,
    ADOPTED
}

enum class LostFoundType {
    LOST,
    FOUND
}

enum class PostType {
    GENERAL,
    ADOPTION,
    LOST_FOUND,
    URGENT
}
