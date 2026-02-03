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
import com.example.wakeonlanhomephone.GithubRelease
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
                        // Simple string comparison or semver depending on your tag format
                        if (latestVersion != currentVersion) { // Assuming naive string check for now
                             // Find the APK asset
                            val apkAsset = release.assets.find { it.name.endsWith(".apk") }
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
            .setTitle("Downloading Update")
            .setDescription("Downloading latest version of wakeonlanhomephone")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "update.apk")
            .setMimeType("application/vnd.android.package-archive")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        // Register Receiver for completion
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
            
            // For FileProvider (Android 7+)
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "update.apk")
            // Note: downloadManager.getUriForDownloadedFile returns a content:// uri if possible, but let's be robust
            
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
        }
    }
}
