package com.paintmixer.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.paintmixer.app.probe.CameraProbe
import com.paintmixer.app.probe.CameraProbeResult
import com.paintmixer.app.probe.toReportText

/**
 * PLAN.md section 4.0: run once on the actual target device before writing
 * any capture code, so the capture path (4.1 vs 4.2 vs the AE/AWB-lock
 * fallback) is a recorded fact rather than a guess. Read-only -- querying
 * CameraCharacteristics needs no runtime permission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceProbeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val results = remember { CameraProbe.probeAll(context) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Device Probe") }) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            Text(
                "Reads CameraCharacteristics for every camera on this device (PLAN.md 4.0). " +
                    "Copy the report and paste it back so the capture path gets recorded in the repo."
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { copyReportToClipboard(context, results) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copy report")
            }
            Spacer(Modifier.height(12.dp))

            if (results.isEmpty()) {
                Text("No cameras reported by CameraManager.")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(results) { result -> CameraProbeCard(result) }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
private fun CameraProbeCard(result: CameraProbeResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Camera ${result.cameraId} (${result.lensFacing})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Hardware level: ${result.hardwareLevel}")
            Text("MANUAL_SENSOR: ${result.hasManualSensor}")
            Text("MANUAL_POST_PROCESSING: ${result.hasManualPostProcessing}")
            Text("RAW: ${result.hasRaw}")
            Text("Exposure time range (ns): ${result.exposureTimeRangeNs ?: "n/a"}")
            Text("Sensitivity (ISO) range: ${result.sensitivityRange ?: "n/a"}")
            Text("Tonemap modes: ${result.availableToneMapModes.joinToString().ifEmpty { "none" }}")
            Spacer(Modifier.height(8.dp))
            Text("Capture path: ${result.capturePath}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun copyReportToClipboard(context: Context, results: List<CameraProbeResult>) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Device probe", results.toReportText()))
}
