package com.example.chatapk.domain.repository

import com.example.chatapk.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun observeCurrentUser(uid: String): Flow<UserProfile?>
    fun observeUsersExcept(uid: String): Flow<List<UserProfile>>
    fun searchUsers(query: String): Flow<List<UserProfile>>
    suspend fun getUser(uid: String): UserProfile?
    suspend fun upsertUser(user: UserProfile)
    suspend fun setPresence(uid: String, online: Boolean)
    suspend fun saveMessagingToken(uid: String)
    suspend fun clearMessagingToken(uid: String)
    suspend fun blockUser(myUid: String, targetUid: String)
    suspend fun unblockUser(myUid: String, targetUid: String)
    suspend fun updatePublicKey(uid: String, publicKey: String)
}
