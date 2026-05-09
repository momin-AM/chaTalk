package com.example.chatapk.presentation.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM")

fun formatTime(millis: Long): String {
    if (millis <= 0) return ""
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormatter)
}

fun formatDateOrTime(millis: Long): String {
    if (millis <= 0) return ""
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateFormatter)
}
