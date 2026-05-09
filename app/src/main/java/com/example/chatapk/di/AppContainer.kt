package com.example.chatapk.di

import android.content.Context
import com.example.chatapk.data.local.ChatDatabase
import com.example.chatapk.data.repository.FirebaseAuthRepository
import com.example.chatapk.data.repository.FirebaseChatRepository
import com.example.chatapk.data.repository.FirebaseUserRepository
import com.example.chatapk.data.repository.SharedPreferenceRepository
import com.example.chatapk.domain.repository.AuthRepository
import com.example.chatapk.domain.repository.ChatRepository
import com.example.chatapk.domain.repository.PreferenceRepository
import com.example.chatapk.domain.repository.UpdateRepository
import com.example.chatapk.domain.repository.UserRepository
import com.example.chatapk.data.repository.GithubUpdateRepository
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val messaging = FirebaseMessaging.getInstance()

    private val db = Room.databaseBuilder(
        appContext,
        ChatDatabase::class.java,
        "chat_db"
    ).build()
    private val chatDao = db.chatDao()

    val preferenceRepository: PreferenceRepository = SharedPreferenceRepository(appContext)

    val updateRepository: UpdateRepository = GithubUpdateRepository(
        context = appContext,
        githubUser = "momin-AM", // Replace with your GitHub username
        repoName = "chaTalk"   // Replace with your Repo name
    )

    val userRepository: UserRepository = FirebaseUserRepository(
        firestore = firestore,
        messaging = messaging
    )

    val chatRepository: ChatRepository = FirebaseChatRepository(
        firestore = firestore,
        users = userRepository,
        chatDao = chatDao
    )

    val authRepository: AuthRepository = FirebaseAuthRepository(
        auth = auth,
        users = userRepository,
        chats = chatRepository
    )

    init {
        NotificationChannels.create(appContext)
    }
}
