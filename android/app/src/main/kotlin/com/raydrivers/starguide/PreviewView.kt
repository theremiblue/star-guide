/*
 * Copyright 2026 Dmitry Vasyliev
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.raydrivers.starguide

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.annotation.CheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


/// Owns Surface instance attached to SurfaceTexture.
sealed interface PreviewSurface {
    companion object {
        fun create(surfaceTexture: SurfaceTexture): Active {
            val surface = Surface(surfaceTexture)
            check(surface.isValid) { "Created invalid preview Surface" }
            return Active(surface)
        }
    }

    class Active internal constructor(
        internal val rawSurface: Surface,
    ) : PreviewSurface {
        val surface: Surface
            get() = rawSurface

        @CheckResult
        internal fun release() : Unavailable {
            rawSurface.release()
            return Unavailable
        }
    }

    data object Unavailable : PreviewSurface
}

@SuppressLint("ViewConstructor")
class PreviewView(
    context: Context,
    private val listener: Listener = Listener {},
) : TextureView(context) {
    fun interface Listener {
        /// All of the users of surface should stop using it before this function returns
        fun onPreviewSurfaceDestroyed()
    }

    // TODO: is the synchronization needed for access to this variable?
    //       we have no guarantees on callback execution, theoretically
    //       invalid surface can be passed to listener
    private val _surface = MutableStateFlow<PreviewSurface>(PreviewSurface.Unavailable)
    val surface: StateFlow<PreviewSurface> = _surface.asStateFlow()

    init {
        surfaceTextureListener = createSurfaceTextureListener()
    }

    private fun createSurfaceTextureListener()
        = object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int,
            ) {
                check(_surface.value is PreviewSurface.Unavailable) {
                    "SurfaceTexture became available while preview surface already exists"
                }

                val active = PreviewSurface.create(surfaceTexture)
                _surface.value = active
            }

            override fun onSurfaceTextureDestroyed(
                surfaceTexture: SurfaceTexture,
            ): Boolean {
                when (val state = _surface.value) {
                    PreviewSurface.Unavailable -> {
                        // Defensive. Destruction without availability is odd,
                        // but not useful to crash on yet.
                    }

                    is PreviewSurface.Active -> {
                        // Let users act on it, then release the resources
                        listener.onPreviewSurfaceDestroyed()
                        _surface.value = state.release()
                    }
                }

                return true
            }

            override fun onSurfaceTextureSizeChanged(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int,
            ) = Unit

            override fun onSurfaceTextureUpdated(
                surfaceTexture: SurfaceTexture,
            ) = Unit
        }
}
