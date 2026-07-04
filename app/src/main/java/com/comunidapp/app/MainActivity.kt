package com.comunidapp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.navigation.ComunidappNavGraph
import com.comunidapp.app.ui.theme.ComunidappTheme
import com.comunidapp.app.viewmodel.SessionState
import com.comunidapp.app.viewmodel.SessionViewModel

class MainActivity : ComponentActivity() {

    private var keepSplashScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { keepSplashScreen }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComunidappTheme {
                val sessionViewModel: SessionViewModel = viewModel()
                val sessionState by sessionViewModel.sessionState.collectAsState()
                keepSplashScreen = sessionState == SessionState.Loading
                ComunidappNavGraph(sessionViewModel = sessionViewModel)
            }
        }
    }
}
