package com.comunidapp.shared.poc

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.comunidapp.shared.home.FakeSharedPetHomeRepository
import com.comunidapp.shared.home.SharedHomeScreen
import com.comunidapp.shared.home.SharedSessionStub
import com.comunidapp.shared.poc.m08.platform.IosImagePicker
import platform.UIKit.UIViewController

/**
 * Thin UIKit entry for the iOS POC host.
 * Starts on shared Home (domain pets), then can open Compose POC launcher.
 */
fun PocIosViewController(): UIViewController =
    ComposeUIViewController {
        MaterialTheme {
            var showPocs by remember { mutableStateOf(false) }
            val imagePicker = remember { IosImagePicker() }
            if (showPocs) {
                PocLauncherApp(
                    imagePicker = imagePicker,
                    onClose = { showPocs = false }
                )
            } else {
                SharedHomeScreen(
                    repository = remember { FakeSharedPetHomeRepository() },
                    session = SharedSessionStub.demoAuthenticated(),
                    onOpenPocLauncher = { showPocs = true }
                )
            }
        }
    }
