package com.example.wakeonwanremotephone

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

    fun checkForUpdate(currentVersion: String, onResult: (Boolean, String?) -> Unit) {
        service.getLatestRelease().enqueue(object : Callback<GithubRelease> {
            override fun onResponse(call: Call<GithubRelease>, response: Response<GithubRelease>) {
                if (response.isSuccessful) {
                    val release = response.body()
                    if (release != null) {
                        val latestVersion = release.tagName.removePrefix("v")
                        if (latestVersion != currentVersion) {
                            val apkAsset = release.assets.find { it.name.contains("remote-phone") && it.name.endsWith(".apk") }
                            if (apkAsset != null) {
                                onResult(true, apkAsset.downloadUrl)
                            } else {
                                onResult(false, null)
                            }
                        } else {
                            onResult(false, null)
                        }
                    } else {
                        onResult(false, null)
                    }
                } else {
                    onResult(false, null)
                }
            }

            override fun onFailure(call: Call<GithubRelease>, t: Throwable) {
                onResult(false, null)
            }
        })
    }

    fun downloadAndInstall(url: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("下載更新")
            .setDescription("正在下載最新版本的遙控器 App...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "wakeonwanremotephone_update.apk")
            .setMimeType("application/vnd.android.package-archive")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId == id) {
                    installApk(downloadId)
                    context.unregisterReceiver(this)
                }
            }
        }
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED)
    }

    private fun installApk(downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = downloadManager.getUriForDownloadedFile(downloadId)
        
        if (uri != null) {
            val intent = Intent(Intent.ACTION_VIEW)
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "wakeonwanremotephone_update.apk")
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "下載失敗，無法讀取檔案", Toast.LENGTH_SHORT).show()
        }
    }
}
