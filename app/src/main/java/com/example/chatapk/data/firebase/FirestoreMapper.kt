package com.example.chatapk.data.firebase

import com.example.chatapk.domain.model.Chat
import com.example.chatapk.domain.model.ChatMessage
import com.example.chatapk.domain.model.MessageStatus
import com.example.chatapk.domain.model.UserProfile
import com.google.firebase.firestore.DocumentSnapshot

fun DocumentSnapshot.toUserProfile(): UserProfile? {
    val uid = getString("uid") ?: id
    val email = getString("email") ?: return null
    return UserProfile(
        uid = uid,
        email = email,
        username = getString("username").orEmpty(),
        profilePictureUrl = getString("profilePictureUrl"),
        online = getBoolean("online") ?: false,
        lastSeen = getLong("lastSeen") ?: 0L,
        fcmTokens = get("fcmTokens") as? List<String> ?: emptyList(),
        blockedUids = get("blockedUids") as? List<String> ?: emptyList()
    )
}

fun DocumentSnapshot.toChat(): Chat {
    val rawStatus = getString("lastMessageStatus") ?: MessageStatus.SENT.name
    return Chat(
        id = id,
        participantIds = get("participantIds") as? List<String> ?: emptyList(),
        participantNames = get("participantNames") as? Map<String, String> ?: emptyMap(),
        lastMessage = getString("lastMessage").orEmpty(),
        lastMessageSenderId = getString("lastMessageSenderId").orEmpty(),
        lastMessageAt = getLong("lastMessageAt") ?: 0L,
        lastMessageStatus = runCatching { MessageStatus.valueOf(rawStatus) }.getOrDefault(MessageStatus.SENT),
        unreadCounts = get("unreadCounts") as? Map<String, Long> ?: emptyMap(),
        typing = get("typing") as? Map<String, Boolean> ?: emptyMap()
    )
}

fun DocumentSnapshot.toChatMessage(): ChatMessage {
    val rawStatus = getString("status") ?: MessageStatus.SENT.name
    return ChatMessage(
        id = id,
        senderId = getString("senderId").orEmpty(),
        receiverId = getString("receiverId").orEmpty(),
        messageText = getString("messageText").orEmpty(),
        timestamp = getLong("timestamp") ?: 0L,
        status = runCatching { MessageStatus.valueOf(rawStatus) }.getOrDefault(MessageStatus.SENT),
        reactions = get("reactions") as? Map<String, String> ?: emptyMap()
    )
}
