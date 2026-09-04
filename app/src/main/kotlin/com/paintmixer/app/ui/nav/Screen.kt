package com.paintmixer.app.ui.nav

/**
 * The eight screens from PLAN.md section 5, plus the debug-only device probe
 * from section 4.0. Routes are plain strings for now (no arguments wired
 * yet) -- palette/shot ids get threaded through starting in Phase 3/4 when
 * there is real data to pass.
 */
enum class Screen(val route: String, val title: String) {
    PaletteList("palette_list", "Palettes"),
    PaletteCapture("palette_capture", "Capture Palette"),
    WhiteReference("white_reference", "White Reference"),
    PalettePicking("palette_picking", "Pick Colours"),
    TargetCapture("target_capture", "Capture Target"),
    TargetPick("target_pick", "Pick Target Colour"),
    Result("result", "Result"),
    Export("export", "Export / Share"),
    DeviceProbe("device_probe", "Device Probe")
}
