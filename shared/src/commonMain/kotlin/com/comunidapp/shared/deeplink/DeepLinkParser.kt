package com.comunidapp.shared.deeplink

/**
 * Parser allowlisted — alinea rutas públicas web (`/mascota`, `/adopciones`, …)
 * y scheme custom `leover://passport/{code}`.
 */
object DeepLinkParser {

    private val publicCodePattern = Regex("^[A-Za-z0-9_\\-.:]{1,128}$")
    private val forbiddenSchemePrefixes = listOf(
        "javascript:",
        "file:",
        "data:",
        "content:",
        "intent:",
        "about:"
    )

    fun parse(rawUrl: String): DeepLinkTarget {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return DeepLinkTarget.Unsupported("blank")
        }
        val lower = trimmed.lowercase()
        if (forbiddenSchemePrefixes.any { lower.startsWith(it) }) {
            return DeepLinkTarget.Unsupported("forbidden_scheme")
        }

        val scheme = extractScheme(trimmed)
        return when (scheme?.lowercase()) {
            "https" -> parseHttps(trimmed)
            DeepLinkHosts.CUSTOM_SCHEME -> parseCustom(trimmed)
            null -> DeepLinkTarget.Unsupported("missing_scheme")
            else -> DeepLinkTarget.Unsupported("unsupported_scheme")
        }
    }

    private fun extractScheme(raw: String): String? {
        val idx = raw.indexOf(':')
        if (idx <= 0) return null
        val candidate = raw.substring(0, idx)
        if (!candidate.all { it.isLetter() || it == '+' || it == '-' || it == '.' }) return null
        return candidate
    }

    private fun parseHttps(raw: String): DeepLinkTarget {
        val withoutScheme = raw.removePrefix("https://").removePrefix("HTTPS://")
        val slash = withoutScheme.indexOf('/')
        val hostPart = if (slash < 0) withoutScheme else withoutScheme.substring(0, slash)
        val host = hostPart.substringBefore(':').substringBefore('@').lowercase()
        if (host !in DeepLinkHosts.HTTPS_HOSTS) {
            return DeepLinkTarget.Unsupported("wrong_host")
        }
        val pathAndQuery = if (slash < 0) "" else withoutScheme.substring(slash)
        val path = pathAndQuery.substringBefore('?').substringBefore('#')
        val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
        if (segments.size < 2) {
            return DeepLinkTarget.Unsupported("incomplete_path")
        }
        val kind = segments[0].lowercase()
        val rawCode = segments[1]
        if (rawCode == "." || rawCode == "..") {
            return DeepLinkTarget.Unsupported("invalid_code")
        }
        val code = normalizePublicCode(rawCode)
            ?: return DeepLinkTarget.Unsupported("invalid_code")
        return when (kind) {
            "mascota" -> DeepLinkTarget.PetPublic(code)
            "adopciones" -> DeepLinkTarget.AdoptionPublic(code)
            "perdidos" -> DeepLinkTarget.LostCase(code)
            "encontrados" -> DeepLinkTarget.FoundCase(code)
            else -> DeepLinkTarget.Unsupported("unknown_path")
        }
    }

    private fun parseCustom(raw: String): DeepLinkTarget {
        // leover://passport/CODE or leover:///passport/CODE
        val afterScheme = raw
            .removePrefix("leover://")
            .removePrefix("LEOVER://")
            .removePrefix("leover:")
            .removePrefix("LEOVER:")
            .trimStart('/')
        val path = afterScheme.substringBefore('?').substringBefore('#')
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.size < 2) {
            return DeepLinkTarget.Unsupported("incomplete_custom_path")
        }
        val kind = segments[0].lowercase()
        val rawCode = segments[1]
        if (rawCode == "." || rawCode == "..") {
            return DeepLinkTarget.Unsupported("invalid_code")
        }
        val code = normalizePublicCode(rawCode)
            ?: return DeepLinkTarget.Unsupported("invalid_code")
        return when (kind) {
            "passport" -> DeepLinkTarget.Passport(code)
            "mascota" -> DeepLinkTarget.PetPublic(code)
            "adopciones" -> DeepLinkTarget.AdoptionPublic(code)
            "perdidos" -> DeepLinkTarget.LostCase(code)
            "encontrados" -> DeepLinkTarget.FoundCase(code)
            else -> DeepLinkTarget.Unsupported("unknown_custom_path")
        }
    }

    internal fun normalizePublicCode(raw: String): String? {
        val decoded = try {
            decodeUriComponent(raw.trim())
        } catch (_: Throwable) {
            return null
        }.trim()
        if (decoded.isEmpty()) return null
        if (!publicCodePattern.matches(decoded)) return null
        return decoded
    }

    /**
     * Decodifica %XX básico (UTF-8) sin dependencia de java.net.
     */
    private fun decodeUriComponent(value: String): String {
        if ('%' !in value && '+' !in value) return value
        val bytes = ArrayList<Byte>(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '%' && i + 2 < value.length -> {
                    val hex = value.substring(i + 1, i + 3)
                    val b = hex.toIntOrNull(16) ?: error("bad_pct")
                    bytes.add(b.toByte())
                    i += 3
                }
                c == '+' -> {
                    bytes.add(' '.code.toByte())
                    i++
                }
                else -> {
                    // BMP ASCII path — encode char as UTF-8 single byte when < 128
                    val code = c.code
                    if (code < 128) {
                        bytes.add(code.toByte())
                    } else {
                        // Keep multi-byte chars as UTF-8 via string rebuild fallback
                        val utf8 = value.substring(i, i + 1).encodeToByteArray()
                        utf8.forEach { bytes.add(it) }
                    }
                    i++
                }
            }
        }
        return bytes.toByteArray().decodeToString()
    }
}
