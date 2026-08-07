package com.vloc.feature.altitude

import android.annotation.SuppressLint
import com.vloc.util.AppLogger

/**
 * 海拔校准源：天气模块定位成功后上报 GPS 海拔，海拔模块消费使用。
 *
 * 气压→海拔换算需要「当地实时海平面气压（QNH）」做基准，
 * 标准大气假设会带来数百米偏差。本对象承载校准对中的参考海拔：
 * [AltitudeViewModel] 用「新鲜 GPS 海拔 + 配对气压」反推 QNH。
 *
 * 跨 feature 单向依赖：weather → altitude（仅 [reportGpsAltitude] 一个调用点）。
 */
object AltitudeCalibration {

    private const val TAG = "AltitudeCalibration"

    private var gpsAltitude: Double? = null
    private var reportTimeMs: Long = 0L

    /**
     * 上报 GPS 海拔（天气模块定位成功时调用）。
     * 每次上报都会更新，允许反复校准。
     *
     * @param altitude 定位 SDK 提供的海拔（米）；无效值（0/异常）忽略
     */
    @SuppressLint("DefaultLocale")
    fun reportGpsAltitude(altitude: Double) {
        if (altitude <= 0.0 || altitude >= 9000.0) {
            AppLogger.w(TAG, "GPS海拔无效，忽略: ${altitude}m")
            return
        }
        AppLogger.i(TAG, "GPS海拔上报: ${String.format("%.1f", altitude)}m")
        gpsAltitude = altitude
        reportTimeMs = System.currentTimeMillis()
    }

    /**
     * 读取新鲜（[maxAgeMs] 内）的 GPS 海拔；过期/无数据返回 null。
     * 一次性消费，避免重复校准。
     */
    @Synchronized
    fun takeFresh(maxAgeMs: Long = 60_000L): Double? {
        val alt = gpsAltitude ?: return null
        if (System.currentTimeMillis() - reportTimeMs > maxAgeMs) {
            AppLogger.d(TAG, "GPS海拔已过期，清除")
            gpsAltitude = null
            return null
        }
        gpsAltitude = null
        return alt
    }

    /**
     * 仅查询当前 GPS 海拔（不消费、不过期检查），供日志/调试使用。
     */
    @Synchronized
    fun peekGpsAltitude(): Double? = gpsAltitude
}