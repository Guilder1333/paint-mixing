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
    var error by remember { mutableStateOf<String?>(null) }
    val liveSettings by controller.liveMeteredSettings.collectAsState()
    val previewView = remember { PreviewView(context) }

    DisposableEffect(Unit) {
        onDispose { controller.unbind() }
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
                    val settings = liveSettings
                    Text(
                        if (settings == null) {
                            "Metering..."
                        } else {
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
                        onClick = {
                            val toLock = liveSettings ?: return@Button
                            capturing = true
                            error = null
                            scope.launch {
                                try {
                                    val dir = File(context.filesDir, "palettes").apply { mkdirs() }
                                    val file = File(dir, "${UUID.randomUUID()}.jpg")
                                    val used = controller.lockAndCapture(toLock, file)
                                    pending.imagePath = file.absolutePath
                                    pending.capture = used
                                    onCaptured()
                                } catch (e: Exception) {
                                    error = "Capture failed: ${e.message}"
                                } finally {
                                    capturing = false
                                }
                            }
                        }
                    ) { Text(if (capturing) "Capturing..." else "Shutter") }

                    OutlinedButton(onClick = onBack) { Text("Back") }
                }
            }
        }
    }
}
