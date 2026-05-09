package com.example.chatapk.domain.repository

import com.example.chatapk.domain.model.Chat
import com.example.chatapk.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeChats(currentUserId: String): Flow<List<Chat>>
    fun observeMessages(chatId: String): Flow<List<ChatMessage>>
    suspend fun getOrCreateChat(currentUserId: String, otherUser: com.example.chatapk.domain.model.UserProfile): Result<String>
    suspend fun sendMessage(chatId: String, senderId: String, receiverId: String, text: String): Result<Unit>
    suspend fun markIncomingDelivered(chatId: String, currentUserId: String)
    suspend fun markChatSeen(chatId: String, currentUserId: String)
    suspend fun setTyping(chatId: String, currentUserId: String, isTyping: Boolean)
    suspend fun reactToMessage(chatId: String, messageId: String, currentUserId: String, emoji: String?)
    suspend fun deleteChat(chatId: String): Result<Unit>
    suspend fun clearLocalData()
}
