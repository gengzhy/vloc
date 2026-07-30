package com.vloc.util

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import kotlin.system.exitProcess

fun ComponentActivity.setupDoubleBackExit(intervalMs: Long = 1000) {
    var lastBackPressedTime = 0L
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val now = System.currentTimeMillis()
            if (now - lastBackPressedTime < intervalMs) {
                finishAffinity()
                exitProcess(0)
            } else {
                lastBackPressedTime = now
                Toast.makeText(
                    this@setupDoubleBackExit,
                    "再按一次退出程序",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    })
}
