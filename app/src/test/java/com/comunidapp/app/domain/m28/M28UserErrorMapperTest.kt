package com.comunidapp.app.domain.m28

import com.comunidapp.app.data.repository.M28AccessException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M28UserErrorMapperTest {
    @Test
    fun doesNotExposeGrantRevokedCode() {
        val msg = M28UserErrorMapper.message(M28AccessException("M28_GRANT_REVOKED"))
        assertFalse(msg.contains("M28_GRANT_REVOKED"))
        assertTrue(msg.contains("permiso"))
    }
}
