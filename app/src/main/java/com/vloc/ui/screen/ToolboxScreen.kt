package com.vloc.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vloc.R
import com.vloc.feature.altitude.AltitudeCard
import com.vloc.feature.compass.CompassCard
import com.vloc.feature.weather.WeatherCard

/**
 * 「工具箱」页面：顶部标题行 + 功能卡片列表（指南针 / 海拔 / 天气，纵向可滚动）。
 *
 * 各卡片来自独立 feature 模块（com.vloc.feature.*），
 * 本页面仅依赖各模块唯一入口，功能实现与本页解耦。
 * 设置入口保留在「我的」页，本页不含菜单按钮。
 *
 * @param visible 所在 Tab 是否可见（驱动天气首见加载）
 * @param apiKey  高德 API Key（透传天气卡片）
 */
@Composable
fun ToolboxScreen(
    visible: Boolean,
    apiKey: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        Text(
            text = stringResource(R.string.toolbox_title),
            color = Color.Black,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CompassCard(visible = visible)
            AltitudeCard(visible = visible)
            WeatherCard(
                apiKey = apiKey,
                visible = visible
            )
        }
    }
}
