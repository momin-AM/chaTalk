package com.example.chatapk.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatapk.presentation.common.UserAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (state.isDarkMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                    titleContentColor = if (state.isDarkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = if (state.isDarkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            // Profile Section
            state.currentUser?.let { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatar(
                        username = user.username,
                        modifier = Modifier.size(72.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Text(
                            text = user.username,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = user.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp)

            // Settings Items
            SettingsItem(
                title = "Account",
                subtitle = "Security notifications, change number",
                icon = Icons.Default.Person
            )

            SettingsToggleItem(
                title = "Dark Mode",
                subtitle = "Toggle app theme",
                icon = Icons.Default.Brightness4,
                checked = state.isDarkMode,
                onCheckedChange = viewModel::toggleDarkMode
            )

            SettingsItem(
                title = "End-to-End Encryption",
                subtitle = "Your messages are secured with ECDH",
                icon = Icons.Default.Lock
            )
            
            if (state.fingerprint.isNotEmpty()) {
                Text(
                    text = "Security Fingerprint:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 72.dp)
                )
                Text(
                    text = state.fingerprint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 72.dp)
                )
            }

            SettingsItem(
                title = "App Info",
                subtitle = "chaTalk v1.3 - Production Ready",
                icon = Icons.Default.Info
            )

            SettingsItem(
                title = "Logout",
                subtitle = "Sign out from this device",
                icon = Icons.Default.Logout,
                titleColor = Color.Red,
                onClick = viewModel::logout
            )

            Spacer(Modifier.height(32.dp))
            
            Text(
                text = "we will implement more functions soon",
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    titleColor: Color = Color.Unspecified,
    onClick: () -> Unit = {}
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(title, color = titleColor, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) }
    )
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}
