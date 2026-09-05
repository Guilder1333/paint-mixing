package com.paintmixer.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.paintmixer.app.capture.RemoteShutterController
import com.paintmixer.app.capture.ShutterAccessibilityService

/**
 * Debug tool: shows, live, exactly what arrives when a hardware button is
 * pressed -- every key event `MainActivity.dispatchKeyEvent` sees and every
 * key `ShutterAccessibilityService` intercepts -- whether or not this app
 * recognises or consumes it. Exists because plain `dispatchKeyEvent` received
 * NOTHING at all on the target device (not even the phone's own volume
 * keys); this replaces guessing further with actually seeing what the
 * platform delivers.
 *
 * (An earlier version also logged a `MediaSessionCompat`-based path for a
 * Bluetooth headset's play/pause button; that approach was rolled back
 * after proving unreliable across several attempts -- see PLAN.md Phase 2.)
 *
 * Registers itself as a shutter listener (so a real press is also reported
 * as "CONSUMED"), but takes no photo -- no camera involved here at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteDiagnosticsScreen(remoteShutter: RemoteShutterController, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val log by remoteShutter.diagnostics.collectAsState()
    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    DisposableEffect(Unit) {
        remoteShutter.registerListener()
        onDispose { remoteShutter.unregisterListener() }
    }

    // There's no direct callback for "the user changed this in Settings and came back" -- re-check
    // on every resume, which covers returning from the Accessibility settings shortcut below.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Remote Trigger Diagnostics") }) }) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Press the volume keys, a Bluetooth remote, and the headset play button one at " +
                    "a time. Every signal that reaches this app shows up below, whether or not " +
                    "it did anything -- including ones this app doesn't recognise."
            )
            Text(
                "Accessibility shutter service: " + if (accessibilityEnabled) "ENABLED" else "not enabled"
            )
            if (!accessibilityEnabled) {
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text("Open Accessibility settings")
                }
                Text("Find \"Paint Mixer shutter\" in the list and turn it on, then come back to this screen.")
            }

            if (log.isEmpty()) {
                Text("(nothing yet)")
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(log) { line -> Text(line) }
            }
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
    }
}

private fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val expected = "${context.packageName}/${ShutterAccessibilityService::class.java.name}"
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}
