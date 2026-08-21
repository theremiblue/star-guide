/*
 * Copyright 2026 Dmitry Vasyliev
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.raydrivers.starguide

object CoreBridge {
    init {
        System.loadLibrary("core")
    }

    external fun add(a: Int, b: Int): Int
}
