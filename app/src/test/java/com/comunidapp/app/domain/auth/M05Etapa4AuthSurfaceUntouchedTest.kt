package com.comunidapp.app.domain.auth

import com.comunidapp.app.data.repository.AuthRepository
import com.comunidapp.app.domain.user.UsernameValidators
import org.junit.Assert.assertTrue
import org.junit.Test

class M05Etapa4AuthSurfaceUntouchedTest {

    @Test
    fun `auth repository and username validator public APIs remain available`() {
        val authMethods = AuthRepository::class.java.methods.map { it.name }.toSet()

        assertTrue("getCurrentUser" in authMethods)
        assertTrue(UsernameValidators.validate("leo_user").isSuccess)
    }
}
