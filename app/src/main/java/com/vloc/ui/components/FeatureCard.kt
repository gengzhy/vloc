package com.vloc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 通用功能卡片壳：统一「我的」页功能卡片的视觉规范。
 *
 * 布局：左侧浅色圆角背景块承载彩色图标，中部标题 + 副标题，右侧占位数值。
 * 各 feature 模块（指南针 / 海拔 / 天气等）仅通过本组件组装 UI，
 * 保证样式一致、避免重复代码；真实数据接入后由 [valueText] 承载。
 * [onClick] 非空时整卡可点击（如失败重试），为空时保持纯展示。
 */
@Composable
fun FeatureCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    valueText: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        FeatureCardHeader(
            icon = icon,
            iconTint = iconTint,
            title = title,
            subtitle = subtitle,
            valueText = valueText
        )
    }
}

/**
 * 卡片头部布局：左侧图标块 + 中部标题/副标题 + 右侧数值。
 *
 * 独立抽出供带展开区的卡片（如天气卡片）复用：
 * 卡片自行组装「头部 + 展开内容」，头部样式与 [FeatureCard] 严格一致。
 * [trailing] 为数值右侧的可选插槽（如展开/收缩箭头）。
 */
@Composable
fun FeatureCardHeader(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    valueText: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 浅色圆角背景块 + 彩色图标
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(iconTint.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // 标题 + 副标题
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 13.sp
            )
        }

        // 右侧占位数值（后续承载真实数据）
        Text(
            text = valueText,
            color = Color(0xFF00BCD4),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        trailing?.invoke()
    }
}
