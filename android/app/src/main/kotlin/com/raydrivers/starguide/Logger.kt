/*
 * Copyright 2026 Dmitry Vasyliev
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.raydrivers.starguide

import android.util.Log

internal object Logger {
    fun d(tag: String, message: String) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (Log.isLoggable(tag, Log.INFO)) {
            Log.i(tag, message)
        }
    }

    fun w(tag: String, message: String) {
        if (Log.isLoggable(tag, Log.WARN)) {
            Log.w(tag, message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (Log.isLoggable(tag, Log.ERROR)) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }
}
