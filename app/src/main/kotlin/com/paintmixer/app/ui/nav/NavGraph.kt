package com.paintmixer.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.paintmixer.app.capture.PendingPaletteCapture
import com.paintmixer.app.capture.RemoteShutterController
import com.paintmixer.app.data.AppDatabase
import com.paintmixer.app.ui.screens.CaptureRepeatabilityScreen
import com.paintmixer.app.ui.screens.DeviceProbeScreen
import com.paintmixer.app.ui.screens.PaletteCaptureScreen
import com.paintmixer.app.ui.screens.PalettePickingScreen
import com.paintmixer.app.ui.screens.PlaceholderScreen
import com.paintmixer.app.ui.screens.RemoteDiagnosticsScreen
import com.paintmixer.app.ui.screens.WhiteReferenceScreen

@Composable
fun PaintMixerNavHost(
    database: AppDatabase,
    remoteShutter: RemoteShutterController,
    navController: NavHostController = rememberNavController()
) {
    val pending = remember { PendingPaletteCapture() }
    val paletteDao = remember { database.paletteDao() }

    NavHost(navController = navController, startDestination = Screen.PaletteList.route) {
        // Palette creation flow: List -> Capture -> White reference -> Picking -> back to List.
        composable(Screen.PaletteList.route) {
            PlaceholderScreen(
                screen = Screen.PaletteList,
                nextLabel = "New palette",
                onNext = {
                    pending.reset()
                    navController.navigate(Screen.PaletteCapture.route)
                },
                extraActions = listOf(
                    "Match a target" to { navController.navigate(Screen.TargetCapture.route) },
                    "Device probe (debug)" to { navController.navigate(Screen.DeviceProbe.route) },
                    "Capture repeatability test (debug)" to { navController.navigate(Screen.CaptureRepeatabilityTest.route) },
                    "Remote trigger diagnostics (debug)" to { navController.navigate(Screen.RemoteDiagnostics.route) }
                )
            )
        }
        composable(Screen.PaletteCapture.route) {
            PaletteCaptureScreen(
                pending = pending,
                remoteShutter = remoteShutter,
                onCaptured = { navController.navigate(Screen.WhiteReference.route) },
                onBack = navController::popBackStack
            )
        }
        composable(Screen.WhiteReference.route) {
            WhiteReferenceScreen(
                pending = pending,
                onConfirmed = { navController.navigate(Screen.PalettePicking.route) },
                onBack = navController::popBackStack
            )
        }
        composable(Screen.PalettePicking.route) {
            PalettePickingScreen(
                pending = pending,
                paletteDao = paletteDao,
                onSaved = {
                    navController.navigate(Screen.PaletteList.route) {
                        popUpTo(Screen.PaletteList.route) { inclusive = true }
                    }
                },
                onBack = navController::popBackStack
            )
        }

        // Target matching flow: Capture -> Pick -> Result -> Export. Still placeholders -- Phase 4.
        composable(Screen.TargetCapture.route) {
            PlaceholderScreen(
                screen = Screen.TargetCapture,
                nextLabel = "Shutter",
                onNext = { navController.navigate(Screen.TargetPick.route) },
                onBack = navController::popBackStack
            )
        }
        composable(Screen.TargetPick.route) {
            PlaceholderScreen(
                screen = Screen.TargetPick,
                nextLabel = "Find mix",
                onNext = { navController.navigate(Screen.Result.route) },
                onBack = navController::popBackStack
            )
        }
        composable(Screen.Result.route) {
            PlaceholderScreen(
                screen = Screen.Result,
                nextLabel = "Export",
                onNext = { navController.navigate(Screen.Export.route) },
                onBack = navController::popBackStack
            )
        }
        composable(Screen.Export.route) {
            PlaceholderScreen(
                screen = Screen.Export,
                nextLabel = "Done",
                onNext = {
                    navController.navigate(Screen.PaletteList.route) {
                        popUpTo(Screen.PaletteList.route) { inclusive = true }
                    }
                },
                onBack = navController::popBackStack
            )
        }

        composable(Screen.DeviceProbe.route) {
            DeviceProbeScreen(onBack = navController::popBackStack)
        }
        composable(Screen.CaptureRepeatabilityTest.route) {
            CaptureRepeatabilityScreen(
                paletteDao = paletteDao,
                remoteShutter = remoteShutter,
                onBack = navController::popBackStack
            )
        }
        composable(Screen.RemoteDiagnostics.route) {
            RemoteDiagnosticsScreen(remoteShutter = remoteShutter, onBack = navController::popBackStack)
        }
    }
}
