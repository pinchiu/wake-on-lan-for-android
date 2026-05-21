package com.example.wakeonlanhomephone

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.io.File

interface GithubService {
    @GET("releases/latest")
    fun getLatestRelease(): Call<GithubRelease>
}

class UpdateManager(private val context: Context) {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/repos/pinchiu/wake-on-lan-for-android/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val service = retrofit.create(GithubService::class.java)

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val size = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until size) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    fun checkForUpdate(currentVersion: String, onResult: (Boolean, String?, String?) -> Unit) {
        service.getLatestRelease().enqueue(object : Callback<GithubRelease> {
            override fun onResponse(call: Call<GithubRelease>, response: Response<GithubRelease>) {
                if (response.isSuccessful) {
                    val release = response.body()
                    if (release != null) {
                        val latestVersion = release.tagName.removePrefix("v")
                        if (isNewerVersion(latestVersion, currentVersion)) {
                            val apkAsset = release.assets?.find { it.name.contains("home-phone") && it.name.endsWith(".apk") }
                            if (apkAsset != null) {
                                onResult(true, apkAsset.downloadUrl, release.tagName)
                            } else {
                                onResult(false, null, null)
                            }
                        } else {
                            onResult(false, null, null)
                        }
                    } else {
                        onResult(false, null, null)
                    }
                } else {
                    onResult(false, null, null)
                }
            }

            override fun onFailure(call: Call<GithubRelease>, t: Throwable) {
                onResult(false, null, null)
            }
        })
    }

    fun downloadAndInstall(url: String) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "wakeonlanhomephone_update.apk")
        if (file.exists()) {
            file.delete()
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("下載更新")
            .setDescription("正在下載最新版本的家用喚醒助手")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "wakeonlanhomephone_update.apk")
            .setMimeType("application/vnd.android.package-archive")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId == id) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    var status = DownloadManager.STATUS_FAILED
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        if (statusIndex != -1) {
                            status = cursor.getInt(statusIndex)
                        }
                        cursor.close()
                    }

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        installApk()
                    } else {
                        Toast.makeText(context, "下載失敗", Toast.LENGTH_SHORT).show()
                    }
                    try {
                        context.applicationContext.unregisterReceiver(this)
                    } catch (e: IllegalArgumentException) {
                        // Already unregistered
                    }
                }
            }
        }
        context.applicationContext.registerReceiver(
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    private fun installApk() {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "wakeonlanhomephone_update.apk")
        if (file.exists()) {
            val intent = Intent(Intent.ACTION_VIEW)
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "找不到安裝檔", Toast.LENGTH_SHORT).show()
        }
    }
}

