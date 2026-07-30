package com.vloc.model

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.amap.api.maps.model.LatLng
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow

class LocalViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AndroidViewModel"
        private const val PREFS_NAME = "vloc_settings"
        private const val KEY_DEFAULT_LAT = "default_lat"
        private const val KEY_DEFAULT_LNG = "default_lng"
        private const val KEY_DEFAULT_ALT = "default_alt"
        private const val KEY_AMAP_API_KEY = "amap_api_key"
        private const val KEY_DISCLAIMER_AGREED = "disclaimer_agreed"
        const val DEFAULT_LAT = 39.9042
        const val DEFAULT_LNG = 116.4074
        const val DEFAULT_ALT = 55.0
    }

    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 从 SharedPreferences 读取保存的位置，没有则使用默认值
    val selectPoint = MutableStateFlow(loadSavedLocation())

    /**
     * 从 SharedPreferences 加载位置
     */
    private fun loadSavedLocation(): LatLng {
        val lat = prefs.getString(KEY_DEFAULT_LAT, null)?.toDoubleOrNull() ?: DEFAULT_LAT
        val lng = prefs.getString(KEY_DEFAULT_LNG, null)?.toDoubleOrNull() ?: DEFAULT_LNG
        return LatLng(lat, lng)
    }

    /**
     * 更新当前选中的点（同时保存到本地）
     */
    fun updateSelectPoint(latLng: LatLng) {
        selectPoint.value = latLng
        // 自动保存到 SharedPreferences
        prefs.edit {
            putString(KEY_DEFAULT_LAT, latLng.latitude.toString())
            putString(KEY_DEFAULT_LNG, latLng.longitude.toString())
        }
    }

    /**
     * 手动保存当前选中的点为默认位置（菜单点击时调用）
     */
    fun saveAsDefaultLocation() {
        val latLng = selectPoint.value
        // 已经自动保存了，这里可以额外做点什么，比如保存海拔
        prefs.edit(commit = true) {  // 使用commit确保同步写入磁盘
            putString(KEY_DEFAULT_LAT, latLng.latitude.toString())
            putString(KEY_DEFAULT_LNG, latLng.longitude.toString())
            putString(KEY_DEFAULT_ALT, DEFAULT_ALT.toString())
        }
    }

    /**
     * 强制同步保存到磁盘（退出前调用）
     */
    fun forceSaveToDisk() {
        val latLng = selectPoint.value
        Log.d(TAG, "持久化默认地图经纬度数据：${latLng.latitude},${latLng.longitude}")
        prefs.edit(commit = true) {
            putString(KEY_DEFAULT_LAT, latLng.latitude.toString())
            putString(KEY_DEFAULT_LNG, latLng.longitude.toString())
        }
    }

    /**
     * 获取保存的高德 API Key
     */
    fun getSavedApiKey(): String? {
        return prefs.getString(KEY_AMAP_API_KEY, null)
    }

    /**
     * 保存高德 API Key
     */
    fun saveApiKey(key: String) {
        prefs.edit { putString(KEY_AMAP_API_KEY, key) }
    }

    fun isDisclaimerAgreed(): Boolean {
        return prefs.getBoolean(KEY_DISCLAIMER_AGREED, false)
    }

    fun saveDisclaimerAgreed() {
        prefs.edit { putBoolean(KEY_DISCLAIMER_AGREED, true) }
    }

}