package com.comunidapp.app.domain.user

import com.comunidapp.app.data.model.User

/**
 * PERSON already resolved by [com.comunidapp.app.viewmodel.SessionViewModel].
 * Pet create/edit and other PERSON-gated screens must read this, not a
 * second JWT/cache lookup that can still be empty after login.
 * Logout clears it so user A cannot leak into user B.
 */
object SessionResolvedPerson {
    @Volatile
    private var person: User? = null

    fun current(): User? = person

    fun set(user: User?) {
        person = user?.takeIf { !SessionPersonRouting.isJwtStub(it) }
    }

    fun clear() {
        person = null
    }
}
