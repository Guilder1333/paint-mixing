package com.paintmixer.app.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.paintmixer.app.PaintMixerApp

/**
 * Intercepts hardware key events at the Accessibility layer -- a lower,
 * more privileged level than a regular app's `Activity.dispatchKeyEvent`.
 *
 * Exists because `dispatchKeyEvent` received NOTHING at all on the target
 * device, not even the phone's own volume keys (confirmed with
 * `RemoteDiagnosticsScreen`). Something below the normal app layer --
 * plausibly Nothing OS's own hardware-button handling -- is swallowing
 * these before any app-level code runs. `AccessibilityService.onKeyEvent`
 * is the standard mechanism "hardware button remapper" apps use
 * specifically because it sits at a lower level than that: services
 * requesting `FLAG_REQUEST_FILTER_KEY_EVENTS` get first refusal on hardware
 * key events system-wide, even over apps that aren't focused.
 *
 * Scoped to the same volume/camera keys as `MainActivity.dispatchKeyEvent`
 * -- a Bluetooth headset's play/pause button is a different mechanism
 * entirely (MediaSession-routed, not a key event at all) and was rolled
 * back after proving unreliable; see PLAN.md Phase 2.
 *
 * Android does not let an app grant this to itself -- the user has to
 * enable it once under Settings > Accessibility > Paint Mixer. See
 * `RemoteDiagnosticsScreen`'s "Open Accessibility settings" shortcut.
 */
class ShutterAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        remoteShutter.log("ShutterAccessibilityService connected")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        remoteShutter.log("AccessibilityService.onKeyEvent: ${KeyEvent.keyCodeToString(event.keyCode)}")
        if (isShutterKey(event.keyCode)) {
            return remoteShutter.tryTrigger("AccessibilityService:${KeyEvent.keyCodeToString(event.keyCode)}")
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Only key events matter here -- see onKeyEvent.
    }

    override fun onInterrupt() {}

    private val remoteShutter: RemoteShutterController
        get() = (application as PaintMixerApp).remoteShutter

    private fun isShutterKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_CAMERA -> true
        else -> false
    }
}
