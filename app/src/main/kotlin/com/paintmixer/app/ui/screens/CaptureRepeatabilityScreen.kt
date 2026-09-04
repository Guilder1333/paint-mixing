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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.paintmixer.app.capture.ImageSampling
import com.paintmixer.app.data.Palette
import com.paintmixer.app.data.PaletteDao
import com.paintmixer.core.color.DeltaE
import com.paintmixer.core.color.Lab
import com.paintmixer.core.color.LinearRgb
import com.paintmixer.core.color.WhiteBalance
import com.paintmixer.core.color.toLab
import com.paintmixer.core.color.toXyz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Debug tool for PLAN.md Phase 2's acceptance test: "photograph the same
 * static scene 10 times across a session, sample the same patch in each,
 * and confirm the spread in normalised Lab is small (max Delta-E between
 * any two shots < 1.5)". Replays the most recently saved palette's
 * [com.paintmixer.app.data.CaptureSettings] verbatim (no metering) and
 * always samples the frame centre, so "the same patch" is guaranteed
 * without relying on tapping the identical pixel by hand each time.
 *
 * The second half of the test -- close and reopen the app, confirm replayed
 * settings reproduce the same values -- is exactly what persisting the
 * palette to Room and reloading it here proves: this screen doesn't hold
 * any settings itself, it always re-reads them from the database.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureRepeatabilityScreen(paletteDao: PaletteDao, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var palette by remember { mutableStateOf<Palette?>(null) }
    var whiteRefLinear by remember { mutableStateOf<LinearRgb?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val p = paletteDao.mostRecent()
        palette = p
        if (p == null) {
            loadError = "No saved palette yet -- create one first (Palettes > New palette)."
            return@LaunchedEffect
        }
        try {
            val bmp = withContext(Dispatchers.IO) { ImageSampling.decodeUpright(File(p.imagePath)) }
            val whiteSample = ImageSampling.samplePatch(bmp, p.whiteRefX, p.whiteRefY)
            whiteRefLinear = whiteSample.toLinearRgb(p.capture.linearTonemap)
        } catch (e: Exception) {
            loadError = "Could not load the palette's white reference: ${e.message}"
        }
    }

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
    val previewView = remember { PreviewView(context) }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val shots = remember { mutableStateListOf<Lab>() }

    DisposableEffect(Unit) {
        onDispose { controller.unbind() }
    }

    LaunchedEffect(hasCameraPermission, palette) {
        if (hasCameraPermission && palette != null) {
            try {
                controller.bind(lifecycleOwner, previewView)
            } catch (e: Exception) {
                error = "Camera failed to start: ${e.message}"
            }
        }
    }

    val maxDeltaE = remember(shots.size) {
        var max = 0.0
        for (i in shots.indices) {
            for (j in i + 1 until shots.size) {
                max = maxOf(max, DeltaE.ciede2000(shots[i], shots[j]))
            }
        }
        max
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Capture Repeatability Test") }) }) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            loadError?.let { Text(it, modifier = Modifier.padding(16.dp)) }

            val p = palette
            if (p != null && hasCameraPermission) {
                Text(
                    "Replaying: ${p.name} -- ${p.capture.exposureTimeNs}ns @ ISO ${p.capture.iso}",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { previewView })

                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    error?.let { Text(it) }
                    Text("Shots this session: ${shots.size}   Max ΔE spread: ${"%.3f".format(maxDeltaE)}")

                    Button(
                        enabled = !capturing && whiteRefLinear != null,
                        onClick = {
                            capturing = true
                            error = null
                            scope.launch {
                                try {
                                    val dir = File(context.filesDir, "repeatability").apply { mkdirs() }
                                    val file = File(dir, "${UUID.randomUUID()}.jpg")
                                    controller.lockAndCapture(p.capture, file)
                                    val bmp = withContext(Dispatchers.IO) { ImageSampling.decodeUpright(file) }
                                    val centerSample = ImageSampling.samplePatch(bmp, 0.5f, 0.5f)
                                    val linear = centerSample.toLinearRgb(p.capture.linearTonemap)
                                    val normalised = WhiteBalance.normalise(
                                        linear,
                                        whiteRefLinear!!,
                                        p.whiteRefReflectance.toDouble()
                                    )
                                    shots.add(normalised.toXyz().toLab())
                                } catch (e: Exception) {
                                    error = "Capture failed: ${e.message}"
                                } finally {
                                    capturing = false
                                }
                            }
                        }
                    ) { Text(if (capturing) "Capturing..." else "Shoot (samples the frame centre)") }

                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(shots.size) { i ->
                            val lab = shots[i]
                            Text("#${i + 1}: L=${"%.2f".format(lab.l)} a=${"%.2f".format(lab.a)} b=${"%.2f".format(lab.b)}")
                        }
                    }
                }
            }

            OutlinedButton(onClick = onBack, modifier = Modifier.padding(16.dp)) { Text("Back") }
        }
    }
}
