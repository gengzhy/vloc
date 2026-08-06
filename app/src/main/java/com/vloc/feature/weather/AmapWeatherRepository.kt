package com.vloc.feature.weather

import android.content.Context
import com.amap.api.services.weather.LocalWeatherForecastResult
import com.amap.api.services.weather.LocalWeatherLiveResult
import com.amap.api.services.weather.WeatherSearch
import com.amap.api.services.weather.WeatherSearchQuery
import com.vloc.util.AppLogger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * 高德天气实现：基于内置 AMap Search SDK 的天气查询
 * （复用应用级 Android 平台 Key，与地图/定位/逆地理编码同 Key 同平台，
 * 避免 Web 服务 REST 接口对 Key 平台类型的额外要求）。
 *
 * 封装范式与 [RealLocationProvider] 保持一致：
 * 异步回调 → suspendCancellableCoroutine 桥接，withTimeout 兜底防 SDK 不回调挂起。
 */
class AmapWeatherRepository : WeatherRepository {

    companion object {
        private const val TAG = "AmapWeatherRepository"
        private const val TIMEOUT_MS = 10_000L
        private const val SUCCESS_CODE = 1000
    }

    override suspend fun fetchRealtimeWeather(
        context: Context,
        adcode: String
    ): Result<WeatherInfo> {
        AppLogger.d(TAG, "发起天气查询，adcode=$adcode")
        val search = try {
            WeatherSearch(context.applicationContext)
        } catch (e: Exception) {
            AppLogger.e(TAG, "WeatherSearch 初始化失败", e)
            return Result.failure(e)
        }
        search.query = WeatherSearchQuery(adcode, WeatherSearchQuery.WEATHER_TYPE_LIVE)

        return try {
            withTimeout(TIMEOUT_MS.milliseconds) {
                suspendCancellableCoroutine { cont ->
                    search.setOnWeatherSearchListener(
                        object : WeatherSearch.OnWeatherSearchListener {
                            override fun onWeatherLiveSearched(
                                result: LocalWeatherLiveResult?,
                                rCode: Int
                            ) {
                                if (!cont.isActive) return
                                val live = result?.liveResult
                                if (rCode == SUCCESS_CODE && live != null) {
                                    AppLogger.i(
                                        TAG,
                                        "天气查询成功：${live.temperature}℃ ${live.weather}，" +
                                            "湿度=${live.humidity}，风向=${live.windDirection}，" +
                                            "风力=${live.windPower}"
                                    )
                                    cont.resume(
                                        Result.success(
                                            WeatherInfo(
                                                temperature = live.temperature ?: "--",
                                                weather = live.weather.orEmpty(),
                                                humidity = live.humidity.orEmpty(),
                                                windDirection = live.windDirection.orEmpty(),
                                                windPower = live.windPower.orEmpty()
                                            )
                                        )
                                    )
                                } else {
                                    AppLogger.w(
                                        TAG,
                                        "天气查询失败：rCode=$rCode，liveResult=${live != null}"
                                    )
                                    cont.resume(
                                        Result.failure(
                                            IllegalStateException("天气查询失败，错误码：$rCode")
                                        )
                                    )
                                }
                            }

                            override fun onWeatherForecastSearched(
                                result: LocalWeatherForecastResult?,
                                rCode: Int
                            ) {
                                // 仅查询实况天气，忽略预报回调
                            }
                        }
                    )
                    search.searchWeatherAsyn()
                }
            }
        } catch (e: TimeoutCancellationException) {
            AppLogger.e(TAG, "天气查询超时（${TIMEOUT_MS}ms 无回调）：$e")
            Result.failure(e)
        } catch (e: Exception) {
            AppLogger.e(TAG, "天气查询异常", e)
            Result.failure(e)
        }
    }
}
