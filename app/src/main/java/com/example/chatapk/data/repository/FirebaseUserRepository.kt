package com.example.chatapk.data.repository

import com.example.chatapk.data.firebase.snapshots
import com.example.chatapk.data.firebase.toUserProfile
import com.example.chatapk.domain.model.UserProfile
import com.example.chatapk.domain.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirebaseUserRepository(
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging
) : UserRepository {
    private val users = firestore.collection("users")

    override fun observeCurrentUser(uid: String): Flow<UserProfile?> =
        users.whereEqualTo("uid", uid)
            .limit(1)
            .snapshots()
            .map { snapshot -> snapshot.documents.firstOrNull()?.toUserProfile() }

    override fun observeUsersExcept(uid: String): Flow<List<UserProfile>> =
        users.orderBy("username")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toUserProfile() }.filterNot { it.uid == uid }
            }

    override fun searchUsers(query: String): Flow<List<UserProfile>> =
        users.orderBy("username")
            .startAt(query)
            .endAt(query + "\uf8ff")
            .limit(20)
            .snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toUserProfile() } }

    override suspend fun getUser(uid: String): UserProfile? =
        users.document(uid).get().await().toUserProfile()

    override suspend fun upsertUser(user: UserProfile) {
        users.document(user.uid).set(
            mapOf(
                "uid" to user.uid,
                "email" to user.email,
                "username" to user.username,
                "profilePictureUrl" to user.profilePictureUrl,
                "online" to user.online,
                "lastSeen" to user.lastSeen
                // We don't overwrite fcmTokens here to avoid clearing them accidentally
            ),
            SetOptions.merge()
        ).await()
    }

    override suspend fun setPresence(uid: String, online: Boolean) {
        users.document(uid).set(
            mapOf(
                "online" to online,
                "lastSeen" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
    }

    override suspend fun saveMessagingToken(uid: String) {
        val token = messaging.token.await()
        users.document(uid).set(
            mapOf("fcmTokens" to com.google.firebase.firestore.FieldValue.arrayUnion(token)),
            SetOptions.merge()
        ).await()
    }

    override suspend fun clearMessagingToken(uid: String) {
        val token = messaging.token.await()
        users.document(uid).set(
            mapOf("fcmTokens" to com.google.firebase.firestore.FieldValue.arrayRemove(token)),
            SetOptions.merge()
        ).await()
    }
}
