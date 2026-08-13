package com.comunidapp.shared.poc

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.comunidapp.shared.adoption.FakeAdoptionRepository
import com.comunidapp.shared.auth.IosSupabaseConfigReader
import com.comunidapp.shared.auth.createAuthRepository
import com.comunidapp.shared.auth.createSecureSessionStorage
import com.comunidapp.shared.lostfound.FakeLostFoundRepository
import com.comunidapp.shared.pets.FakeSharedPetsRepository
import com.comunidapp.shared.poc.m08.platform.IosImagePicker
import com.comunidapp.shared.profile.FakeUserProfileRepository
import com.comunidapp.shared.vertical.LeoVerSharedApp
import platform.UIKit.UIViewController

/**
 * Host iOS: SESSION = REAL_REMOTE (supabase-kt + Keychain).
 * Perfil / mascotas / LF / adopciones: SHARED_FAKE (intencional KMP-5).
 */
fun PocIosViewController(): UIViewController =
    ComposeUIViewController {
        MaterialTheme {
            var showLegacyPocs by remember { mutableStateOf(false) }
            val imagePicker = remember { IosImagePicker() }
            val authRepository = remember {
                createAuthRepository(
                    config = IosSupabaseConfigReader.read(),
                    storage = createSecureSessionStorage()
                )
            }
            val profileRepository = remember { FakeUserProfileRepository() }
            val petsRepository = remember { FakeSharedPetsRepository() }
            val lostFoundRepository = remember { FakeLostFoundRepository() }
            val adoptionRepository = remember { FakeAdoptionRepository() }

            if (showLegacyPocs) {
                PocLauncherApp(
                    imagePicker = imagePicker,
                    onClose = { showLegacyPocs = false }
                )
            } else {
                LeoVerSharedApp(
                    sessionRepository = authRepository,
                    profileRepository = profileRepository,
                    petsRepository = petsRepository,
                    lostFoundRepository = lostFoundRepository,
                    adoptionRepository = adoptionRepository,
                    authRepository = authRepository,
                    onOpenLegacyPocs = { showLegacyPocs = true }
                )
            }
        }
    }
