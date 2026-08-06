package com.vloc.feature.compass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 指南针表盘（Canvas 绘制，参考经典罗盘视觉）：
 * - 深灰外环带：每 30° 度数数字（0 红色 + 红色刻度，其余白色），随盘旋转
 * - 黑色盘面：8 方位中 N 红、E/S/W 白，随盘旋转
 * - 黄绿细密刻度环（每 2° 小刻度、每 30° 长刻度）
 * - 内圈青色细圆
 * - 顺时针渐隐扫掠尾迹（装饰，雷达风格）
 * - 固定顶部红色索引三角 + 中心黄绿指针（指向当前航向）
 *
 * 盘面整体旋转 -azimuth，使当前航向对齐顶部索引。
 */
@Composable
fun CompassDial(
    azimuth: Double,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = minOf(this.size.width, this.size.height) / 2f

        // 外环带 + 黑色盘面
        drawCircle(color = BandColor, radius = r, center = center)
        drawCircle(color = FaceColor, radius = r * BAND_INNER_RATIO, center = center)

        // 扫掠尾迹（固定，装饰）
        drawSweep(center, r)

        // 内圈青色细圆
        drawCircle(
            color = TealColor,
            radius = r * TEAL_RATIO,
            center = center,
            style = Stroke(width = 2f)
        )

        // 旋转盘面：刻度环 / 方位字母 / 度数数字
        rotate(degrees = -azimuth.toFloat(), pivot = center) {
            drawTickRing(center, r)
            drawCardinals(textMeasurer, center, r)
            drawDegreeNumbers(textMeasurer, center, r)
        }

        // 固定顶部红色索引
        drawTopIndex(center, r)

        // 固定中心指针（指向顶部索引 = 当前航向）
        drawCenterArrow(center, r)
    }
}

private val BandColor = Color(0xFF1F1F1F)
private val FaceColor = Color.Black
private val TickOlive = Color(0xFFC5E063)
private val TealColor = Color(0xFF2C7F8C)
private val DialRed = Color(0xFFE53935)
private val DialWhite = Color.White

private const val BAND_INNER_RATIO = 0.80f
private const val NUMBER_RATIO = 0.895f
private const val CARDINAL_RATIO = 0.68f
private const val TICK_OUTER_RATIO = 0.60f
private const val TICK_INNER_RATIO = 0.545f
private const val TICK_LONG_INNER_RATIO = 0.51f
private const val TEAL_RATIO = 0.36f

/** 黄绿刻度环：每 2° 小刻度，每 30° 长刻度 */
private fun DrawScope.drawTickRing(center: Offset, r: Float) {
    for (deg in 0 until 360 step 2) {
        val isLong = deg % 30 == 0
        val rad = Math.toRadians(deg.toDouble())
        val outer = r * TICK_OUTER_RATIO
        val inner = r * if (isLong) TICK_LONG_INNER_RATIO else TICK_INNER_RATIO
        drawLine(
            color = TickOlive,
            start = Offset(
                center.x + (inner * sin(rad)).toFloat(),
                center.y - (inner * cos(rad)).toFloat()
            ),
            end = Offset(
                center.x + (outer * sin(rad)).toFloat(),
                center.y - (outer * cos(rad)).toFloat()
            ),
            strokeWidth = if (isLong) 3f else 1.5f
        )
    }
}

/** 方位字母：N 红加粗，E/S/W 白加粗；随盘旋转、切向排布 */
private fun DrawScope.drawCardinals(measurer: TextMeasurer, center: Offset, r: Float) {
    val labels = arrayOf("N", "E", "S", "W")
    labels.forEachIndexed { index, label ->
        val deg = index * 90f
        val style = TextStyle(
            color = if (index == 0) DialRed else DialWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        rotate(degrees = deg, pivot = center) {
            drawCenteredText(
                measurer,
                label,
                Offset(center.x, center.y - r * CARDINAL_RATIO),
                style
            )
        }
    }
}

/** 外环度数数字：每 30°，0 红色其余白色；随盘旋转、切向排布 */
private fun DrawScope.drawDegreeNumbers(measurer: TextMeasurer, center: Offset, r: Float) {
    for (deg in 0 until 360 step 30) {
        val style = TextStyle(
            color = if (deg == 0) DialRed else DialWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal
        )
        rotate(degrees = deg.toFloat(), pivot = center) {
            // 0 位置附红色刻度
            if (deg == 0) {
                drawLine(
                    color = DialRed,
                    start = Offset(center.x, center.y - r * 0.99f),
                    end = Offset(center.x, center.y - r * 0.94f),
                    strokeWidth = 3f
                )
            }
            drawCenteredText(
                measurer,
                deg.toString(),
                Offset(center.x, center.y - r * NUMBER_RATIO),
                style
            )
        }
    }
}

/** 顺时针渐隐扫掠尾迹（自顶部索引起，分段递减透明度） */
private fun DrawScope.drawSweep(center: Offset, r: Float) {
    val segments = 14
    val segmentDeg = 5f
    val sweepRadius = r * TICK_OUTER_RATIO
    val rect = Rect(
        left = center.x - sweepRadius,
        top = center.y - sweepRadius,
        right = center.x + sweepRadius,
        bottom = center.y + sweepRadius
    )
    for (i in 0 until segments) {
        val alpha = 0.40f * (1f - i.toFloat() / segments)
        drawArc(
            color = TickOlive.copy(alpha = alpha),
            startAngle = -90f + i * segmentDeg,
            sweepAngle = segmentDeg,
            useCenter = true,
            topLeft = Offset(rect.left, rect.top),
            size = androidx.compose.ui.geometry.Size(rect.width, rect.height)
        )
    }
}

/** 固定顶部红色索引三角（指向盘内） */
private fun DrawScope.drawTopIndex(center: Offset, r: Float) {
    val path = Path().apply {
        moveTo(center.x, center.y - r * 0.92f)
        lineTo(center.x - 6f, center.y - r + 1f)
        lineTo(center.x + 6f, center.y - r + 1f)
        close()
    }
    drawPath(path, DialRed)
}

/** 固定中心黄绿指针（指向顶部索引 = 当前航向） */
private fun DrawScope.drawCenterArrow(center: Offset, r: Float) {
    val path = Path().apply {
        moveTo(center.x, center.y - r * 0.14f)
        lineTo(center.x - r * 0.10f, center.y + r * 0.10f)
        lineTo(center.x + r * 0.10f, center.y + r * 0.10f)
        close()
    }
    drawPath(path, TickOlive)
}

/** 以 pos 为中心绘制文字 */
private fun DrawScope.drawCenteredText(
    measurer: TextMeasurer,
    text: String,
    pos: Offset,
    style: TextStyle
) {
    val result = measurer.measure(AnnotatedString(text), style)
    drawText(
        textLayoutResult = result,
        topLeft = Offset(
            pos.x - result.size.width / 2f,
            pos.y - result.size.height / 2f
        )
    )
}
