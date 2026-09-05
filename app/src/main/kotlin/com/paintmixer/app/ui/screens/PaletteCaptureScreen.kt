package com.paintmixer.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.paintmixer.app.capture.CameraController
import com.paintmixer.app.capture.PendingPaletteCapture
import com.paintmixer.app.capture.RemoteShutterController
import com.paintmixer.app.capture.withLinearExposureBoost
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * PLAN.md section 5, screen 2: camera preview, shutter, current manual
 * settings shown for verification. There's no separate "meter" step --
 * the camera runs normal auto 3A the whole time the preview is up, and
 * Shutter freezes+locks whatever was last observed and shoots with it
 * (PLAN.md section 4.1). That locked reading is what gets persisted and
 * replayed for every target shot against this palette.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaletteCaptureScreen(
    pending: PendingPaletteCapture,
    remoteShutter: RemoteShutterController,
    onCaptured: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val controller = remember { CameraController(context) }
    var capturing by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val liveSettings by controller.liveMeteredSettings.collectAsState()
    val previewView = remember { PreviewView(context) }

    DisposableEffect(Unit) {
        remoteShutter.registerListener()
        onDispose {
            remoteShutter.unregisterListener()
            controller.unbind()
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            try {
                controller.bind(lifecycleOwner, previewView)
            } catch (e: Exception) {
                error = "Camera failed to start: ${e.message}"
            }
        }
    }

    fun shutter(delaySeconds: Int) {
        // Metering targets a normally tonemapped (brightness-boosted) JPEG; the identity tonemap
        // this app actually shoots with removes that boost, so the metered exposure needs
        // compensating before it's locked in -- otherwise the linear capture comes out needlessly
        // dark (confirmed: white read ~90/255 without this). See Camera2ManualOptions.kt.
        val toLock = liveSettings?.withLinearExposureBoost() ?: return
        if (capturing) return
        capturing = true
        error = null
        scope.launch {
            try {
                // Lock exposure/WB immediately (so they stop drifting), then optionally give the
                // phone a few seconds to go still before the shutter actually fires -- the
                // repeatability test showed hand-shake right at button-press was a real noise
                // source. See PLAN.md Phase 2. Both options are offered since a delay isn't
                // needed (just slower) once a hands-off trigger is actually in use.
                controller.lock(toLock)
                for (remaining in delaySeconds downTo 1) {
                    countdown = remaining
                    delay(1000)
                }
                countdown = null

                val dir = File(context.filesDir, "palettes").apply { mkdirs() }
                val file = File(dir, "${UUID.randomUUID()}.jpg")
                controller.shootLocked(file)
                pending.imagePath = file.absolutePath
                pending.capture = toLock.copy(linearTonemap = true, manualControlUsed = true)
                onCaptured()
            } catch (e: Exception) {
                error = "Capture failed: ${e.message}"
            } finally {
                capturing = false
                countdown = null
            }
        }
    }

    LaunchedEffect(Unit) {
        // No delay for a remote trigger -- the point of one is that nothing touches the phone,
        // so there's nothing for the timer to wait out.
        remoteShutter.events.collect { shutter(delaySeconds = 0) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Capture Palette") }) }) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (!hasCameraPermission) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Camera permission is required to photograph the palette.")
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant camera permission")
                    }
                    OutlinedButton(onClick = onBack) { Text("Back") }
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { previewView }
                )

                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val settings = liveSettings?.withLinearExposureBoost()
                    Text(
                        if (settings == null) {
                            "Metering..."
                        } else {
                            // These are what Shutter will actually lock in -- exposure already
                            // includes the boost that compensates for the identity tonemap curve
                            // (see Camera2ManualOptions.kt), not the raw metered reading.
                            "Exposure: ${settings.exposureTimeNs} ns   ISO: ${settings.iso}\n" +
                                "AWB gains: R=${"%.2f".format(settings.awbGainR)} " +
                                "Geven=${"%.2f".format(settings.awbGainGEven)} " +
                                "Godd=${"%.2f".format(settings.awbGainGOdd)} " +
                                "B=${"%.2f".format(settings.awbGainB)}"
                        }
                    )
                    error?.let { Text(it) }

                    Button(
                        enabled = liveSettings != null && !capturing,
                        onClick = { shutter(SELF_TIMER_SECONDS) }
                    ) {
                        Text(
                            when {
                                countdown != null -> "Hold still... $countdown"
                                capturing -> "Capturing..."
                                else -> "Shutter (${SELF_TIMER_SECONDS}s self-timer)"
                            }
                        )
                    }
                    OutlinedButton(
                        enabled = liveSettings != null && !capturing,
                        onClick = { shutter(0) }
                    ) {
                        Text(if (capturing) "Capturing..." else "Shutter (no delay)")
                    }
                    Text("A Bluetooth remote (volume-key click) fires the no-delay version -- nothing touches the phone, so there's nothing for a timer to wait out.")

                    OutlinedButton(onClick = onBack) { Text("Back") }
                }
            }
        }
    }
}

/** Seconds between locking exposure and the shutter actually firing -- lets hand-shake settle. */
private const val SELF_TIMER_SECONDS = 3
