package com.example.chatapk.data.repository

import com.example.chatapk.domain.model.UserProfile
import com.example.chatapk.domain.repository.AuthRepository
import com.example.chatapk.domain.repository.ChatRepository
import com.example.chatapk.domain.repository.UserRepository
import com.example.chatapk.security.EncryptionManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val users: UserRepository,
    private val chats: ChatRepository,
    private val encryptionManager: EncryptionManager
) : AuthRepository {
    override val currentUserId: String?
        get() = auth.currentUser?.uid

    override fun authState(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signUp(
        email: String,
        password: String,
        username: String
    ): Result<UserProfile> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val firebaseUser = requireNotNull(result.user)
        val user = UserProfile(
            uid = firebaseUser.uid,
            email = firebaseUser.email ?: email.trim(),
            username = username.trim(),
            online = true,
            lastSeen = System.currentTimeMillis(),
            publicKey = encryptionManager.getPublicKeyBase64()
        )
        users.upsertUser(user)
        users.saveMessagingToken(user.uid)
        user
    }

    override suspend fun login(email: String, password: String): Result<UserProfile> = runCatching {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val firebaseUser = requireNotNull(result.user)
        
        // Ensure public key is set
        val publicKey = encryptionManager.getPublicKeyBase64()
        users.updatePublicKey(firebaseUser.uid, publicKey)

        users.setPresence(firebaseUser.uid, online = true)
        users.saveMessagingToken(firebaseUser.uid)
        users.getUser(firebaseUser.uid) ?: UserProfile(
            uid = firebaseUser.uid,
            email = firebaseUser.email ?: email.trim(),
            username = firebaseUser.email?.substringBefore("@").orEmpty(),
            online = true,
            lastSeen = System.currentTimeMillis()
        ).also { users.upsertUser(it) }
    }

    override suspend fun logout() {
        auth.currentUser?.uid?.let { uid ->
            users.setPresence(uid, online = false)
            users.clearMessagingToken(uid)
        }
        chats.clearLocalData()
        auth.signOut()
    }
}
