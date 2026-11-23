package com.liquidtv.app.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidtv.app.domain.model.Category
import com.liquidtv.app.ui.components.AppBackground
import com.liquidtv.app.ui.components.LiquidGlassSurface
import com.liquidtv.app.ui.components.ParentalPinDialog
import com.liquidtv.app.ui.theme.LiquidColors
import com.liquidtv.app.ui.theme.LiquidTypography
import kotlinx.coroutines.launch

@Composable
fun CategoriesScreen(
    onCategoryClick: (String) -> Unit,
    onLanguagesClick: () -> Unit = {},
    onCountriesClick: () -> Unit = {},
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var pendingProtectedCategory by remember { mutableStateOf<Category?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    AppBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 88.dp, start = 36.dp, end = 36.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Browse Categories",
                        style = LiquidTypography.HeadlineMedium.copy(
                            brush = Brush.linearGradient(
                                listOf(LiquidColors.TextPrimary, LiquidColors.AccentVioletLight)
                            )
                        ),
                    )
                    Text(
                        text = if (uiState.isLoading) (uiState.loadingMessage ?: "Loading categories...") else "${uiState.categories.size} categories available",
                        style = LiquidTypography.BodySmall,
                        color = LiquidColors.TextTertiary,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeaderQuickPill(
                        label = "Languages",
                        icon = Icons.Filled.Language,
                        onClick = onLanguagesClick,
                    )
                    HeaderQuickPill(
                        label = "Countries",
                        icon = Icons.Filled.Public,
                        onClick = onCountriesClick,
                    )
                }
            }

            if (uiState.isLoading && uiState.categories.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(color = LiquidColors.AccentBlue)
                        Text(
                            text = uiState.loadingMessage ?: "Indexing categories...",
                            style = LiquidTypography.BodyMedium,
                            color = LiquidColors.TextSecondary,
                        )
                    }
                }
            } else if (uiState.categories.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "No categories available in active playlist",
                            style = LiquidTypography.HeadlineSmall,
                            color = LiquidColors.TextSecondary,
                        )
                        LiquidGlassSurface(
                            modifier = Modifier
                                .clickable { viewModel.refresh() }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            cornerRadius = 14.dp,
                            glassAlpha = 0.25f,
                            borderColor = LiquidColors.AccentBlue,
                        ) {
                            Text("Refresh Playlist", style = LiquidTypography.LabelLarge, color = LiquidColors.AccentBlue)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 230.dp),
                    contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(uiState.categories, key = { it.id }) { category ->
                        val isLocked = viewModel.isCategoryLocked(category)
                        CategoryGridTile(
                            category = category,
                            isLocked = isLocked,
                            onClick = {
                                if (isLocked) {
                                    pinError = null
                                    pendingProtectedCategory = category
                                } else {
                                    onCategoryClick(category.name)
                                }
                            },
                        )
                    }
                }
            }
        }

        ParentalPinDialog(
            visible = pendingProtectedCategory != null,
            title = "Category Locked",
            subtitle = "Enter Parental Control PIN to open \"${pendingProtectedCategory?.name.orEmpty()}\"",
            confirmLabel = "Unlock",
            errorMessage = pinError,
            onConfirm = { enteredPin ->
                val target = pendingProtectedCategory ?: return@ParentalPinDialog
                scope.launch {
                    if (viewModel.verifyPin(enteredPin)) {
                        pendingProtectedCategory = null
                        pinError = null
                        onCategoryClick(target.name)
                    } else {
                        pinError = "Incorrect PIN. Try again."
                    }
                }
            },
            onDismiss = {
                pendingProtectedCategory = null
                pinError = null
            },
        )
    }
}

@Composable
private fun HeaderQuickPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LiquidGlassSurface(
        modifier = Modifier
            .height(48.dp)
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        cornerRadius = 24.dp,
        glassAlpha = if (isFocused) 0.28f else 0.14f,
        borderAlpha = if (isFocused) 0.8f else 0.25f,
        isFocused = isFocused,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = label, tint = LiquidColors.AccentBlueLight, modifier = Modifier.size(18.dp))
            Text(label, style = LiquidTypography.LabelMedium, color = LiquidColors.TextPrimary)
        }
    }
}

@Composable
private fun CategoryGridTile(
    category: Category,
    isLocked: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val icon = getCategoryIcon(category.name)
    val colors = getCategoryColors(category.name)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.first.copy(alpha = if (isFocused) 0.60f else 0.28f),
                        colors.second.copy(alpha = if (isFocused) 0.50f else 0.18f),
                    )
                )
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) colors.first.copy(alpha = 0.95f) else colors.first.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(interactionSource, null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (isFocused) 0.20f else 0.08f), Color.Transparent)
                    )
                ),
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Filled.Lock else icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.size(72.dp).padding(end = 8.dp, bottom = 8.dp),
            )
        }

        if (isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(LiquidColors.ErrorRed.copy(alpha = 0.85f), CircleShape)
                    .padding(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Locked Category",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                colors.first.copy(alpha = if (isFocused) 0.80f else 0.45f),
                                colors.second.copy(alpha = if (isFocused) 0.60f else 0.30f),
                            )
                        ),
                        CircleShape,
                    )
                    .border(1.dp, Color.White.copy(alpha = if (isFocused) 0.5f else 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Filled.Lock else icon,
                    contentDescription = category.name,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = category.name,
                    style = LiquidTypography.TitleMedium,
                    color = LiquidColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isLocked) "PIN Protected • ${category.channelCount} ch" else "${category.channelCount} channels",
                    style = LiquidTypography.LabelSmall,
                    color = if (isFocused) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.55f),
                )
            }
        }
    }
}

private fun getCategoryColors(name: String): Pair<Color, Color> = when {
    name.contains("news", ignoreCase = true) -> LiquidColors.CategoryNews to LiquidColors.CategoryNewsEnd
    name.contains("sport", ignoreCase = true) -> LiquidColors.CategorySports to LiquidColors.CategorySportsEnd
    name.contains("movie", ignoreCase = true) || name.contains("film", ignoreCase = true) || name.contains("cinema", ignoreCase = true) || name.contains("anim", ignoreCase = true) -> LiquidColors.CategoryMovies to LiquidColors.CategoryMoviesEnd
    name.contains("music", ignoreCase = true) -> LiquidColors.CategoryMusic to LiquidColors.CategoryMusicEnd
    name.contains("kid", ignoreCase = true) || name.contains("child", ignoreCase = true) || name.contains("cartoon", ignoreCase = true) -> LiquidColors.CategoryKids to LiquidColors.CategoryKidsEnd
    name.contains("doc", ignoreCase = true) -> LiquidColors.CategoryDoc to LiquidColors.CategoryDocEnd
    name.contains("entertain", ignoreCase = true) || name.contains("general", ignoreCase = true) -> LiquidColors.CategoryEnt to LiquidColors.CategoryEntEnd
    name.contains("education", ignoreCase = true) || name.contains("school", ignoreCase = true) -> LiquidColors.AccentTeal to LiquidColors.AccentGreen
    name.contains("cook", ignoreCase = true) || name.contains("food", ignoreCase = true) -> LiquidColors.AccentGold to LiquidColors.AccentOrange
    name.contains("travel", ignoreCase = true) -> LiquidColors.AccentCyan to LiquidColors.AccentTeal
    name.contains("weather", ignoreCase = true) -> LiquidColors.AccentBlueLight to LiquidColors.AccentCyan
    name.contains("religious", ignoreCase = true) || name.contains("church", ignoreCase = true) -> LiquidColors.AccentGold to LiquidColors.AccentOrange.copy(alpha = 0.5f)
    name.contains("business", ignoreCase = true) -> LiquidColors.AccentIndigo to LiquidColors.AccentBlue
    else -> LiquidColors.AccentBlue to LiquidColors.AccentViolet
}

private fun getCategoryIcon(name: String): ImageVector = when {
    name.contains("news", true) -> Icons.Filled.Newspaper
    name.contains("sport", true) -> Icons.Filled.SportsSoccer
    name.contains("movie", true) || name.contains("anim", true) -> Icons.Filled.Movie
    name.contains("music", true) -> Icons.Filled.MusicNote
    name.contains("doc", true) -> Icons.Filled.VideoLibrary
    name.contains("kid", true) -> Icons.Filled.ChildCare
    name.contains("entertain", true) -> Icons.Filled.Celebration
    name.contains("education", true) -> Icons.Filled.School
    name.contains("business", true) -> Icons.Filled.Business
    name.contains("lifestyle", true) -> Icons.Filled.Spa
    name.contains("religious", true) -> Icons.Filled.Church
    name.contains("cook", true) -> Icons.Filled.Restaurant
    name.contains("travel", true) -> Icons.Filled.Flight
    name.contains("science", true) -> Icons.Filled.Science
    name.contains("weather", true) -> Icons.Filled.WbSunny
    else -> Icons.Filled.Tv
}
