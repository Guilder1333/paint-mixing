package com.paintmixer.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.paintmixer.app.ui.screens.DeviceProbeScreen
import com.paintmixer.app.ui.screens.PlaceholderScreen

@Composable
fun PaintMixerNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.PaletteList.route) {
        // Palette creation flow: List -> Capture -> White reference -> Picking -> back to List.
        composable(Screen.PaletteList.route) {
            PlaceholderScreen(
                screen = Screen.PaletteList,
                nextLabel = "New palette",
                onNext = { navController.navigate(Screen.PaletteCapture.route) },
                secondaryLabel = "Match a target",
                onSecondary = { navController.navigate(Screen.TargetCapture.route) },
                debugLabel = "Device probe (debug)",
                onDebug = { navController.navigate(Screen.DeviceProbe.route) }
            )
        }
        composable(Screen.PaletteCapture.route) {
            PlaceholderScreen(
                screen = Screen.PaletteCapture,
                nextLabel = "Shutter",
                onNext = { navController.navigate(Screen.WhiteReference.route) },
                onBack = navController::popBackStack
            )
        }
        composable(Screen.WhiteReference.route) {
            PlaceholderScreen(
                screen = Screen.WhiteReference,
                nextLabel = "Tapped white card",
                onNext = { navController.navigate(Screen.PalettePicking.route) },
                onBack = navController::popBackStack
            )
        }
        composable(Screen.PalettePicking.route) {
            PlaceholderScreen(
                screen = Screen.PalettePicking,
                nextLabel = "Save palette",
                onNext = {
                    navController.navigate(Screen.PaletteList.route) {
                        popUpTo(Screen.PaletteList.route) { inclusive = true }
                    }
                },
                onBack = navController::popBackStack
            )
        }

        // Target matching flow: Capture -> Pick -> Result -> Export.
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
    }
}
