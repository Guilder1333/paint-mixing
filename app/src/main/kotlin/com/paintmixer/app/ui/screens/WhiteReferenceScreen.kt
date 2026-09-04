package com.paintmixer.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.paintmixer.app.capture.ImageSampling
import com.paintmixer.app.capture.PatchSample
import com.paintmixer.app.capture.PendingPaletteCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val WHITE_PAPER_REFLECTANCE = 0.90f
private const val GREY_CARD_REFLECTANCE = 0.18f

/**
 * PLAN.md section 5, screen 3: "Tap the white card". Not skippable -- the
 * shot cannot be saved without it (PLAN.md "Scope": the white reference is
 * mandatory, there is no unnormalised path). A basic tap-to-sample for now;
 * the offset magnifier loupe from section 4.3 is a follow-up UX pass, not
 * required for the underlying maths to be correct.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteReferenceScreen(
    pending: PendingPaletteCapture,
    onConfirmed: () -> Unit,
    onBack: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var tapXNorm by remember { mutableStateOf<Float?>(null) }
    var tapYNorm by remember { mutableStateOf<Float?>(null) }
    var sample by remember { mutableStateOf<PatchSample?>(null) }
    var reflectance by remember { mutableFloatStateOf(pending.whiteRefReflectance) }
    var displaySize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(pending.imagePath) {
        val path = pending.imagePath
        if (path == null) {
            loadError = "No captured image -- go back and shoot the palette first."
            return@LaunchedEffect
        }
        try {
            bitmap = withContext(Dispatchers.IO) { ImageSampling.decodeUpright(File(path)) }
        } catch (e: Exception) {
            loadError = "Could not load the captured image: ${e.message}"
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("White Reference") }) }) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Tap the white card (or grey card) in the photo.")
            loadError?.let { Text(it) }

            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Captured palette photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                        .onSizeChanged { displaySize = it }
                        .pointerInput(bmp) {
                            detectTapGestures { offset: Offset ->
                                if (displaySize.width <= 0 || displaySize.height <= 0) return@detectTapGestures
                                val xNorm = (offset.x / displaySize.width).coerceIn(0f, 1f)
                                val yNorm = (offset.y / displaySize.height).coerceIn(0f, 1f)
                                tapXNorm = xNorm
                                tapYNorm = yNorm
                                sample = ImageSampling.samplePatch(bmp, xNorm, yNorm)
                            }
                        }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = reflectance == WHITE_PAPER_REFLECTANCE,
                        onClick = { reflectance = WHITE_PAPER_REFLECTANCE },
                        label = { Text("White paper (0.90)") }
                    )
                    FilterChip(
                        selected = reflectance == GREY_CARD_REFLECTANCE,
                        onClick = { reflectance = GREY_CARD_REFLECTANCE },
                        label = { Text("18% grey card (0.18)") }
                    )
                }

                sample?.let { s ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            color = Color(s.medianR / 255f, s.medianG / 255f, s.medianB / 255f)
                        ) {}
                        Column {
                            Text("median RGB ${s.medianR}, ${s.medianG}, ${s.medianB} (patch ${s.patchSizePx}px)")
                            if (s.isBlownOut) Text("Too bright / blown out -- tap elsewhere.")
                            if (s.isInconsistent()) Text("Inconsistent area (std dev ${"%.1f".format(s.maxStdDev)}) -- tap a flatter spot.")
                        }
                    }
                }
            }

            Button(
                enabled = sample != null && sample?.isBlownOut == false && sample?.isInconsistent() == false,
                onClick = {
                    pending.whiteRefX = tapXNorm
                    pending.whiteRefY = tapYNorm
                    pending.whiteRefReflectance = reflectance
                    onConfirmed()
                }
            ) { Text("Confirm white point") }

            OutlinedButton(onClick = onBack) { Text("Back") }
        }
    }
}
