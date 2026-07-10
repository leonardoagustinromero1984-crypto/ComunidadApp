package com.comunidapp.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.navigation.ComunidappNavGraph
import com.comunidapp.app.notifications.PushTokenRegistrar
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.viewmodel.SessionState
import com.comunidapp.app.viewmodel.SessionViewModel
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var keepSplashScreen = true

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            lifecycleScope.launch { PushTokenRegistrar.syncCurrentToken() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { keepSplashScreen }
        super.onCreate(savedInstanceState)
        handleAuthDeepLink(intent)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            ComunidappTheme {
                val sessionViewModel: SessionViewModel = viewModel()
                val sessionState by sessionViewModel.sessionState.collectAsState()
                keepSplashScreen = sessionState == SessionState.Loading
                LaunchedEffect(sessionState) {
                    if (sessionState == SessionState.LoggedIn) {
                        PushTokenRegistrar.syncCurrentToken()
                    }
                }
                ComunidappNavGraph(sessionViewModel = sessionViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeepLink(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleAuthDeepLink(intent: Intent?) {
        if (!BuildConfig.SUPABASE_ENABLED || intent == null) return
        if (intent.data?.scheme == com.comunidapp.app.data.remote.supabase.SupabaseAuthConfig.SCHEME) {
            supabase.handleDeeplinks(intent)
        }
    }
}
