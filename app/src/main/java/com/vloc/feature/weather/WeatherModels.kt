package com.vloc.feature.weather

/**
 * 真实定位结果（设备实际位置，与首页模拟目标点严格区分）。
 *
 * @param latitude     纬度
 * @param longitude    经度
 * @param adcode       行政区划编码（天气查询入参）
 * @param locationText 可读位置文案（如"深圳市·南山区"）
 * @param altitude     GPS 海拔（米，定位 SDK 提供；缺失为 0.0，供海拔模块校准）
 */
data class RealLocation(
    val latitude: Double,
    val longitude: Double,
    val adcode: String,
    val locationText: String,
    val altitude: Double = 0.0
)

/**
 * 实时天气结果。
 *
 * @param temperature   气温（℃，字符串）
 * @param weather       天气现象（如"多云"）
 * @param humidity      湿度（%，字符串）
 * @param windDirection 风向（如"北风"）
 * @param windPower     风力等级（如"1级"）
 */
data class WeatherInfo(
    val temperature: String,
    val weather: String,
    val humidity: String,
    val windDirection: String,
    val windPower: String
)

/**
 * 天气卡片 UI 状态。
 */
sealed interface WeatherUiState {
    /** 定位/查询进行中 */
    data object Loading : WeatherUiState

    /** 成功：位置 + 气温 + 展开详情（湿度/风向/风力） */
    data class Success(
        val locationText: String,
        val temperature: String,
        val weather: String,
        val humidity: String = "",
        val windDirection: String = "",
        val windPower: String = ""
    ) : WeatherUiState

    /** 失败：展示可读原因（字符串资源 ID），点击卡片可重试 */
    data class Error(val messageResId: Int) : WeatherUiState
}
