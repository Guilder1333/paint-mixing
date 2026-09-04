package com.paintmixer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.paintmixer.app.ui.nav.PaintMixerNavHost
import com.paintmixer.app.ui.theme.PaintMixerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = (application as PaintMixerApp).container.database
        setContent {
            PaintMixerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PaintMixerNavHost(database = database)
                }
            }
        }
    }
}
