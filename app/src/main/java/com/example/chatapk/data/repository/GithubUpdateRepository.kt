package com.example.chatapk.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.example.chatapk.domain.repository.UpdateRepository
import com.example.chatapk.domain.repository.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class GithubUpdateRepository(
    private val context: Context,
    private val githubUser: String,
    private val repoName: String
) : UpdateRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun checkForUpdate(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$githubUser/$repoName/releases")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "ChatApk-App")
            connection.connect()

            if (connection.responseCode == 200) {
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val releases = json.parseToJsonElement(responseBody).jsonArray
                
                if (releases.isEmpty()) {
                    return@withContext UpdateResult.NoUpdateAvailable
                }

                val latestRelease = releases[0].jsonObject
                val latestVersion = latestRelease["tag_name"]?.jsonPrimitive?.content ?: ""
                
                val cleanLatest = latestVersion.lowercase().removePrefix("v").trim()
                val cleanCurrent = currentVersion.lowercase().removePrefix("v").trim()

                if (isNewer(cleanLatest, cleanCurrent)) {
                    val assets = latestRelease["assets"]?.jsonArray
                    val apkAsset = assets?.firstOrNull { 
                        it.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true 
                    }
                    val downloadUrl = apkAsset?.jsonObject["browser_download_url"]?.jsonPrimitive?.content ?: ""
                    
                    if (downloadUrl.isNotEmpty()) {
                        UpdateResult.NewVersionAvailable(latestVersion, downloadUrl)
                    } else {
                        UpdateResult.Error("No APK found in the latest release")
                    }
                } else {
                    UpdateResult.NoUpdateAvailable
                }
            } else {
                UpdateResult.Error("GitHub API Error: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            UpdateResult.Error("Network Error: ${e.localizedMessage}")
        }
    }

    suspend fun downloadAndInstallApk(url: String) = withContext(Dispatchers.IO) {
        try {
            val apkFile = File(context.cacheDir, "update.apk")
            val downloadUrl = URL(url)
            val connection = downloadUrl.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "ChatApk-App")
            connection.connect()
            
            connection.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (verifySignature(apkFile)) {
                installApk(apkFile)
            } else {
                Log.e("Update", "Signature verification failed!")
                apkFile.delete()
            }
        } catch (e: Exception) {
            Log.e("Update", "Download error", e)
        }
    }

    private fun verifySignature(apkFile: File): Boolean {
        return try {
            val packageManager = context.packageManager
            
            // Get current app signature
            val currentSignature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }

            // Get downloaded APK signature
            val downloadedPackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
            }
            
            val downloadedSignature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                downloadedPackageInfo?.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                downloadedPackageInfo?.signatures
            }

            if (currentSignature.isNullOrEmpty() || downloadedSignature.isNullOrEmpty()) return false
            
            // Compare the first signature
            currentSignature[0].toCharsString() == downloadedSignature[0].toCharsString()
        } catch (e: Exception) {
            Log.e("Update", "Verification error", e)
            false
        }
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun isNewer(latest: String, current: String): Boolean {
        if (latest.isEmpty()) return false
        if (current.isEmpty()) return true
        
        val latestParts = latest.split('.').mapNotNull { it.toIntOrNull() }
        val currentParts = current.split('.').mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(latestParts.size, currentParts.size)
        
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
