package com.comunidapp.shared.poc

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.comunidapp.shared.adoption.FakeAdoptionRepository
import com.comunidapp.shared.auth.IosSupabaseConfigReader
import com.comunidapp.shared.auth.createSecureSessionStorage
import com.comunidapp.shared.lostfound.FakeLostFoundRepository
import com.comunidapp.shared.poc.m08.platform.IosImagePicker
import com.comunidapp.shared.remote.SharedRemoteRuntime
import com.comunidapp.shared.vertical.LeoVerSharedApp
import platform.UIKit.UIViewController

/**
 * Host iOS KMP-6:
 * SESSION / PROFILE / PETS = REAL_REMOTE (un solo SharedRemoteRuntime).
 * LOST_FOUND / ADOPTIONS = SHARED_FAKE.
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
            val lostFoundRepository = remember { FakeLostFoundRepository() }
            val adoptionRepository = remember { FakeAdoptionRepository() }

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
                    lostFoundRepository = lostFoundRepository,
                    adoptionRepository = adoptionRepository,
                    authRepository = runtime.authRepository,
                    onOpenLegacyPocs = { showLegacyPocs = true }
                )
            }
        }
    }
