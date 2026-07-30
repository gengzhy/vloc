package com.vloc.util

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 坐标转换：GCJ02 转 WGS84
 */
object GCJ02ToWGS84Util {

    /**
     * GCJ-02 转 WGS-84 转换
     */
    fun gcj02ToWgs84(lat: Double, lng: Double): DoubleArray {
        val a = 6378245.0
        val ee = 0.00669342162296594323
        var dLat = transformLat(lng - 105.0, lat - 35.0)
        var dLng = transformLng(lng - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * Math.PI
        var magic = sin(radLat)
        magic = 1 - ee * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = dLat * 180.0 / (a * (1 - ee) / (magic * sqrtMagic) * Math.PI)
        dLng = dLng * 180.0 / (a / sqrtMagic * cos(radLat) * Math.PI)
        return doubleArrayOf(lat - dLat, lng - dLng)
    }

    private fun transformLat(lng: Double, lat: Double): Double {
        var ret = -100.0 + 2.0 * lng + 3.0 * lat + 0.2 * lat * lat +
                0.1 * lng * lat + 0.2 * sqrt(abs(lng))
        ret += (20.0 * sin(6.0 * lng * Math.PI) + 20.0 * sin(2.0 * lng * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(lat * Math.PI) + 40.0 * sin(lat / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (160.0 * sin(lat / 12.0 * Math.PI) + 320 * sin(lat * Math.PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(lng: Double, lat: Double): Double {
        var ret = 300.0 + lng + 2.0 * lat + 0.1 * lng * lng +
                0.1 * lng * lat + 0.1 * sqrt(abs(lng))
        ret += (20.0 * sin(6.0 * lng * Math.PI) + 20.0 * sin(2.0 * lng * Math.PI)) * 2.0 / 3.0
        ret += (20.0 * sin(lng * Math.PI) + 40.0 * sin(lng / 3.0 * Math.PI)) * 2.0 / 3.0
        ret += (150.0 * sin(lng / 12.0 * Math.PI) + 300.0 * sin(lng / 30.0 * Math.PI)) * 2.0 / 3.0
        return ret
    }
}