package com.comunidapp.shared.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
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

/**
 * Keychain iOS — tokens de sesión. Nunca NSUserDefaults.
 */
actual fun createSecureSessionStorage(): SecureSessionStorage = IosKeychainSecureSessionStorage()

@OptIn(ExperimentalForeignApi::class)
class IosKeychainSecureSessionStorage(
    private val service: String = "com.leover.shared.auth.session"
) : SecureSessionStorage {

    override fun read(key: String): String? {
        val query = nsQuery(account = key, returnData = true)
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query.asCFDictionary(), result.ptr)
            if (status == errSecItemNotFound) return null
            if (status != errSecSuccess) return null
            val data = result.value as? NSData ?: return null
            return NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
        }
    }

    override fun write(key: String, value: String) {
        val payload = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val updateQuery = nsQuery(account = key, returnData = false)
        val attrs = NSMutableDictionary().apply {
            setObject(payload, forKey = castKey(kSecValueData))
        }
        val updateStatus = SecItemUpdate(updateQuery.asCFDictionary(), attrs.asCFDictionary())
        if (updateStatus == errSecSuccess) return
        if (updateStatus != errSecItemNotFound) {
            SecItemDelete(updateQuery.asCFDictionary())
        }
        val add = nsQuery(account = key, returnData = false).apply {
            setObject(payload, forKey = castKey(kSecValueData))
            setObject(
                kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                forKey = castKey(kSecAttrAccessible)
            )
        }
        SecItemAdd(add.asCFDictionary(), null)
    }

    override fun remove(key: String) {
        SecItemDelete(nsQuery(account = key, returnData = false).asCFDictionary())
    }

    private fun nsQuery(account: String, returnData: Boolean): NSMutableDictionary {
        val dict = NSMutableDictionary()
        dict.setObject(kSecClassGenericPassword, forKey = castKey(kSecClass))
        dict.setObject(service, forKey = castKey(kSecAttrService))
        dict.setObject(account, forKey = castKey(kSecAttrAccount))
        if (returnData) {
            dict.setObject(true, forKey = castKey(kSecReturnData))
            dict.setObject(kSecMatchLimitOne, forKey = castKey(kSecMatchLimit))
        }
        return dict
    }
}

@OptIn(ExperimentalForeignApi::class)
@Suppress("UNCHECKED_CAST")
private fun castKey(key: Any?): platform.Foundation.NSCopyingProtocol =
    key as platform.Foundation.NSCopyingProtocol

@OptIn(ExperimentalForeignApi::class)
@Suppress("UNCHECKED_CAST")
private fun NSMutableDictionary.asCFDictionary(): CFDictionaryRef = this as CFDictionaryRef
