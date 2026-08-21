package com.raydrivers.starguide

object CoreBridge {
    init {
        System.loadLibrary("core")
    }

    external fun add(a: Int, b: Int): Int
}
