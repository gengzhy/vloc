package com.vloc.feature.compass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vloc.R
import com.vloc.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.round
import kotlin.time.Duration.Companion.milliseconds

/**
 * 指南针卡片 ViewModel：方向传感器实时监测。
 *
 * 设计（与海拔模块同范式）：
 * - 传感器高频回调仅做【环形 EMA 平滑】写内存（角度跨 0/360 走最短路径，
 *   避免北↔西北跳变绕圈），不触发重组
 * - 100ms ticker 协程统一发射 [CompassUiState]，保证指针跟手
 * - 生命周期由卡片驱动：Tab 可见且 resumed 时 [start]，否则 [stop]
 *
 * 主源：TYPE_ROTATION_VECTOR；备源（老设备）：加速度 + 磁场组合。
 * 假设竖屏持握（getOrientation 默认坐标系）。
 */
class CompassViewModel : ViewModel() {

    companion object {
        private const val TAG = "CompassViewModel"

        /** UI 刷新间隔（指南针需快于海拔，指针才跟手） */
        private const val TICK_MS = 100L

        /** EMA 低通滤波系数：越小越平滑 */
        private const val ALPHA = 0.25

        /** 8 方位名，自北起顺时针 */
        private val DIRECTIONS = arrayOf(
            "北", "东北", "东", "东南", "南", "西南", "西", "西北",
        )
    }

    private val _uiState = MutableStateFlow<CompassUiState>(CompassUiState.Loading)
    val uiState: StateFlow<CompassUiState> = _uiState.asStateFlow()

    private var sensorManager: SensorManager? = null
    private var listener: SensorEventListener? = null
    private var tickerJob: Job? = null

    // 备源所需原始数据
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    // 平滑后的方位角（度，0–360）
    private var azimuthDeg = 0.0
    private var hasAzimuthSample = false

    /**
     * 启动监测。重复调用自动去重。
     */
    fun start(context: Context) {
        if (tickerJob != null) {
            AppLogger.d(TAG, "start 重复调用，忽略")
            return
        }
        val sm = context.applicationContext
            .getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationVector = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetic = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (rotationVector == null && ((accelerometer == null) || (magnetic == null))) {
            AppLogger.w(TAG, "设备无方向传感器")
            _uiState.value = CompassUiState.Error(R.string.compass_no_sensor)
            return
        }

        AppLogger.i(
            TAG,
            "启动监测：rotationVector=${rotationVector != null}，" +
                "备源(加速度+磁场)=${accelerometer != null && magnetic != null}",
        )
        sensorManager = sm
        _uiState.value = CompassUiState.Loading

        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val target = when (event.sensor?.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(
                            rotationMatrix, event.values
                        )
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        Math.toDegrees(orientation[0].toDouble()).normalize360()
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        gravity.copyFrom(event.values)
                        computeFallbackAzimuth()
                    }

                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        geomagnetic.copyFrom(event.values)
                        computeFallbackAzimuth()
                    }

                    else -> null
                }
                if (target != null) {
                    azimuthDeg = if (hasAzimuthSample) {
                        smoothCircular(azimuthDeg, target)
                    } else {
                        target
                    }
                    hasAzimuthSample = true
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // 精度变化不影响展示
            }
        }
        listener = l
        rotationVector?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
        if (rotationVector == null) {
            accelerometer?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
            magnetic?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
        }

        tickerJob = viewModelScope.launch {
            while (isActive) {
                emitSnapshot()
                delay(TICK_MS.milliseconds)
            }
        }
    }

    /**
     * 停止监测（注销监听 + 取消 ticker），保留最后一次 Success 供收缩态展示。
     */
    fun stop() {
        if (tickerJob == null) return
        AppLogger.i(TAG, "停止监测")
        tickerJob?.cancel()
        tickerJob = null
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
        sensorManager = null
    }

    override fun onCleared() {
        stop()
    }

    /** 备源：加速度 + 磁场 → 旋转矩阵 → 方位角 */
    private fun computeFallbackAzimuth(): Double? =
        if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
            SensorManager.getOrientation(rotationMatrix, orientation)
            Math.toDegrees(orientation[0].toDouble()).normalize360()
        } else {
            null
        }

    private fun emitSnapshot() {
        if (!hasAzimuthSample) return
        val degrees = azimuthDeg.roundToInt360()
        _uiState.value = CompassUiState.Success(
            direction = DIRECTIONS[round(degrees / 45.0).toInt() % 8],
            degrees = degrees,
            azimuth = azimuthDeg
        )
    }

    private fun Double.normalize360(): Double {
        var v = this % 360.0
        if (v < 0) v += 360.0
        return v
    }

    private fun Double.roundToInt360(): Int {
        val r = round(this).toInt()
        return if (r >= 360) 0 else r
    }

    /** 环形最短路径 EMA：跨 0/360 边界不绕圈 */
    private fun smoothCircular(current: Double, target: Double): Double {
        var delta = target - current
        while (delta >= 180.0) delta -= 360.0
        while (delta < -180.0) delta += 360.0
        return (current + ALPHA * delta).normalize360()
    }

    private fun FloatArray.copyFrom(values: FloatArray) {
        for (i in 0 until minOf(size, values.size)) {
            this[i] = values[i]
        }
    }
}
