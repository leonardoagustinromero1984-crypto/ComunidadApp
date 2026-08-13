package com.comunidapp.shared.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/**
 * Keychain iOS — tokens de sesión. Nunca NSUserDefaults.
 * Implementación CF nativa (sin casts ObjC inválidos).
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun createSecureSessionStorage(): SecureSessionStorage =
    IosKeychainSecureSessionStorage()

@OptIn(ExperimentalForeignApi::class)
internal class IosKeychainSecureSessionStorage(
    private val service: String = "com.leover.shared.auth.session"
) : SecureSessionStorage {

    override fun read(key: String): String? = memScoped {
        val query = buildBaseQuery(account = key, returnData = true)
        val out = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, out.ptr)
        CFRelease(query)
        if (status == errSecItemNotFound || status != errSecSuccess) return null
        val dataRef = out.value ?: return null
        stringFromCfData(dataRef.reinterpret())
    }

    override fun write(key: String, value: String) {
        memScoped {
            val data = cfDataFromString(value)
            val updateQuery = buildBaseQuery(account = key, returnData = false)
            val attrs = cfDictionaryOf(
                kSecValueData to data
            )
            val updateStatus = SecItemUpdate(updateQuery, attrs)
            CFRelease(attrs)
            if (updateStatus == errSecSuccess) {
                CFRelease(updateQuery)
                CFRelease(data)
                return
            }
            if (updateStatus != errSecItemNotFound) {
                SecItemDelete(updateQuery)
            }
            CFRelease(updateQuery)

            val serviceRef = cfString(service)
            val accountRef = cfString(key)
            val addQuery = cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to serviceRef,
                kSecAttrAccount to accountRef,
                kSecValueData to data,
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            )
            CFRelease(serviceRef)
            CFRelease(accountRef)
            SecItemAdd(addQuery, null)
            CFRelease(addQuery)
            CFRelease(data)
        }
    }

    override fun remove(key: String) {
        memScoped {
            val query = buildBaseQuery(account = key, returnData = false)
            SecItemDelete(query)
            CFRelease(query)
        }
    }

    private fun MemScope.buildBaseQuery(account: String, returnData: Boolean): CFDictionaryRef {
        val serviceRef = cfString(service)
        val accountRef = cfString(account)
        val dict = if (returnData) {
            cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to serviceRef,
                kSecAttrAccount to accountRef,
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne
            )
        } else {
            cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to serviceRef,
                kSecAttrAccount to accountRef
            )
        }
        // Dictionary retains values; balance Create from cfString.
        CFRelease(serviceRef)
        CFRelease(accountRef)
        return dict
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun cfString(value: String): CFStringRef =
    CFStringCreateWithCString(kCFAllocatorDefault, value, kCFStringEncodingUTF8)
        ?: error("CFStringCreateWithCString failed")

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.cfDataFromString(value: String): CFDataRef {
    val bytes = value.encodeToByteArray()
    return bytes.usePinned { pinned ->
        CFDataCreate(
            kCFAllocatorDefault,
            pinned.addressOf(0).reinterpret(),
            bytes.size.convert()
        ) ?: error("CFDataCreate failed")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun stringFromCfData(data: CFDataRef): String? {
    val length = CFDataGetLength(data).toInt()
    if (length <= 0) return ""
    val src = CFDataGetBytePtr(data) ?: return null
    val bytes = ByteArray(length)
    bytes.usePinned { dst ->
        memcpy(dst.addressOf(0), src, length.convert())
    }
    return bytes.decodeToString()
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.cfDictionaryOf(vararg pairs: Pair<CFTypeRef?, CFTypeRef?>): CFDictionaryRef {
    val count = pairs.size
    val keys = allocArray<CFTypeRefVar>(count)
    val values = allocArray<CFTypeRefVar>(count)
    pairs.forEachIndexed { index, (key, value) ->
        keys[index] = key
        values[index] = value
    }
    return CFDictionaryCreate(
        kCFAllocatorDefault,
        keys.reinterpret(),
        values.reinterpret(),
        count.convert(),
        kCFTypeDictionaryKeyCallBacks.ptr,
        kCFTypeDictionaryValueCallBacks.ptr
    ) ?: error("CFDictionaryCreate failed")
}
