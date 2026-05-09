package com.example.chatapk.domain.model

data class Chat(
    val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageAt: Long = 0L,
    val lastMessageStatus: MessageStatus = MessageStatus.SENT,
    val unreadCounts: Map<String, Long> = emptyMap(),
    val typing: Map<String, Boolean> = emptyMap()
)
