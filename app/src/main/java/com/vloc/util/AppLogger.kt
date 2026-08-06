package com.vloc.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App 级运行日志：内存环形缓冲（最新在前）+ StateFlow 驱动 UI，
 * 同时同步转发 Logcat（行为与直接用 [Log] 一致）。
 *
 * 设计取舍：仅内存保留最近 [MAX_ENTRIES] 条，不落盘 ——
 * 用于运行时排查（设置抽屉「日志」页查看），与项目现有
 * SharedPreferences 轻量存储风格一致，零新增依赖。
 */
object AppLogger {

    private const val TAG = "AppLogger"
    private const val MAX_ENTRIES = 500

    enum class Level { DEBUG, INFO, WARN, ERROR }

    /**
     * 单条日志。[time] 在写入时格式化，UI 直接展示。
     */
    data class Entry(
        val time: String,
        val level: Level,
        val tag: String,
        val message: String
    )

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val buffer = ArrayDeque<Entry>(MAX_ENTRIES)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message, null)

    fun i(tag: String, message: String) = log(Level.INFO, tag, message, null)

    fun w(tag: String, message: String) = log(Level.WARN, tag, message, null)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.ERROR, tag, message, throwable)

    /** 清空全部日志（UI「清空」按钮） */
    fun clear() {
        synchronized(this) {
            buffer.clear()
            _entries.value = emptyList()
        }
    }

    private fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        // 转发 Logcat（保留 throwable 堆栈）
        when (level) {
            Level.DEBUG -> Log.d(tag, message, throwable)
            Level.INFO -> Log.i(tag, message, throwable)
            Level.WARN -> Log.w(tag, message, throwable)
            Level.ERROR -> Log.e(tag, message, throwable)
        }

        val entry = Entry(
            time = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = if (throwable != null) "$message | $throwable" else message
        )
        synchronized(this) {
            buffer.addFirst(entry)
            while (buffer.size > MAX_ENTRIES) {
                buffer.removeLast()
            }
            _entries.value = buffer.toList()
        }
    }
}
