package com.example.chatapk.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chatapk.domain.model.Chat
import com.example.chatapk.domain.model.ChatMessage
import com.example.chatapk.domain.model.UserProfile
import com.example.chatapk.domain.repository.AuthRepository
import com.example.chatapk.domain.repository.ChatRepository
import com.example.chatapk.domain.repository.PreferenceRepository
import com.example.chatapk.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val currentUserId: String = "",
    val receiver: UserProfile? = null,
    val chat: Chat? = null,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val isDarkMode: Boolean = false,
    val error: String? = null
)

class ChatViewModel(
    private val chatId: String,
    private val receiverId: String,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val preferenceRepository: PreferenceRepository,
    private val externalScope: CoroutineScope
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        val currentUserId = authRepository.currentUserId.orEmpty()
        _uiState.update { it.copy(currentUserId = currentUserId) }

        viewModelScope.launch {
            preferenceRepository.isDarkMode.collect { isDark ->
                _uiState.update { it.copy(isDarkMode = isDark) }
            }
        }

        viewModelScope.launch {
            userRepository.getUser(receiverId)?.let { receiver ->
                _uiState.update { it.copy(receiver = receiver) }
            }
        }
        viewModelScope.launch {
            chatRepository.observeMessages(chatId)
                .catch { e -> _uiState.update { it.copy(error = e.message ?: "Failed to load messages") } }
                .collect { messages ->
                    _uiState.update { it.copy(messages = messages) }
                    chatRepository.markIncomingDelivered(chatId, currentUserId)
                }
        }
        viewModelScope.launch {
            chatRepository.observeChats(currentUserId)
                .catch { e -> _uiState.update { it.copy(error = e.message ?: "Failed to load chat info") } }
                .collect { chats ->
                    _uiState.update { it.copy(chat = chats.firstOrNull { chat -> chat.id == chatId }) }
                }
        }
        viewModelScope.launch {
            chatRepository.markChatSeen(chatId, currentUserId)
        }
    }

    fun updateInput(value: String) {
        _uiState.update { it.copy(input = value, error = null) }
        viewModelScope.launch {
            chatRepository.setTyping(chatId, uiState.value.currentUserId, value.isNotBlank())
        }
    }

    fun send() {
        val state = uiState.value
        if (state.input.isBlank() || state.isSending) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            val result = chatRepository.sendMessage(
                chatId = chatId,
                senderId = state.currentUserId,
                receiverId = receiverId,
                text = state.input
            )
            result.fold(
                onSuccess = { _uiState.update { it.copy(input = "", isSending = false) } },
                onFailure = { error -> _uiState.update { it.copy(error = error.message, isSending = false) } }
            )
        }
    }

    fun markSeen() {
        viewModelScope.launch {
            chatRepository.markChatSeen(chatId, uiState.value.currentUserId)
        }
    }

    fun react(messageId: String, emoji: String?) {
        viewModelScope.launch {
            chatRepository.reactToMessage(chatId, uiState.value.currentUserId, messageId, emoji)
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            chatRepository.deleteChat(chatId)
            _uiState.update { it.copy(isSending = false) }
        }
    }

    override fun onCleared() {
        val uid = uiState.value.currentUserId
        if (uid.isNotBlank()) {
            externalScope.launch {
                chatRepository.setTyping(chatId, uid, false)
            }
        }
        super.onCleared()
    }

    class Factory(
        private val chatId: String,
        private val receiverId: String,
        private val authRepository: AuthRepository,
        private val chatRepository: ChatRepository,
        private val userRepository: UserRepository,
        private val preferenceRepository: PreferenceRepository,
        private val externalScope: CoroutineScope
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(chatId, receiverId, authRepository, chatRepository, userRepository, preferenceRepository, externalScope) as T
    }
}
