package com.comunidapp.app.core

/**
 * Bugs funcionales detectados en prueba real.
 * No resolverlos dentro de UI-V2-03 (ni inventando IDs en Android, ni cambiando contratos).
 *
 * Identificador: KNOWN_FUNCTIONAL_BUGS_NOT_TOUCHED
 */
object KnownFunctionalBugsNotTouched {

    const val KNOWN_FUNCTIONAL_BUGS_NOT_TOUCHED = "KNOWN_FUNCTIONAL_BUGS_NOT_TOUCHED"

    /**
     * INSERT en `public.lost_found_posts` falla en PostgreSQL/Supabase:
     * el trigger/función de `public_code` llama `gen_random_bytes(integer)`
     * sin schema (`extensions.gen_random_bytes`) o sin la extensión `pgcrypto`.
     *
     * UI-V2-03: no SQL, no IDs client-side, no ocultar el fallo cambiando el contrato.
     */
    const val LOST_FOUND_DB_ERROR = "function gen_random_bytes(integer) does not exist"
    const val LOST_FOUND_DB_FIX_REQUIRED = false
    const val LOST_FOUND_SQL_HOTFIX = "082_lost_found_public_code_pgcrypto_schema.sql"
    const val LOST_POST_CREATION = "HOTFIX_082"
    const val FOUND_POST_CREATION = "HOTFIX_082"
    const val RAW_BACKEND_ERROR_HIDDEN = true
    const val RAW_SUPABASE_ERROR_EXPOSED_TO_USER = "FIXED"
}
