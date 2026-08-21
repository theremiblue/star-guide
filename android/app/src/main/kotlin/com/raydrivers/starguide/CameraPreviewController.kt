/*
 * Copyright 2026 Dmitry Vasyliev
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.raydrivers.starguide

import android.content.Context

import com.raydrivers.starguide.CameraPreviewSession
import com.raydrivers.starguide.PreviewSurface

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

    // TODO: should check permissions also
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
            session = session.close()
        }

        return session is CameraPreviewSession.PreviewRunning
    }

    fun stop() {
        session = session.close()
    }
}
