package com.amerthamer.tramagent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.amerthamer.tramagent.ui.nav.AppNavHost
import com.amerthamer.tramagent.ui.theme.TramAgentTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Sötét téma: világos ikonok a rendszersávokon
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            TramAgentTheme {
                AppNavHost()
            }
        }
    }
}
