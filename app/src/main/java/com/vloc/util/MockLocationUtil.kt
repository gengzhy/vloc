package com.vloc.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings

object MockLocationUtil {

    /** 检测是否拥有模拟定位权限（适配Android 6+） */
    fun isMockEnable(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val appOpsManager =
                    context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val op = AppOpsManager.OPSTR_MOCK_LOCATION
                // 检查OP_MOCK_LOCATION权限状态
                val mode = appOpsManager.checkOpNoThrow(
                    op, Process.myUid(), context.packageName
                )
                // mode == MODE_ALLOWED 表示拥有模拟定位权限
                mode == AppOpsManager.MODE_ALLOWED
            } else {
                // 低版本兼容原逻辑
                val mockPkg = Settings.Secure.getString(
                    context.contentResolver, Settings.Secure.ALLOW_MOCK_LOCATION
                )
                mockPkg == context.packageName
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** 跳转开发者选项-模拟位置设置页面 */
    fun goMockSetting(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

}