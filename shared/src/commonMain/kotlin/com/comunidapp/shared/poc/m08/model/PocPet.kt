package com.comunidapp.shared.poc.m08.model

/**
 * Minimal pet model for POC 2 (inspired by M08 PetForm media flow).
 * Not a full migration of Pet domain.
 */
data class PocPet(
    val id: String,
    val name: String,
    val speciesLabel: String,
    val photoUrl: String? = null,
    /** Local-only selection; never uploaded in this POC. */
    val pendingMedia: FileRef? = null
)

enum class PocMediaBackendMode {
    /** List/detail pet data may come from a real read path if wired. */
    BACKEND_REAL,
    /** Attachment / picker side-effects stay local for the native POC. */
    FAKE_FOR_NATIVE_POC
}
