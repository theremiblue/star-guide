/*
 * Copyright 2026 Dmitry Vasyliev
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.raydrivers.starguide

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    companion object {
        private const val LOG_TAG = "MainActivity"
    }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted -> when (granted) {
            true  -> onCameraPermissionGranted()
            false -> onCameraPermissionDenied()
        }
    }

    private lateinit var cameraPreview: CameraPreview


    private fun hasCameraPermission(): Boolean =
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

        Log.w(LOG_TAG, "Camera permission denied")
    }

    private fun onCameraPermissionGranted() {
        check(hasCameraPermission()) {
            "onCameraPermissionGranted called, but camera permission is denied"
        }

        Log.d(LOG_TAG, "Camera permission granted")
    }

    private fun getContentView(): View {
        val result = CoreBridge.add(2, 3)

        return TextView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            gravity = Gravity.CENTER
            @SuppressLint("SetTextI18n")
            text = "core_add(2, 3) = $result"
            textSize = 32f
            setTypeface(typeface, Typeface.BOLD)
        }
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
        // Foreground only work goes here, camera sensor, reading, etc
    }

    override fun onPause() {
        super.onPause()
        // no heavy cleanup here
    }
}
