package com.comunidapp.app.domain.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendshipErrorMapperTest {

    @Test
    fun permissionDeniedSelect_neverShowsUrlTokenOrGrant() {
        val raw = RuntimeException(
            "permission denied for table friendships " +
                "GET /rest/v1/friendships?requester_id=eq.abc " +
                "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.aaa.bbb " +
                "GRANT SELECT ON public.friendships TO authenticated;"
        )
        val message = FriendshipErrorMapper.userMessage(raw, FriendshipErrorMapper.Operation.SEND)
        assertEquals(
            "No pudimos completar la solicitud de amistad. Intentá nuevamente.",
            message
        )
        assertFalse(message.contains("Bearer", ignoreCase = true))
        assertFalse(message.contains("eyJ"))
        assertFalse(message.contains("rest/v1"))
        assertFalse(message.contains("GRANT", ignoreCase = true))
        assertFalse(message.contains("Authorization", ignoreCase = true))
        assertTrue(FriendshipErrorMapper.looksTechnical(raw.message!!))
    }

    @Test
    fun load_usesLoadCopy() {
        val message = FriendshipErrorMapper.userMessage(
            RuntimeException("42501"),
            FriendshipErrorMapper.Operation.LOAD
        )
        assertEquals("No pudimos cargar tus amistades. Intentá nuevamente.", message)
    }

    @Test
    fun domainIllegalArgument_passesThrough() {
        val message = FriendshipErrorMapper.userMessage(
            IllegalArgumentException("Ya hay una solicitud pendiente"),
            FriendshipErrorMapper.Operation.SEND
        )
        assertEquals("Ya hay una solicitud pendiente", message)
    }
}
