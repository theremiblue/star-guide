/*
 * Copyright 2026 Dmitry Vasyliev
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalAtomicApi::class)

package com.raydrivers.starguide

import android.Manifest
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resumeWithException

private const val LOG_TAG = "Camera2Extensions"

@JvmInline
value class CameraId(
    val value: String,
) {
    companion object {
        @Throws(BackFacingCameraException::class)
        fun backFacing(cameraManager: CameraManager): CameraId {
            val cameraIds = try {
                cameraManager.cameraIdList
            } catch (error: CameraAccessException) {
                throw BackFacingCameraException("CameraManager.cameraIdList", error)
            }

            for (id in cameraIds) {
                val characteristics = try {
                    cameraManager.getCameraCharacteristics(id)
                } catch (error: CameraAccessException) {
                    val operation = "CameraManager.getCameraCharacteristics($id)"
                    throw BackFacingCameraException(operation, error)
                }

                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

                if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    return CameraId(id)
                }
            }

            throw BackFacingCameraException(cameraIds.size)
        }
    }
}

/// Tied to CameraDevice lifetime
class CameraDispatch {
    private val thread = HandlerThread("StarGuideCamera").apply {
        start()
    }

    val handler: Handler = Handler(thread.looper)

    val executor: Executor = Executor { command ->
        handler.post(command)
    }

    fun quit() {
        thread.quitSafely()
    }

    fun shutdown() {
        thread.quitSafely()

        try {
            thread.join()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}

/// We need to track state to close camera properly
/// inside the AwaitCameraDeviceStateCallback
private enum class CameraDeviceState {
    OPENING,
    OPENED,
    TERMINATED,
}

/// This callback cares only about the camera state while opening
private class AwaitCameraDeviceStateCallback(
    private val continuation: CancellableContinuation<CameraDevice>,
) : CameraDevice.StateCallback() {
    companion object {
        private const val LOG_TAG = "Camera2Extensions.AwaitCameraDeviceStateCallback"
    }

    private val state = AtomicReference(CameraDeviceState.OPENING)

    fun cancelOpening() {
        Logger.v(LOG_TAG, "Opening cancelled")
        state.compareAndSet(
            expectedValue = CameraDeviceState.OPENING,
            newValue = CameraDeviceState.TERMINATED,
        )
    }

    fun failOpening(e: CameraOpenException) {
        if (
            state.compareAndSet(
                expectedValue = CameraDeviceState.OPENING,
                newValue = CameraDeviceState.TERMINATED
            )
        ) {
            continuation.resumeWithException(e)
        }
    }

    override fun onOpened(camera: CameraDevice) {
        if (state.compareAndSet(
            expectedValue = CameraDeviceState.OPENING,
            newValue = CameraDeviceState.OPENED
        )) {
            Logger.v(LOG_TAG, "Camera device opened for cameraId=${camera.id}")

            continuation.resume(camera) { _, rejectedCamera, _ ->
                Logger.v(LOG_TAG, "Continuation for opened camera cancelled, terminating")

                rejectedCamera.close()

                state.compareAndSet(
                    expectedValue = CameraDeviceState.OPENED,
                    newValue = CameraDeviceState.TERMINATED
                )
            }
        }

        when (state.load()) {
            CameraDeviceState.TERMINATED -> {
                // Opening was cancelled or failed before Camera2 delivered the device.
                // Nobody owns this camera, so close it.
                Logger.v(LOG_TAG, "Closing unowned camera")
                camera.close()
            }

            CameraDeviceState.OPENED -> {
                // Unexpected duplicate onOpened().
                // Do NOT close: this may be the device already handed to the caller.
                Logger.i(LOG_TAG, "Unexpected duplicate onOpened call")
            }

            CameraDeviceState.OPENING -> {
                // State changed concurrently between CAS and load.
                // Don't make a decision from this snapshot.
            }
        }
    }

    override fun onDisconnected(camera: CameraDevice) {
        Logger.v(LOG_TAG, "Camera device disconnected for cameraId=${camera.id}")

        when (state.exchange(CameraDeviceState.TERMINATED)) {
            CameraDeviceState.OPENING -> {
                camera.use { camera ->
                    continuation.resumeWithException(CameraOpenException(CameraId(camera.id)))
                }
            }

            CameraDeviceState.OPENED -> { }

            CameraDeviceState.TERMINATED -> {
                camera.close() // TODO: why
            }
        }
    }

    override fun onError(camera: CameraDevice, errorCode: Int) {
        when (state.exchange(CameraDeviceState.TERMINATED)) {
            CameraDeviceState.OPENING -> {
                camera.use { camera ->
                    continuation.resumeWithException(CameraOpenException(CameraId(camera.id), errorCode))
                }
            }

            CameraDeviceState.OPENED -> { }

            CameraDeviceState.TERMINATED -> {
                camera.close()
            }
        }
    }
}

@Throws(CameraOpenException::class)
@RequiresPermission(Manifest.permission.CAMERA)
internal suspend fun CameraManager.awaitOpenCamera(
    cameraId: CameraId,
    dispatch: CameraDispatch,
): CameraDevice = suspendCancellableCoroutine { continuation ->
    val stateCallback = AwaitCameraDeviceStateCallback(continuation)

    continuation.invokeOnCancellation {
        stateCallback.cancelOpening()
    }

    try {
        openCamera(cameraId.value, stateCallback, dispatch.handler)
    } catch (error: CameraAccessException) {
        stateCallback.failOpening(CameraOpenException(cameraId, error))
    } catch (error: SecurityException) {
        stateCallback.failOpening(CameraOpenException(cameraId, error))
    }
}

private fun CameraDevice.createPreviewSessionLegacy(
    previewSurface: PreviewSurface.Active,
    dispatch: CameraDispatch,
    callback: CameraCaptureSession.StateCallback,
) {
    try {
        @Suppress("DEPRECATION")
        createCaptureSession(
            listOf(previewSurface.surface),
            callback,
            dispatch.handler,
        )
    } catch (error: CameraAccessException) {
        throw CameraSessionConfigureException(id, "createCaptureSessionLegacy", error)
    } catch (error: IllegalArgumentException) {
        throw CameraSessionConfigureException(id, "createCaptureSessionLegacy", error)
    } catch (error: IllegalStateException) {
        throw CameraSessionConfigureException(id, "createCaptureSessionLegacy", error)
    }
}

@RequiresApi(Build.VERSION_CODES.P)
private fun CameraDevice.createPreviewSessionModern(
    previewSurface: PreviewSurface.Active,
    dispatch: CameraDispatch,
    callback: CameraCaptureSession.StateCallback,
) {
    val outputConfiguration = OutputConfiguration(previewSurface.surface)

    val sessionConfiguration = SessionConfiguration(
        SessionConfiguration.SESSION_REGULAR,
        listOf(outputConfiguration),
        dispatch.executor,
        callback
    )

    try {
        createCaptureSession(sessionConfiguration)
    } catch (error: CameraAccessException) {
        throw CameraSessionConfigureException(id, "createCaptureSessionModern", error)
    } catch (error: IllegalArgumentException) {
        throw CameraSessionConfigureException(id, "createCaptureSessionModern", error)
    } catch (error: IllegalStateException) {
        throw CameraSessionConfigureException(id, "createCaptureSessionModern", error)
    }
}

@Throws(CameraSessionConfigureException::class)
internal fun CameraDevice.createPreviewSession(
    previewSurface: PreviewSurface.Active,
    dispatch: CameraDispatch,
    callback: CameraCaptureSession.StateCallback,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        createPreviewSessionModern(previewSurface, dispatch, callback)
    } else {
        createPreviewSessionLegacy(previewSurface, dispatch, callback)
    }
}

@Throws(CameraSessionConfigureException::class)
internal suspend fun CameraDevice.awaitCreateCaptureSession(
    previewSurface: PreviewSurface.Active,
    dispatch: CameraDispatch,
): CameraCaptureSession = suspendCancellableCoroutine { continuation ->
    val completed = AtomicBoolean(false)

    fun completeFailure(error: CameraSessionConfigureException) {
        if (completed.compareAndSet(expectedValue = false, newValue = true)) {
            continuation.resumeWithException(error)
        }
    }

    val callback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            if (completed.compareAndSet(expectedValue = false, newValue = true)) {
                continuation.resume(session) { _, rejectedSession, _ ->
                    rejectedSession.close()
                }
            } else {
                session.close()
            }
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            session.close()

            completeFailure(CameraSessionConfigureException(id))
        }
    }

    continuation.invokeOnCancellation {
        completed.store(true)
    }

    try {
        createPreviewSession(previewSurface, dispatch, callback)
    } catch (error: CameraSessionConfigureException) {
        completeFailure(error)
    }
}

@Throws(CameraPreviewStartException::class)
internal fun CameraCaptureSession.startRepeatingPreview(
    camera: CameraDevice,
    previewSurface: PreviewSurface.Active,
    dispatch: CameraDispatch,
): Int {
    val requestBuilder = try {
        camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
    } catch (error: CameraAccessException) {
        throw CameraPreviewStartException(camera.id, "createCaptureRequest", error)
    } catch (error: IllegalArgumentException) {
        throw CameraPreviewStartException(camera.id, "createCaptureRequest", error)
    } catch (error: IllegalStateException) {
        throw CameraPreviewStartException(camera.id, "createCaptureRequest", error)
    }

    val request = requestBuilder.apply {
        addTarget(previewSurface.surface)
    }.build()

    try {
        return setRepeatingRequest(request, null, dispatch.handler)
    } catch (error: CameraAccessException) {
        throw CameraPreviewStartException(camera.id, "setRepeatingRequest", error)
    } catch (error: IllegalArgumentException) {
        throw CameraPreviewStartException(camera.id, "setRepeatingRequest", error)
    } catch (error: IllegalStateException) {
        throw CameraPreviewStartException(camera.id, "setRepeatingRequest", error)
    }
}
