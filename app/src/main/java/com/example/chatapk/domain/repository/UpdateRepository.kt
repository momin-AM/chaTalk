package com.example.chatapk.domain.repository

interface UpdateRepository {
    suspend fun checkForUpdate(currentVersion: String): UpdateResult
}

sealed class UpdateResult {
    data class NewVersionAvailable(val version: String, val downloadUrl: String) : UpdateResult()
    object NoUpdateAvailable : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}
