package com.example.chatapk.data.repository

import com.example.chatapk.data.firebase.snapshots
import com.example.chatapk.data.firebase.toChat
import com.example.chatapk.data.firebase.toChatMessage
import com.example.chatapk.data.local.ChatDao
import com.example.chatapk.data.local.LocalChatMessage
import com.example.chatapk.data.local.toDomain
import com.example.chatapk.data.local.toLocal
import com.example.chatapk.domain.model.Chat
import com.example.chatapk.domain.model.ChatMessage
import com.example.chatapk.domain.model.MessageStatus
import com.example.chatapk.domain.model.UserProfile
import com.example.chatapk.domain.repository.ChatRepository
import com.example.chatapk.domain.repository.UserRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class FirebaseChatRepository(
    private val firestore: FirebaseFirestore,
    private val users: UserRepository,
    private val chatDao: ChatDao
) : ChatRepository {
    private val chats = firestore.collection("chats")
    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    private val syncJobs = mutableMapOf<String, Job>()
    private val mutex = Mutex()

    override fun observeChats(currentUserId: String): Flow<List<Chat>> = flow {
        mutex.withLock {
            if (syncJobs["chats_$currentUserId"]?.isActive != true) {
                syncJobs["chats_$currentUserId"] = repositoryScope.launch {
                    chats.whereArrayContains("participantIds", currentUserId)
                        .orderBy("lastMessageAt", Query.Direction.DESCENDING)
                        .snapshots()
                        .collect { snapshot ->
                            val localChats = snapshot.documents.map { it.toChat().toLocal() }
                            chatDao.insertChats(localChats)
                        }
                }
            }
        }
        emitAll(chatDao.observeChats().map { list -> list.map { it.toDomain() } })
    }

    override fun observeMessages(chatId: String): Flow<List<ChatMessage>> = flow {
        mutex.withLock {
            if (syncJobs["messages_$chatId"]?.isActive != true) {
                syncJobs["messages_$chatId"] = repositoryScope.launch {
                    chats.document(chatId)
                        .collection("messages")
                        .orderBy("timestamp", Query.Direction.ASCENDING)
                        .snapshots()
                        .collect { snapshot ->
                            val localMessages = snapshot.documents.map { it.toChatMessage().toLocal(chatId) }
                            chatDao.insertMessages(localMessages)
                        }
                }
            }
        }
        emitAll(chatDao.observeMessages(chatId).map { list -> list.map { it.toDomain() } })
    }

    override suspend fun getOrCreateChat(
        currentUserId: String,
        otherUser: UserProfile
    ): Result<String> = runCatching {
        val currentUser = requireNotNull(users.getUser(currentUserId))
        val chatId = chatIdFor(currentUserId, otherUser.uid)
        val chatRef = chats.document(chatId)
        val existing = chatRef.get().await()
        if (!existing.exists()) {
            chatRef.set(
                mapOf(
                    "participantIds" to listOf(currentUserId, otherUser.uid).sorted(),
                    "participantNames" to mapOf(
                        currentUserId to currentUser.username,
                        otherUser.uid to otherUser.username
                    ),
                    "lastMessage" to "",
                    "lastMessageSenderId" to "",
                    "lastMessageAt" to 0L,
                    "lastMessageStatus" to MessageStatus.SENT.name,
                    "unreadCounts" to mapOf(currentUserId to 0L, otherUser.uid to 0L),
                    "typing" to mapOf(currentUserId to false, otherUser.uid to false),
                    "createdAt" to System.currentTimeMillis()
                )
            ).await()
        }
        chatId
    }

    override suspend fun sendMessage(
        chatId: String,
        senderId: String,
        receiverId: String,
        text: String
    ): Result<Unit> = runCatching {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Message cannot be empty" }
        val now = System.currentTimeMillis()
        val chatRef = chats.document(chatId)
        val messageRef = chatRef.collection("messages").document()
        val batch = firestore.batch()

        batch.set(
            messageRef,
            mapOf(
                "senderId" to senderId,
                "receiverId" to receiverId,
                "messageText" to trimmed,
                "timestamp" to now,
                "status" to MessageStatus.SENT.name,
                "reactions" to emptyMap<String, String>()
            )
        )
        batch.update(
            chatRef,
            mapOf(
                "lastMessage" to trimmed,
                "lastMessageSenderId" to senderId,
                "lastMessageAt" to now,
                "lastMessageStatus" to MessageStatus.SENT.name,
                "unreadCounts.$receiverId" to FieldValue.increment(1),
                "typing.$senderId" to false
            )
        )
        batch.commit().await()
        
        // Optimistic update to local DB
        chatDao.insertMessages(listOf(
            LocalChatMessage(
                id = messageRef.id,
                chatId = chatId,
                senderId = senderId,
                receiverId = receiverId,
                messageText = trimmed,
                timestamp = now,
                status = MessageStatus.SENT,
                reactions = emptyMap()
            )
        ))
    }

    override suspend fun markIncomingDelivered(chatId: String, currentUserId: String) {
        if (currentUserId.isBlank()) return
        runCatching {
            val chatRef = chats.document(chatId)
            val chatDoc = chatRef.get().await()
            if (!chatDoc.exists()) return@runCatching
            
            val lastSenderId = chatDoc.getString("lastMessageSenderId")
            val currentStatus = chatDoc.getString("lastMessageStatus")

            // Only proceed if we are the receiver of the last message
            if (lastSenderId == currentUserId || lastSenderId.isNullOrBlank()) return@runCatching

            val snapshot = chatRef.collection("messages")
                .whereEqualTo("receiverId", currentUserId)
                .whereEqualTo("status", MessageStatus.SENT.name)
                .get()
                .await()
            
            val batch = firestore.batch()
            var needsUpdate = false
            
            if (!snapshot.isEmpty) {
                snapshot.documents.forEach { doc ->
                    batch.update(doc.reference, "status", MessageStatus.DELIVERED.name)
                }
                needsUpdate = true
            }

            if (currentStatus == MessageStatus.SENT.name) {
                batch.update(chatRef, "lastMessageStatus", MessageStatus.DELIVERED.name)
                needsUpdate = true
            }
            
            if (needsUpdate) {
                batch.commit().await()
            }
        }
    }

    override suspend fun markChatSeen(chatId: String, currentUserId: String) {
        if (currentUserId.isBlank()) return
        runCatching {
            val chatRef = chats.document(chatId)
            val chatDoc = chatRef.get().await()
            if (!chatDoc.exists()) return@runCatching

            val lastSenderId = chatDoc.getString("lastMessageSenderId")
            val currentStatus = chatDoc.getString("lastMessageStatus")
            val unreadCount = (chatDoc.get("unreadCounts") as? Map<*, *>)?.get(currentUserId) as? Long ?: 0L

            // Only proceed if we are the receiver of the last message or have unread messages
            if (lastSenderId == currentUserId && unreadCount == 0L) return@runCatching

            val snapshot = chatRef.collection("messages")
                .whereEqualTo("receiverId", currentUserId)
                .whereIn("status", listOf(MessageStatus.SENT.name, MessageStatus.DELIVERED.name))
                .get()
                .await()

            val batch = firestore.batch()
            var needsUpdate = false

            if (!snapshot.isEmpty) {
                snapshot.documents.forEach { doc ->
                    batch.update(doc.reference, "status", MessageStatus.SEEN.name)
                }
                needsUpdate = true
            }

            if (unreadCount > 0L) {
                batch.update(chatRef, "unreadCounts.$currentUserId", 0L)
                needsUpdate = true
            }

            if (lastSenderId != currentUserId && currentStatus != MessageStatus.SEEN.name) {
                batch.update(chatRef, "lastMessageStatus", MessageStatus.SEEN.name)
                needsUpdate = true
            }
            
            if (needsUpdate) {
                batch.commit().await()
            }
        }
    }

    override suspend fun setTyping(chatId: String, currentUserId: String, isTyping: Boolean) {
        runCatching {
            chats.document(chatId)
                .update("typing.$currentUserId", isTyping)
                .await()
        }
    }

    override suspend fun reactToMessage(
        chatId: String,
        messageId: String,
        currentUserId: String,
        emoji: String?
    ) {
        runCatching {
            val value = emoji?.takeIf { it.isNotBlank() } ?: FieldValue.delete()
            chats.document(chatId)
                .collection("messages")
                .document(messageId)
                .update("reactions.$currentUserId", value)
                .await()
        }
    }

    override suspend fun deleteChat(chatId: String): Result<Unit> = runCatching {
        chatDao.deleteMessages(chatId)
        val chatRef = chats.document(chatId)
        val messages = chatRef.collection("messages").get().await()
        val batch = firestore.batch()
        messages.documents.forEach { batch.delete(it.reference) }
        batch.update(chatRef, mapOf(
            "lastMessage" to "",
            "lastMessageAt" to 0L,
            "unreadCounts" to mapOf<String, Long>()
        ))
        batch.commit().await()
    }

    override suspend fun clearLocalData() {
        chatDao.clearChats()
        chatDao.clearMessages()
    }

    private fun chatIdFor(first: String, second: String): String =
        listOf(first, second).sorted().joinToString(separator = "_")
}
