package com.paintmixer.app.capture

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Routes a hardware "remote shutter" press -- a Bluetooth shutter remote's
 * volume-key click -- from the Activity's key dispatch (and, as a lower-
 * level fallback, `ShutterAccessibilityService`) down to whichever capture
 * screen is currently on screen.
 *
 * Exists because the Phase 2 repeatability test showed the ΔE spread rising
 * specifically when the on-screen Shutter button was touched by hand
 * (camera shake): a remote trigger means nothing touches the phone during
 * the exposure. Most cheap Bluetooth camera remotes work by emulating a
 * volume-key press -- that is a de facto convention, not something Android
 * wires to the shutter on its own, so it still needs an app-side key
 * listener.
 *
 * (A Bluetooth headset's play/pause button was also tried, via
 * `MediaSessionCompat`, but proved unreliable on the target device across
 * several attempts and was rolled back -- see PLAN.md Phase 2 for the full
 * trail if that's revisited.)
 *
 * [diagnostics] and `com.paintmixer.app.ui.screens.RemoteDiagnosticsScreen`
 * surface every raw signal on-screen, so what arrives (or doesn't) can
 * actually be seen instead of guessed at blind.
 */
class RemoteShutterController {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    private var listenerCount = 0

    /** A capture screen calls this while on screen and able to act on a shutter press. */
    fun registerListener() {
        listenerCount++
    }

    fun unregisterListener() {
        listenerCount = (listenerCount - 1).coerceAtLeast(0)
    }

    val hasListener: Boolean get() = listenerCount > 0

    private val _diagnostics = MutableStateFlow<List<String>>(emptyList())

    /** Most-recent-first log of every raw signal seen, whether or not it was consumed. */
    val diagnostics: StateFlow<List<String>> = _diagnostics.asStateFlow()

    /** Records a raw signal for [RemoteDiagnosticsScreen] without necessarily triggering a shot. */
    fun log(message: String) {
        val stamped = "%tT.%<tL  %s".format(System.currentTimeMillis(), message)
        _diagnostics.value = (listOf(stamped) + _diagnostics.value).take(MAX_LOG_LINES)
    }

    /**
     * @return true if a capture screen is listening and the press was consumed (so the caller
     * should NOT also let the key do its default thing, e.g. changing system volume); false to
     * let default key handling proceed.
     */
    fun tryTrigger(source: String): Boolean {
        val consumed = hasListener
        log("$source -> ${if (consumed) "CONSUMED (shutter fired)" else "ignored (no capture screen listening)"}")
        if (consumed) _events.tryEmit(Unit)
        return consumed
    }

    private companion object {
        const val MAX_LOG_LINES = 30
    }
}
