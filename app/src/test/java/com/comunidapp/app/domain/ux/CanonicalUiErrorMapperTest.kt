package com.comunidapp.app.domain.ux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalUiErrorMapperTest {

    @Test
    fun duplicateKeyIsFriendly() {
        val raw = "duplicate key value violates unique constraint \"organizations_slug_uidx\" SQLSTATE 23505"
        val message = CanonicalUiErrorMapper.userMessage(raw)
        assertEquals(CanonicalUiErrorMapper.DUPLICATE_PUBLIC_IDENTIFIER, message)
        assertFalse(message.contains("SQLSTATE", ignoreCase = true))
        assertFalse(message.contains("duplicate key", ignoreCase = true))
        assertFalse(message.contains("organizations_slug_uidx"))
    }

    @Test
    fun alreadyExistsForRequestIsRecoveryCopy() {
        assertEquals(
            CanonicalUiErrorMapper.ALREADY_EXISTS_ORG,
            CanonicalUiErrorMapper.userMessage("ORGANIZATION_ALREADY_EXISTS_FOR_REQUEST")
        )
    }

    @Test
    fun permissionAndNetworkAreFriendly() {
        assertEquals(CanonicalUiErrorMapper.PERMISSION, CanonicalUiErrorMapper.userMessage("403 Forbidden"))
        assertEquals(CanonicalUiErrorMapper.NETWORK, CanonicalUiErrorMapper.userMessage("failed to connect to host"))
        assertTrue(CanonicalUiErrorMapper.isUnsafeToShow("JWT eyJhbGciOi"))
    }

    @Test
    fun errorKindsCoverTheUxContract() {
        assertEquals(
            CanonicalUiErrorKind.NETWORK,
            CanonicalUiErrorMapper.kind("failed to connect to host")
        )
        assertEquals(
            CanonicalUiErrorKind.PERMISSION,
            CanonicalUiErrorMapper.kind("403 Forbidden")
        )
        assertEquals(
            CanonicalUiErrorKind.ALREADY_EXISTS,
            CanonicalUiErrorMapper.kind("duplicate key value violates unique constraint")
        )
        assertEquals(
            CanonicalUiErrorKind.NOT_FOUND,
            CanonicalUiErrorMapper.kind("NOT_FOUND")
        )
        assertEquals(
            CanonicalUiErrorKind.VALIDATION,
            CanonicalUiErrorMapper.kind("VALIDATION failed")
        )
        assertEquals(
            CanonicalUiErrorKind.SERVER,
            CanonicalUiErrorMapper.kind("500 INTERNAL")
        )
    }
}
