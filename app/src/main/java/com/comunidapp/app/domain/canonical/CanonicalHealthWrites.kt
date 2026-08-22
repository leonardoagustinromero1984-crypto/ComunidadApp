package com.comunidapp.app.domain.canonical

/**
 * Declared health writes. Provenance is forced to DECLARED by the server RPCs.
 */
object CanonicalHealthWrites {
    val rpcNames: List<String> = listOf(
        CanonicalBackend.RPC_RECORD_PET_ALLERGY,
        CanonicalBackend.RPC_RECORD_PET_MEDICATION,
        CanonicalBackend.RPC_RECORD_PET_VACCINATION,
        CanonicalBackend.RPC_RECORD_PET_PARASITE_TREATMENT,
        CanonicalBackend.RPC_RECORD_PET_WEIGHT,
        CanonicalBackend.RPC_RECORD_PET_CONDITION,
        CanonicalBackend.RPC_SET_PET_CARE_INSTRUCTIONS
    )

    const val PROVENANCE = "DECLARED"
}
