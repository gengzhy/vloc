package com.vloc.feature.altitude

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
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

/**
 * 海拔卡片 ViewModel：气压/磁场传感器实时监测。
 *
 * 设计：
 * - 传感器高频回调（~60ms）仅做 EMA 低通平滑并写入内存变量，不触发重组
 * - 500ms ticker 协程统一格式化发射 [AltitudeUiState]，控制刷新频率
 * - 生命周期由卡片驱动：Tab 可见且 resumed 时 [start]，否则 [stop]，避免常驻耗电
 *
 * 海拔换算：[SensorManager.getAltitude]（海平面基准气压 → 当前气压）。
 * 基准气压默认标准大气；若拿到新鲜 GPS 海拔（[AltitudeCalibration]），
 * 用 ISA 公式反推当地实时海平面气压（QNH）做校准，显著降低偏差。
 * 磁场强度：三轴向量模长 sqrt(x²+y²+z²)。
 */
class AltitudeViewModel : ViewModel() {

    companion object {
        private const val TAG = "AltitudeViewModel"

        /** UI 刷新间隔 */
        private const val TICK_MS = 500L

        /** EMA 低通滤波系数：越小越平滑 */
        private const val ALPHA = 0.15

        private const val NO_VALUE = "--"
    }

    private val _uiState = MutableStateFlow<AltitudeUiState>(AltitudeUiState.Loading)
    val uiState: StateFlow<AltitudeUiState> = _uiState.asStateFlow()

    private var sensorManager: SensorManager? = null
    private var listener: SensorEventListener? = null
    private var tickerJob: Job? = null

    private var hasPressure = false
    private var hasMagnetic = false

    // 平滑后的传感器值（仅监听线程写、ticker 读，单协程上下文无竞争）
    private var pressureHpa = 0.0
    private var magneticUt = 0.0
    private var hasPressureSample = false
    private var hasMagneticSample = false

    /** 校准后的海平面基准气压（hPa）；null 表示未校准（用标准大气） */
    private var calibratedQnh: Double? = null

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
        val pressure = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
        val magnetic = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        hasPressure = pressure != null
        hasMagnetic = magnetic != null

        if (!hasPressure && !hasMagnetic) {
            AppLogger.w(TAG, "设备无气压/磁场传感器")
            _uiState.value = AltitudeUiState.Error(R.string.altitude_no_sensor)
            return
        }

        AppLogger.i(
            TAG,
            "启动监测：气压传感器=$hasPressure，磁场传感器=$hasMagnetic",
        )
        sensorManager = sm
        _uiState.value = AltitudeUiState.Loading

        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                when (event.sensor?.type) {
                    Sensor.TYPE_PRESSURE -> {
                        val v = event.values[0].toDouble()
                        pressureHpa = if (hasPressureSample) {
                            pressureHpa + (ALPHA * (v - pressureHpa))
                        } else {
                            v
                        }
                        hasPressureSample = true
                    }

                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        val x = event.values[0].toDouble()
                        val y = event.values[1].toDouble()
                        val z = event.values[2].toDouble()
                        val v = sqrt(x * x + y * y + z * z)
                        magneticUt = if (hasMagneticSample) {
                            magneticUt + (ALPHA * (v - magneticUt))
                        } else {
                            v
                        }
                        hasMagneticSample = true
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // 精度变化不影响展示
            }
        }
        listener = l
        pressure?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
        magnetic?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }

        tickerJob = viewModelScope.launch {
            while (isActive) {
                tryCalibrate()
                emitSnapshot()
                delay(TICK_MS.milliseconds)
            }
        }
    }

    /**
     * 一次性校准：新鲜 GPS 海拔 + 当前气压样本 → ISA 反推 QNH。
     *
     * QNH = P_ref × (1 − 0.0065×h_ref / 288.15)^(−5.255)
     */
    private fun tryCalibrate() {
        if (calibratedQnh != null || !hasPressureSample) return
        val gpsAltitude = AltitudeCalibration.takeFresh() ?: return

        val qnh = pressureHpa *
            (1.0 - 0.0065 * gpsAltitude / 288.15).pow(-5.255)
        calibratedQnh = qnh
        AppLogger.i(
            TAG,
            "海拔校准完成：GPS海拔=${String.format(Locale.US, "%.1f", gpsAltitude)}m，" +
                "配对气压=${String.format(Locale.US, "%.1f", pressureHpa)}hPa，" +
                "QNH=${String.format(Locale.US, "%.1f", qnh)}hPa"
        )
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

    private fun emitSnapshot() {
        val basePressure =
            (calibratedQnh ?: SensorManager.PRESSURE_STANDARD_ATMOSPHERE.toDouble()).toFloat()
        _uiState.value = AltitudeUiState.Success(
            altitude = if (hasPressureSample) {
                String.format(
                    Locale.US,
                    "%.1f",
                    SensorManager.getAltitude(basePressure, pressureHpa.toFloat())
                )
            } else {
                NO_VALUE
            },
            pressure = if (hasPressureSample) {
                String.format(Locale.US, "%.1f", pressureHpa)
            } else {
                NO_VALUE
            },
            magnetic = if (hasMagneticSample) {
                String.format(Locale.US, "%.1f", magneticUt)
            } else {
                NO_VALUE
            },
            calibrated = calibratedQnh != null
        )
    }
}
