package com.vloc.ui.map

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.AMapOptions
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle

/**
 * 封装的高德地图 Composable 组件
 *
 * @param initialPosition 初始化经纬度坐标
 * @param jumpToPosition 当此值变化时，地图视角跳转到该位置并打点（用于搜索结果定位）
 * @param visible 是否可见（用于 Tab 保活显隐：隐藏时置 View.INVISIBLE，避免 alpha 对嵌入 View 不生效）
 * @param onMapClick 地图点击事件
 */
@Composable
fun AMapComposeView(
    modifier: Modifier = Modifier,
    initialPosition: LatLng? = null,
    jumpToPosition: LatLng? = null,
    visible: Boolean = true,
    onMapClick: (LatLng) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // 使用 remember 保持 MapView 实例，避免重组时重复创建
    val mapView = remember { MapView(context) }

    // 核心优化：使用 DisposableEffect 绑定生命周期
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize(), update = { view ->
            // Tab 隐藏时不绘制地图（View 级隐藏，Compose alpha 对嵌入 View 不生效）
            view.visibility = if (visible) android.view.View.VISIBLE else android.view.View.INVISIBLE
            val aMap = view.map

            // 配置蓝点定位样式：持续显示方向箭头（compass 模式），保持视角跟随
            val myLocationStyle = MyLocationStyle().apply {
                // LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER：持续定位+显示方向箭头，但不强制锁定视角中心
                // 这样用户可以自由拖动地图，箭头仍然指示方向
                myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
                // 定位间隔（毫秒），0 表示只定位一次后不再刷新
                interval(2000)
            }
            aMap.myLocationStyle = myLocationStyle

            // 开启定位蓝点（含方向箭头）
            aMap.isMyLocationEnabled = true

            // 关闭 SDK 内置定位按钮，使用自定义按钮
            aMap.uiSettings.isMyLocationButtonEnabled = false
            aMap.uiSettings.zoomPosition = AMapOptions.ZOOM_POSITION_RIGHT_CENTER
            aMap.uiSettings.isZoomGesturesEnabled = true
            aMap.uiSettings.isZoomControlsEnabled = true

            // 地图点击监听
            aMap.setOnMapClickListener { latLng ->
                aMap.clear()
                aMap.addMarker(MarkerOptions().position(latLng))
                onMapClick(latLng)
            }
        })

        // 自定义定位按钮
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(40.dp)
                .background(Color.Transparent)
                .clickable {
                    val aMap = mapView.map
                    val myLocation = aMap.myLocation
                    if (myLocation != null) {
                        val latLng = LatLng(myLocation.latitude, myLocation.longitude)
                        aMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "定位到当前位置",
                tint = Color.Gray,  // 蓝色
                modifier = Modifier.size(32.dp)
            )
        }
    }

    // 在地图初始化完成后，移动到默认位置并添加标记
    if (initialPosition != null) {
        LaunchedEffect(Unit) {
            val aMap = mapView.map
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialPosition, 16f))
            aMap.addMarker(MarkerOptions().position(initialPosition))
        }
    }

    // 外部触发：搜索选中后跳转到指定坐标
    if (jumpToPosition != null) {
        LaunchedEffect(Unit) {
            val aMap = mapView.map
            aMap.clear()
            aMap.addMarker(MarkerOptions().position(jumpToPosition))
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(jumpToPosition, 16f))
        }
    }
}