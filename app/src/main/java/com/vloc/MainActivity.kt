package com.vloc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.amap.api.maps.MapsInitializer
import com.vloc.model.LocalViewModel
import com.vloc.service.MockLocationService
import com.vloc.ui.screen.ApiKeyScreen
import com.vloc.ui.screen.MainScreen
import com.vloc.util.NetworkUtil
import com.vloc.util.setupDoubleBackExit
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupDoubleBackExit()

        if (!NetworkUtil.isNetAvailable(this)) {
            Toast.makeText(this, "当前无网络，地图无法加载，请开启WIFI/流量", Toast.LENGTH_LONG)
                .show()
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), 1001
            )
        }

        val vm: LocalViewModel by viewModels()
        val savedApiKey = vm.getSavedApiKey()
        if (!savedApiKey.isNullOrEmpty()) {
            MapsInitializer.setApiKey(savedApiKey)
        }

        MapsInitializer.updatePrivacyShow(this@MainActivity, true, true)
        MapsInitializer.updatePrivacyAgree(this@MainActivity, true)

        setContent {
            var showDisclaimer by remember { mutableStateOf(!vm.isDisclaimerAgreed()) }

            if (showDisclaimer) {
                AlertDialog(
                    onDismissRequest = { },
                    title = {
                        Text(
                            text = getString(R.string.warning_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.Red
                        )
                    },
                    text = {
                        Text(
                            text = getString(R.string.warning_content),
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = Color.Red
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.saveDisclaimerAgreed()
                            showDisclaimer = false
                        }) {
                            Text("同意")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            finishAffinity()
                            exitProcess(0)
                        }) {
                            Text("退出")
                        }
                    }
                )
            }

            if (!showDisclaimer) {
                if (savedApiKey.isNullOrEmpty()) {
                    ApiKeyScreen(
                        onSave = { key ->
                            vm.saveApiKey(key)
                            recreate()
                        },
                        onShowToast = { msg ->
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    MainScreen(
                        vm = vm,
                        savedApiKey = savedApiKey,
                        context = this@MainActivity,
                        onStartMock = { lat, lng -> startMockService(lat, lng) },
                        onStopMock = { stopMockService() },
                        onRecreate = { recreate() },
                        onExit = {
                            vm.forceSaveToDisk()
                            finishAffinity()
                            exitProcess(0)
                        }
                    )
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startMockService(lat: Double, lng: Double, alt: Double = 55.0) {
        val intent = Intent(this, MockLocationService::class.java).apply {
            putExtra(MockLocationService.EXTRA_LAT, lat)
            putExtra(MockLocationService.EXTRA_LNG, lng)
            putExtra(MockLocationService.EXTRA_ALT, alt)
        }
        startForegroundService(intent)
    }

    private fun stopMockService() {
        val intent = Intent(this, MockLocationService::class.java)
        stopService(intent)
    }

}
