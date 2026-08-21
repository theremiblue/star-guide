/*
 * Copyright 2026 Dmitry Vasyliev
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.remiblue.starguide

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class CameraPermissionState {
    Unknown,
    Denied,
    Granted,
}

class MainActivityViewModel : ViewModel() {
    private val _cameraPermission = MutableStateFlow(CameraPermissionState.Unknown)

    val cameraPermission = _cameraPermission.asStateFlow()

    fun updateCameraPermission(granted: Boolean) {
        _cameraPermission.value = if (granted) {
            CameraPermissionState.Granted
        } else {
            CameraPermissionState.Denied
        }
    }
}

sealed interface PreviewGate {
    data object NotReady : PreviewGate

    data class Ready(
        val surface: PreviewSurface.Active,
    ) : PreviewGate
}

fun previewReadiness(
    permission: StateFlow<CameraPermissionState>,
    surface: StateFlow<PreviewSurface>,
): Flow<PreviewGate> =
    combine(
        permission,
        surface,
    ) { permissionState, surfaceState ->
        when {
            permissionState != CameraPermissionState.Granted -> PreviewGate.NotReady
            surfaceState !is PreviewSurface.Active -> PreviewGate.NotReady

            else -> PreviewGate.Ready(surfaceState)
        }
    }.distinctUntilChanged()

class MainActivity : ComponentActivity() {
    companion object {
        private const val LOG_TAG = "MainActivity"
    }

    private val viewModel: MainActivityViewModel by viewModels()

    private var cameraPreviewController: CameraPreviewController = CameraPreviewController()

    private val previewView: PreviewView by lazy {
        PreviewView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    private val permissionDeniedView: View by lazy {
        TextView(this).apply {
            @SuppressLint("SetTextI18N")
            text = "Camera permission is not granted"
            gravity = Gravity.CENTER

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    private val readiness by lazy {
            previewReadiness(
                permission = viewModel.cameraPermission,
                surface = previewView.surface,
            )
        }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.updateCameraPermission(granted)

            if (granted) {
                Logger.d(LOG_TAG, "Camera permission granted")
            } else {
                Logger.w(LOG_TAG, "Camera permission denied")
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
                viewModel.updateCameraPermission(true)
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

    // TODO: lifecycle doesn't work
    // Current problem is when you revoke permission in the background in settings - app crashes
    // also, you don't see permission denied text
    private fun observeCameraPreview() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                readiness.collectLatest { readiness ->
                    when (readiness) {
                        PreviewGate.NotReady -> Unit
                        is PreviewGate.Ready -> {
                            // The permission is guaranteed to be granted here
                            @SuppressLint("MissingPermission")
                            startCameraPreview(readiness.surface)
                        }
                    }
                }
            }
        }
    }

    private fun observeCameraPermission() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.cameraPermission.collect { permission ->
                    updateContentView(permission)
                }
            }
        }
    }

    private fun updateContentView(
        permission: CameraPermissionState,
    ) {
        when (permission) {
            CameraPermissionState.Granted ->
                setContentView(previewView)

            CameraPermissionState.Unknown,
            CameraPermissionState.Denied ->
                setContentView(permissionDeniedView)
        }
    }

    // TODO: cancellation for repeatOnLifecycle should be handled somehow
    // preview should be stopped when activity is not in foreground
    @RequiresPermission(Manifest.permission.CAMERA)
    suspend fun startCameraPreview(surface: PreviewSurface.Active) {
        val started = cameraPreviewController.start(this, surface)

        if (!started) {
            Logger.e(LOG_TAG, "Failed to start camera preview")
            return
        }
    }

    internal fun stopCameraPreview() {
        cameraPreviewController.stop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        observeCameraPreview()
        observeCameraPermission()

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

        /// Permission can be changed in the settings while activity wasn't in foreground
        viewModel.updateCameraPermission(hasCameraPermission())
        // TODO: how to restart preview?

        // Foreground only work goes here, camera sensor, reading, etc
    }

    override fun onPause() {
        stopCameraPreview()

        // no heavy cleanup here

        super.onPause()
    }
}
