package com.comunidapp.shared.crypto

/**
 * SHA-256 hex lowercase — expect/actual sin dependencias nuevas.
 */
expect fun sha256Hex(data: ByteArray): String

fun sha256HexOfUtf8(text: String): String = sha256Hex(text.encodeToByteArray())

fun sha256HexOfHexToken(hexToken: String): String {
    val clean = hexToken.trim().lowercase().removePrefix("<").removeSuffix(">").replace(" ", "")
    val bytes = hexStringToBytes(clean)
    return sha256Hex(bytes)
}

internal fun hexStringToBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "HEX_LENGTH" }
    return ByteArray(hex.length / 2) { i ->
        hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}

fun bytesToHex(bytes: ByteArray): String =
    bytes.joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
