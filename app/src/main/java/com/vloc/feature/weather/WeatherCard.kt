package com.vloc.feature.weather

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vloc.R
import com.vloc.ui.components.FeatureCardHeader

/**
 * 「天气」功能模块唯一对外入口。
 *
 * 内嵌真实逻辑：首次可见时经 [WeatherViewModel] 串联
 * 「真实定位 → 高德实时天气查询」，并按 [WeatherUiState] 渲染：
 * - Loading：副标题显示「定位中…」
 * - Success：副标题显示「位置 + 天气现象」，右侧显示气温；
 *   整卡可点击展开/收缩，展开显示高德提供的更多实况字段
 *   （温度/天气状态/湿度/风向/风力等级，2 列网格）
 * - Error：副标题显示可读原因，整卡可点击重试
 *
 * 实现逻辑全部收敛在本包内，宿主页面仅传入 [apiKey] 与 [visible]，零感知。
 *
 * @param apiKey  高德 API Key（宿主已保存的密钥）
 * @param visible 所在 Tab 是否可见（Tab 保活场景下，仅在首次可见时加载，避免重复定位）
 */
@Composable
fun WeatherCard(
    apiKey: String?,
    visible: Boolean,
    modifier: Modifier = Modifier,
    weatherViewModel: WeatherViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by weatherViewModel.uiState.collectAsState()

    // 首次可见时加载；Tab 切换保活不重复请求，失败后点击卡片手动重试
    var hasLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible && !hasLoaded) {
            hasLoaded = true
            weatherViewModel.load(context, apiKey)
        }
    }

    var expanded by remember { mutableStateOf(false) }
    // 离开成功态（如重试回到 Loading/Error）时收起展开区
    LaunchedEffect(uiState) {
        if (uiState !is WeatherUiState.Success) {
            expanded = false
        }
    }

    val subtitle: String
    val valueText: String
    val onClick: (() -> Unit)?

    when (val state = uiState) {
        WeatherUiState.Loading -> {
            subtitle = stringResource(R.string.weather_locating)
            valueText = "-- ℃"
            onClick = null
        }

        is WeatherUiState.Success -> {
            subtitle = if (state.weather.isBlank()) {
                state.locationText
            } else {
                "${state.locationText} · ${state.weather}"
            }
            valueText = "${state.temperature} ℃"
            onClick = { expanded = !expanded }
        }

        is WeatherUiState.Error -> {
            subtitle = stringResource(state.messageResId)
            valueText = "-- ℃"
            onClick = { weatherViewModel.load(context, apiKey) }
        }
    }

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "weather_arrow"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            FeatureCardHeader(
                icon = Icons.Default.WbSunny,
                iconTint = Color(0xFFFF8F00),
                title = stringResource(R.string.profile_weather_title),
                subtitle = subtitle,
                valueText = valueText,
                modifier = if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                },
                trailing = if (uiState is WeatherUiState.Success) {
                    {
                        // 温度与箭头间距，避免拥挤
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
                visible = expanded && uiState is WeatherUiState.Success,
                enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
            ) {
                val state = uiState as WeatherUiState.Success
                WeatherDetailGrid(state)
            }
        }
    }
}

/**
 * 展开区详情网格：2 列 × 3 行，展示高德实况天气提供的字段。
 * 字段缺失（空串）统一显示「--」。
 */
@Composable
private fun WeatherDetailGrid(state: WeatherUiState.Success) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            DetailCell(
                label = "温度",
                value = if (state.temperature.isBlank() || state.temperature == "--") {
                    "--"
                } else {
                    "${state.temperature}℃"
                },
                modifier = Modifier.weight(1f)
            )
            DetailCell(
                label = "天气状态",
                value = state.weather.ifBlank { "--" },
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            DetailCell(
                label = "湿度",
                value = state.humidity.ifBlank { "--" }.let {
                    if (it == "--") it else "$it%"
                },
                modifier = Modifier.weight(1f)
            )
            DetailCell(
                label = "风向",
                value = state.windDirection.ifBlank { "--" },
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            DetailCell(
                label = "风力等级",
                value = state.windPower.ifBlank { "--" }.let {
                    if (it == "--") it else "${it}级"
                },
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
