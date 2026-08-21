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

        // TODO: am I correct that only PreviewView can release the surface?
        //       as I understand - this is perfect, because it's tied to Texture lifetime
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
    private val listener: Listener,
) : TextureView(context) {
    interface Listener {
        fun onPreviewSurfaceAvailable(surface: PreviewSurface.Active)
        fun onPreviewSurfaceDestroyed()
    }

    private var surface: PreviewSurface = PreviewSurface.Unavailable

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
                check(surface is PreviewSurface.Unavailable) {
                    "SurfaceTexture became available while preview surface already exists"
                }

                // TODO: not 100% sure about this too, also (like below) workaround for types
                val newSurface = PreviewSurface.create(surfaceTexture)
                surface = newSurface

                listener.onPreviewSurfaceAvailable(newSurface)
            }

            override fun onSurfaceTextureDestroyed(
                surfaceTexture: SurfaceTexture,
            ): Boolean {
                // TODO: I'm not 100% sure that this is safe
                //       This is a workaround to allow smartcast in Active branch
                when (val state = surface) {
                    PreviewSurface.Unavailable -> {
                        // Defensive. Destruction without availability is odd,
                        // but not useful to crash on yet.
                    }

                    is PreviewSurface.Active -> {
                        // TODO: document the order of operations and intended use
                        listener.onPreviewSurfaceDestroyed()
                        surface = state.release()
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
