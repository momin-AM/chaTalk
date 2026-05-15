package com.example.chatapk.presentation.chat

import android.util.Log
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
import com.example.chatapk.domain.repository.ForwardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val currentUserId: String = "",
    val currentUser: UserProfile? = null,
    val receiver: UserProfile? = null,
    val chat: Chat? = null,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val isDarkMode: Boolean = false,
    val isBlockedByMe: Boolean = false,
    val hasBlockedMe: Boolean = false,
    val isForwarding: Boolean = false,
    val error: String? = null
)

class ChatViewModel(
    private val chatId: String,
    private val receiverId: String,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val preferenceRepository: PreferenceRepository,
    private val forwardManager: ForwardManager,
    private val externalScope: CoroutineScope
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var messagesJob: Job? = null
    private var chatsJob: Job? = null

    init {
        val currentUserId = authRepository.currentUserId.orEmpty()
        _uiState.update { it.copy(currentUserId = currentUserId) }

        if (currentUserId.isNotBlank()) {
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
                userRepository.observeCurrentUser(currentUserId).collect { user ->
                    _uiState.update { it.copy(
                        currentUser = user,
                        isBlockedByMe = user?.blockedUids?.contains(receiverId) == true
                    ) }
                }
            }
            
            viewModelScope.launch {
                userRepository.observeCurrentUser(receiverId).collect { user ->
                    _uiState.update { it.copy(
                        hasBlockedMe = user?.blockedUids?.contains(currentUserId) == true
                    ) }
                }
            }
            
            startObserving()
            
            viewModelScope.launch {
                chatRepository.markChatSeen(chatId, currentUserId)
            }
        } else {
            _uiState.update { it.copy(error = "User not authenticated") }
        }
    }

    private fun startObserving() {
        val currentUserId = uiState.value.currentUserId
        if (currentUserId.isBlank()) return
        
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.observeMessages(chatId)
                .catch { e -> 
                    Log.e("ChatViewModel", "Error observing messages", e)
                    _uiState.update { it.copy(error = e.message ?: "Failed to load messages") } 
                }
                .collect { messages ->
                    _uiState.update { it.copy(messages = messages) }
                    chatRepository.markIncomingDelivered(chatId, currentUserId)
                }
        }

        chatsJob?.cancel()
        chatsJob = viewModelScope.launch {
            chatRepository.observeChats(currentUserId)
                .catch { e -> 
                    Log.e("ChatViewModel", "Error observing chats", e)
                    _uiState.update { it.copy(error = e.message ?: "Failed to load chat info") } 
                }
                .collect { chats ->
                    val chat = chats.firstOrNull { it.id == chatId }
                    _uiState.update { it.copy(chat = chat) }
                }
        }
    }

    fun updateInput(value: String) {
        _uiState.update { it.copy(input = value, error = null) }
        viewModelScope.launch {
            if (uiState.value.currentUserId.isNotBlank()) {
                chatRepository.setTyping(chatId, uiState.value.currentUserId, value.isNotBlank())
            }
        }
    }

    fun send() {
        val state = uiState.value
        if (state.input.isBlank() || state.isSending || state.currentUserId.isBlank()) return
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
        val uid = uiState.value.currentUserId
        if (uid.isNotBlank()) {
            viewModelScope.launch {
                chatRepository.markChatSeen(chatId, uid)
            }
        }
    }

    fun react(messageId: String, emoji: String?) {
        val uid = uiState.value.currentUserId
        if (uid.isNotBlank()) {
            viewModelScope.launch {
                chatRepository.reactToMessage(chatId, messageId, uid, emoji)
            }
        }
    }

    fun toggleBlock() {
        val state = uiState.value
        val myUid = state.currentUserId
        if (myUid.isBlank()) return
        
        viewModelScope.launch {
            if (state.isBlockedByMe) {
                userRepository.unblockUser(myUid, receiverId)
            } else {
                userRepository.blockUser(myUid, receiverId)
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            chatRepository.deleteChat(chatId)
            _uiState.update { it.copy(isSending = false) }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            chatRepository.deleteMessage(chatId, messageId)
        }
    }

    fun setForwarding(messageText: String) {
        forwardManager.startForwarding(messageText)
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
        private val forwardManager: ForwardManager,
        private val externalScope: CoroutineScope
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(chatId, receiverId, authRepository, chatRepository, userRepository, preferenceRepository, forwardManager, externalScope) as T
    }
}
