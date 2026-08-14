package com.comunidapp.shared.auth

import com.comunidapp.shared.crypto.sha256Hex
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume

actual fun isAppleSignInAvailable(): Boolean = true

/**
 * Sign in with Apple vía AuthenticationServices (Kotlin/Native).
 * Requiere capability en device real — sim unsigned puede fallar → ConfigurationRequired.
 */
class AppleSignInIosController : AppleSignInController {
    override suspend fun requestCredential(): AppleSignInPlatformResult {
        return try {
            val rawNonce = generateNonceHex(32)
            val hashedNonce = sha256Hex(rawNonce.encodeToByteArray())
            suspendCancellableCoroutine { cont ->
                val provider = ASAuthorizationAppleIDProvider()
                val request = provider.createRequest()
                request.requestedScopes = listOf(ASAuthorizationScopeFullName, ASAuthorizationScopeEmail)
                request.nonce = hashedNonce

                val delegate = AppleAuthDelegate { result ->
                    if (cont.isActive) cont.resume(result)
                }
                val controller = ASAuthorizationController(authorizationRequests = listOf(request))
                controller.delegate = delegate
                controller.presentationContextProvider = delegate
                delegate.rawNonce = rawNonce
                controller.performRequests()
            }
        } catch (t: Throwable) {
            AppleSignInPlatformResult.Failed(
                t.message?.take(80) ?: "APPLE_SIGN_IN_FAILED"
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun generateNonceHex(byteCount: Int): String {
    val bytes = ByteArray(byteCount)
    bytes.usePinned { pinned ->
        val status = SecRandomCopyBytes(kSecRandomDefault, byteCount.toULong(), pinned.addressOf(0))
        if (status != errSecSuccess) {
            // Fallback deterministic-ish scramble if SecRandom fails (should be rare).
            for (i in bytes.indices) {
                bytes[i] = (i * 31 + 17).toByte()
            }
        }
    }
    return bytes.joinToString("") { b ->
        val v = b.toInt() and 0xff
        v.toString(16).padStart(2, '0')
    }
}

@OptIn(ExperimentalForeignApi::class)
private class AppleAuthDelegate(
    private val onResult: (AppleSignInPlatformResult) -> Unit
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    var rawNonce: String? = null
    private var finished = false

    override fun presentationAnchorForAuthorizationController(
        controller: ASAuthorizationController
    ): UIWindow {
        val windows = UIApplication.sharedApplication.windows
        val key = windows?.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow
        return key ?: (windows?.firstOrNull() as? UIWindow) ?: UIWindow()
    }

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization
    ) {
        if (finished) return
        finished = true
        val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        if (credential == null) {
            onResult(AppleSignInPlatformResult.Failed("APPLE_CREDENTIAL_MISSING"))
            return
        }
        val tokenData = credential.identityToken
        if (tokenData == null) {
            onResult(AppleSignInPlatformResult.Failed("APPLE_ID_TOKEN_MISSING"))
            return
        }
        val idToken = NSString.create(tokenData, NSUTF8StringEncoding)?.toString()
        if (idToken.isNullOrBlank()) {
            onResult(AppleSignInPlatformResult.Failed("APPLE_ID_TOKEN_MISSING"))
            return
        }
        onResult(AppleSignInPlatformResult.Success(idToken = idToken, rawNonce = rawNonce))
    }

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError: NSError
    ) {
        if (finished) return
        finished = true
        val code = didCompleteWithError.code
        // ASAuthorizationErrorCanceled = 1001
        if (code == 1001L) {
            onResult(AppleSignInPlatformResult.Cancelled)
        } else {
            onResult(
                AppleSignInPlatformResult.Failed(
                    didCompleteWithError.localizedDescription.take(80)
                )
            )
        }
    }
}

/**
 * Bridge opcional Swift → Kotlin si se prefiere ASAuthorization en Swift.
 */
object AppleAuthBridge {
    private var handler: ((String /* hashedNonce */, (String?, String?, String?) -> Unit) -> Unit)? = null

    fun registerSwiftHandler(
        handler: (hashedNonce: String, callback: (idToken: String?, rawNonce: String?, error: String?) -> Unit) -> Unit
    ) {
        this.handler = handler
    }

    suspend fun requestViaSwift(rawNonce: String, hashedNonce: String): AppleSignInPlatformResult {
        val h = handler ?: return AppleSignInPlatformResult.ConfigurationRequired
        return suspendCancellableCoroutine { cont ->
            h(hashedNonce) { idToken, returnedNonce, error ->
                val result = when {
                    error == "cancelled" -> AppleSignInPlatformResult.Cancelled
                    !idToken.isNullOrBlank() ->
                        AppleSignInPlatformResult.Success(idToken, returnedNonce ?: rawNonce)
                    else -> AppleSignInPlatformResult.Failed(error ?: "APPLE_SIGN_IN_FAILED")
                }
                if (cont.isActive) cont.resume(result)
            }
        }
    }
}
