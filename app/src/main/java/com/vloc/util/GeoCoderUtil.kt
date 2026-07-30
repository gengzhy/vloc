package com.vloc.util

import android.content.Context
import android.util.Log
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult

/**
 * 逆地理编码工具类：根据经纬度获取地址信息
 *
 * 使用示例：
 * ```
 * GeoCoderUtil.reverseGeocode(context, 39.9042, 116.4074) { address, result ->
 *     if (address != null) {
 *         Log.d("TAG", "地址：$address")
 *     }
 * }
 * ```
 */
object GeoCoderUtil {

    private const val TAG = "GeoCoderUtil"
    private const val DEFAULT_SEARCH_RADIUS = 200f  // 搜索半径（米）

    /**
     * 逆地理编码结果
     */
    data class AddressInfo(
        val formattedAddress: String,   // 完整地址
        val province: String,           // 省
        val city: String,               // 市
        val district: String,           // 区/县
        val township: String,           // 乡镇/街道
        val neighborhood: String,       // 社区/小区
        val building: String            // 建筑
    )

    /**
     * 根据经纬度获取地址（异步回调）
     *
     * @param context  上下文
     * @param latitude  纬度
     * @param longitude 经度
     * @param radius    搜索半径（米），默认200m
     * @param callback  回调：AddressInfo成功时非null，失败时为null并附带错误码
     */
    fun reverseGeocode(
        context: Context,
        latitude: Double,
        longitude: Double,
        radius: Float = DEFAULT_SEARCH_RADIUS,
        callback: (AddressInfo?, errorCode: Int) -> Unit
    ) {
        val geocodeSearch = GeocodeSearch(context)
        val latLonPoint = LatLonPoint(latitude, longitude)
        val query = RegeocodeQuery(latLonPoint, radius, GeocodeSearch.AMAP)

        geocodeSearch.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
            override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                if (rCode == 1000 && result != null && result.regeocodeAddress != null) {
                    val addr = result.regeocodeAddress
                    val info = AddressInfo(
                        formattedAddress = addr.formatAddress ?: "",
                        province = addr.province ?: "",
                        city = addr.city ?: "",
                        district = addr.district ?: "",
                        township = addr.township ?: "",
                        neighborhood = addr.neighborhood ?: "",
                        building = addr.building ?: ""
                    )
                    callback(info, rCode)
                } else {
                    Log.e(TAG, "逆地理编码失败，错误码：$rCode")
                    callback(null, rCode)
                }
            }

            override fun onGeocodeSearched(result: com.amap.api.services.geocoder.GeocodeResult?, rCode: Int) {
                // 正向地理编码（地址→经纬度），此处不使用
            }
        })

        geocodeSearch.getFromLocationAsyn(query)
    }

    /**
     * 根据经纬度获取简要地址名称（异步回调）
     *
     * @param context  上下文
     * @param latitude  纬度
     * @param longitude 经度
     * @param callback  回调：地址字符串，失败时返回null
     */
    fun getAddressName(
        context: Context,
        latitude: Double,
        longitude: Double,
        callback: (String?) -> Unit
    ) {
        reverseGeocode(context, latitude, longitude) { info, _ ->
            callback(info?.formattedAddress)
        }
    }
}
