package com.example.chatapk.data.repository

import android.content.Context
import android.content.Intent
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
            // Use the list endpoint instead of '/latest' to support pre-releases
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

                // GitHub sorts by date, so the first one is the newest
                val latestRelease = releases[0].jsonObject
                val latestVersion = latestRelease["tag_name"]?.jsonPrimitive?.content ?: ""
                
                val cleanLatest = latestVersion.lowercase().removePrefix("v").trim()
                val cleanCurrent = currentVersion.lowercase().removePrefix("v").trim()

                if (cleanLatest != cleanCurrent && cleanLatest.isNotEmpty()) {
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

            installApk(apkFile)
        } catch (_: Exception) {
            // Log error
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
}
