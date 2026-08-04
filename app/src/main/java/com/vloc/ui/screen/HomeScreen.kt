package com.vloc.ui.screen

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import androidx.compose.ui.Alignment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.amap.api.maps.model.LatLng
import com.vloc.model.LocalViewModel
import com.vloc.ui.components.ActionBar
import com.vloc.ui.components.SearchBar
import com.vloc.ui.map.AMapComposeView
import com.vloc.util.AddressSearchUtil
import com.vloc.util.GeoCoderUtil
import com.vloc.util.MockLocationUtil
import com.vloc.util.NetworkUtil

/**
 * 首页：承载模拟定位主功能（地图 + 搜索 + 穿越/回归）。
 * 菜单入口已移至「我的」页面。
 */
@Composable
fun HomeScreen(
    vm: LocalViewModel,
    context: Context,
    visible: Boolean,
    onStartMock: (lat: Double, lng: Double) -> Unit,
    onStopMock: () -> Unit
) {
    val hasMockPermission = remember { MockLocationUtil.isMockEnable(context) }
    val hasNet = remember { NetworkUtil.isNetAvailable(context) }

    val latLng = vm.selectPoint.collectAsState().value
    var msg by remember {
        mutableStateOf("已加载默认位置(${latLng.latitude},${latLng.longitude})")
    }

    var searchText by remember { mutableStateOf("") }
    val searchResults = remember { mutableStateListOf<AddressSearchUtil.SearchResult>() }
    var jumpTarget by remember { mutableStateOf<LatLng?>(null) }

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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AMapComposeView(
                modifier = Modifier.fillMaxSize(),
                initialPosition = vm.selectPoint.collectAsState().value,
                jumpToPosition = jumpTarget,
                visible = visible,
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
                    context.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
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
                    context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
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
}
