package com.liquidtv.app.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.SubscriptionType
import com.liquidtv.app.domain.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val editingSubscriptionId: String? = null,
    val selectedType: SubscriptionType = SubscriptionType.XTREAM,
    val displayName: String = "",
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val m3uUrl: String = "",
    val userAgent: String = "",
    val referer: String = "",
    val showAdvancedHeaders: Boolean = false,
    val isConnecting: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var connectJob: Job? = null

    init {
        viewModelScope.launch {
            subscriptionRepository.syncInfo.collect { info ->
                if (_uiState.value.isConnecting) {
                    _uiState.update {
                        it.copy(
                            progress = info.loadingProgress,
                            statusMessage = info.statusMessage ?: it.statusMessage,
                        )
                    }
                }
            }
        }
    }

    fun loadForEdit(subscriptionId: String?) {
        if (subscriptionId.isNullOrBlank()) return
        if (_uiState.value.editingSubscriptionId == subscriptionId) return
        viewModelScope.launch {
            val sub = subscriptionRepository.getSubscriptionById(subscriptionId) ?: return@launch
            val existingPass = if (sub.type == SubscriptionType.XTREAM) {
                subscriptionRepository.getDecryptedXtreamPassword(sub.id).orEmpty()
            } else ""
            val existingM3u = if (sub.type == SubscriptionType.M3U) {
                subscriptionRepository.getDecryptedM3uUrl(sub.id).orEmpty()
            } else ""

            _uiState.update {
                it.copy(
                    editingSubscriptionId = sub.id,
                    selectedType = sub.type,
                    displayName = sub.name,
                    serverUrl = if (sub.type == SubscriptionType.XTREAM) sub.serverUrl else "",
                    username = sub.username.orEmpty(),
                    password = existingPass,
                    m3uUrl = existingM3u,
                    userAgent = sub.userAgent.orEmpty(),
                    referer = sub.referer.orEmpty(),
                    showAdvancedHeaders = !sub.userAgent.isNullOrBlank() || !sub.referer.isNullOrBlank(),
                    errorMessage = null,
                )
            }
        }
    }

    fun selectType(type: SubscriptionType) {
        if (_uiState.value.isConnecting) return
        _uiState.update { it.copy(selectedType = type, errorMessage = null) }
    }

    fun setDisplayName(value: String) = _uiState.update { it.copy(displayName = value, errorMessage = null) }
    fun setServerUrl(value: String) = _uiState.update { it.copy(serverUrl = value, errorMessage = null) }
    fun setUsername(value: String) = _uiState.update { it.copy(username = value, errorMessage = null) }
    fun setPassword(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun togglePasswordVisibility() = _uiState.update { it.copy(showPassword = !it.showPassword) }
    fun setM3uUrl(value: String) = _uiState.update { it.copy(m3uUrl = value, errorMessage = null) }
    fun setUserAgent(value: String) = _uiState.update { it.copy(userAgent = value, errorMessage = null) }
    fun setReferer(value: String) = _uiState.update { it.copy(referer = value, errorMessage = null) }
    fun toggleAdvancedHeaders() = _uiState.update { it.copy(showAdvancedHeaders = !it.showAdvancedHeaders) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun connect() {
        val current = _uiState.value
        if (current.isConnecting) return

        val validationError = validateInputs(current)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isConnecting = true,
                    progress = 0.05f,
                    statusMessage = "جارٍ الاتصال بالخادم...",
                    errorMessage = null,
                    isSuccess = false,
                )
            }

            val result = when (current.selectedType) {
                SubscriptionType.XTREAM -> subscriptionRepository.addOrUpdateXtreamSubscription(
                    existingId = current.editingSubscriptionId,
                    displayName = current.displayName,
                    serverUrl = current.serverUrl,
                    username = current.username,
                    password = current.password,
                    activate = true,
                )
                SubscriptionType.M3U -> subscriptionRepository.addOrUpdateM3uSubscription(
                    existingId = current.editingSubscriptionId,
                    displayName = current.displayName,
                    playlistUrl = current.m3uUrl,
                    userAgent = current.userAgent,
                    referer = current.referer,
                    activate = true,
                )
            }

            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        progress = 1f,
                        statusMessage = "تم الاتصال بنجاح",
                        errorMessage = null,
                        isSuccess = true,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        progress = 0f,
                        statusMessage = null,
                        errorMessage = throwable.message ?: "تعذّر الاتصال. تأكد من صحة البيانات وحاول مجدداً.",
                        isSuccess = false,
                    )
                }
            }
        }
    }

    private fun validateInputs(state: LoginUiState): String? {
        return when (state.selectedType) {
            SubscriptionType.XTREAM -> {
                val url = state.serverUrl.trim()
                when {
                    url.isEmpty() -> "يرجى إدخال رابط خادم Xtream (يبدأ بـ http:// أو https://)."
                    !url.startsWith("http://", true) && !url.startsWith("https://", true) ->
                        "يجب أن يبدأ رابط الخادم بـ http:// أو https://"
                    state.username.isBlank() -> "يرجى إدخال اسم المستخدم."
                    state.password.isBlank() -> "يرجى إدخال كلمة المرور."
                    else -> null
                }
            }
            SubscriptionType.M3U -> {
                val url = state.m3uUrl.trim()
                when {
                    url.isEmpty() -> "يرجى إدخال رابط قائمة M3U (يبدأ بـ http:// أو https://)."
                    !url.startsWith("http://", true) && !url.startsWith("https://", true) ->
                        "يجب أن يبدأ رابط القائمة بـ http:// أو https://"
                    else -> null
                }
            }
        }
    }
}
