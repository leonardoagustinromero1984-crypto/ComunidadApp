package com.comunidapp.shared.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
actual fun sha256Hex(data: ByteArray): String {
    val out = UByteArray(CC_SHA256_DIGEST_LENGTH)
    data.asUByteArray().usePinned { inputPinned ->
        out.usePinned { outPinned ->
            CC_SHA256(
                inputPinned.addressOf(0),
                data.size.convert(),
                outPinned.addressOf(0)
            )
        }
    }
    return bytesToHex(out.toByteArray())
}
