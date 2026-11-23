package com.liquidtv.app.ui.screens.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.PlaylistInfo
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionsUiState(
    val subscriptions: List<Subscription> = emptyList(),
    val activeSubscription: Subscription? = null,
    val syncInfo: PlaylistInfo = PlaylistInfo(),
    val busySubscriptionId: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    private val _busyId = MutableStateFlow<String?>(null)
    private val _message = MutableStateFlow<String?>(null)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SubscriptionsUiState> = combine(
        combine(
            subscriptionRepository.allSubscriptions,
            subscriptionRepository.activeSubscription,
            subscriptionRepository.syncInfo,
        ) { all, active, sync -> Triple(all, active, sync) },
        combine(_busyId, _message, _error) { busyId, msg, err -> Triple(busyId, msg, err) },
    ) { (all, active, sync), (busyId, msg, err) ->
        SubscriptionsUiState(
            subscriptions = all,
            activeSubscription = active,
            syncInfo = sync,
            busySubscriptionId = busyId,
            message = msg,
            error = err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SubscriptionsUiState(),
    )

    fun activate(subscriptionId: String) {
        viewModelScope.launch {
            _busyId.value = subscriptionId
            _error.value = null
            subscriptionRepository.activateSubscription(subscriptionId)
                .onSuccess { _message.value = "Switched active subscription" }
                .onFailure { _error.value = it.message ?: "Failed to switch subscription" }
            _busyId.value = null
        }
    }

    fun sync(subscriptionId: String) {
        viewModelScope.launch {
            _busyId.value = subscriptionId
            _error.value = null
            subscriptionRepository.syncSubscription(subscriptionId)
                .onSuccess { _message.value = "Subscription synced successfully" }
                .onFailure { _error.value = it.message ?: "Sync failed" }
            _busyId.value = null
        }
    }

    fun delete(subscriptionId: String) {
        viewModelScope.launch {
            _busyId.value = subscriptionId
            _error.value = null
            subscriptionRepository.deleteSubscription(subscriptionId)
                .onSuccess { _message.value = "Subscription deleted" }
                .onFailure { _error.value = it.message ?: "Delete failed" }
            _busyId.value = null
        }
    }

    fun clearFeedback() {
        _message.value = null
        _error.value = null
    }
}
