package com.comunidapp.shared.poc

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.comunidapp.shared.auth.IosSupabaseConfigReader
import com.comunidapp.shared.auth.createSecureSessionStorage
import com.comunidapp.shared.poc.m08.platform.IosImagePicker
import com.comunidapp.shared.remote.SharedRemoteRuntime
import com.comunidapp.shared.vertical.LeoVerSharedApp
import platform.UIKit.UIViewController

/**
 * Host iOS KMP-10:
 * SESSION / PROFILE / PETS / LOST_FOUND / ADOPTIONS = REAL_REMOTE
 * LOST/FOUND PUBLISH + MEDIA WRITE/READ = REAL_REMOTE
 * (un solo SharedRemoteRuntime / SupabaseClient + Storage + MediaResolver).
 */
fun PocIosViewController(): UIViewController =
    ComposeUIViewController {
        MaterialTheme {
            var showLegacyPocs by remember { mutableStateOf(false) }
            val imagePicker = remember { IosImagePicker() }
            val runtime = remember {
                SharedRemoteRuntime.create(
                    config = IosSupabaseConfigReader.read(),
                    storage = createSecureSessionStorage()
                )
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
                    authRepository = runtime.authRepository,
                    mediaResolver = runtime.mediaResolver,
                    imagePicker = imagePicker,
                    onOpenLegacyPocs = { showLegacyPocs = true }
                )
            }
        }
    }
