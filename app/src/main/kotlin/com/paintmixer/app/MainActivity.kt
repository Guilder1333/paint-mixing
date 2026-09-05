package com.paintmixer.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.paintmixer.app.ui.nav.PaintMixerNavHost
import com.paintmixer.app.ui.theme.PaintMixerTheme

class MainActivity : ComponentActivity() {

    private val remoteShutter get() = (application as PaintMixerApp).remoteShutter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = (application as PaintMixerApp).container.database
        setContent {
            PaintMixerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PaintMixerNavHost(database = database, remoteShutter = remoteShutter)
                }
            }
        }
    }

    /**
     * Intercepted here (not `onKeyDown`) so this gets first refusal on the event, ahead of the
     * view hierarchy. EVERY key event is logged to [remoteShutter]'s diagnostics -- see
     * `RemoteDiagnosticsScreen`.
     *
     * Only volume/camera keys are handled here -- the de facto convention a cheap Bluetooth
     * shutter remote uses. A Bluetooth headset's play/pause button turned out to need a very
     * different (and, on the target device, ultimately unreliable) mechanism entirely --
     * MediaSession-based media-button routing, not ordinary key dispatch -- and was rolled back;
     * see PLAN.md Phase 2 for the full trail if that gets revisited.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            remoteShutter.log("dispatchKeyEvent: ${KeyEvent.keyCodeToString(event.keyCode)}")
        }
        if (event.action == KeyEvent.ACTION_DOWN && isRemoteShutterKey(event.keyCode)) {
            if (remoteShutter.tryTrigger("dispatchKeyEvent:${KeyEvent.keyCodeToString(event.keyCode)}")) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isRemoteShutterKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_CAMERA -> true
        else -> false
    }
}
