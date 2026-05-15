package com.example.chatapk.domain.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ForwardManager {
    private val _forwardingMessage = MutableStateFlow<String?>(null)
    val forwardingMessage = _forwardingMessage.asStateFlow()

    fun startForwarding(message: String) {
        _forwardingMessage.value = message
    }

    fun clearForwarding() {
        _forwardingMessage.value = null
    }
}
