package com.comunidapp.app.data.model

enum class AccountType {
    PERSON,
    SHELTER,
    VET,
    TRAINER,
    WALKER,
    SHOP,
    FOSTER_HOME;

    companion object {
        fun fromString(value: String?): AccountType =
            entries.find { it.name == value } ?: PERSON
    }
}

enum class PetSpecies {
    DOG,
    CAT,
    HORSE,
    COW,
    SHEEP,
    GOAT,
    PIG,
    RABBIT,
    HAMSTER,
    GUINEA_PIG,
    BIRD,
    FISH,
    REPTILE,
    CHICKEN,
    DUCK,
    DONKEY,
    OTHER;

    companion object {
        fun fromString(value: String?): PetSpecies =
            entries.find { it.name == value } ?: OTHER
    }
}

enum class SterilizationStatus {
    YES,
    NO,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): SterilizationStatus? =
            value?.let { v -> entries.find { it.name == v } }
    }
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
    DRAFT,
    PUBLISHED,
    PAUSED,
    ADOPTED,
    CLOSED;

    companion object {
        fun fromString(value: String?): AdoptionStatus {
            val raw = value?.trim()?.uppercase().orEmpty()
            return when (raw) {
                "AVAILABLE" -> PUBLISHED // legacy
                "IN_PROCESS" -> PAUSED // legacy
                else -> entries.find { it.name == raw } ?: PUBLISHED
            }
        }

        fun isOpen(status: AdoptionStatus): Boolean =
            status == DRAFT || status == PUBLISHED || status == PAUSED
    }
}

enum class LostFoundType {
    LOST,
    FOUND
}

enum class LostFoundStatus {
    ACTIVE,
    RESOLVED;

    companion object {
        fun fromString(value: String?): LostFoundStatus =
            entries.find { it.name == value } ?: ACTIVE
    }
}

enum class PostType {
    GENERAL,
    QUESTION,
    PROMO,
    ADOPTION,
    LOST_FOUND,
    URGENT;

    companion object {
        fun fromString(value: String?): PostType =
            entries.find { it.name == value } ?: GENERAL
    }
}
