package com.comunidapp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.comunidapp.app.navigation.ComunidappNavGraph
import com.comunidapp.app.ui.theme.ComunidappTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComunidappTheme {
                ComunidappNavGraph()
            }
        }
    }
}
