package com.liquidtv.app.ui.screens.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.model.SeriesItem
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeriesUiState(
    val series: List<SeriesItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SeriesUiState> = combine(
        channelRepository.getSeries(),
        channelRepository.getAllCategories(MediaType.SERIES_EPISODE),
        channelRepository.getLockedCategoryNames(),
        userPreferencesRepository.userPreferences,
        _selectedCategory,
    ) { seriesList, categories, lockedCats, prefs, selectedCat ->
        val enforceLock = prefs.parentalControlEnabled && prefs.hasParentalPin && lockedCats.isNotEmpty()
        val visibleSeries = seriesList.filter { item ->
            val matchesLock = !enforceLock || (item.categoryName?.trim()?.lowercase() !in lockedCats)
            val matchesCat = selectedCat == null || item.categoryName.equals(selectedCat, ignoreCase = true)
            matchesLock && matchesCat
        }
        val visibleCategories = if (enforceLock) {
            categories.filter { it.name.trim().lowercase() !in lockedCats }
        } else {
            categories
        }
        SeriesUiState(
            series = visibleSeries,
            categories = visibleCategories,
            selectedCategory = selectedCat,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SeriesUiState(),
    )

    fun selectCategory(category: String?) {
        _selectedCategory.value = if (_selectedCategory.value == category) null else category
    }

    fun toggleFavorite(seriesId: String) {
        viewModelScope.launch {
            channelRepository.toggleSeriesFavorite(seriesId)
        }
    }
}
