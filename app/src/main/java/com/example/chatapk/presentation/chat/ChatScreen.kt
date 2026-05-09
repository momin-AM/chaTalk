package com.example.chatapk.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.chatapk.domain.model.ChatMessage
import com.example.chatapk.domain.model.MessageStatus
import com.example.chatapk.presentation.common.UserAvatar
import com.example.chatapk.presentation.common.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val receiverName = state.receiver?.username ?: "Chat"
    val receiverTyping = state.chat?.typing
        ?.filterKeys { it != state.currentUserId }
        ?.values
        ?.any { it } == true

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
            viewModel.markSeen()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(receiverName, Modifier.size(36.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = receiverName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (state.isBlockedByMe || state.hasBlockedMe) "" 
                                       else if (receiverTyping) "typing..." 
                                       else if (state.receiver?.online == true) "online" 
                                       else "last seen ${formatTime(state.receiver?.lastSeen ?: 0L)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete all messages") },
                                onClick = {
                                    viewModel.clearChat()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (state.isBlockedByMe) "Unblock User" else "Block User") },
                                onClick = {
                                    viewModel.toggleBlock()
                                    showMenu = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (state.isDarkMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                    titleContentColor = if (state.isDarkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = if (state.isDarkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = if (state.isDarkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                )
            )
        },
        bottomBar = {
            if (state.isBlockedByMe) {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleBlock() },
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "You blocked this contact. Tap to unblock.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else if (state.hasBlockedMe) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "You cannot message this contact.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                MessageInput(
                    value = state.input,
                    isSending = state.isSending,
                    onValueChange = viewModel::updateInput,
                    onSend = viewModel::send
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(padding)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            state.error?.let { error ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            modifier = Modifier.padding(12.dp),
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            items(state.messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    isMine = message.senderId == state.currentUserId,
                    currentUserId = state.currentUserId,
                    onReact = { emoji -> viewModel.react(message.id, emoji) }
                )
            }
        }
    }
}

@Composable
private fun MessageInput(
    value: String,
    isSending: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 140.dp),
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Message") },
                maxLines = 5
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                enabled = value.isNotBlank() && !isSending,
                onClick = onSend
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    currentUserId: String,
    onReact: (String?) -> Unit
) {
    var showReactions by remember { mutableStateOf(false) }
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val shape = RoundedCornerShape(
        topStart = 10.dp,
        topEnd = 10.dp,
        bottomStart = if (isMine) 10.dp else 2.dp,
        bottomEnd = if (isMine) 2.dp else 10.dp
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .background(bubbleColor, shape)
                    .clickable { showReactions = !showReactions }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(text = message.messageText)
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    if (isMine) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = statusTicks(message.status),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (message.status == MessageStatus.SEEN) Color(0xFF34B7F1) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                if (message.reactions.isNotEmpty()) {
                    Text(
                        text = message.reactions.values.joinToString(" "),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (showReactions) {
                Row {
                    listOf("👍", "❤️", "😂", "😮").forEach { emoji ->
                        TextButton(onClick = { onReact(emoji) }) {
                            Text(emoji)
                        }
                    }
                    if (message.reactions[currentUserId] != null) {
                        TextButton(onClick = { onReact(null) }) {
                            Text("Clear")
                        }
                    }
                }
            }
        }
    }
}

private fun statusTicks(status: MessageStatus): String =
    when (status) {
        MessageStatus.SENT -> "✓"
        MessageStatus.DELIVERED -> "✓✓"
        MessageStatus.SEEN -> "✓✓"
    }
