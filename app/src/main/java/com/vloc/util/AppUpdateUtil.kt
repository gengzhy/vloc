package com.vloc.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import com.vloc.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val apkUrl: String,
    val versionCode: Int
)

object AppUpdateUtil {

    private const val GITHUB_API = "https://api.github.com/repos/gengzhy/vloc/releases/latest"

    fun checkUpdate(): ReleaseInfo? {
        return try {
            val url = URL(GITHUB_API)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            parseRelease(response)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseRelease(json: String): ReleaseInfo? {
        return try {
            val obj = JSONObject(json)
            val tagName = obj.getString("tag_name")
            val name = obj.optString("name", tagName)
            val body = obj.optString("body", "")

            var apkUrl = ""
            val assets = obj.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.getString("name")
                if (assetName.endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }

            if (apkUrl.isEmpty()) return null

            ReleaseInfo(
                tagName = tagName,
                name = name,
                body = body,
                apkUrl = apkUrl,
                versionCode = parseVersionCode(tagName)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseVersionCode(tagName: String): Int {
        val clean = tagName.trimStart('v', 'V')
        val parts = clean.split(".")
        var code = 0
        for (i in parts.indices) {
            code = code * 100 + (parts[i].toIntOrNull() ?: 0)
        }
        return code
    }

    fun hasUpdate(remote: ReleaseInfo): Boolean {
        return remote.versionCode > BuildConfig.VERSION_CODE
    }

    fun getCurrentVersionName(): String {
        return BuildConfig.VERSION_NAME
    }

    fun downloadAndInstall(context: Context, apkUrl: String) {
        val request = DownloadManager.Request(apkUrl.toUri()).apply {
            setTitle("vloc 版本更新")
            setDescription("正在下载新版本...")
            setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "vloc-update.apk"
            )
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = downloadManager.enqueue(request)

        Thread {
            try {
                var downloading = true
                while (downloading) {
                    val query = DownloadManager.Query().setFilterById(id)
                    val cursor = downloadManager.query(query)
                    if (cursor.moveToFirst()) {
                        val status =
                            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            downloading = false
                            val uri = downloadManager.getUriForDownloadedFile(id)
                            if (uri != null) {
                                installApk(context, uri)
                            }
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            downloading = false
                        }
                    }
                    cursor.close()
                    Thread.sleep(500)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun installApk(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(uri, "application/vnd.android.package-archive")
        }
        context.startActivity(intent)
    }
}
