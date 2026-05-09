package com.example.chatapk.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chatapk.domain.model.UserProfile
import com.example.chatapk.domain.repository.AuthRepository
import com.example.chatapk.domain.repository.PreferenceRepository
import com.example.chatapk.domain.repository.UserRepository
import com.example.chatapk.security.EncryptionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val currentUser: UserProfile? = null,
    val isDarkMode: Boolean = false,
    val publicKey: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val preferenceRepository: PreferenceRepository,
    private val encryptionManager: EncryptionManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val uid = authRepository.currentUserId ?: return
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            userRepository.observeCurrentUser(uid).collect { user ->
                _uiState.update { it.copy(currentUser = user, isLoading = false) }
            }
        }

        viewModelScope.launch {
            preferenceRepository.isDarkMode.collect { isDark ->
                _uiState.update { it.copy(isDarkMode = isDark) }
            }
        }

        _uiState.update { it.copy(publicKey = encryptionManager.getPublicKeyBase64()) }
    }

    fun toggleDarkMode(isDark: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setDarkMode(isDark)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val userRepository: UserRepository,
        private val preferenceRepository: PreferenceRepository,
        private val encryptionManager: EncryptionManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(authRepository, userRepository, preferenceRepository, encryptionManager) as T
    }
}
