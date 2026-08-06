package com.vloc.feature.weather

import android.content.Context

/**
 * 天气数据源抽象：数据源可替换点。
 *
 * 当前默认实现为 [AmapWeatherRepository]（高德搜索 SDK 天气接口）。
 * 若后续更换为其他开源数据源（如 Open-Meteo / 和风天气），
 * 只需新增一个实现类并在 ViewModel 中替换注入，UI 与定位层零改动。
 */
interface WeatherRepository {

    /**
     * 查询实时天气。
     *
     * @param context 应用上下文（SDK 类数据源初始化/鉴权所需）
     * @param adcode  行政区划编码
     */
    suspend fun fetchRealtimeWeather(context: Context, adcode: String): Result<WeatherInfo>
}
