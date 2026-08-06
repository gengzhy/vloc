package com.vloc.feature.weather

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vloc.R
import com.vloc.feature.altitude.AltitudeCalibration
import com.vloc.util.AppLogger
import com.vloc.util.NetworkUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 天气卡片 ViewModel：串联「真实定位 → 实时天气查询 → UI 状态」。
 *
 * 全部逻辑收敛在 feature.weather 包内，宿主页面零感知。
 */
class WeatherViewModel : ViewModel() {

    companion object {
        private const val TAG = "WeatherViewModel"
    }

    private val repository: WeatherRepository = AmapWeatherRepository()

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private var loading = false

    /**
     * 加载「实际位置 + 实时气温」。重复调用自动去重（加载中忽略）。
     */
    fun load(context: Context, apiKey: String?) {
        if (loading) {
            AppLogger.d(TAG, "load 重复调用，忽略")
            return
        }
        if (apiKey.isNullOrBlank()) {
            AppLogger.w(TAG, "未配置高德 Key，跳过加载")
            _uiState.value = WeatherUiState.Error(R.string.weather_no_key)
            return
        }
        if (!NetworkUtil.isNetAvailable(context)) {
            AppLogger.w(TAG, "网络不可用，跳过加载")
            _uiState.value = WeatherUiState.Error(R.string.weather_no_network)
            return
        }

        AppLogger.i(TAG, "开始加载：定位 + 天气")
        loading = true
        _uiState.value = WeatherUiState.Loading

        viewModelScope.launch {
            val locationResult = RealLocationProvider.locateOnce(context)
            val location = locationResult.getOrNull()

            if (location == null) {
                val e = locationResult.exceptionOrNull()
                AppLogger.w(TAG, "定位失败：${e?.message}")
                loading = false
                _uiState.value = WeatherUiState.Error(
                    mapLocationErrorRes(e)
                )
                return@launch
            }

            // 定位成功即上报 GPS 海拔，供海拔模块气压校准（与天气查询无关）
            if (location.altitude != 0.0) {
                AltitudeCalibration.reportGpsAltitude(location.altitude)
            }

            // adcode 缺失时降级：仅展示位置，温度占位
            if (location.adcode.isEmpty()) {
                AppLogger.w(TAG, "定位成功但 adcode 为空，降级展示位置")
                loading = false
                _uiState.value = WeatherUiState.Success(
                    locationText = location.locationText,
                    temperature = "--",
                    weather = ""
                )
                return@launch
            }

            val weatherResult = repository.fetchRealtimeWeather(context, location.adcode)
            loading = false

            weatherResult.fold(
                onSuccess = { weather ->
                    AppLogger.i(
                        TAG,
                        "加载完成：${location.locationText} ${weather.temperature}℃ ${weather.weather}"
                    )
                    _uiState.value = WeatherUiState.Success(
                        locationText = location.locationText,
                        temperature = weather.temperature,
                        weather = weather.weather,
                        humidity = weather.humidity,
                        windDirection = weather.windDirection,
                        windPower = weather.windPower
                    )
                },
                onFailure = { e ->
                    AppLogger.w(TAG, "天气查询失败，降级展示位置：${e.message}")
                    _uiState.value = WeatherUiState.Success(
                        locationText = location.locationText,
                        temperature = "--",
                        weather = ""
                    )
                }
            )
        }
    }

    private fun mapLocationErrorRes(e: Throwable?): Int = when (e) {
        is RealLocationProvider.LocationException.NoPermission -> R.string.weather_no_permission
        is RealLocationProvider.LocationException.LocateFailed -> R.string.weather_locate_failed
        else -> R.string.weather_load_failed
    }
}
