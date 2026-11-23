package com.liquidtv.app.ui.screens.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.domain.model.Channel
import com.liquidtv.app.domain.model.MediaType
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MoviesUiState(
    val movies: List<Channel> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MoviesUiState> = combine(
        channelRepository.getMovies(),
        channelRepository.getAllCategories(MediaType.MOVIE),
        channelRepository.getLockedCategoryNames(),
        userPreferencesRepository.userPreferences,
        _selectedCategory,
    ) { movies, categories, lockedCats, prefs, selectedCat ->
        val enforceLock = prefs.parentalControlEnabled && prefs.hasParentalPin && lockedCats.isNotEmpty()
        val visibleMovies = movies.filter { movie ->
            val matchesLock = !enforceLock || (movie.groupTitle?.trim()?.lowercase() !in lockedCats)
            val matchesCategory = selectedCat == null || movie.groupTitle.equals(selectedCat, ignoreCase = true)
            matchesLock && matchesCategory
        }
        val visibleCategories = if (enforceLock) {
            categories.filter { it.name.trim().lowercase() !in lockedCats }
        } else {
            categories
        }
        MoviesUiState(
            movies = visibleMovies,
            categories = visibleCategories,
            selectedCategory = selectedCat,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MoviesUiState(),
    )

    fun selectCategory(category: String?) {
        _selectedCategory.value = if (_selectedCategory.value == category) null else category
    }

    fun toggleFavorite(movieId: String) {
        viewModelScope.launch {
            channelRepository.toggleFavorite(movieId)
        }
    }
}
