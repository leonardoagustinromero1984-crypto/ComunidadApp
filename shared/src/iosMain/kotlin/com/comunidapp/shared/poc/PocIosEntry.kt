package com.comunidapp.shared.poc

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.comunidapp.shared.auth.AppleSignInIosController
import com.comunidapp.shared.auth.IosSupabaseConfigReader
import com.comunidapp.shared.auth.createSecureSessionStorage
import com.comunidapp.shared.deeplink.DeepLinkNavigationController
import com.comunidapp.shared.deeplink.DeepLinkPendingStore
import com.comunidapp.shared.deeplink.IosDeepLinkBridge
import com.comunidapp.shared.deeplink.NotificationIntentParser
import com.comunidapp.shared.poc.m08.platform.IosImagePicker
import com.comunidapp.shared.push.IosPushRegistrationCoordinator
import com.comunidapp.shared.remote.SharedRemoteRuntime
import com.comunidapp.shared.vertical.LeoVerSharedApp
import kotlinx.coroutines.flow.collect
import platform.UIKit.UIViewController

/**
 * Host iOS KMP-17/18/19:
 * Deep links + Apple Sign In + APNs foundation.
 * Un solo SharedRemoteRuntime / SupabaseClient.
 */
fun PocIosViewController(): UIViewController =
    ComposeUIViewController {
        MaterialTheme {
            var showLegacyPocs by remember { mutableStateOf(false) }
            val imagePicker = remember { IosImagePicker() }
            val deepLinkController = remember { DeepLinkNavigationController() }
            val appleSignIn = remember { AppleSignInIosController() }
            val pushCoordinator = remember { IosPushRegistrationCoordinator() }
            val runtime = remember {
                SharedRemoteRuntime.create(
                    config = IosSupabaseConfigReader.read(),
                    storage = createSecureSessionStorage()
                )
            }

            LaunchedEffect(deepLinkController) {
                IosDeepLinkBridge.observe().collect { url ->
                    if (!url.isNullOrBlank()) {
                        deepLinkController.offerRawUrl(url)
                        IosDeepLinkBridge.consume()
                    }
                }
            }

            // Pending notification extras (set via offerNotificationDeepLink) → controller
            LaunchedEffect(Unit) {
                val pending = DeepLinkPendingStore.peek()
                if (pending != null) {
                    deepLinkController.offer(pending)
                }
            }

            if (showLegacyPocs) {
                PocLauncherApp(
                    imagePicker = imagePicker,
                    onClose = { showLegacyPocs = false }
                )
            } else {
                LeoVerSharedApp(
                    sessionRepository = runtime.authRepository,
                    profileRepository = runtime.profileRepository,
                    petsRepository = runtime.petsRepository,
                    lostFoundRepository = runtime.lostFoundRepository,
                    adoptionRepository = runtime.adoptionRepository,
                    adoptionApplicationRepository = runtime.adoptionApplicationRepository,
                    publicContentRepository = runtime.publicContentRepository,
                    authRepository = runtime.authRepository,
                    mediaResolver = runtime.mediaResolver,
                    imagePicker = imagePicker,
                    deepLinkController = deepLinkController,
                    pushInstallationRepository = runtime.pushInstallationRepository,
                    pushRegistrationCoordinator = pushCoordinator,
                    notificationPreferencesRepository = runtime.notificationPreferencesRepository,
                    appleSignInController = appleSignIn,
                    onOpenLegacyPocs = { showLegacyPocs = true }
                )
            }
        }
    }

/**
 * Helper for Swift notification tap → typed deep link.
 */
fun offerIosNotificationExtras(deepLinkType: String?, resourceId: String?) {
    val target = NotificationIntentParser.fromPushExtras(deepLinkType, resourceId)
    DeepLinkPendingStore.set(target)
}
