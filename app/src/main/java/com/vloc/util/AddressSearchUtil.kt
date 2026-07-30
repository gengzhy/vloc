package com.vloc.util

import android.content.Context
import android.util.Log
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.AMapException
import com.amap.api.services.geocoder.GeocodeQuery
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeResult

/**
 * 地址模糊搜索工具类（正向地理编码：地址 → 经纬度列表）
 */
object AddressSearchUtil {

    private const val TAG = "AddressSearchUtil"

    /**
     * 搜索结果条目
     */
    data class SearchResult(
        val name: String,       // 地址名称
        val district: String,   // 所在区域
        val latLng: LatLng      // 经纬度
    ) {
        /** 显示在下拉列表中的文字 */
        val displayText: String
            get() = if (district.isNotEmpty()) "[$district] $name" else name
    }

    /**
     * 根据关键词模糊搜索地址（正向地理编码）
     *
     * @param context   上下文
     * @param keyword   搜索关键词
     * @param city      限定城市（为空则全国搜索），如 "北京"
     * @param callback  回调：成功时返回结果列表，失败时返回空列表
     */
    fun searchAddress(
        context: Context,
        keyword: String,
        city: String = "",
        callback: (List<SearchResult>) -> Unit
    ) {
        if (keyword.isBlank()) {
            callback(emptyList())
            return
        }
        try {
            val geocodeSearch = GeocodeSearch(context)
            val query = GeocodeQuery(keyword, city)

            geocodeSearch.setOnGeocodeSearchListener(object :
                GeocodeSearch.OnGeocodeSearchListener {

                override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) {
                    if (rCode == 1000 && result != null && !result.geocodeAddressList.isNullOrEmpty()) {
                        val results = result.geocodeAddressList.mapNotNull { addr ->
                            val loc = addr.latLonPoint ?: return@mapNotNull null
                            SearchResult(
                                name = addr.formatAddress ?: addr.district ?: keyword,
                                district = addr.district ?: "",
                                latLng = LatLng(loc.latitude, loc.longitude)
                            )
                        }
                        callback(results)
                    } else {
                        Log.w(TAG, "正向地理编码无结果，errorCode=$rCode")
                        callback(emptyList())
                    }
                }

                override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                    // 逆向编码不使用
                }
            })

            geocodeSearch.getFromLocationNameAsyn(query)
        } catch (e: AMapException) {
            Log.e(TAG, "地址搜索异常：${e.errorMessage}")
            callback(emptyList())
        }
    }
}
