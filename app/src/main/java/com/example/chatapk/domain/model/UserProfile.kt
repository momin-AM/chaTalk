package com.example.chatapk.domain.model

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val profilePictureUrl: String? = null,
    val online: Boolean = false,
    val lastSeen: Long = 0L,
    val fcmTokens: List<String> = emptyList()
)
