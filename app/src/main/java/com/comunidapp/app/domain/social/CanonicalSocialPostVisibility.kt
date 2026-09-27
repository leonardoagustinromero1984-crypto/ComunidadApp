package com.comunidapp.app.domain.social



/**

 * Canonical social post visibility — matches `social_posts.visibility` in SQL

 * (`PUBLIC`, `FOLLOWERS`, legacy `PRIVATE`).

 *

 * Product UI (v1): only **Público** and **Privado**.

 * - Público → `PUBLIC`

 * - Privado → `FOLLOWERS` (accepted connections only; never show "FOLLOWERS" to users)

 */

enum class CanonicalSocialPostVisibility {

    PUBLIC,

    FOLLOWERS,

    /** Legacy/backend value; treated like FOLLOWERS for feed + wall rules. */

    PRIVATE;



    fun toRpcValue(): String = when (this) {

        PUBLIC -> "PUBLIC"

        FOLLOWERS, PRIVATE -> "FOLLOWERS"

    }



    companion object {

        fun fromRaw(raw: String?): CanonicalSocialPostVisibility =

            when (raw?.trim()?.uppercase()) {

                "PUBLIC" -> PUBLIC

                "FOLLOWERS" -> FOLLOWERS

                "PRIVATE" -> PRIVATE

                else -> FOLLOWERS

            }



        /** Options shown in publish UI — exactly two chips. */

        fun uiPublishOptions(): List<CanonicalSocialPostVisibility> =

            listOf(PUBLIC, FOLLOWERS)



        fun label(visibility: CanonicalSocialPostVisibility): String = when (visibility) {

            PUBLIC -> "Público"

            FOLLOWERS, PRIVATE -> "Privado"

        }

    }

}

