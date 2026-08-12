package com.comunidapp.shared.poc

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.ComposeUIViewController
import com.comunidapp.shared.poc.m08.platform.IosImagePicker
import platform.UIKit.UIViewController

/**
 * Thin UIKit entry for the iOS POC host.
 * Swift only wraps this controller — no duplicated POC UI.
 */
fun PocIosViewController(): UIViewController =
    ComposeUIViewController {
        MaterialTheme {
            PocLauncherApp(imagePicker = IosImagePicker())
        }
    }
