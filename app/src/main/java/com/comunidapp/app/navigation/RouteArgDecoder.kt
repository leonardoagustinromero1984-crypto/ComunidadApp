package com.comunidapp.app.navigation

import java.net.URLDecoder

/**
 * Decodes navigation arguments on minSdk 26.
 * [URLDecoder.decode] with a [java.nio.charset.Charset] requires API 33.
 * The String charset overload is available since API 1 and matches UTF-8.
 */
object RouteArgDecoder {
    fun decode(raw: String?): String =
        URLDecoder.decode(raw.orEmpty(), Charsets.UTF_8.name())
}
