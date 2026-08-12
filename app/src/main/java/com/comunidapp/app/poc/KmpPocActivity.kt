package com.comunidapp.app.poc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.comunidapp.shared.poc.m08.M08PocGraph
import com.comunidapp.shared.poc.m08.platform.rememberAndroidImagePicker
import com.comunidapp.shared.poc.m08.ui.M08PocApp

/**
 * Isolated Android host for KMP POC 2 (navigation + image picker).
 * Does not alter ComunidappNavGraph / production navigation.
 * POC 1 (M22) sources remain in :shared for comparison.
 */
class KmpPocActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = M08PocGraph.repository()
        setContent {
            MaterialTheme {
                val picker = rememberAndroidImagePicker()
                M08PocApp(
                    repository = repository,
                    imagePicker = picker,
                    onClose = { finish() }
                )
            }
        }
    }
}
