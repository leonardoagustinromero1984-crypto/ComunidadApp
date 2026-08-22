package com.comunidapp.app.domain.canonical

/**
 * Consumer hit a missing or contradictory canonical contract.
 * Do not invent SQL or silently write a workaround.
 */
data class CanonicalSchemaBlocker(
    val consumer: String,
    val expectedContract: String,
    val actualContract: String,
    val requiredChange: String,
    val affectedMigrationOrEntity: String
) {
    fun asException(): CanonicalSchemaBlockerException =
        CanonicalSchemaBlockerException(this)

    fun reportLine(): String = buildString {
        append("CANONICAL_SCHEMA_BLOCKER consumer=").append(consumer)
        append(" expected=").append(expectedContract)
        append(" actual=").append(actualContract)
        append(" required=").append(requiredChange)
        append(" entity=").append(affectedMigrationOrEntity)
    }
}

class CanonicalSchemaBlockerException(
    val blocker: CanonicalSchemaBlocker
) : IllegalStateException(blocker.reportLine())

object CanonicalSchemaBlockers {
    val petUpdate = CanonicalSchemaBlocker(
        consumer = "ANDROID/KMP pet profile edit",
        expectedContract = "canon_update_pet(name, species, sex, size, birth, locality) or pets UPDATE for holders",
        actualContract = "pets has SELECT-only RLS; no canon_update_pet in 1020",
        requiredChange = "Add holder UPDATE policy and/or canon_update_pet RPC",
        affectedMigrationOrEntity = "1007 pets / 1020 rls_rpc"
    )

    val petAvatarPersist = CanonicalSchemaBlocker(
        consumer = "ANDROID/KMP pet photo persist",
        expectedContract = "canon_set_pet_avatar(pet_id, media_asset_id) writing pets.avatar_asset_id",
        actualContract = "canon_register_media exists; pets.avatar_asset_id exists; no write path for holders",
        requiredChange = "Add canon_set_pet_avatar or pets UPDATE of avatar_asset_id for holders",
        affectedMigrationOrEntity = "1006 media_assets / 1007 pets.avatar_asset_id / 1020"
    )

    val petHealthWrite = CanonicalSchemaBlocker(
        consumer = "ANDROID health declared write",
        expectedContract = "RPCs or RLS INSERT on pet_allergies, pet_medications, pet_vaccinations, …",
        actualContract = "Health tables exist (1009); authenticated has no INSERT; no health write RPC",
        requiredChange = "Add canon_record_* health RPCs or holder INSERT policies",
        affectedMigrationOrEntity = "1009 health_declared / 1020 rls_rpc"
    )

    val publicAdoption = CanonicalSchemaBlocker(
        consumer = "WEB /adopciones/[publicCode]",
        expectedContract = "canon_public_adoption(text) granted to anon + authenticated",
        actualContract = "Only canon_public_pet and canon_public_lost_found exist",
        requiredChange = "Add redacted canon_public_adoption (no PII, no exact coordinates)",
        affectedMigrationOrEntity = "1013 rescue / 1020 rls_rpc"
    )

    val usernameLookup = CanonicalSchemaBlocker(
        consumer = "ANDROID signup username availability",
        expectedContract = "canon_is_username_available(text) without leaking other persons",
        actualContract = "persons_self_select only; no username RPC; unique index at insert",
        requiredChange = "Add existence-only RPC (boolean) that does not return person rows",
        affectedMigrationOrEntity = "1002 persons / 1020 rls_rpc"
    )

    val profileRpcs = CanonicalSchemaBlocker(
        consumer = "ANDROID profile onboarding / update",
        expectedContract = "canon_update_my_person / persons self UPDATE of display_name, avatar, locality",
        actualContract = "persons_self_update exists; legacy complete_profile_onboarding / update_my_profile / is_username_available do not",
        requiredChange = "Stop calling legacy profile RPCs; use persons UPDATE or add thin canon RPCs",
        affectedMigrationOrEntity = "1002 persons"
    )

    val mediaRead = CanonicalSchemaBlocker(
        consumer = "ANDROID/WEB media render after reload",
        expectedContract = "SELECT media_assets by id for owner/holder, or signed URL minted at read time",
        actualContract = "No media_assets SELECT policy; no storage.objects policies in 1020",
        requiredChange = "Add media SELECT for owner + storage policies for public-media/private-media",
        affectedMigrationOrEntity = "1006 media / 1020 storage buckets"
    )

    val responsibilityList = CanonicalSchemaBlocker(
        consumer = "ANDROID multi-owner family list",
        expectedContract = "SELECT pet_responsibility_links for holders or canon_list_pet_holders",
        actualContract = "Table exists; no SELECT grant/policy for authenticated",
        requiredChange = "Add holder SELECT on pet_responsibility_links or list RPC",
        affectedMigrationOrEntity = "1007 pet_responsibility_links / 1020"
    )
}
