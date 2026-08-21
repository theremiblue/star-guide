/*
 * Copyright 2026 Dmitry Vasyliev
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.raydrivers.starguide

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Surface
import android.view.View
import android.view.ViewGroup

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class MainActivity : ComponentActivity() {
    companion object {
        private const val LOG_TAG = "MainActivity"
    }

    private val previewView: PreviewView by lazy {
        PreviewView(this, previewListener).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    private var cameraPreviewSession: CameraPreviewSession = CameraPreviewSession.Closed

    private val previewListener = object : PreviewView.Listener {
        override fun onPreviewSurfaceAvailable(surface: PreviewSurface.Active) {
            if (!hasCameraPermission()) return

            // TODO: I don't like multiple paths leading here
            startCameraPreview(surface)
        }

        override fun onPreviewSurfaceDestroyed() {
            Logger.d(LOG_TAG, "preview destroyed")

            stopCameraPreview()
        }
    }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            when (granted) {
                true -> onCameraPermissionGranted()
                false -> onCameraPermissionDenied()
            }
        }

    internal fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    private fun showCameraPermissionRationale() {
        /*
         * TODO: guide user through the permissions:
         *  - https://developer.android.com/training/permissions/explaining-access
         *  - https://developer.android.com/training/permissions/requesting
         * For now implementing only the permission request itself
         */
    }

    private fun resolveCameraPermission() {
        when {
            hasCameraPermission() -> {
                onCameraPermissionGranted()
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            ) -> run {
                showCameraPermissionRationale()
            }

            else -> {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun onCameraPermissionDenied() {
        check(!hasCameraPermission()) {
            "onCameraPermissionDenied called, but camera permission is granted"
        }

        Logger.w(LOG_TAG, "Camera permission denied")
    }

    private fun onCameraPermissionGranted() {
        check(hasCameraPermission()) {
            "onCameraPermissionGranted called, but camera permission is denied"
        }

        Logger.d(LOG_TAG, "Camera permission granted")
    }

    internal fun startCameraPreview(surface: PreviewSurface.Active) {
        check(hasCameraPermission()) {
            "startCameraPreview called without camera permission"
        }
        Logger.d(LOG_TAG, "Starting camera preview")

        cameraPreviewSession = when (val session = cameraPreviewSession) {
            CameraPreviewSession.Closed -> {
                Logger.d(LOG_TAG, "Starting camera preview")

                @SuppressLint("MissingPermission", "Already checked above, linter doesn't recognize")
                CameraPreviewSession.open(
                    context = this,
                    previewSurface = surface,
                    transition = { next ->
                        cameraPreviewSession = next
                    },
                )
            }

            is CameraPreviewSession.Opening -> {
                session
            }

            is CameraPreviewSession.Running -> {
                session
            }
        }
    }

    internal fun stopCameraPreview() {
        Logger.d(LOG_TAG, "Stop camera preview")
        cameraPreviewSession = cameraPreviewSession.close()
    }

    private fun getContentView(): View {
        return previewView
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(getContentView())

        resolveCameraPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()

        // UI-related, don't fully understand yet
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onResume() {
        super.onResume()

        // TODO: how to restart preview?

        // Foreground only work goes here, camera sensor, reading, etc
    }

    override fun onPause() {
        stopCameraPreview()

        // no heavy cleanup here

        super.onPause()
    }
}
