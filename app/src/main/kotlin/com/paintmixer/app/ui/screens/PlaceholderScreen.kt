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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paintmixer.app.ui.nav.Screen

/**
 * A stand-in body for every screen in PLAN.md section 5, until each is built
 * out in its own phase. Proves the nav graph wires up and is easy to delete
 * screen-by-screen as real content replaces it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(
    screen: Screen,
    nextLabel: String,
    onNext: () -> Unit,
    onBack: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    debugLabel: String? = null,
    onDebug: (() -> Unit)? = null
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(screen.title) }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Text("Screen: ${screen.title}")
            Text("Route: ${screen.route}")
            Button(onClick = onNext) { Text(nextLabel) }
            if (secondaryLabel != null && onSecondary != null) {
                OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
            if (onBack != null) {
                OutlinedButton(onClick = onBack) { Text("Back") }
            }
            if (debugLabel != null && onDebug != null) {
                TextButton(onClick = onDebug) { Text(debugLabel) }
            }
        }
    }
}
