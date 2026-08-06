package com.vloc.feature.compass

/**
 * 指南针卡片 UI 状态。
 *
 * 数据来自系统方向传感器（ROTATION_VECTOR，备源加速度+磁场），无网络请求。
 */
sealed interface CompassUiState {

    /** 监听已启动、尚无有效数据 */
    data object Loading : CompassUiState

    /**
     * 实时监测成功。
     *
     * @param direction 方位名（北/东北/东/东南/南/西南/西/西北）
     * @param degrees   整数角度 0–360
     * @param azimuth   平滑后的连续方位角（0–360，供表盘旋转，避免取整跳动）
     */
    data class Success(
        val direction: String,
        val degrees: Int,
        val azimuth: Double = degrees.toDouble()
    ) : CompassUiState

    /** 失败：设备无方向传感器（字符串资源 ID） */
    data class Error(val messageResId: Int) : CompassUiState
}
