package com.vloc.feature.altitude

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.vloc.util.AppLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * GPS 海拔提供者：通过系统原生 [LocationManager]（GPS_PROVIDER）主动获取一次带海拔的定位。
 *
 * 背景：气压→海拔换算需要「当地实时海平面气压（QNH）」做基准，
 * 此前校准依赖高德定位顺带上报的海拔，但高德缓存/WiFi 定位回调海拔恒为 0.0，
 * 导致校准永远失败，海拔只能用标准大气压(1013.25hPa)兜底，产生数百米偏差。
 *
 * 本类直接走 Android 系统定位接口获取 GPS 海拔（WGS84 椭球高），
 * 典型误差 ±10~20m，远优于标准大气兜底方案。
 */
object GpsAltitudeProvider {

    private const val TAG = "GpsAltitudeProvider"

    /** 等待实时 GPS fix 的单次超时（冷启动/室内可能需要 30s+） */
    private const val FIX_TIMEOUT_MS = 60_000L

    /**
     * 带海拔的 lastKnownLocation 水平/垂直精度上限（米）。
     * 只要精度达标就接受，不限年龄：海拔校准对位置时效不敏感，
     * 且后续拿到更新的 GPS fix 会重新校准覆盖（[AltitudeViewModel] 允许多次校准）。
     * 室内 GPS 无信号时，旧的高精度带海拔 fix 远优于标准大气兜底（数百米误差）。
     */
    private const val LAST_KNOWN_MAX_ACCURACY_M = 100f

    /**
     * 获取一次 GPS 海拔。
     *
     * 流程：
     * 1. 权限缺失 → 返回 null
     * 2. lastKnownLocation（GPS/网络）新鲜且带海拔 → 直接返回（秒级响应）
     * 3. 否则注册 GPS 监听等首个带海拔的 fix，[FIX_TIMEOUT_MS] 超时返回 null
     *
     * @return 海拔（米）；无法获取返回 null
     */
    suspend fun fetchGpsAltitude(context: Context): Double? {
        val appContext = context.applicationContext
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) {
            AppLogger.w(TAG, "无定位权限，无法获取 GPS 海拔")
            return null
        }

        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // 快速通道：最近一次已知定位若新鲜且带海拔，直接复用
        lastKnownAltitude(lm)?.let { alt ->
            AppLogger.i(TAG, "lastKnownLocation 命中，GPS海拔=${format(alt)}m")
            return alt
        }

        AppLogger.d(TAG, "开始等待 GPS fix（超时 ${FIX_TIMEOUT_MS}ms）")
        val altitude = withTimeoutOrNull(FIX_TIMEOUT_MS.milliseconds) {
            awaitGpsFix(lm)
        }
        if (altitude == null) {
            AppLogger.w(TAG, "GPS fix 超时，未获取到海拔（可能处于室内）")
        } else {
            AppLogger.i(TAG, "GPS fix 成功，海拔=${format(altitude)}m")
        }
        return altitude
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownAltitude(lm: LocationManager): Double? {
        val now = System.currentTimeMillis()
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            val last = try {
                lm.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            }
            if (last == null || !last.hasAltitude()) continue
            // 水平精度过差丢弃（排除粗定位伪 fix；带海拔的 fix 基本都是真 GPS）
            if (last.hasAccuracy() && last.accuracy > LAST_KNOWN_MAX_ACCURACY_M) {
                AppLogger.d(
                    TAG,
                    "lastKnown($provider) 水平精度过差: ${last.accuracy}m，丢弃"
                )
                continue
            }
            // 垂直精度不可信时丢弃（无垂直精度信息的 fix 不限制，如旧机型/API 26 以下）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                last.hasVerticalAccuracy() &&
                last.verticalAccuracyMeters > LAST_KNOWN_MAX_ACCURACY_M
            ) {
                AppLogger.d(
                    TAG,
                    "lastKnown($provider) 垂直精度过差: ${last.verticalAccuracyMeters}m，丢弃"
                )
                continue
            }
            AppLogger.i(
                TAG,
                "lastKnown($provider) 命中：海拔=${format(altitudeOf(last))}m，" +
                    "年龄=${((now - last.time) / 1000)}s，hAcc=${last.accuracy}m"
            )
            return altitudeOf(last)
        }
        return null
    }

    /**
     * 提取海拔：使用 [Location.getAltitude]（WGS84 椭球高）。
     * 不用 mslAlt：多数主流指南针/海拔 APP 展示的也是椭球高，
     * 与用户参照值同口径；椭球高与正高的常数差在同一地点恒定，
     * 校准后不影响气压海拔的相对变化跟踪。
     */
    private fun altitudeOf(location: Location): Double = location.altitude

    /**
     * 注册 GPS 监听，挂起直到拿到首个带海拔的定位；协程取消时自动注销。
     */
    @SuppressLint("MissingPermission")
    private suspend fun awaitGpsFix(lm: LocationManager): Double? =
        suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!location.hasAltitude()) return
                    if (cont.isActive) {
                        cont.resume(altitudeOf(location))
                    }
                    try {
                        lm.removeUpdates(this)
                    } catch (_: Exception) {
                    }
                }

                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                    AppLogger.d(TAG, "GPS状态变化: provider=$provider, status=$status")
                }

                override fun onProviderEnabled(provider: String) {
                    AppLogger.d(TAG, "GPS提供者已启用: $provider")
                }

                override fun onProviderDisabled(provider: String) {
                    AppLogger.w(TAG, "GPS提供者已禁用: $provider")
                }
            }

            try {
                // GPS 主通道：持续更新，直到拿到带海拔的 fix
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
                // 被动通道：搭便车其他 App 的定位结果（室内 GPS 无信号时的救命通道）
                lm.requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "注册 GPS 监听失败", e)
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }

            cont.invokeOnCancellation {
                try {
                    lm.removeUpdates(listener)
                } catch (_: Exception) {
                }
            }
        }

    private fun format(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)
}
