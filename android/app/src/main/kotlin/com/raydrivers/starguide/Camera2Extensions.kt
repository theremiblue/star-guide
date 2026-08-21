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
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resumeWithException


@JvmInline
value class CameraId private constructor(
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

class CameraDispatch {
    private val thread = HandlerThread("StarGuideCamera").apply {
        start()
    }

    val handler: Handler = Handler(thread.looper)

    val executor: Executor = Executor { command ->
        handler.post(command)
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

// TODO: I don't like the naming, proof that we await?
@Throws(CameraOpenException::class)
@RequiresPermission(Manifest.permission.CAMERA)
internal suspend fun CameraManager.awaitOpenCamera(
    cameraId: CameraId,
    dispatch: CameraDispatch,
): CameraDevice = suspendCancellableCoroutine { continuation ->
    val completed = AtomicBoolean(false)

    fun completeFail(error: CameraOpenException) {
        if (completed.compareAndSet(expectedValue = false, newValue = true)) {
            continuation.resumeWithException(error)
        }
    }

    val callback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            if (completed.compareAndSet(expectedValue = false, newValue = true)) {
                continuation.resume(camera) { _, _, _ ->
                    camera.close()
                }
            } else {
                camera.close()
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            completeFail(CameraOpenException(camera.id))
        }

        override fun onError(camera: CameraDevice, errorCode: Int) {
            camera.close()
            completeFail(CameraOpenException(camera.id, errorCode))
        }
    }

    continuation.invokeOnCancellation {
        completed.store(true)
    }

    try {
        openCamera(cameraId.value, callback, dispatch.handler)
    } catch (error: CameraAccessException) {
        completeFail(CameraOpenException(cameraId, error))
    } catch (error: SecurityException) {
        completeFail(CameraOpenException(cameraId, error))
    }
}

@Suppress("DEPRECATION")
private fun CameraDevice.createPreviewSessionLegacy(
    previewSurface: PreviewSurface.Active,
    dispatch: CameraDispatch,
    callback: CameraCaptureSession.StateCallback,
) {
    try {
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
                continuation.resume(session) { _, _, _ ->
                    session.close()
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
