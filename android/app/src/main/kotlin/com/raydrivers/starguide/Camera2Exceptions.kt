/*
 * Copyright 2026 Dmitry Vasyliev
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.raydrivers.starguide

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraDevice

internal fun cameraErrorName(errorCode: Int): String {
    return when (errorCode) {
        CameraDevice.StateCallback.ERROR_CAMERA_IN_USE ->
            "ERROR_CAMERA_IN_USE($errorCode)"

        CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE ->
            "ERROR_MAX_CAMERAS_IN_USE($errorCode)"

        CameraDevice.StateCallback.ERROR_CAMERA_DISABLED ->
            "ERROR_CAMERA_DISABLED($errorCode)"

        CameraDevice.StateCallback.ERROR_CAMERA_DEVICE ->
            "ERROR_CAMERA_DEVICE($errorCode)"

        CameraDevice.StateCallback.ERROR_CAMERA_SERVICE ->
            "ERROR_CAMERA_SERVICE($errorCode)"

        else -> "UNKNOWN($errorCode)"
    }
}

private class CameraDisconnectedCause(
    cameraId: String,
) : RuntimeException(
    "CameraDevice.StateCallback.onDisconnected: cameraId=$cameraId"
)

private class CameraOpenErrorCause(
    cameraId: String,
    errorCode: Int,
) : RuntimeException(
    "CameraDevice.StateCallback.onError: cameraId=$cameraId error=${cameraErrorName(errorCode)}"
)

private class CameraSessionConfigureCause(
    cameraId: String,
) : RuntimeException(
    "CameraCaptureSession.StateCallback.onConfigureFailed: cameraId=$cameraId"
)

internal fun camera2ExtensionMessage(
    message: String,
    cause: Throwable?,
): String {
    if (cause == null) return message

    return "$message cause=${cause.javaClass.name}: ${cause.message}"
}

internal sealed class Camera2ExtensionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(camera2ExtensionMessage(message, cause), cause)

internal class BackFacingCameraException : Camera2ExtensionException {
    constructor(
        cameraCount: Int,
    ) : super("No back-facing camera found: inspectedCameraCount=$cameraCount")

    constructor(
        operation: String,
        cause: CameraAccessException,
    ) : super("Failed to find back-facing camera: operation=$operation", cause)
}

internal class CameraOpenException : Camera2ExtensionException {
    constructor(
        cameraId: String,
    ) : super(
        "Camera disconnected before CameraManager.openCamera completed: cameraId=$cameraId",
        CameraDisconnectedCause(cameraId),
    )

    constructor(
        cameraId: String,
        errorCode: Int,
    ) : super(
        "CameraManager.openCamera callback failed: cameraId=$cameraId error=${cameraErrorName(errorCode)}",
        CameraOpenErrorCause(cameraId, errorCode),
    )

    constructor(
        cameraId: CameraId,
        cause: CameraAccessException,
    ) : super("CameraManager.openCamera failed: cameraId=${cameraId.value}", cause)

    constructor(
        cameraId: CameraId,
        cause: SecurityException,
    ) : super("CameraManager.openCamera denied by camera permission: cameraId=${cameraId.value}", cause)
}

internal class CameraSessionConfigureException : Camera2ExtensionException {
    constructor(
        cameraId: String,
    ) : super(
        "Camera capture session configure callback failed: cameraId=$cameraId",
        CameraSessionConfigureCause(cameraId),
    )

    constructor(
        cameraId: String,
        operation: String,
        cause: CameraAccessException,
    ) : super("Camera capture session failed: cameraId=$cameraId operation=$operation", cause)

    constructor(
        cameraId: String,
        operation: String,
        cause: IllegalArgumentException,
    ) : super("Camera capture session failed: cameraId=$cameraId operation=$operation", cause)

    constructor(
        cameraId: String,
        operation: String,
        cause: IllegalStateException,
    ) : super("Camera capture session failed: cameraId=$cameraId operation=$operation", cause)
}

internal class CameraPreviewStartException : Camera2ExtensionException {
    constructor(
        cameraId: String,
        operation: String,
        cause: CameraAccessException,
    ) : super("Camera preview failed to start: cameraId=$cameraId operation=$operation", cause)

    constructor(
        cameraId: String,
        operation: String,
        cause: IllegalArgumentException,
    ) : super("Camera preview failed to start: cameraId=$cameraId operation=$operation", cause)

    constructor(
        cameraId: String,
        operation: String,
        cause: IllegalStateException,
    ) : super("Camera preview failed to start: cameraId=$cameraId operation=$operation", cause)
}
