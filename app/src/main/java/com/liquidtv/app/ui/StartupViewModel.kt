package com.liquidtv.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.Subscription
import com.liquidtv.app.domain.model.UserPreferences
import com.liquidtv.app.domain.repository.SubscriptionRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface AppStartupGate {
    data object Checking : AppStartupGate
    data object NeedsLogin : AppStartupGate
    data class Ready(val activeSubscription: Subscription) : AppStartupGate
}

data class RootUiState(
    val gate: AppStartupGate = AppStartupGate.Checking,
    val preferences: UserPreferences = UserPreferences(),
)

@HiltViewModel
class StartupViewModel @Inject constructor(
    subscriptionRepository: SubscriptionRepository,
    userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val rootState: StateFlow<RootUiState> = combine(
        subscriptionRepository.allSubscriptions,
        subscriptionRepository.activeSubscription,
        userPreferencesRepository.userPreferences,
    ) { allSubscriptions, activeSubscription, prefs ->
        val gate = when {
            allSubscriptions.isEmpty() || activeSubscription == null -> AppStartupGate.NeedsLogin
            else -> AppStartupGate.Ready(activeSubscription)
        }
        RootUiState(
            gate = gate,
            preferences = prefs,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RootUiState(),
    )
}
