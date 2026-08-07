package com.vloc.feature.altitude

import android.annotation.SuppressLint
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
 * 海拔换算：
 * - 有 GPS 海拔时 → 直接显示 GPS 海拔（最准确）
 * - 无 GPS 海拔时 → 用校准过的 QNH + 当前气压计算
 * - 无 QNH 时 → 用标准大气压计算（可能有数百米偏差，属降级方案）
 *
 * 基准气压默认标准大气；校准源有两条：
 * 1. 启动监测时主动通过系统 [GpsAltitudeProvider]（LocationManager）获取 GPS 海拔；
 * 2. 天气模块高德定位顺带上报（[AltitudeCalibration]，缓存/WiFi 定位海拔为 0 时会被忽略）。
 * 拿到新鲜 GPS 海拔后用 ISA 公式反推当地实时海平面气压（QNH）做校准，显著降低偏差。
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

        /** GPS 海拔拉取失败后的重试间隔（避免频繁拉起 GPS 耗电） */
        private const val GPS_RETRY_INTERVAL_MS = 60_000L
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

    // 原始传感器值（未经EMA平滑），供日志调试
    private var rawPressureHpa = 0.0
    private var rawMagneticUt = 0.0

    /** 校准后的海平面基准气压（hPa）；null 表示未校准（用标准大气） */
    private var calibratedQnh: Double? = null

    /** 已成功校准的次数，用于日志 */
    private var calibrateCount = 0

    /** 当前正在进行的 GPS 海拔拉取任务 */
    private var gpsFetchJob: Job? = null

    /** 上次发起 GPS 拉取的时间，用于重试节流 */
    private var lastGpsFetchMs = 0L

    /** start 时保存 appContext，供 ticker 内的重试使用 */
    private var appContext: Context? = null

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
        AppLogger.i(
            TAG,
            "标准大气压基准=${SensorManager.PRESSURE_STANDARD_ATMOSPHERE}hPa",
        )
        sensorManager = sm
        appContext = context.applicationContext
        _uiState.value = AltitudeUiState.Loading

        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                when (event.sensor?.type) {
                    Sensor.TYPE_PRESSURE -> {
                        val v = event.values[0].toDouble()
                        rawPressureHpa = v
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
                        rawMagneticUt = v
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
                AppLogger.d(TAG, "传感器精度变化: sensor=${sensor?.name}, accuracy=$accuracy")
            }
        }
        listener = l
        pressure?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
        magnetic?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }

        // 未校准时主动向系统 GPS 要一次海拔（不依赖高德定位顺带上报，
        // 高德缓存/WiFi 定位海拔恒为 0.0 会被校准通道拒绝，导致永远停留在标准大气兜底）。
        // 拿到后写入 AltitudeCalibration，由下方 ticker 的 tryCalibrate 消费反推 QNH。
        // 超时/失败后由 ticker 周期重试（[ensureGpsFetch]）。
        ensureGpsFetch()

        tickerJob = viewModelScope.launch {
            while (isActive) {
                tryCalibrate()
                ensureGpsFetch()
                emitSnapshot()
                delay(TICK_MS.milliseconds)
            }
        }
    }

    /**
     * 确保有一个 GPS 海拔拉取任务在进行；已校准/进行中/重试冷却中则跳过。
     */
    private fun ensureGpsFetch() {
        if (calibratedQnh != null) return
        if (gpsFetchJob?.isActive == true) return
        if (System.currentTimeMillis() - lastGpsFetchMs < GPS_RETRY_INTERVAL_MS) return
        val ctx = appContext ?: return
        lastGpsFetchMs = System.currentTimeMillis()
        gpsFetchJob = viewModelScope.launch {
            val gpsAlt = GpsAltitudeProvider.fetchGpsAltitude(ctx)
            if (gpsAlt != null) {
                AltitudeCalibration.reportGpsAltitude(gpsAlt)
            }
        }
    }

    /**
     * 尝试校准：新鲜 GPS 海拔 + 当前气压样本 → ISA 反推 QNH。
     * 允许多次校准（每次拿到新GPS数据都校准）。
     *
     * QNH = P_ref × (1 − 0.0065×h_ref / 288.15)^(−5.255)
     */
    @SuppressLint("DefaultLocale")
    private fun tryCalibrate() {
        if (!hasPressureSample) return

        val gpsAltitude = AltitudeCalibration.takeFresh()
        if (gpsAltitude == null) {
            // 每次 tick 都检查 GPS 状态（通过 peek），输出调试日志
            val peek = AltitudeCalibration.peekGpsAltitude()
            if (peek != null && calibratedQnh == null) {
                AppLogger.d(
                    TAG,
                    "GPS数据待消费中: ${String.format("%.1f", peek)}m, " +
                        "当前气压=${String.format("%.1f", pressureHpa)}hPa, " +
                        "等待校准..."
                )
            }
            return
        }

        val qnh = pressureHpa *
            (1.0 - 0.0065 * gpsAltitude / 288.15).pow(-5.255)
        calibratedQnh = qnh
        calibrateCount++
        AppLogger.i(
            TAG,
            "海拔校准完成(#$calibrateCount): GPS海拔=${String.format(Locale.US, "%.1f", gpsAltitude)}m，" +
                "配对气压=${String.format(Locale.US, "%.1f", pressureHpa)}hPa，" +
                "QNH=${String.format(Locale.US, "%.1f", qnh)}hPa，" +
                "反算验证海拔=${String.format(Locale.US, "%.1f", SensorManager.getAltitude(qnh.toFloat(), pressureHpa.toFloat()))}m"
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
        // 日志：输出原始传感器数据和处理后数据，便于调试
        if (hasPressureSample) {
            AppLogger.d(
                TAG,
                "气压数据: 原始=${String.format(Locale.US, "%.1f", rawPressureHpa)}hPa, " +
                    "EMA平滑=${String.format(Locale.US, "%.1f", pressureHpa)}hPa, " +
                    "基准QNH=${calibratedQnh?.let { String.format(Locale.US, "%.1f", it) } ?: "标准大气压(${SensorManager.PRESSURE_STANDARD_ATMOSPHERE}hPa)"}"
            )
        }

        val basePressure =
            (calibratedQnh ?: SensorManager.PRESSURE_STANDARD_ATMOSPHERE.toDouble()).toFloat()

        // GPS海拔优先：有新鲜GPS数据时直接使用，否则用气压计算
        val gpsAltitude = AltitudeCalibration.peekGpsAltitude()
        val altitudeText: String
        val usedGps: Boolean

        if (gpsAltitude != null) {
            // 使用GPS直接提供的海拔（最准确）
            altitudeText = String.format(Locale.US, "%.1f", gpsAltitude)
            usedGps = true
        } else if (hasPressureSample) {
            // 使用气压传感器计算的海拔
            val calcAltitude = SensorManager.getAltitude(basePressure, pressureHpa.toFloat())
            altitudeText = String.format(Locale.US, "%.1f", calcAltitude)
            usedGps = false
        } else {
            altitudeText = NO_VALUE
            usedGps = false
        }

        AppLogger.d(
            TAG,
            "海拔计算: GPS=${gpsAltitude?.let { String.format(Locale.US, "%.1f", it) } ?: "无"}, " +
                "计算结果=$altitudeText m, 来源=${if (usedGps) "GPS直接" else "气压计算(基准=${String.format(Locale.US, "%.1f", basePressure)}hPa)"}"
        )

        _uiState.value = AltitudeUiState.Success(
            altitude = altitudeText,
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
            calibrated = calibratedQnh != null || usedGps
        )
    }
}