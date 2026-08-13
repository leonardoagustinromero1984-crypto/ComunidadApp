package com.comunidapp.shared.poc

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.comunidapp.shared.pets.FakeSharedPetsRepository
import com.comunidapp.shared.profile.FakeUserProfileRepository
import com.comunidapp.shared.session.FakeSessionRepository
import com.comunidapp.shared.poc.m08.platform.IosImagePicker
import com.comunidapp.shared.vertical.LeoVerSharedApp
import platform.UIKit.UIViewController

/**
 * Host iOS: vertical sesión/perfil/mascotas (SHARED_FAKE + SESSION_STUB).
 * POCs legacy quedan como herramientas de desarrollo.
 */
fun PocIosViewController(): UIViewController =
    ComposeUIViewController {
        MaterialTheme {
            var showLegacyPocs by remember { mutableStateOf(false) }
            val imagePicker = remember { IosImagePicker() }
            val sessionRepository = remember { FakeSessionRepository() }
            val profileRepository = remember { FakeUserProfileRepository() }
            val petsRepository = remember { FakeSharedPetsRepository() }

            if (showLegacyPocs) {
                PocLauncherApp(
                    imagePicker = imagePicker,
                    onClose = { showLegacyPocs = false }
                )
            } else {
                LeoVerSharedApp(
                    sessionRepository = sessionRepository,
                    profileRepository = profileRepository,
                    petsRepository = petsRepository,
                    onOpenLegacyPocs = { showLegacyPocs = true }
                )
            }
        }
    }
