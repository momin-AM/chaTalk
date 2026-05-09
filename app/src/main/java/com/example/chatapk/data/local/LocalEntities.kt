package com.example.chatapk.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.chatapk.domain.model.ChatMessage
import com.example.chatapk.domain.model.Chat
import com.example.chatapk.domain.model.MessageStatus

@Entity(tableName = "messages")
data class LocalChatMessage(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val receiverId: String,
    val messageText: String,
    val timestamp: Long,
    val status: MessageStatus,
    val reactions: Map<String, String>
)

@Entity(tableName = "chats")
data class LocalChat(
    @PrimaryKey val id: String,
    val participantIds: List<String>,
    val participantNames: Map<String, String>,
    val lastMessage: String,
    val lastMessageSenderId: String,
    val lastMessageAt: Long,
    val lastMessageStatus: MessageStatus,
    val unreadCounts: Map<String, Long>,
    val typing: Map<String, Boolean>
)

fun LocalChatMessage.toDomain() = ChatMessage(
    id = id,
    senderId = senderId,
    receiverId = receiverId,
    messageText = messageText,
    timestamp = timestamp,
    status = status,
    reactions = reactions
)

fun ChatMessage.toLocal(chatId: String) = LocalChatMessage(
    id = id,
    chatId = chatId,
    senderId = senderId,
    receiverId = receiverId,
    messageText = messageText,
    timestamp = timestamp,
    status = status,
    reactions = reactions
)

fun LocalChat.toDomain() = Chat(
    id = id,
    participantIds = participantIds,
    participantNames = participantNames,
    lastMessage = lastMessage,
    lastMessageSenderId = lastMessageSenderId,
    lastMessageAt = lastMessageAt,
    lastMessageStatus = lastMessageStatus,
    unreadCounts = unreadCounts,
    typing = typing
)

fun Chat.toLocal() = LocalChat(
    id = id,
    participantIds = participantIds,
    participantNames = participantNames,
    lastMessage = lastMessage,
    lastMessageSenderId = lastMessageSenderId,
    lastMessageAt = lastMessageAt,
    lastMessageStatus = lastMessageStatus,
    unreadCounts = unreadCounts,
    typing = typing
)
