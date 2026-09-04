package com.paintmixer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paintmixer.app.capture.PendingPaletteCapture
import com.paintmixer.app.data.Palette
import com.paintmixer.app.data.PaletteDao
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * PLAN.md section 5, screen 4. Colour picking (tap to add, loupe, ordered
 * list, rename) is Phase 3 -- not built yet. For now Save just persists the
 * palette shot + white reference, which is what Phase 2's repeatability/
 * replay test actually needs (a palette's `CaptureSettings` has to survive
 * an app restart so they can be reused verbatim for a later shot).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalettePickingScreen(
    pending: PendingPaletteCapture,
    paletteDao: PaletteDao,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Pick Colours") }) }) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Colour picking UI is Phase 3. Saving now just persists the palette shot and its locked capture settings, so they can be replayed for target shots.")
            error?.let { Text(it) }

            Button(
                enabled = !saving && pending.isReadyToSave,
                onClick = {
                    val imagePath = pending.imagePath
                    val capture = pending.capture
                    val whiteRefX = pending.whiteRefX
                    val whiteRefY = pending.whiteRefY
                    if (imagePath == null || capture == null || whiteRefX == null || whiteRefY == null) {
                        error = "Missing capture or white reference -- go back and redo that step."
                        return@Button
                    }
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            paletteDao.insert(
                                Palette(
                                    id = UUID.randomUUID().toString(),
                                    name = "Palette ${System.currentTimeMillis()}",
                                    imagePath = imagePath,
                                    createdAt = System.currentTimeMillis(),
                                    whiteRefX = whiteRefX,
                                    whiteRefY = whiteRefY,
                                    whiteRefReflectance = pending.whiteRefReflectance,
                                    capture = capture
                                )
                            )
                            pending.reset()
                            onSaved()
                        } catch (e: Exception) {
                            error = "Save failed: ${e.message}"
                        } finally {
                            saving = false
                        }
                    }
                }
            ) { Text(if (saving) "Saving..." else "Save palette") }

            OutlinedButton(onClick = onBack) { Text("Back") }
        }
    }
}
