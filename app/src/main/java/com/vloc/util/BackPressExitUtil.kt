package com.vloc.util

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import kotlin.system.exitProcess

/**
 * 注册全局「双击返回退出」回调（Activity 层，只调用一次）。
 *
 * 该回调最先注册、优先级最低，是返回键的兜底默认行为；
 * 弹层/子页面通过 [BackPressOverlay]（或 BackHandler）后注册，
 * 由系统返回分发器按「后注册先处理」自动拦截，无需在此感知任何页面。
 *
 * 效果：无任何弹层打开时，连续两次返回退出程序；
 * 有弹层时，返回键先逐个关闭弹层，全部关闭后才回到双击退出。
 */
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
