package com.vloc.feature.altitude

/**
 * 海拔校准源：天气模块定位成功后上报 GPS 海拔，海拔模块一次性消费。
 *
 * 气压→海拔换算需要「当地实时海平面气压（QNH）」做基准，
 * 标准大气假设会带来数百米偏差。本对象承载校准对中的参考海拔：
 * [AltitudeViewModel] 用「新鲜 GPS 海拔 + 配对气压」反推 QNH。
 *
 * 跨 feature 单向依赖：weather → altitude（仅 [reportGpsAltitude] 一个调用点）。
 */
object AltitudeCalibration {

    private var gpsAltitude: Double? = null
    private var reportTimeMs: Long = 0L

    /**
     * 上报 GPS 海拔（天气模块定位成功时调用）。
     *
     * @param altitude 定位 SDK 提供的海拔（米）；无效值（0/异常）忽略
     */
    fun reportGpsAltitude(altitude: Double) {
        if (altitude <= -500.0 || altitude >= 9000.0) return
        gpsAltitude = altitude
        reportTimeMs = System.currentTimeMillis()
    }

    /**
     * 读取并清除新鲜（[maxAgeMs] 内）的 GPS 海拔；过期/无数据返回 null。
     * 一次性消费，避免重复校准。
     */
    @Synchronized
    fun takeFresh(maxAgeMs: Long = 60_000L): Double? {
        val alt = gpsAltitude ?: return null
        if (System.currentTimeMillis() - reportTimeMs > maxAgeMs) {
            gpsAltitude = null
            return null
        }
        gpsAltitude = null
        return alt
    }
}
