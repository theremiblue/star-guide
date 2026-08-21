/*
 * Copyright 2026 Dmitry Vasyliev
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.raydrivers.starguide

import android.Manifest
import android.content.Context
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
import androidx.annotation.CheckResult
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import java.util.concurrent.Executor

@JvmInline
value class CameraId private constructor(
    val value: String,
) {
    companion object {
        fun backFacing(cameraManager: CameraManager): CameraId {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)

                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

                if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    return CameraId(id)
                }
            }

            // TODO: is this good error reporting?
            error("No back-facing camera found")
        }
    }
}

internal class CameraDispatch {
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

/*
 * TODO: Handle stale async callbacks.
 *
 * Camera2 callbacks can arrive after close() was already called.
 * Example:
 *
 *   open()
 *   close()
 *   onOpened(camera) arrives later
 *
 * Current first version ignores that race. Later add either:
 * - generation token owned by MainActivity/session, or
 * - closed flag inside Opening checked by every callback.
 */
public sealed interface CameraPreviewSession {
    companion object {
        private const val LOG_TAG = "CameraPreviewSession"

        @RequiresPermission(Manifest.permission.CAMERA)
        fun open(
            context: Context,
            previewSurface: PreviewSurface.Active,
            transition: (CameraPreviewSession) -> Unit,
        ): Opening {
            check(previewSurface.surface.isValid) {
                "Cannot open camera preview with invalid Surface"
            }

            val cameraManager =
                context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            val cameraId = CameraId.backFacing(cameraManager)

            val dispatch = CameraDispatch()

            val opening = Opening(
                cameraId = cameraId,
                dispatch = dispatch,
                previewSurface = previewSurface,
                transition = transition,
            )

            try {
                Logger.d(LOG_TAG, "Opening camera preview: cameraId=${cameraId.value}")

                cameraManager.openCamera(
                    cameraId.value,
                    opening.createCameraDeviceCallback(),
                    dispatch.handler,
                )
            } catch (error: CameraAccessException) {
                Logger.e(LOG_TAG, "Failed to open camera", error)

                dispatch.shutdown()

                transition(Closed)
            }

            return opening
        }
    }

    @CheckResult
    fun close() : Closed

    data object Closed : CameraPreviewSession {
        override fun close(): Closed = this
    }

    class Opening internal constructor(
        private val cameraId: CameraId,
        private val dispatch: CameraDispatch,
        private val previewSurface: PreviewSurface.Active,
        private val transition: (CameraPreviewSession) -> Unit,
    ) : CameraPreviewSession {
        override fun close() : Closed {
            Logger.d(LOG_TAG, "Closing opening camera preview: cameraId=${cameraId.value}")

            dispatch.shutdown()

            return Closed
        }

        internal fun tryStartRepeatingPreview(
            camera: CameraDevice,
            session: CameraCaptureSession,
        ): CameraPreviewSession {
            try {
                val requestBuilder =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

                requestBuilder.addTarget(previewSurface.surface)

                session.setRepeatingRequest(
                    requestBuilder.build(),
                    null,
                    dispatch.handler,
                )

                Logger.d(LOG_TAG, "Preview repeating request started")

                return Running(
                    cameraDevice = camera,
                    captureSession = session,
                    dispatch = dispatch,
                    previewSurface = previewSurface,
                )
            } catch (error: CameraAccessException) {
                Logger.e(LOG_TAG, "Failed to start repeating preview", error)

                session.close()
                camera.close()

                return close()
            }
        }

        fun createCameraDeviceCallback(): CameraDevice.StateCallback =
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Logger.d(LOG_TAG, "Camera opened: cameraId=${camera.id}")

                    tryCreatePreviewSession(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Logger.w(LOG_TAG, "Camera disconnected: cameraId=${camera.id}")

                    // TODO: do we close here or in state?
                    camera.close()
                    transition(close())
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Logger.e(LOG_TAG, "Camera error: cameraId=${camera.id} error=$error")

                    // TODO: do we close here or in state?
                    camera.close()
                    transition(close())
                }
            }

        internal fun tryCreatePreviewSession(camera: CameraDevice) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    createPreviewSessionModern(camera)
                } else {
                    createPreviewSessionLegacy(camera)
                }
            } catch (error: CameraAccessException) {
                Logger.e(LOG_TAG, "Failed to create preview session", error)

                // TODO: do we close here or in state?
                camera.close()
                transition(close())
            }
        }

        @Suppress("DEPRECATION")
        private fun createPreviewSessionLegacy(camera: CameraDevice) {
            camera.createCaptureSession(
                listOf(previewSurface.surface),
                createCaptureSessionCallback(camera),
                dispatch.handler,
            )
        }

        @RequiresApi(Build.VERSION_CODES.P)
        private fun createPreviewSessionModern(camera: CameraDevice) {
            val outputConfiguration = OutputConfiguration(previewSurface.surface)

            val sessionConfiguration = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                listOf(outputConfiguration),
                dispatch.executor,
                createCaptureSessionCallback(camera),
            )

            camera.createCaptureSession(sessionConfiguration)
        }

        private fun createCaptureSessionCallback(
            camera: CameraDevice,
        ): CameraCaptureSession.StateCallback =
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    Logger.d(LOG_TAG, "Preview session configured")

                    transition(
                        tryStartRepeatingPreview(
                            camera = camera,
                            session = session,
                        )
                    )
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Logger.e(LOG_TAG, "Preview session configuration failed")

                    session.close()
                    camera.close()

                    transition(close())
                }
            }


    }

    class Running internal constructor(
        private val cameraDevice: CameraDevice,
        private val captureSession: CameraCaptureSession,
        private val dispatch: CameraDispatch,
        private val previewSurface: PreviewSurface.Active,
    ) : CameraPreviewSession {
        override fun close(): Closed {
            Logger.d(LOG_TAG, "Closing running camera preview: cameraId=${cameraDevice.id}")

            captureSession.close()
            cameraDevice.close()
            dispatch.shutdown()
            // TODO: how?
            // previewSurface.release()

            return Closed
        }
    }
}
