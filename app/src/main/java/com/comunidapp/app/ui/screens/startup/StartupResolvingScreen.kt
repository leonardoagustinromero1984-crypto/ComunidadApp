package com.comunidapp.app.ui.screens.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.BrandGreen

/**
 * Neutral gate while session, tutorial and active context resolve.
 * No feed, no user data, no home composition.
 */
@Composable
fun StartupResolvingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandBackground),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = BrandGreen)
    }
}
