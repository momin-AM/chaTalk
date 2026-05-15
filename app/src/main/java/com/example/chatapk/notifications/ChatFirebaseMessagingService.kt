package com.example.chatapk.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.chatapk.R
import com.example.chatapk.ChatApkApplication
import com.example.chatapk.MainActivity
import com.example.chatapk.di.NotificationChannels
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ChatFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update("fcmTokens", FieldValue.arrayUnion(token))
    }

    override fun onMessageReceived(message: RemoteMessage) {
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
                kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withTimeoutOrNull(5000) {
                        val sender = container.userRepository.getUser(senderId)
                        if (sender?.publicKey != null) {
                            encryptionManager.decrypt(body, sender.publicKey)
                        } else {
                            "New message"
                        }
                    } ?: "New message"
                }
            } catch (e: Exception) {
                "New message"
            }
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chatId", message.data["chatId"])
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(System.currentTimeMillis().toInt(), notification)
    }
}
