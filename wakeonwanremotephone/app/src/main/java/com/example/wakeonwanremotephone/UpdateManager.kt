package com.example.wakeonwanremotephone

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.io.File
import java.util.concurrent.atomic.AtomicLong

interface GithubService {
    @GET("releases/latest")
    fun getLatestRelease(): Call<GithubRelease>
}

class UpdateManager(context: Context) {

    /**
     * Never retain an Activity: download callbacks and the completion receiver may outlive it.
     */
    private val appContext: Context = context.applicationContext

    private val localizedContext: Context
        get() = AppLocale.wrap(appContext)

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/repos/pinchiu/wake-on-lan-for-android/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val service = retrofit.create(GithubService::class.java)

    companion object {
        private const val UPDATE_FILE_NAME = "wakeonwanremotephone_update.apk"

        /**
         * Compare dotted numeric versions after removing a leading `v`/`V` and any
         * `-prerelease` / `+build` suffix. Missing segments count as zero.
         */
        internal fun isNewerVersion(latest: String, current: String): Boolean {
            val latestParts = parseVersion(latest)
            val currentParts = parseVersion(current)
            val size = maxOf(latestParts.size, currentParts.size)
            for (index in 0 until size) {
                val latestPart = latestParts.getOrElse(index) { 0 }
                val currentPart = currentParts.getOrElse(index) { 0 }
                if (latestPart > currentPart) return true
                if (latestPart < currentPart) return false
            }
            return false
        }

        private fun parseVersion(version: String): List<Int> {
            val cleaned = version.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .substringBefore('+')
            if (cleaned.isEmpty()) return emptyList()
            return cleaned.split('.').map { it.toIntOrNull() ?: 0 }
        }
    }

    fun checkForUpdate(currentVersion: String, onResult: (Boolean, String?, String?) -> Unit) {
        service.getLatestRelease().enqueue(object : Callback<GithubRelease> {
            override fun onResponse(call: Call<GithubRelease>, response: Response<GithubRelease>) {
                if (response.isSuccessful) {
                    val release = response.body()
                    if (release != null) {
                        val latestVersion = release.tagName.removePrefix("v")
                        if (isNewerVersion(latestVersion, currentVersion)) {
                            val apkAsset = release.assets?.find { it.name.contains("remote-phone") && it.name.endsWith(".apk") }
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
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase(java.util.Locale.ROOT)
        val host = uri.host?.lowercase(java.util.Locale.ROOT).orEmpty()
        val trustedHost = host == "github.com" || host.endsWith(".githubusercontent.com")
        if (scheme != "https" || !trustedHost) {
            showToast(R.string.update_download_failed)
            return
        }

        val downloadDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: run {
            showToast(R.string.update_download_failed)
            return
        }
        val file = File(downloadDir, UPDATE_FILE_NAME)
        if (file.exists()) {
            file.delete()
        }

        val downloadManager =
            appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (downloadManager == null) {
            showToast(R.string.update_download_failed)
            return
        }

        val request = DownloadManager.Request(uri)
            .setTitle(localizedContext.getString(R.string.update_download_title))
            .setDescription(localizedContext.getString(R.string.update_download_description))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, UPDATE_FILE_NAME)
            .setMimeType("application/vnd.android.package-archive")

        val downloadIdRef = AtomicLong(-1L)
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                val expectedId = downloadIdRef.get()
                if (expectedId == -1L || expectedId != id) return

                var status = DownloadManager.STATUS_FAILED
                try {
                    downloadManager.query(DownloadManager.Query().setFilterById(expectedId))?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            if (statusIndex != -1) {
                                status = cursor.getInt(statusIndex)
                            }
                        }
                    }
                } catch (_: Exception) {
                    status = DownloadManager.STATUS_FAILED
                }

                try {
                    appContext.unregisterReceiver(this)
                } catch (_: IllegalArgumentException) {
                    // Already unregistered
                }

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    installApk()
                } else {
                    showToast(R.string.update_download_failed)
                }
            }
        }

        // Register first: enqueue can complete (and broadcast) before a receiver registered
        // afterwards is delivered the sticky completion.
        ContextCompat.registerReceiver(
            appContext,
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
        val downloadId = try {
            downloadManager.enqueue(request)
        } catch (_: Exception) {
            try {
                appContext.unregisterReceiver(onComplete)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
            showToast(R.string.update_download_failed)
            return
        }
        downloadIdRef.set(downloadId)
    }

    private fun installApk() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            try {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${appContext.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(settingsIntent)
            } catch (_: Exception) {
                showToast(R.string.update_installer_not_found)
            }
            return
        }

        val downloadDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val file = downloadDir?.let { File(it, UPDATE_FILE_NAME) }
        if (file == null || !file.exists()) {
            showToast(R.string.update_installer_not_found)
            return
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val contentUri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.provider", file)
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        } catch (_: Exception) {
            showToast(R.string.update_installer_not_found)
        }
    }

    private fun showToast(resId: Int) {
        Toast.makeText(appContext, localizedContext.getString(resId), Toast.LENGTH_SHORT).show()
    }
}
