package com.paintmixer.app.capture

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.paintmixer.app.data.CaptureSettings
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns the CameraX preview + still-capture pipeline for one screen, and is
 * the only place PLAN.md section 4.1's manual request gets assembled and
 * applied. Two ways in:
 *
 * - Bind, watch [liveMeteredSettings] while the camera runs its normal
 *   auto 3A, then [lockAndCapture] whatever was last observed -- this is
 *   how a brand-new palette gets its very first settings (PaletteCapture
 *   screen).
 * - Bind and immediately [lockAndCapture] an already-known [CaptureSettings]
 *   (a palette's stored settings, replayed verbatim) -- this is the target-
 *   shot / repeatability-test path. No metering involved, by design: PLAN.md
 *   section 3 requires target shots to reuse the palette's exact numbers,
 *   not re-derive their own.
 */
class CameraController(private val context: Context) {

    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    private val _liveMeteredSettings = MutableStateFlow<CaptureSettings?>(null)

    /** The camera's own auto-3A read-out, updated continuously until [lockAndCapture] is called. */
    val liveMeteredSettings: StateFlow<CaptureSettings?> = _liveMeteredSettings.asStateFlow()

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var camera2Control: Camera2CameraControl? = null

    /**
     * Binds preview + still capture to [lifecycleOwner]. Call exactly once per screen (e.g. from
     * a `LaunchedEffect(Unit)`), not from an `AndroidView` `update` block -- that re-runs on
     * every recomposition, which with [liveMeteredSettings] changing every frame would mean
     * rebinding the camera continuously.
     */
    suspend fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val provider = ProcessCameraProvider.getInstance(context).await()
        this.provider = provider

        val previewBuilder = Preview.Builder()
        Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    // Runs on CameraX's own callback thread, not necessarily main -- MutableStateFlow.value is
                    // safe to set from any thread, and collectAsState() picks it up regardless of emitter thread.
                    captureSettingsFromResult(result)?.let { _liveMeteredSettings.value = it }
                }
            }
        )
        val preview = previewBuilder.build().also { it.surfaceProvider = previewView.surfaceProvider }

        val capture = ImageCapture.Builder()
            .setJpegQuality(JPEG_QUALITY)
            .build()

        provider.unbindAll()
        val boundCamera = provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            capture
        )

        camera = boundCamera
        imageCapture = capture
        camera2Control = Camera2CameraControl.from(boundCamera.cameraControl)
    }

    /**
     * Locks the camera to exactly [settings] (PLAN.md 4.1's full manual request). Split out from
     * [shootLocked] so a caller can insert a self-timer delay in between -- lock immediately (so
     * exposure/WB stop drifting), then let the phone sit still for a few seconds before the
     * shutter actually fires, rather than firing the instant a finger leaves the touch button.
     */
    suspend fun lock(settings: CaptureSettings) {
        val control = checkNotNull(camera2Control) { "bind() must be called before lock()" }
        control.addCaptureRequestOptions(manualCaptureRequestOptions(settings)).await()
    }

    /** Takes one photo to [outputFile] using whatever was last passed to [lock]. */
    suspend fun shootLocked(outputFile: File) {
        val capture = checkNotNull(imageCapture) { "bind() must be called before shootLocked()" }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        capture.takePictureSuspend(outputOptions, mainExecutor)
    }

    /**
     * [lock] then immediately [shootLocked], no delay in between. Returns the settings actually
     * recorded -- same values, stamped as manual/linear-tonemap, so the caller can persist them
     * verbatim (a new palette) or compare them against a palette's already-stored settings (they
     * should be identical byte-for-byte on replay, since nothing here re-derives anything).
     */
    suspend fun lockAndCapture(settings: CaptureSettings, outputFile: File): CaptureSettings {
        lock(settings)
        shootLocked(outputFile)
        return settings.copy(linearTonemap = true, manualControlUsed = true)
    }

    /** Returns to normal auto 3A -- e.g. to re-meter, or on leaving a metering screen. */
    fun unlock() {
        camera2Control?.clearCaptureRequestOptions()
    }

    /** Releases the camera. Call from `onDispose` when the owning screen leaves composition. */
    fun unbind() {
        provider?.unbindAll()
        camera = null
        imageCapture = null
        camera2Control = null
        _liveMeteredSettings.value = null
    }

    private fun captureSettingsFromResult(result: CaptureResult): CaptureSettings? {
        val exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return null
        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return null
        val gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS)
        val focusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE)
        return CaptureSettings(
            exposureTimeNs = exposureTimeNs,
            iso = iso,
            awbGainR = gains?.red ?: 1f,
            awbGainGEven = gains?.greenEven ?: 1f,
            awbGainGOdd = gains?.greenOdd ?: 1f,
            awbGainB = gains?.blue ?: 1f,
            focusDistance = focusDistance,
            // Still the auto-metering read-out at this point, not yet the locked manual shot.
            linearTonemap = false,
            manualControlUsed = false
        )
    }
}

private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
    addListener(
        {
            try {
                cont.resume(get())
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        },
        Executor { it.run() }
    )
    cont.invokeOnCancellation { cancel(false) }
}

private suspend fun ImageCapture.takePictureSuspend(
    outputFileOptions: ImageCapture.OutputFileOptions,
    executor: Executor
): ImageCapture.OutputFileResults = suspendCancellableCoroutine { cont ->
    takePicture(
        outputFileOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                cont.resume(outputFileResults)
            }

            override fun onError(exception: ImageCaptureException) {
                cont.resumeWithException(exception)
            }
        }
    )
}

private const val JPEG_QUALITY = 100
