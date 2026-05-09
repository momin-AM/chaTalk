package com.example.chatapk.domain.model

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val messageText: String = "",
    val timestamp: Long = 0L,
    val status: MessageStatus = MessageStatus.SENT,
    val reactions: Map<String, String> = emptyMap()
)

enum class MessageStatus {
    SENT,
    DELIVERED,
    SEEN
}
