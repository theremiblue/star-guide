/*
 * Copyright 2026 Dmitry Vasyliev
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.remiblue.starguide

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission

/**
 * This class acts as an owner and orchestrator for camera preview flow.
 *
 * This level handles all underlying errors.
 */
class CameraPreviewController {
    companion object {
        private const val LOG_TAG = "CameraPreviewController"
    }

    private var session: CameraPreviewSession = CameraPreviewSession.Closed

    // TODO: would be very neat to have this outside of this class
    private suspend inline fun <
        S: CameraPreviewSession,
        R: CameraPreviewSession
    > S.transition(
        block: suspend S.() -> R,
    ): R {
        val next = block()
        session = next
        return next
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    suspend fun start(
        context: Context,
        surface: PreviewSurface.Active
    ): Boolean {
        check(session is CameraPreviewSession.Closed) {
            "start called with active camera preview session"
        }

        Logger.d(LOG_TAG, "Starting camera preview")
        try {
            CameraPreviewSession.Closed
                .transition { open(context, surface) }
                .transition { configureSession() }
                .transition { startPreview() }
        } catch (error: Throwable) {
            Logger.e(LOG_TAG, "Failed to start camera preview: ${error.toString()}")
            session = session.close()
        }

        return session is CameraPreviewSession.PreviewRunning
    }

    fun stop() {
        Logger.d(LOG_TAG, "Stopping camera preview")
        session = session.close()
    }
}
