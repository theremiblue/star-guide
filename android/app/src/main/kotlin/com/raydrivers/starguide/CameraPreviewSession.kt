/*
 * Copyright 2026 Dmitry Vasyliev
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.raydrivers.starguide

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import androidx.annotation.CheckResult
import androidx.annotation.RequiresPermission


/**
 * Typestate interface representing valid camera Preview state transitions.
 *
 * Any state's transition function throws exception on error that is defined in Camera2Exceptions.
 * Reason for this design - we don't have any context here to handle failure depending on the problem.
 */
sealed interface CameraPreviewSession {
    companion object {
        private const val LOG_TAG = "CameraPreviewSession"
    }

    @CheckResult
    fun close() : Closed

    /// Initial state, no resources are used
    data object Closed : CameraPreviewSession {
        override fun close(): Closed = this

        @RequiresPermission(Manifest.permission.CAMERA)
        @Throws(BackFacingCameraException::class,
                CameraOpenException::class)
        suspend fun open(
            context: Context,
            previewSurface: PreviewSurface.Active,
        ): DeviceOpened {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val dispatch = CameraDispatch()

            fun fail(error: Throwable): Nothing {
                dispatch.shutdown()
                throw error
            }

            val cameraId = try {
                CameraId.backFacing(cameraManager)
            } catch (error: BackFacingCameraException) {
                fail(error)
            }

            Logger.d(LOG_TAG, "Opening camera device: cameraId=${cameraId.value}")

            val camera = try {
                cameraManager.awaitOpenCamera(cameraId, dispatch)
            } catch (error: CameraOpenException) {
                fail(error)
            }

            val session = DeviceOpened(
                cameraId = cameraId,
                camera = camera,
                dispatch = dispatch,
                previewSurface = previewSurface,
            )

            return session
        }
    }

    /// Camera opened successfully
    class DeviceOpened internal constructor(
        private val cameraId: CameraId,
        private val camera: CameraDevice,
        private val dispatch: CameraDispatch,
        private val previewSurface: PreviewSurface.Active,
    ) : CameraPreviewSession {
        override fun close(): Closed {
            Logger.d(LOG_TAG, "Closing opened camera device: cameraId=${cameraId.value}")

            camera.close()
            dispatch.shutdown()

            return Closed
        }

        @Throws(CameraSessionConfigureException::class)
        suspend fun configureSession(): SessionConfigured {
            Logger.d(LOG_TAG, "Configuring camera capture session: cameraId=${cameraId.value}")

            fun fail(error: Throwable): Nothing {
                @SuppressWarnings("CheckResult") close()
                throw error
            }

            val captureSession = try {
                camera.awaitCreateCaptureSession(previewSurface, dispatch)
            } catch (error: CameraSessionConfigureException) {
                fail(error)
            }

            val session = SessionConfigured(
                cameraId = cameraId,
                camera = camera,
                captureSession = captureSession,
                dispatch = dispatch,
                previewSurface = previewSurface,
            )

            return session
        }
    }

    /// CameraCaptureSession configuration succeeded
    class SessionConfigured internal constructor(
        private val cameraId: CameraId,
        private val camera: CameraDevice,
        private val captureSession: CameraCaptureSession,
        private val dispatch: CameraDispatch,
        private val previewSurface: PreviewSurface.Active,
    ) : CameraPreviewSession {
        override fun close(): Closed {
            Logger.d(LOG_TAG, "Closing configured capture session: cameraId=${cameraId.value}")

            captureSession.close()
            camera.close()
            dispatch.shutdown()

            return Closed
        }

        @Throws(CameraPreviewStartException::class)
        fun startPreview(): PreviewRunning {
            fun fail(error: Throwable): Nothing {
                @SuppressWarnings("CheckResult") close()
                throw error
            }

            Logger.d(LOG_TAG, "Starting the preview: cameraId=${cameraId.value}")

            val requestSequenceId = try {
                captureSession.startRepeatingPreview(camera, previewSurface, dispatch)
            } catch (error: CameraPreviewStartException) {
                fail(error)
            }

            check(requestSequenceId >= 0) {
                "Invalid preview request sequence id: cameraId=${cameraId.value} requestSequenceId=$requestSequenceId"
            }

            val session = PreviewRunning(
                cameraId = cameraId,
                camera = camera,
                captureSession = captureSession,
                dispatch = dispatch,
                previewSurface = previewSurface,
                requestSequenceId = requestSequenceId,
            )

            return session
        }
    }

    /// Preview running, request succeeded
    class PreviewRunning internal constructor(
        private val cameraId: CameraId,
        private val camera: CameraDevice,
        private val captureSession: CameraCaptureSession,
        private val dispatch: CameraDispatch,
        @Suppress("unused")
        private val previewSurface: PreviewSurface.Active,
        private val requestSequenceId: Int,
    ) : CameraPreviewSession {
        override fun close(): Closed {
            Logger.d(LOG_TAG, "Closing running camera preview: cameraId=${cameraId.value} requestSequenceId=$requestSequenceId")

            captureSession.close()
            camera.close()
            dispatch.shutdown()

            return Closed
        }
    }
}
