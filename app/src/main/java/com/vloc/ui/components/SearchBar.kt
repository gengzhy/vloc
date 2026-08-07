package com.vloc.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vloc.util.AddressSearchUtil
import kotlinx.coroutines.launch

/**
 * 地图顶部搜索栏：焦点驱动的伸缩形态。
 *
 * - 位置、高度、内边距与原版完全一致，仅宽度变化；
 * - 无焦点时收缩为左侧椭圆胶囊，避免遮挡地图；
 * - 点击获得焦点后动画展开至全宽，供输入与展示候选结果；
 * - 焦点丢失（如点地图、选中结果）自动收回，外部可通过
 *   FocusManager.clearFocus() 主动收起；
 * - 候选结果展示期间保持展开，避免结果列表脱离输入框悬浮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    searchResults: List<AddressSearchUtil.SearchResult>,
    onSearch: () -> Unit,
    onResultClick: (AddressSearchUtil.SearchResult) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val expanded = focused || searchResults.isNotEmpty()
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val interactionScope = rememberCoroutineScope()
    // BasicTextField 不会像 OutlinedTextField 那样自动把焦点写入 interactionSource，
    // 而 DecorationBox 的 focused 配色（focusedContainerColor/聚焦边框）全靠它判定；
    // 这里手动转发焦点事件，否则聚焦后背景透明度永远不切换
    var focusHandle by remember { mutableStateOf<FocusInteraction.Focus?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val targetWidth = if (expanded) maxWidth else (maxWidth / 8).coerceAtLeast(40.dp)
            val animatedWidth by animateDpAsState(
                targetValue = targetWidth,
                animationSpec = tween(250),
                label = "searchBarWidth"
            )

            val fieldColors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = Color.White.copy(alpha = 0.6f)
            )

            BasicTextField(
                value = searchText,
                onValueChange = onSearchTextChange,
                modifier = Modifier
                    .width(animatedWidth)
                    .height(40.dp)
                    .onFocusChanged { state ->
                        focused = state.isFocused
                        interactionScope.launch {
                            focusHandle?.let { interactionSource.emit(FocusInteraction.Unfocus(it)) }
                            focusHandle = if (state.isFocused) {
                                FocusInteraction.Focus().also { interactionSource.emit(it) }
                            } else null
                        }
                    },
                textStyle = TextStyle(fontSize = 14.sp, color = Color.Black),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearch() }
                )
            ) { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = searchText,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    colors = fieldColors,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true,
                            isError = false,
                            interactionSource = interactionSource,
                            colors = fieldColors,
                            shape = RoundedCornerShape(24.dp),
                            focusedBorderThickness = 2.dp,
                            unfocusedBorderThickness = 1.dp,
                        )
                    }
                )
            }
        }

        if (expanded && searchResults.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .shadow(4.dp, RoundedCornerShape(8.dp))
                    .background(Color.White, RoundedCornerShape(8.dp))
            ) {
                items(searchResults) { result ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onResultClick(result)
                                // 选中后收起键盘并让搜索框失焦，自动回到胶囊态
                                focusManager.clearFocus()
                            }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = result.name,
                            fontSize = 12.sp,
                            color = Color.Black
                        )
                        if (result.district.isNotEmpty()) {
                            Text(
                                text = result.district,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    HorizontalDivider(
                        thickness = 0.5.dp, color = Color.LightGray
                    )
                }
            }
        }
    }
}
