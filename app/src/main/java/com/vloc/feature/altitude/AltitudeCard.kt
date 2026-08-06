package com.vloc.feature.altitude

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vloc.R
import com.vloc.ui.components.FeatureCardHeader

/**
 * 「海拔」功能模块唯一对外入口。
 *
 * 内嵌真实逻辑：[AltitudeViewModel] 监听气压/磁场传感器，
 * 按 [AltitudeUiState] 渲染：
 * - Loading：副标题显示「传感器启动中…」
 * - Success：副标题显示「实时监测中…」，右侧显示海拔；
 *   整卡可点击展开/收缩，展开显示 海拔/气压/磁场（2 列网格）
 * - Error：副标题显示「无气压/磁场传感器」，不可展开
 *
 * 生命周期：仅「Tab 可见 且 Activity resumed」时启动监测，
 * 切走/退后台即停止，避免常驻耗电。
 *
 * @param visible 所在 Tab 是否可见
 */
@Composable
fun AltitudeCard(
    visible: Boolean,
    modifier: Modifier = Modifier,
    altitudeViewModel: AltitudeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by altitudeViewModel.uiState.collectAsState()

    // 双驱动：Tab 可见 + Activity resumed
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember(lifecycleOwner) {
        mutableStateOf(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            resumed = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(visible, resumed) {
        if (visible && resumed) {
            altitudeViewModel.start(context)
        } else {
            altitudeViewModel.stop()
        }
    }

    var expanded by remember { mutableStateOf(false) }
    // 离开成功态时收起展开区
    LaunchedEffect(uiState) {
        if (uiState !is AltitudeUiState.Success) {
            expanded = false
        }
    }

    val subtitle: String
    val valueText: String
    val clickable: Boolean

    when (val state = uiState) {
        AltitudeUiState.Loading -> {
            subtitle = stringResource(R.string.altitude_loading)
            valueText = "-- m"
            clickable = false
        }

        is AltitudeUiState.Success -> {
            subtitle = stringResource(
                if (state.calibrated) {
                    R.string.altitude_monitoring_calibrated
                } else {
                    R.string.altitude_monitoring
                }
            )
            valueText = "${state.altitude} m"
            clickable = true
        }

        is AltitudeUiState.Error -> {
            subtitle = stringResource(state.messageResId)
            valueText = "-- m"
            clickable = false
        }
    }

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "altitude_arrow"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            FeatureCardHeader(
                icon = Icons.Default.Terrain,
                iconTint = Color(0xFF2E8B57),
                title = stringResource(R.string.profile_altitude_title),
                subtitle = subtitle,
                valueText = valueText,
                modifier = if (clickable) {
                    Modifier.clickable { expanded = !expanded }
                } else {
                    Modifier
                },
                trailing = if (uiState is AltitudeUiState.Success) {
                    {
                        // 海拔与箭头间距，避免拥挤
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "收缩" else "展开",
                            tint = Color.Gray,
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(arrowRotation)
                        )
                    }
                } else {
                    null
                }
            )

            AnimatedVisibility(
                visible = expanded && uiState is AltitudeUiState.Success,
                enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
            ) {
                val state = uiState as AltitudeUiState.Success
                AltitudeDetailGrid(state)
            }
        }
    }
}

/**
 * 展开区详情网格：2 列，展示 海拔 / 气压 / 磁场。
 * 传感器缺失字段显示「--」。
 */
@Composable
private fun AltitudeDetailGrid(state: AltitudeUiState.Success) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            DetailCell(
                label = "海拔",
                value = if (state.altitude == "--") "--" else "${state.altitude}m",
                modifier = Modifier.weight(1f)
            )
            DetailCell(
                label = "气压",
                value = if (state.pressure == "--") "--" else "${state.pressure}hPa",
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            DetailCell(
                label = "磁场",
                value = if (state.magnetic == "--") "--" else "${state.magnetic}µT",
                modifier = Modifier.weight(1f)
            )
            // 右列占位，保持 2 列对齐
            Column(modifier = Modifier.weight(1f)) {}
        }
    }
}

/**
 * 详情单元格：标签（灰、小）+ 数值（黑、加粗），与卡片整体视觉一致。
 */
@Composable
private fun DetailCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = Color.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
