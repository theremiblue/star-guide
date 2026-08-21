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


// TODO: any async/concurrency problems?
sealed interface CameraPreviewSession {
    companion object {
        private const val LOG_TAG = "CameraPreviewSession"
    }

    @CheckResult
    fun close() : Closed

    sealed interface Transition<out State : CameraPreviewSession> {
        val session: CameraPreviewSession

        data class Next<out State : CameraPreviewSession>(
            override val session: State,
        ) : Transition<State>

        data class Failure(
            override val session: Closed,
            val error: Throwable,
        ) : Transition<Nothing>
    }

    suspend fun <State : CameraPreviewSession> stateTransition(
        transition: suspend () -> Transition<State>,
    ): Transition<State> = transition()

    /// Initial state, no resources are used
    data object Closed : CameraPreviewSession {
        override fun close(): Closed = this

        @RequiresPermission(Manifest.permission.CAMERA)
        suspend fun open(
            context: Context,
            previewSurface: PreviewSurface.Active,
        ): Transition<DeviceOpened> = stateTransition {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val dispatch = CameraDispatch()

            fun failure(error: Throwable): Transition.Failure {
                dispatch.shutdown()
                return Transition.Failure(close(), error)
            }

            val cameraId = try {
                CameraId.backFacing(cameraManager)
            } catch (error: BackFacingCameraException) {
                return@stateTransition failure(error)
            }

            Logger.d(LOG_TAG, "Opening camera preview: cameraId=${cameraId.value}")

            val camera = try {
                cameraManager.awaitOpenCamera(cameraId, dispatch)
            } catch (error: CameraOpenException) {
                return@stateTransition failure(error)
            }

            val session = DeviceOpened(
                cameraId = cameraId,
                camera = camera,
                dispatch = dispatch,
                previewSurface = previewSurface,
            )

            Transition.Next(session)
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

        suspend fun configureSession(): Transition<SessionConfigured> = stateTransition {
            Logger.d(LOG_TAG, "Configuring camera capture session: cameraId=${cameraId.value}")

            val captureSession = try {
                camera.awaitCreateCaptureSession(previewSurface, dispatch)
            } catch (error: CameraSessionConfigureException) {
                return@stateTransition Transition.Failure(close(), error)
            }

            val session = SessionConfigured(
                cameraId = cameraId,
                camera = camera,
                captureSession = captureSession,
                dispatch = dispatch,
                previewSurface = previewSurface,
            )

            Transition.Next(session)
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

        suspend fun startPreview(): Transition<PreviewRunning> = stateTransition {
            val requestSequenceId = try {
                captureSession.startRepeatingPreview(camera, previewSurface, dispatch)
            } catch (error: CameraPreviewStartException) {
                return@stateTransition Transition.Failure(close(), error)
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

            Transition.Next(session)
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
