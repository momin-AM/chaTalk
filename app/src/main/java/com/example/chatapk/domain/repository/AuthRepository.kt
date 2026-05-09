package com.example.chatapk.domain.repository

import com.example.chatapk.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUserId: String?
    fun authState(): Flow<String?>
    suspend fun signUp(email: String, password: String, username: String): Result<UserProfile>
    suspend fun login(email: String, password: String): Result<UserProfile>
    suspend fun logout()
}
