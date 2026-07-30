package com.vloc.ui.screen

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amap.api.maps.model.LatLng
import com.vloc.R
import com.vloc.model.LocalViewModel
import com.vloc.ui.components.ActionBar
import com.vloc.ui.components.SearchBar
import com.vloc.ui.components.SettingsDrawer
import com.vloc.ui.map.AMapComposeView
import com.vloc.util.AddressSearchUtil
import com.vloc.util.GeoCoderUtil
import com.vloc.util.MockLocationUtil
import com.vloc.util.NetworkUtil

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
    val hasMockPermission = remember { MockLocationUtil.isMockEnable(context) }
    val hasNet = remember { NetworkUtil.isNetAvailable(context) }

    var menuExpanded by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val latLng = vm.selectPoint.collectAsState().value
    var msg by remember {
        mutableStateOf("已加载默认位置(${latLng.latitude},${latLng.longitude})")
    }

    var searchText by remember { mutableStateOf("") }
    val searchResults = remember { mutableStateListOf<AddressSearchUtil.SearchResult>() }
    var jumpTarget by remember { mutableStateOf<LatLng?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!hasNet) {
                Text(
                    text = "⚠️ 当前网络异常，地图加载失败",
                    modifier = Modifier.padding(10.dp)
                )
            }

            if (!hasMockPermission) {
                Text(
                    text = "⚠️ 未获取模拟定位权限，请先在设置中开启",
                    modifier = Modifier.padding(10.dp),
                    color = Color.Red
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = Color.Black,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "设置",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AMapComposeView(
                    modifier = Modifier.fillMaxSize(),
                    initialPosition = vm.selectPoint.collectAsState().value,
                    jumpToPosition = jumpTarget,
                    onMapClick = { point ->
                        vm.updateSelectPoint(point)
                        GeoCoderUtil.getAddressName(
                            context, point.latitude, point.longitude
                        ) { address ->
                            val latLonStr = "(${point.latitude},${point.longitude})"
                            msg = address?.let { "已选中：$it$latLonStr" }
                                ?: "已选中：$latLonStr"
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                ) {
                    SearchBar(
                        searchText = searchText,
                        onSearchTextChange = { text ->
                            searchText = text
                            if (text.isBlank()) {
                                searchResults.clear()
                            } else {
                                AddressSearchUtil.searchAddress(context, text) { results ->
                                    searchResults.clear()
                                    searchResults.addAll(results)
                                }
                            }
                        },
                        searchResults = searchResults,
                        onSearch = {
                            if (searchText.isNotBlank()) {
                                AddressSearchUtil.searchAddress(context, searchText) { results ->
                                    searchResults.clear()
                                    searchResults.addAll(results)
                                }
                            }
                        },
                        onResultClick = { result ->
                            val selected = result.latLng
                            vm.updateSelectPoint(selected)
                            jumpTarget = selected
                            msg =
                                "已选中：${result.name}\n(${selected.latitude},${selected.longitude})"
                            searchText = ""
                            searchResults.clear()
                        }
                    )
                }
            }

            ActionBar(
                msg = msg,
                onStartMock = {
                    if (!hasNet) {
                        Toast.makeText(context, "无网络无法使用", Toast.LENGTH_SHORT).show()
                        return@ActionBar
                    }
                    if (!hasMockPermission) {
                        Toast.makeText(context, "无模拟定位权限，请先开启", Toast.LENGTH_SHORT)
                            .show()
                        return@ActionBar
                    }
                    val wifiManager =
                        context.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                    if (wifiManager.isWifiEnabled) {
                        Toast.makeText(
                            context,
                            "请关闭 WiFi！WiFi 会导致定位闪回真实位置",
                            Toast.LENGTH_LONG
                        ).show()
                        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        return@ActionBar
                    }
                    val locationManager =
                        context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
                    if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        Toast.makeText(context, "请开启 GPS 定位服务", Toast.LENGTH_LONG).show()
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        return@ActionBar
                    }
                    val point = vm.selectPoint.value
                    onStartMock(point.latitude, point.longitude)
                    msg = "已启动模拟定位(${point.latitude}, ${point.longitude})"
                },
                onStopMock = {
                    onStopMock()
                    msg = "已停止模拟定位"
                }
            )
        }

        SettingsDrawer(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
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
                if (!hasMockPermission) {
                    MockLocationUtil.goMockSetting(context)
                } else {
                    Toast.makeText(context, "已开模拟位置权限", Toast.LENGTH_LONG).show()
                }
            },
            onExit = onExit,
            onAbout = { showAboutDialog = true }
        )
    }

    if (showApiKeyDialog) {
        var dialogKeyInput by remember { mutableStateOf(savedApiKey) }
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
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Text)
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

    if (showAboutDialog) {
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
                    Text(
                        text = "Version " + stringResource(R.string.version),
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
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
