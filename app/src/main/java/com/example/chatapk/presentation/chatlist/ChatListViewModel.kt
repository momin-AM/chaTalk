package com.example.chatapk.presentation.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chatapk.domain.model.Chat
import com.example.chatapk.domain.model.UserProfile
import com.example.chatapk.domain.repository.AuthRepository
import com.example.chatapk.domain.repository.ChatRepository
import com.example.chatapk.domain.repository.PreferenceRepository
import com.example.chatapk.domain.repository.UpdateRepository
import com.example.chatapk.domain.repository.UpdateResult
import com.example.chatapk.domain.repository.UserRepository
import com.example.chatapk.domain.repository.ForwardManager
import com.example.chatapk.data.repository.GithubUpdateRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatListUiState(
    val currentUserId: String = "",
    val currentUser: UserProfile? = null,
    val chats: List<Chat> = emptyList(),
    val searchResults: List<UserProfile> = emptyList(),
    val searchQuery: String = "",
    val isSearchMode: Boolean = false,
    val isDarkMode: Boolean = false,
    val isLoading: Boolean = true,
    val updateAvailable: String? = null,
    val updateDownloadUrl: String? = null,
    val isDownloadingUpdate: Boolean = false,
    val infoMessage: String = "beta version",
    val error: String? = null,
    val forwardingMessage: String? = null,
    val selectedChatIds: Set<String> = emptySet(),
    val isForwardingInProgress: Boolean = false
)

@kotlinx.coroutines.FlowPreview
class ChatListViewModel(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val preferenceRepository: PreferenceRepository,
    private val updateRepository: UpdateRepository,
    private val forwardManager: ForwardManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()
    private var chatJob: Job? = null
    private var currentUserJob: Job? = null

    init {
        authRepository.currentUserId?.let(::start)
        viewModelScope.launch {
            preferenceRepository.isDarkMode.collect { isDark ->
                _uiState.update { it.copy(isDarkMode = isDark) }
            }
        }
        viewModelScope.launch {
            forwardManager.forwardingMessage.collect { message ->
                _uiState.update { it.copy(forwardingMessage = message) }
            }
        }
    }

    private fun start(uid: String) {
        _uiState.update { it.copy(currentUserId = uid) }
        viewModelScope.launch {
            userRepository.saveMessagingToken(uid)
        }
        chatJob?.cancel()
        currentUserJob?.cancel()

        chatJob = viewModelScope.launch {
            chatRepository.observeChats(uid)
                .catch { e -> _uiState.update { it.copy(error = e.message ?: "Failed to load chats", isLoading = false) } }
                .collect { chats ->
                    _uiState.update { it.copy(chats = chats, isLoading = false) }
                    chats.forEach { chat ->
                        launch {
                            chatRepository.markIncomingDelivered(chat.id, uid)
                        }
                    }
                }
        }

        currentUserJob = viewModelScope.launch {
            userRepository.observeCurrentUser(uid)
                .catch { e -> _uiState.update { it.copy(error = e.message ?: "Failed to load profile") } }
                .collect { user ->
                    _uiState.update { it.copy(currentUser = user) }
                }
        }

        viewModelScope.launch {
            _uiState.map { it.searchQuery }
                .distinctUntilChanged()
                .debounce(300)
                .collectLatest { query ->
                    if (query.isBlank()) {
                        _uiState.update { it.copy(searchResults = emptyList()) }
                        return@collectLatest
                    }
                    userRepository.searchUsers(query)
                        .catch { e -> _uiState.update { it.copy(error = e.message) } }
                        .collect { results ->
                            _uiState.update { it.copy(searchResults = results.filter { it.uid != uid }) }
                        }
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSearchMode(enabled: Boolean) {
        _uiState.update { it.copy(isSearchMode = enabled, searchQuery = if (enabled) it.searchQuery else "") }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setDarkMode(enabled)
        }
    }

    fun checkForUpdate(currentVersion: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(infoMessage = "beta version", error = null) }
            when (val result = updateRepository.checkForUpdate(currentVersion)) {
                is UpdateResult.NewVersionAvailable -> {
                    _uiState.update { it.copy(
                        updateAvailable = result.version,
                        updateDownloadUrl = result.downloadUrl
                    ) }
                }
                is UpdateResult.NoUpdateAvailable -> {
                    _uiState.update { it.copy(updateAvailable = null, infoMessage = "Your app is up to date") }
                }
                is UpdateResult.Error -> {
                    _uiState.update { it.copy(error = result.message) }
                }
            }
        }
    }

    fun downloadAndInstallUpdate() {
        val url = uiState.value.updateDownloadUrl ?: return
        _uiState.update { it.copy(isDownloadingUpdate = true) }
        viewModelScope.launch {
            (updateRepository as? GithubUpdateRepository)?.downloadAndInstallApk(url)
            _uiState.update { it.copy(isDownloadingUpdate = false, updateAvailable = null) }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(updateAvailable = null) }
    }

    fun openChat(otherUser: UserProfile, onReady: (chatId: String, receiverId: String) -> Unit) {
        val uid = uiState.value.currentUserId
        if (uid.isBlank()) return
        viewModelScope.launch {
            val result = chatRepository.getOrCreateChat(uid, otherUser)
            result.fold(
                onSuccess = { chatId -> onReady(chatId, otherUser.uid) },
                onFailure = { error -> _uiState.update { it.copy(error = error.message) } }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            chatRepository.deleteChat(chatId).onFailure { error ->
                _uiState.update { it.copy(error = error.message) }
            }
        }
    }

    fun blockUser(targetUid: String) {
        val myUid = _uiState.value.currentUserId
        if (myUid.isBlank()) return
        viewModelScope.launch {
            userRepository.blockUser(myUid, targetUid)
        }
    }

    fun unblockUser(targetUid: String) {
        val myUid = _uiState.value.currentUserId
        if (myUid.isBlank()) return
        viewModelScope.launch {
            userRepository.unblockUser(myUid, targetUid)
        }
    }

    fun startForwarding(messageText: String) {
        _uiState.update { it.copy(forwardingMessage = messageText, selectedChatIds = emptySet()) }
    }

    fun cancelForwarding() {
        forwardManager.clearForwarding()
        _uiState.update { it.copy(selectedChatIds = emptySet()) }
    }

    fun toggleChatSelection(chatId: String) {
        _uiState.update { state ->
            val newSelection = if (state.selectedChatIds.contains(chatId)) {
                state.selectedChatIds - chatId
            } else {
                state.selectedChatIds + chatId
            }
            state.copy(selectedChatIds = newSelection)
        }
    }

    fun completeForwarding() {
        val state = _uiState.value
        val message = state.forwardingMessage ?: return
        val senderId = state.currentUserId
        if (senderId.isBlank()) return

        _uiState.update { it.copy(isForwardingInProgress = true) }
        
        viewModelScope.launch {
            state.selectedChatIds.forEach { chatId ->
                val chat = state.chats.find { it.id == chatId }
                val receiverId = chat?.participantIds?.firstOrNull { it != senderId }
                if (receiverId != null) {
                    chatRepository.sendMessage(chatId, senderId, receiverId, message)
                }
            }
            _uiState.update { it.copy(
                selectedChatIds = emptySet(),
                isForwardingInProgress = false,
                infoMessage = "Message forwarded successfully",
                error = null
            ) }
            forwardManager.clearForwarding()
        }
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val chatRepository: ChatRepository,
        private val userRepository: UserRepository,
        private val preferenceRepository: PreferenceRepository,
        private val updateRepository: UpdateRepository,
        private val forwardManager: ForwardManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatListViewModel(authRepository, chatRepository, userRepository, preferenceRepository, updateRepository, forwardManager) as T
    }
}
