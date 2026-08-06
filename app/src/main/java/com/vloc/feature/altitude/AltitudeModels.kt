package com.vloc.feature.altitude

/**
 * 海拔卡片 UI 状态。
 *
 * 数据全部来自系统传感器（气压 / 磁场），无网络请求。
 */
sealed interface AltitudeUiState {

    /** 监听已启动、尚无有效数据 */
    data object Loading : AltitudeUiState

    /**
     * 实时监测成功。
     *
     * @param altitude  海拔（m），传感器缺失为 "--"
     * @param pressure  气压（hPa），传感器缺失为 "--"
     * @param magnetic  磁场强度（µT，三轴模长），传感器缺失为 "--"
     * @param calibrated 海拔是否已用 GPS 海拔校准（false 为标准大气基准）
     */
    data class Success(
        val altitude: String,
        val pressure: String,
        val magnetic: String,
        val calibrated: Boolean = false
    ) : AltitudeUiState

    /** 失败：设备无气压/磁场传感器（字符串资源 ID） */
    data class Error(val messageResId: Int) : AltitudeUiState
}
