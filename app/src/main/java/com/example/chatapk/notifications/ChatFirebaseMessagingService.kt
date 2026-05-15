package com.example.chatapk.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.chatapk.R
import com.example.chatapk.ChatApkApplication
import com.example.chatapk.di.NotificationChannels
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.util.Log

class ChatFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Log.d("FCM", "New token: $token")
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update("fcmTokens", FieldValue.arrayUnion(token))
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d("FCM", "Message received from: ${message.from}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val title = message.data["title"] ?: message.notification?.title ?: "New message"
        var body = message.data["body"] ?: message.notification?.body ?: "Open ChatApk to read it"

        val senderId = message.data["senderId"]
        if (!senderId.isNullOrBlank() && body.isNotEmpty()) {
            val container = (application as ChatApkApplication).container
            val encryptionManager = container.encryptionManager
            body = try {
                val sender = kotlinx.coroutines.runBlocking { container.userRepository.getUser(senderId) }
                if (sender?.publicKey != null) {
                    encryptionManager.decrypt(body, sender.publicKey)
                } else {
                    "New message" // Hide encrypted text if we can't decrypt
                }
            } catch (e: Exception) {
                "New message"
            }
        }

        val notification = NotificationCompat.Builder(this, NotificationChannels.MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(System.currentTimeMillis().toInt(), notification)
    }
}
