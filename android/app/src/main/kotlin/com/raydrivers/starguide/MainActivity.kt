package com.raydrivers.starguide

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val result = CoreBridge.add(2, 3)

        setContentView(TextView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            gravity = Gravity.CENTER
            @SuppressLint("SetTextI18n")
            text = "core_add(2, 3) = $result"
            textSize = 32f
            setTypeface(typeface, Typeface.BOLD)
        })
    }
}
