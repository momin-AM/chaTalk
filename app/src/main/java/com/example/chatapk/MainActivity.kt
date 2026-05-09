package com.example.chatapk

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.chatapk.presentation.navigation.ChatNavHost
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val container = (application as ChatApkApplication).container

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.authRepository.currentUserId?.let { uid ->
                    container.applicationScope.launch {
                        container.userRepository.setPresence(uid, true)
                    }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                container.authRepository.currentUserId?.let { uid ->
                    container.applicationScope.launch {
                        container.userRepository.setPresence(uid, false)
                    }
                }
            }
        })

        // Log FCM token for debugging
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                android.util.Log.d("FCM_TOKEN", "Token: ${task.result}")
            }
        }

        setContent {
            val isDarkMode by container.preferenceRepository.isDarkMode.collectAsState(initial = isSystemInDarkTheme())
            ChatApkTheme(isDarkMode = isDarkMode) {
                ChatNavHost(container = container)
            }
        }
    }
}

@Composable
private fun ChatApkTheme(isDarkMode: Boolean, content: @Composable () -> Unit) {
    val light = lightColorScheme(
        primary = WhatsAppGreen,
        onPrimary = Color.White,
        secondary = AccentGreen,
        onSecondary = Color.White,
        surface = LightSurface,
        onSurface = Color.Black,
        background = LightBackground,
        onBackground = Color.Black,
        primaryContainer = LightBubbleMine,
        secondaryContainer = LightBubbleOther,
        tertiaryContainer = LightChatBackground
    )
    val dark = darkColorScheme(
        primary = AccentGreen,
        onPrimary = Color(0xFF0B141A), // Dark color for bright green
        secondary = WhatsAppGreen,
        onSecondary = Color.White,
        surface = DarkSurface,
        onSurface = Color.White,
        background = DarkBackground,
        onBackground = Color.White,
        primaryContainer = DarkBubbleMine,
        secondaryContainer = DarkBubbleOther,
        tertiaryContainer = DarkChatBackground
    )

    MaterialTheme(
        colorScheme = if (isDarkMode) dark else light,
        content = {
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as android.app.Activity).window
                    window.statusBarColor = if (isDarkMode) DarkSurface.toArgb() else WhatsAppGreen.toArgb()
                    // Use light icons (white) for both dark mode and light mode (since the light mode bar is dark green)
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                }
            }
            content()
        }
    )
}

private val WhatsAppGreen = Color(0xFF075E54)
private val AccentGreen = Color(0xFF25D366)
private val LightBackground = Color(0xFFF6F7F2)
private val LightSurface = Color(0xFFFFFFFF)
private val DarkBackground = Color(0xFF0B141A)
private val DarkSurface = Color(0xFF111B21)

private val LightBubbleMine = Color(0xFFDCF8C6)
private val LightBubbleOther = Color(0xFFFFFFFF)
private val DarkBubbleMine = Color(0xFF005C4B)
private val DarkBubbleOther = Color(0xFF202C33)
private val LightChatBackground = Color(0xFFECE5DD)
private val DarkChatBackground = Color(0xFF0B141A)
