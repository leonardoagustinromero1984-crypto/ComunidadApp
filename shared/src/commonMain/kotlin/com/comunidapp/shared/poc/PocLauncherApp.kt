package com.comunidapp.shared.poc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comunidapp.shared.poc.m08.M08PocGraph
import com.comunidapp.shared.poc.m08.platform.ImagePicker
import com.comunidapp.shared.poc.m08.ui.M08PocApp
import com.comunidapp.shared.poc.m22.M22PocGraph
import com.comunidapp.shared.poc.m22.ui.M22PocApp

private enum class PocSelection {
    Launcher,
    M22,
    M08
}

/**
 * Minimal shared POC selector — Compose only.
 * Platform hosts supply [imagePicker]; Swift/UIKit stays a thin shell.
 */
@Composable
fun PocLauncherApp(imagePicker: ImagePicker) {
    var selection by remember { mutableStateOf(PocSelection.Launcher) }
    when (selection) {
        PocSelection.Launcher -> PocLauncherScreen(
            onOpenM22 = { selection = PocSelection.M22 },
            onOpenM08 = { selection = PocSelection.M08 }
        )
        PocSelection.M22 -> M22PocApp(
            repository = M22PocGraph.repository(null),
            onClose = { selection = PocSelection.Launcher }
        )
        PocSelection.M08 -> M08PocApp(
            repository = M08PocGraph.repository(),
            imagePicker = imagePicker,
            onClose = { selection = PocSelection.Launcher }
        )
    }
}

@Composable
private fun PocLauncherScreen(
    onOpenM22: () -> Unit,
    onOpenM08: () -> Unit
) {
    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "LeoVer KMP POC",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Host iOS mínimo · UI compartida Compose",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onOpenM22,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("M22 Catalog POC")
            }
            Button(
                onClick = onOpenM08,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("M08 Media POC")
            }
        }
    }
}
