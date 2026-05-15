package com.example.chatapk.presentation.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.chatapk.domain.model.Chat
import com.example.chatapk.domain.model.MessageStatus
import com.example.chatapk.domain.model.UserProfile
import com.example.chatapk.presentation.common.UserAvatar
import com.example.chatapk.presentation.common.formatDateOrTime

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(
    chat: Chat,
    currentUserId: String,
    knownUsers: List<UserProfile>,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: (receiverId: String) -> Unit,
    onLongClick: () -> Unit
) {
    val receiverId = chat.participantIds.firstOrNull { it != currentUserId }.orEmpty()
    val receiverName = chat.participantNames[receiverId]
        ?: knownUsers.firstOrNull { it.uid == receiverId }?.username
        ?: "Unknown"
    val unread = chat.unreadCounts[currentUserId] ?: 0L

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (receiverId.isNotBlank()) onClick(receiverId) },
                onLongClick = onLongClick
            ),
        leadingContent = {
            UserAvatar(
                username = receiverName,
                modifier = Modifier.clip(CircleShape)
            )
        },
        trailingContent = {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { if (receiverId.isNotBlank()) onClick(receiverId) }
                )
            }
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = receiverName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatDateOrTime(chat.lastMessageAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentUserId.isNotBlank() && chat.lastMessageSenderId == currentUserId && chat.lastMessage.isNotBlank()) {
                    val (icon, color) = when (chat.lastMessageStatus) {
                        MessageStatus.SENT ->
                            Icons.Default.Done to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        MessageStatus.DELIVERED ->
                            Icons.Default.DoneAll to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        MessageStatus.SEEN ->
                            Icons.Default.DoneAll to Color(0xFF34B7F1) // WhatsApp Blue
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = color
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    modifier = Modifier.weight(1f),
                    text = chat.lastMessage.ifBlank { "Say hello" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (unread > 0) {
                    Badge {
                        Text(unread.coerceAtMost(99).toString())
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    onOpenChat: (chatId: String, receiverId: String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    if (state.updateAvailable != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = { Text("Update Available") },
            text = {
                Column {
                    Text("A new version (${state.updateAvailable}) is available. Would you like to update?")
                    if (state.isDownloadingUpdate) {
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Downloading update...")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isDownloadingUpdate,
                    onClick = { viewModel.downloadAndInstallUpdate() }
                ) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isDownloadingUpdate,
                    onClick = { viewModel.dismissUpdateDialog() }
                ) {
                    Text("Later")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            val appBarColors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (state.isDarkMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                titleContentColor = if (state.isDarkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                actionIconContentColor = if (state.isDarkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = if (state.isDarkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
            )
            if (state.isSearchMode) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { viewModel.toggleSearchMode(false) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            placeholder = { Text("Search users...") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = if (state.isDarkMode) Color.White else Color.White,
                                unfocusedTextColor = if (state.isDarkMode) Color.White else Color.White,
                                cursorColor = if (state.isDarkMode) Color.White else Color.White
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                        )
                    },
                    actions = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    colors = appBarColors
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        if (state.forwardingMessage != null) {
                            IconButton(onClick = { viewModel.cancelForwarding() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                            }
                        }
                    },
                    title = {
                        if (state.forwardingMessage != null) {
                            Text("Forward to...")
                        } else {
                            Column {
                                Text("chaTalk")
                                state.currentUser?.let {
                                    Text(
                                        text = it.username,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (state.isDarkMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleSearchMode(true) }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (state.isDarkMode) "Light Mode" else "Dark Mode") },
                                    onClick = {
                                        viewModel.toggleDarkMode(!state.isDarkMode)
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = {
                                        onOpenSettings()
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Update App") },
                                    onClick = {
                                        viewModel.checkForUpdate(currentVersion)
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Log out") },
                                    onClick = {
                                        viewModel.logout()
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    },
                    colors = appBarColors
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    val displayMessage = state.error ?: state.infoMessage
                    val textColor = if (state.error != null) {
                        MaterialTheme.colorScheme.error
                    } else if (state.infoMessage != "beta version") {
                        Color(0xFF25D366) // WhatsApp Green for "Message forwarded" etc.
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) // Subtler for "beta version"
                    }

                    Text(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        text = displayMessage,
                        color = textColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (state.infoMessage != "beta version" || state.error != null) FontWeight.Bold else FontWeight.Normal
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }

                if (state.isSearchMode) {
                    if (state.searchResults.isEmpty() && state.searchQuery.isNotBlank()) {
                        item {
                            Text(
                                modifier = Modifier.padding(24.dp),
                                text = "No users found.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(state.searchResults, key = { it.uid }) { user ->
                            ListItem(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.openChat(user, onOpenChat) },
                                leadingContent = {
                                    UserAvatar(
                                        username = user.username,
                                        modifier = Modifier.clip(CircleShape)
                                    )
                                },
                                headlineContent = { Text(user.username) }
                            )
                            HorizontalDivider()
                        }
                    }
                } else {
                    if (state.chats.isEmpty()) {
                        item {
                            Text(
                                modifier = Modifier.padding(24.dp),
                                text = "No conversations yet. Search for people to start chatting!",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(state.chats, key = { it.id }) { chat ->
                            val receiverId = chat.participantIds.firstOrNull { it != state.currentUserId }.orEmpty()
                            var showRowMenu by remember { mutableStateOf(false) }
                            val isBlocked = state.currentUser?.blockedUids?.contains(receiverId) == true
                            val isSelected = state.selectedChatIds.contains(chat.id)

                            Box {
                                ChatRow(
                                    chat = chat,
                                    currentUserId = state.currentUserId,
                                    knownUsers = emptyList<UserProfile>(), 
                                    isSelectionMode = state.forwardingMessage != null,
                                    isSelected = isSelected,
                                    onClick = { rid: String ->
                                        if (state.forwardingMessage != null) {
                                            viewModel.toggleChatSelection(chat.id)
                                        } else {
                                            onOpenChat(chat.id, rid)
                                        }
                                    },
                                    onLongClick = {
                                        if (state.forwardingMessage == null) {
                                            showRowMenu = true
                                        }
                                    }
                                )
                                if (state.forwardingMessage == null) {
                                    DropdownMenu(
                                        expanded = showRowMenu,
                                        onDismissRequest = { showRowMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(if (isBlocked) "Unblock" else "Block") },
                                            onClick = {
                                                if (isBlocked) viewModel.unblockUser(receiverId)
                                                else viewModel.blockUser(receiverId)
                                                showRowMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete Inbox") },
                                            onClick = {
                                                viewModel.deleteChat(chat.id)
                                                showRowMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            if (state.forwardingMessage != null && state.selectedChatIds.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { viewModel.completeForwarding() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = Color(0xFF25D366), // WhatsApp Green
                    contentColor = Color.White
                ) {
                    if (state.isForwardingInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}
