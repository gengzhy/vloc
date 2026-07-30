package com.vloc.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Process
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationCompat
import com.vloc.MainActivity
import com.vloc.R
import com.vloc.util.GCJ02ToWGS84Util

class MockLocationService : Service() {
    companion object {
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        const val EXTRA_ALT = "extra_alt"
        const val DEFAULT_LAT = 39.9042
        const val DEFAULT_LNG = 116.4074
        const val DEFAULT_ALT = 55.0

        private const val HANDLER_MSG_ID = 0
        private const val SERVICE_HANDLER_NAME = "MockLocationService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "mock_location_channel"
        private const val CHANNEL_NAME = "模拟定位服务"
    }

    private lateinit var locManager: LocationManager
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private var isStop = false

    private var curLat = DEFAULT_LAT
    private var curLng = DEFAULT_LNG
    private var curAlt = DEFAULT_ALT
    private var curBea = 0f
    private var curSpeed = 1.2f

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate() {
        super.onCreate()
        locManager = getSystemService(LOCATION_SERVICE) as LocationManager

        removeTestProviderNetwork()
        addTestProviderNetwork()

        removeTestProviderGPS()
        addTestProviderGPS()

        initLocationLoop()
        initNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            curLat = it.getDoubleExtra(EXTRA_LAT, DEFAULT_LAT)
            curLng = it.getDoubleExtra(EXTRA_LNG, DEFAULT_LNG)
            curAlt = it.getDoubleExtra(EXTRA_ALT, DEFAULT_ALT)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isStop = true
        handler.removeMessages(HANDLER_MSG_ID)
        handlerThread.quit()

        removeTestProviderNetwork()
        removeTestProviderGPS()

        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun initLocationLoop() {
        handlerThread = HandlerThread(SERVICE_HANDLER_NAME, Process.THREAD_PRIORITY_FOREGROUND)
        handlerThread.start()
        handler = object : Handler(handlerThread.looper) {
            override fun handleMessage(msg: Message) {
                try {
                    Thread.sleep(100)

                    if (!isStop) {
                        setLocationNetwork()
                        setLocationGPS()

                        sendEmptyMessage(HANDLER_MSG_ID)
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        handler.sendEmptyMessage(HANDLER_MSG_ID)
    }

    private fun removeTestProviderGPS() {
        try {
            if (locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
                locManager.removeTestProvider(LocationManager.GPS_PROVIDER)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    @RequiresApi(Build.VERSION_CODES.S)
    private fun addTestProviderGPS() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locManager.addTestProvider(
                    LocationManager.GPS_PROVIDER,
                    false,
                    true,
                    false,
                    false,
                    true,
                    true,
                    true,
                    ProviderProperties.POWER_USAGE_HIGH,
                    ProviderProperties.ACCURACY_FINE
                )
            } else {
                @Suppress("DEPRECATION") locManager.addTestProvider(
                    LocationManager.GPS_PROVIDER,
                    false,
                    true,
                    false,
                    false,
                    true,
                    true,
                    true,
                    ProviderProperties.POWER_USAGE_HIGH,
                    ProviderProperties.ACCURACY_FINE
                )
            }
            if (!locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setLocationGPS() {
        try {
            val wgs84 = GCJ02ToWGS84Util.gcj02ToWgs84(curLat, curLng)
            val loc = Location(LocationManager.GPS_PROVIDER).apply {
                accuracy = 1F
                altitude = curAlt
                bearing = curBea
                latitude = wgs84[0]
                longitude = wgs84[1]
                time = System.currentTimeMillis()
                speed = curSpeed
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                val bundle = Bundle()
                bundle.putInt("satellites", 7)
                extras = bundle
            }
            locManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeTestProviderNetwork() {
        try {
            if (locManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
                locManager.removeTestProvider(LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    @RequiresApi(Build.VERSION_CODES.S)
    private fun addTestProviderNetwork() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locManager.addTestProvider(
                    LocationManager.NETWORK_PROVIDER,
                    true,
                    false,
                    true,
                    true,
                    true,
                    true,
                    true,
                    ProviderProperties.POWER_USAGE_LOW,
                    ProviderProperties.ACCURACY_COARSE
                )
            } else {
                locManager.addTestProvider(
                    LocationManager.NETWORK_PROVIDER,
                    true,
                    false,
                    true,
                    true,
                    true,
                    true,
                    true,
                    ProviderProperties.POWER_USAGE_LOW,
                    ProviderProperties.ACCURACY_COARSE
                )
            }
            if (!locManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun setLocationNetwork() {
        try {
            val wgs84 = GCJ02ToWGS84Util.gcj02ToWgs84(curLat, curLng)
            val loc = Location(LocationManager.NETWORK_PROVIDER).apply {
                accuracy = 2F
                altitude = curAlt
                bearing = curBea
                latitude = wgs84[0]
                longitude = wgs84[1]
                time = System.currentTimeMillis()
                speed = curSpeed
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            locManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, loc)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initNotification() {
        val channel =
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification =
            NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("vloc 模拟定位")
                .setContentText("正在模拟位置: $curLat, $curLng").setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent).build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}