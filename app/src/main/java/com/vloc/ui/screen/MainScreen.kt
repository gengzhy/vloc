package com.vloc.ui.screen

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vloc.R
import com.vloc.model.LocalViewModel
import com.vloc.ui.components.MainBottomBar
import com.vloc.ui.components.MainTab
import com.vloc.ui.components.SettingsDrawer
import com.vloc.util.AppUpdateUtil
import com.vloc.util.BackPressOverlay
import com.vloc.util.MockLocationUtil
import com.vloc.util.ReleaseInfo

/**
 * 主容器：底部「首页 / 工具箱 / 我的」三 Tab + 应用级浮层（设置抽屉、弹窗）。
 * 三 Tab 同驻组合、显隐切换，地图等状态在切换时保活。
 */
@Composable
fun MainScreen(
    vm: LocalViewModel,
    savedApiKey: String,
    context: Context,
    onStartMock: (lat: Double, lng: Double) -> Unit,
    onStopMock: () -> Unit,
    onRecreate: () -> Unit,
    onExit: () -> Unit
) {
    var currentTab by remember { mutableStateOf(MainTab.HOME) }

    var menuExpanded by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showLogScreen by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var latestRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }

    // 返回键策略：Activity 层的 setupDoubleBackExit 是全局兜底（双击退出）；
    // 每个浮层通过 BackPressOverlay 各自拦截返回键关闭自身，
    // 由系统分发器按「后注册先处理」自动逐层关闭，新增浮层无需改这里。

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                TabPane(visible = currentTab == MainTab.HOME) {
                    HomeScreen(
                        vm = vm,
                        context = context,
                        visible = currentTab == MainTab.HOME,
                        onStartMock = onStartMock,
                        onStopMock = onStopMock
                    )
                }
                TabPane(visible = currentTab == MainTab.TOOL) {
                    ToolboxScreen(
                        visible = currentTab == MainTab.TOOL,
                        apiKey = savedApiKey
                    )
                }
                TabPane(visible = currentTab == MainTab.PROFILE) {
                    ProfileScreen(onMenuClick = { menuExpanded = true })
                }
            }

            MainBottomBar(selected = currentTab, onSelect = { currentTab = it })
        }

        SettingsDrawer(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onBackPress = { menuExpanded = false },
            onSaveDefaultLocation = {
                vm.saveAsDefaultLocation()
                val point = vm.selectPoint.value
                Toast.makeText(
                    context,
                    "已保存为默认位置：${point.latitude}, ${point.longitude}",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onSetApiKey = { showApiKeyDialog = true },
            onEnableMockPermission = {
                if (MockLocationUtil.isMockEnable(context)) {
                    Toast.makeText(context, "已开模拟位置权限", Toast.LENGTH_LONG).show()
                } else {
                    MockLocationUtil.goMockSetting(context)
                }
            },
            onShowLog = { showLogScreen = true },
            onExit = onExit,
            onAbout = { showAboutDialog = true }
        )

        // 全屏日志页：置于浮层最上层，覆盖抽屉与 Tab
        if (showLogScreen) {
            BackPressOverlay(onBackPress = { showLogScreen = false }) {
                LogScreen(onClose = { showLogScreen = false })
            }
        }
    }

    if (showApiKeyDialog) {
        var dialogKeyInput by remember { mutableStateOf(savedApiKey) }
        BackPressOverlay(onBackPress = { showApiKeyDialog = false }) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("设置高德 API Key") },
            text = {
                OutlinedTextField(
                    value = dialogKeyInput,
                    onValueChange = { dialogKeyInput = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dialogKeyInput.isNotBlank()) {
                        vm.saveApiKey(dialogKeyInput.trim())
                        showApiKeyDialog = false
                        onRecreate()
                    } else {
                        Toast.makeText(context, "Key不能为空", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("取消")
                }
            }
        )
        }
    }

    if (showAboutDialog) {
        BackPressOverlay(onBackPress = { showAboutDialog = false }) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.warning_title),
                    color = Color.Red,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.warning_content),
                        color = Color.Red, fontSize = 16.sp
                    )
                    Text(
                        text = stringResource(R.string.app_name),
                        color = Color.Black,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Author " + stringResource(R.string.author),
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Version " + AppUpdateUtil.getCurrentVersionName(),
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        TextButton(
                            onClick = {
                                if (checkingUpdate) return@TextButton
                                checkingUpdate = true
                                Thread {
                                    val release = AppUpdateUtil.checkUpdate()
                                    checkingUpdate = false
                                    if (release != null && AppUpdateUtil.hasUpdate(release)) {
                                        latestRelease = release
                                        showUpdateDialog = true
                                    } else {
                                        (context as? android.app.Activity)?.runOnUiThread {
                                            Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }.start()
                            },
                            enabled = !checkingUpdate
                        ) {
                            Text(if (checkingUpdate) "检查中..." else "检查更新")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("确定")
                }
            }
        )
        }
    }

    if (showUpdateDialog && latestRelease != null) {
        val release = latestRelease!!
        BackPressOverlay(onBackPress = { showUpdateDialog = false }) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("发现新版本 ${release.tagName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "当前版本：${AppUpdateUtil.getCurrentVersionName()}",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "最新版本：${release.tagName}",
                        color = Color(0xFF00BCD4),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (release.body.isNotBlank()) {
                        Text(
                            text = "\n更新日志：",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = release.body,
                            color = Color.DarkGray,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    AppUpdateUtil.downloadAndInstall(context, release.apkUrl)
                    showUpdateDialog = false
                    Toast.makeText(context, "开始下载，可在通知栏查看进度", Toast.LENGTH_LONG).show()
                }) {
                    Text("立即更新")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("稍后再说")
                }
            }
        )
        }
    }
}

/**
 * Tab 保活显隐容器：内容始终保留在组合中，仅做可见/不可见切换，
 * 避免高德 MapView 被销毁重建（重建成本高且会丢失相机/标记状态）。
 *
 * 触摸隔离：仅在【隐藏】时为 Box 挂载 pointerInput 兜底消费层，
 * 消费子级未消费的触摸事件，确保隐藏 Tab 永远不会收到触摸；
 * 【可见】时不挂载任何消费层——若可见时也消费事件，
 * Compose 祖先的消费会取消内嵌 MapView 的拖拽/双指缩放手势流。
 */
@Composable
private fun TabPane(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (visible) 1f else -1f)
            .alpha(if (visible) 1f else 0f)
            .then(
                if (visible) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            )
    ) {
        content()
    }
}
