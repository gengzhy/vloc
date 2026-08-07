package com.vloc.feature.weather

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.vloc.util.AppLogger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * 真实定位提供者：基于高德定位 SDK 的单次定位封装。
 *
 * 仅服务于天气模块，获取【设备真实位置】（与首页模拟目标点无关）。
 * 定位结束立即 stopLocation + onDestroy 释放资源，避免常驻耗电。
 */
object RealLocationProvider {

    private const val TAG = "RealLocationProvider"
    private const val TIMEOUT_MS = 8000L

    /** 整体兜底超时：SDK 不回调时转为定位失败，避免 UI 永远卡在 Loading */
    private const val OVERALL_TIMEOUT_MS = 12_000L

    /** adcode 缺失时逆地理回填的超时 */
    private const val REGEO_TIMEOUT_MS = 5_000L

    /**
     * 单次定位。
     *
     * @return 成功返回 [RealLocation]；权限缺失或定位失败返回 failure
     */
    suspend fun locateOnce(context: Context): Result<RealLocation> {
        AppLogger.d(TAG, "开始单次真实定位")
        return try {
            withTimeout(OVERALL_TIMEOUT_MS.milliseconds) { doLocate(context) }
        } catch (e: TimeoutCancellationException) {
            AppLogger.e(TAG, "定位超时（${OVERALL_TIMEOUT_MS}ms 内无回调）：$e")
            Result.failure(LocationException.LocateFailed("定位超时"))
        }
    }

    private suspend fun doLocate(context: Context): Result<RealLocation> {
        val appContext = context.applicationContext

        // 权限前置检查：无权限直接失败，避免 SDK 报错路径
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            AppLogger.w(TAG, "定位权限缺失（FINE/COARSE 均未授权）")
            return Result.failure(LocationException.NoPermission())
        }

        // 隐私合规（SDK 强制要求，先于 client 创建）
        AMapLocationClient.updatePrivacyShow(appContext, true, true)
        AMapLocationClient.updatePrivacyAgree(appContext, true)

        val client = try {
            AMapLocationClient(appContext)
        } catch (e: Exception) {
            AppLogger.e(TAG, "AMapLocationClient 初始化失败", e)
            return Result.failure(LocationException.LocateFailed("定位初始化失败"))
        }

        val option = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            isOnceLocation = true
            isNeedAddress = true
            httpTimeOut = TIMEOUT_MS
            isMockEnable = false // 天气取真实位置，忽略模拟定位
        }
        client.setLocationOption(option)

        val located = suspendCancellableCoroutine { cont ->
            client.setLocationListener { location ->
                if (cont.isActive) {
                    if (location != null && location.errorCode == 0) {
                        cont.resume(Result.success(location))
                    } else {
                        AppLogger.w(
                            TAG,
                            "定位回调失败：errorCode=${location?.errorCode}，" +
                                "info=${location?.errorInfo}"
                        )
                        cont.resume(
                            Result.failure(
                                LocationException.LocateFailed(
                                    location?.errorInfo ?: "定位失败"
                                )
                            )
                        )
                    }
                }
                release(client)
            }

            cont.invokeOnCancellation { release(client) }
            client.startLocation()
        }

        val raw = located.getOrNull()
            ?: return Result.failure(
                located.exceptionOrNull() ?: LocationException.LocateFailed("定位失败")
            )
        AppLogger.i(
            TAG,
            "定位成功：${buildLocationText(raw)}，adcode=${raw.adCode.orEmpty()}，" +
                "GPS海拔=${raw.altitude}m"
        )

        // 逆地理信息缺失（缓存/GPS 单点 fix 不带地址）时，用搜索 SDK 逆地理回填
        var adcode = raw.adCode.orEmpty()
        var locationText = buildLocationText(raw)
        if (adcode.isEmpty()) {
            AppLogger.w(TAG, "定位结果无 adcode，尝试逆地理编码回填")
            val fallback = fetchAdcodeFallback(appContext, raw)
            if (fallback != null) {
                adcode = fallback.first
                if (fallback.second.isNotEmpty()) {
                    locationText = fallback.second
                }
                AppLogger.i(TAG, "逆地理回填成功：$locationText，adcode=$adcode")
            } else {
                AppLogger.w(TAG, "逆地理回填失败，保持坐标文案降级")
            }
        }

        return Result.success(
            RealLocation(
                latitude = raw.latitude,
                longitude = raw.longitude,
                adcode = adcode,
                locationText = locationText,
                altitude = raw.altitude
            )
        )
    }

    /**
     * 逆地理回填：定位成功但 adcode 缺失时，用搜索 SDK 逆地理编码补全。
     *
     * 与天气查询同 Key 同平台（Android 平台 Key），隐私合规已在 MainActivity 统一声明。
     *
     * @return adcode 与可读位置文案；失败/超时返回 null（不阻断主流程）
     */
    private suspend fun fetchAdcodeFallback(
        context: Context,
        location: AMapLocation
    ): Pair<String, String>? = try {
        withTimeoutOrNull(REGEO_TIMEOUT_MS.milliseconds) {
            suspendCancellableCoroutine { cont ->
                try {
                    val search = GeocodeSearch(context)
                    val query = RegeocodeQuery(
                        LatLonPoint(location.latitude, location.longitude),
                        1000f,
                        GeocodeSearch.AMAP
                    )
                    search.setOnGeocodeSearchListener(
                        object : GeocodeSearch.OnGeocodeSearchListener {
                            override fun onRegeocodeSearched(
                                result: RegeocodeResult?,
                                rCode: Int
                            ) {
                                if (!cont.isActive) return
                                val address = result?.regeocodeAddress
                                val regeoAdcode = address?.adCode.orEmpty()
                                if (rCode == 1000 && regeoAdcode.isNotEmpty()) {
                                    val city = address?.city.orEmpty()
                                    val district = address?.district.orEmpty()
                                    val text = when {
                                        city.isNotEmpty() && district.isNotEmpty() -> "$city·$district"
                                        city.isNotEmpty() -> city
                                        else -> address?.formatAddress.orEmpty()
                                    }
                                    cont.resume(regeoAdcode to text)
                                } else {
                                    AppLogger.w(TAG, "逆地理回填失败：rCode=$rCode")
                                    cont.resume(null)
                                }
                            }

                            override fun onGeocodeSearched(
                                result: GeocodeResult?,
                                rCode: Int
                            ) {
                                // 正向地理编码回调，不使用
                            }
                        }
                    )
                    search.getFromLocationAsyn(query)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "逆地理回填异常", e)
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    } catch (e: Exception) {
        AppLogger.e(TAG, "逆地理回填异常", e)
        null
    }

    /**
     * 位置文案组装：市·区 → 省·市 → 地址 → 经纬度，逐级降级。
     */
    private fun buildLocationText(
        location: AMapLocation
    ): String {
        val city = location.city.orEmpty()
        val district = location.district.orEmpty()
        val province = location.province.orEmpty()
        val address = location.address.orEmpty()

        return when {
            city.isNotEmpty() && district.isNotEmpty() -> "$city·$district"
            city.isNotEmpty() -> city
            province.isNotEmpty() -> province
            address.isNotEmpty() -> address
            else -> String.format(
                java.util.Locale.US, "%.2f, %.2f", location.latitude, location.longitude
            )
        }
    }

    private fun release(client: AMapLocationClient) {
        try {
            client.stopLocation()
            client.onDestroy()
        } catch (_: Exception) {
            // 释放失败不影响主流程
        }
    }

    /** 定位相关异常，便于上层映射可读文案 */
    sealed class LocationException(message: String) : Exception(message) {
        class NoPermission : LocationException("no_permission")
        class LocateFailed(val detail: String) : LocationException("locate_failed")
    }
}
