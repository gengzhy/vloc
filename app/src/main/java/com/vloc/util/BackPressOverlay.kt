package com.vloc.util

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * 通用返回拦截浮层包装器：弹层打开期间拦截返回键执行 [onBackPress]（通常是关闭自身），
 * 不打开时不注册拦截，返回键穿透到更底层（如 Activity 的双击退出）。
 *
 * 依赖系统返回分发器的 LIFO 语义：后注册的回调先被派发，
 * 因此任意数量的弹窗/子页面各自包一层即可自动按叠层顺序逐个关闭，
 * 无需维护任何中心化的关闭优先级清单——新增浮层零改动接入。
 *
 * 用法：
 * ```
 * if (showXxxDialog) {
 *     BackPressOverlay(onBackPress = { showXxxDialog = false }) {
 *         AlertDialog(onDismissRequest = { showXxxDialog = false }, ...)
 *     }
 * }
 * ```
 */
@Composable
fun BackPressOverlay(enabled: Boolean = true, onBackPress: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBackPress)
    content()
}
