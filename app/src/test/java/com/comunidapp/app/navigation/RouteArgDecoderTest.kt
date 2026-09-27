package com.comunidapp.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteArgDecoderTest {
    @Test
    fun decode_usesUtf8StringOverload() {
        assertEquals("hola mundo", RouteArgDecoder.decode("hola%20mundo"))
        assertEquals("", RouteArgDecoder.decode(null))
        assertEquals("mascota/1", RouteArgDecoder.decode("mascota%2F1"))
    }
}
