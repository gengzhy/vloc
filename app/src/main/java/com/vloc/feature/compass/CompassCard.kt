package com.vloc.feature.compass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vloc.R
import com.vloc.ui.components.FeatureCardHeader

/**
 * 「指南针」功能模块唯一对外入口。
 *
 * 内嵌真实逻辑：[CompassViewModel] 监听方向传感器，
 * 按 [CompassUiState] 渲染：
 * - Loading：副标题显示「传感器启动中…」
 * - Success：副标题显示「实时监测中…」，右侧按「方位名 角度°」显示（如 北 355°）；
 *   整卡可点击展开/收缩，展开显示可旋转表盘
 * - Error：副标题显示「无方向传感器」，不可展开
 *
 * 生命周期：仅「Tab 可见 且 Activity resumed」时启动监测，
 * 切走/退后台即停止，避免常驻耗电。
 *
 * @param visible 所在 Tab 是否可见
 */
@Composable
fun CompassCard(
    visible: Boolean,
    modifier: Modifier = Modifier,
    compassViewModel: CompassViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by compassViewModel.uiState.collectAsState()

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
            compassViewModel.start(context)
        } else {
            compassViewModel.stop()
        }
    }

    var expanded by remember { mutableStateOf(false) }
    // 离开成功态时收起展开区
    LaunchedEffect(uiState) {
        if (uiState !is CompassUiState.Success) {
            expanded = false
        }
    }

    val subtitle: String
    val valueText: String
    val clickable: Boolean

    when (val state = uiState) {
        CompassUiState.Loading -> {
            subtitle = stringResource(R.string.compass_loading)
            valueText = "--°"
            clickable = false
        }

        is CompassUiState.Success -> {
            subtitle = stringResource(R.string.compass_monitoring)
            valueText = "${state.direction} ${state.degrees}°"
            clickable = true
        }

        is CompassUiState.Error -> {
            subtitle = stringResource(state.messageResId)
            valueText = "--°"
            clickable = false
        }
    }

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "compass_arrow"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            FeatureCardHeader(
                icon = Icons.Default.Explore,
                iconTint = Color(0xFF6B46C1),
                title = stringResource(R.string.profile_compass_title),
                subtitle = subtitle,
                valueText = valueText,
                modifier = if (clickable) {
                    Modifier.clickable { expanded = !expanded }
                } else {
                    Modifier
                },
                trailing = if (uiState is CompassUiState.Success) {
                    {
                        // 数值与箭头间距，避免拥挤
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
                visible = expanded && uiState is CompassUiState.Success,
                enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
            ) {
                val state = uiState as CompassUiState.Success
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CompassDial(azimuth = state.azimuth)
                }
            }
        }
    }
}
