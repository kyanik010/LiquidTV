package com.liquidtv.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Atmosphere slugs ported verbatim from SONIX (`public.app_themes.atmosphere`).
 */
enum class ThemeAtmosphere(val slug: String) {
    ROYAL_NAVY("royal-navy"),
    CELESTIAL_CYAN("celestial-cyan"),
    RUBY_VELVET("ruby-velvet"),
    PEARL_MARBLE("pearl-marble"),
    EMERALD_PALACE("emerald-palace"),
    MIDNIGHT_SAPPHIRE("midnight-sapphire"),
    CARRARA_MARBLE("carrara-marble"),
    IMPERIAL_AMETHYST("imperial-amethyst"),
    IMPERIAL_BRONZE("imperial-bronze"),
    BLACK_ONYX("black-onyx"),
}

/**
 * Complete color palette for a LiquidTV global theme, holding all 17 exact
 * SONIX color tokens plus derived high-contrast helper tokens.
 */
@Immutable
data class ThemePalette(
    val background: Color,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val card: Color,
    val text: Color,
    val textSecondary: Color,
    val muted: Color,
    val panel: Color,
    val surface: Color,
    val accent: Color,
    val border: Color,
    val focus: Color,
    val selected: Color,
    val danger: Color,
    val success: Color,
    val waveOne: Color,
    val waveTwo: Color,
) {
    val isLight: Boolean
        get() = background.luminance() > 0.45f

    /** High-contrast foreground color rendered on top of filled [accent] surfaces (buttons, pills). */
    val onAccent: Color
        get() = if (accent.luminance() > 0.22f) {
            if (isLight) lerp(text, Color.Black, 0.45f) else background
        } else {
            Color.White
        }

    /** High-contrast foreground color rendered on top of filled [danger] surfaces. */
    val onDanger: Color
        get() = if (danger.luminance() > 0.28f) {
            if (isLight) lerp(text, Color.Black, 0.45f) else background
        } else {
            Color.White
        }

    /** High-contrast foreground color rendered on top of filled [success] surfaces. */
    val onSuccess: Color
        get() = if (success.luminance() > 0.28f) {
            if (isLight) lerp(text, Color.Black, 0.45f) else background
        } else {
            Color.White
        }

    /** Foreground tint for accent-colored text on [card]/[surface]/[panel] so small text stays crisp on light themes. */
    val accentText: Color
        get() = if (isLight) lerp(text, accent, 0.48f) else accent

    /** Secondary accent used for subtle dual-stop theme gradients. */
    val accentSecondary: Color
        get() = waveTwo

    /** Soft focus glow tint derived from [focus] and [accent]. */
    val focusGlow: Color
        get() = focus

    /** Harmonized warning amber shifted for contrast on light vs dark surfaces. */
    val warning: Color
        get() = if (isLight) Color(0xFFB5690A) else Color(0xFFFFB547)

    /** Overlay scrim color for modal dialogs and bottom sheets. */
    val dialogScrim: Color
        get() = Color.Black.copy(alpha = if (isLight) 0.48f else 0.68f)
}

@Immutable
data class ThemeDefinition(
    val id: String,
    val arabicName: String,
    val englishName: String,
    val sortOrder: Int,
    val isActive: Boolean,
    val isDefault: Boolean,
    val atmosphere: ThemeAtmosphere,
    val palette: ThemePalette,
) {
    val isLight: Boolean
        get() = palette.isLight
}

/**
 * Authoritative registry containing the exact 10 original SONIX themes from `public.app_themes`.
 * All HEX codes, atmosphere identifiers, sort orders, and default flags are preserved verbatim.
 */
object AppThemeCatalog {

    const val DEFAULT_THEME_ID = "royal-navy"

    val RoyalNavy = ThemeDefinition(
        id = "royal-navy",
        arabicName = "الكحلي الملكي",
        englishName = "Royal Navy",
        sortOrder = 10,
        isActive = true,
        isDefault = true,
        atmosphere = ThemeAtmosphere.ROYAL_NAVY,
        palette = ThemePalette(
            background = Color(0xFF050B19),
            backgroundTop = Color(0xFF0B1A33),
            backgroundBottom = Color(0xFF1A3152),
            card = Color(0xFF1B3555),
            text = Color(0xFFF8F6F0),
            textSecondary = Color(0xFFC9D5E4),
            muted = Color(0xFF91A4BC),
            panel = Color(0xFF0C1A2D),
            surface = Color(0xFF132844),
            accent = Color(0xFFE4C27A),
            border = Color(0xFF4D6C91),
            focus = Color(0xFFB7D8FF),
            selected = Color(0xFF28496C),
            danger = Color(0xFFF0787C),
            success = Color(0xFF68D5A0),
            waveOne = Color(0xFF376A9D),
            waveTwo = Color(0xFFD5A85E),
        ),
    )

    val CelestialCyan = ThemeDefinition(
        id = "celestial-cyan",
        arabicName = "الكحلي السماوي",
        englishName = "Celestial Cyan",
        sortOrder = 11,
        isActive = true,
        isDefault = false,
        atmosphere = ThemeAtmosphere.CELESTIAL_CYAN,
        palette = ThemePalette(
            background = Color(0xFF04151F),
            backgroundTop = Color(0xFF082B3A),
            backgroundBottom = Color(0xFF0B5260),
            card = Color(0xFF124052),
            text = Color(0xFFF2FEFF),
            textSecondary = Color(0xFFC0E5E8),
            muted = Color(0xFF82B7BE),
            panel = Color(0xFF082432),
            surface = Color(0xFF0D3444),
            accent = Color(0xFF65E3E8),
            border = Color(0xFF367F8E),
            focus = Color(0xFF8AF0F2),
            selected = Color(0xFF1B596B),
            danger = Color(0xFFFF7E91),
            success = Color(0xFF65D8AE),
            waveOne = Color(0xFF25A8B1),
            waveTwo = Color(0xFFB9D7C7),
        ),
    )

    val RubyVelvet = ThemeDefinition(
        id = "ruby-velvet",
        arabicName = "مخمل العقيق",
        englishName = "Ruby Velvet",
        sortOrder = 12,
        isActive = true,
        isDefault = false,
        atmosphere = ThemeAtmosphere.RUBY_VELVET,
        palette = ThemePalette(
            background = Color(0xFF17060D),
            backgroundTop = Color(0xFF2A0B19),
            backgroundBottom = Color(0xFF54172F),
            card = Color(0xFF42172D),
            text = Color(0xFFFFF5F5),
            textSecondary = Color(0xFFE8CDD4),
            muted = Color(0xFFB895A2),
            panel = Color(0xFF260D1A),
            surface = Color(0xFF351426),
            accent = Color(0xFFD9A46D),
            border = Color(0xFF87445D),
            focus = Color(0xFFF1B7C9),
            selected = Color(0xFF61233F),
            danger = Color(0xFFFF7187),
            success = Color(0xFF72CEA2),
            waveOne = Color(0xFFA13A61),
            waveTwo = Color(0xFFD6A16A),
        ),
    )

    val PearlMarble = ThemeDefinition(
        id = "pearl-marble",
        arabicName = "لؤلؤة القصور",
        englishName = "Pearl Marble",
        sortOrder = 13,
        isActive = true,
        isDefault = false,
        atmosphere = ThemeAtmosphere.PEARL_MARBLE,
        palette = ThemePalette(
            background = Color(0xFFE9E0D1),
            backgroundTop = Color(0xFFFFFDF7),
            backgroundBottom = Color(0xFFD7C8B3),
            card = Color(0xFFFFFDF8),
            text = Color(0xFF30271F),
            textSecondary = Color(0xFF5C5043),
            muted = Color(0xFF897B6B),
            panel = Color(0xFFF9F4EA),
            surface = Color(0xFFF1E9DC),
            accent = Color(0xFFB28A51),
            border = Color(0xFFC9B99F),
            focus = Color(0xFFA17A46),
            selected = Color(0xFFE6D8C5),
            danger = Color(0xFFB94C53),
            success = Color(0xFF337B5A),
            waveOne = Color(0xFFC7B18D),
            waveTwo = Color(0xFFA88C67),
        ),
    )

    val EmeraldPalace = ThemeDefinition(
        id = "emerald-palace",
        arabicName = "قصر الزمرد",
        englishName = "Emerald Palace",
        sortOrder = 14,
        isActive = true,
        isDefault = false,
        atmosphere = ThemeAtmosphere.EMERALD_PALACE,
        palette = ThemePalette(
            background = Color(0xFF04150F),
            backgroundTop = Color(0xFF0A261B),
            backgroundBottom = Color(0xFF174A35),
            card = Color(0xFF194331),
            text = Color(0xFFF4FBF4),
            textSecondary = Color(0xFFC6D9CB),
            muted = Color(0xFF8EAA98),
            panel = Color(0xFF0B2118),
            surface = Color(0xFF123326),
            accent = Color(0xFFDCC17A),
            border = Color(0xFF46795D),
            focus = Color(0xFFB8E6C4),
            selected = Color(0xFF245A40),
            danger = Color(0xFFE8797D),
            success = Color(0xFF66D49A),
            waveOne = Color(0xFF2F9068),
            waveTwo = Color(0xFFD2AD62),
        ),
    )

    val MidnightSapphire = ThemeDefinition(
        id = "midnight-sapphire",
        arabicName = "ياقوت منتصف الليل",
        englishName = "Midnight Sapphire",
        sortOrder = 15,
        isActive = true,
        isDefault = false,
        atmosphere = ThemeAtmosphere.MIDNIGHT_SAPPHIRE,
        palette = ThemePalette(
            background = Color(0xFF050B1C),
            backgroundTop = Color(0xFF0B1740),
            backgroundBottom = Color(0xFF1A3670),
            card = Color(0xFF172D57),
            text = Color(0xFFF4F8FF),
            textSecondary = Color(0xFFC8D6EC),
            muted = Color(0xFF8FA5C7),
            panel = Color(0xFF0A1634),
            surface = Color(0xFF112348),
            accent = Color(0xFF91B8F1),
            border = Color(0xFF3F6398),
            focus = Color(0xFFAFCFFF),
            selected = Color(0xFF244477),
            danger = Color(0xFFEF7889),
            success = Color(0xFF68D2A2),
            waveOne = Color(0xFF326CC1),
            waveTwo = Color(0xFF8BADE0),
        ),
    )

    val CarraraMarble = ThemeDefinition(
        id = "carrara-marble",
        arabicName = "رخام كارارا",
        englishName = "Carrara Marble",
        sortOrder = 16,
        isActive = true,
        isDefault = false,
        atmosphere = ThemeAtmosphere.CARRARA_MARBLE,
        palette = ThemePalette(
            background = Color(0xFFE2E8ED),
            backgroundTop = Color(0xFFFFFFFF),
            backgroundBottom = Color(0xFFCDD7E0),
            card = Color(0xFFFFFFFF),
            text = Color(0xFF26323D),
            textSecondary = Color(0xFF4C5C68),
            muted = Color(0xFF778794),
            panel = Color(0xFFF8FAFC),
            surface = Color(0xFFEEF2F5),
            accent = Color(0xFF7895AC),
            border = Color(0xFFBCC9D3),
            focus = Color(0xFF6F8EA8),
            selected = Color(0xFFDFE7ED),
            danger = Color(0xFFB84D59),
            success = Color(0xFF2F805E),
            waveOne = Color(0xFF9DB6C8),
            waveTwo = Color(0xFFB9A58B),
        ),
    )

    val ImperialAmethyst = ThemeDefinition(
        id = "imperial-amethyst",
        arabicName = "الأرجوان الإمبراطوري",
        englishName = "Imperial Amethyst",
        sortOrder = 17,
        isActive = true,
        isDefault = false,
        atmosphere = ThemeAtmosphere.IMPERIAL_AMETHYST,
        palette = ThemePalette(
            background = Color(0xFF100819),
            backgroundTop = Color(0xFF21102F),
            backgroundBottom = Color(0xFF432052),
            card = Color(0xFF342144),
            text = Color(0xFFFBF6FF),
            textSecondary = Color(0xFFDCCCE8),
            muted = Color(0xFFA998B9),
            panel = Color(0xFF1B1027),
            surface = Color(0xFF291936),
            accent = Color(0xFFD5A8E8),
            border = Color(0xFF725487),
            focus = Color(0xFFD9B9F3),
            selected = Color(0xFF4A2E60),
            danger = Color(0xFFF07E98),
            success = Color(0xFF6DCDA5),
            waveOne = Color(0xFF8553B3),
            waveTwo = Color(0xFFC18BBE),
        ),
    )

    val ImperialBronze = ThemeDefinition(
        id = "imperial-bronze",
        arabicName = "برونز الإمبراطورية",
        englishName = "Imperial Bronze",
        sortOrder = 18,
        isActive = true,
        isDefault = false,
        atmosphere = ThemeAtmosphere.IMPERIAL_BRONZE,
        palette = ThemePalette(
            background = Color(0xFF120D09),
            backgroundTop = Color(0xFF24180F),
            backgroundBottom = Color(0xFF49301A),
            card = Color(0xFF3A2818),
            text = Color(0xFFFFF8EE),
            textSecondary = Color(0xFFE5D2BB),
            muted = Color(0xFFB29A7F),
            panel = Color(0xFF21170F),
            surface = Color(0xFF302114),
            accent = Color(0xFFD9A66A),
            border = Color(0xFF80603B),
            focus = Color(0xFFE9C69A),
            selected = Color(0xFF56391F),
            danger = Color(0xFFE97C6F),
            success = Color(0xFF7AC99B),
            waveOne = Color(0xFFA56D3E),
            waveTwo = Color(0xFFD5A45E),
        ),
    )

    val BlackOnyx = ThemeDefinition(
        id = "black-onyx",
        arabicName = "أونيكس أسود",
        englishName = "Black Onyx",
        sortOrder = 19,
        isActive = true,
        isDefault = false,
        atmosphere = ThemeAtmosphere.BLACK_ONYX,
        palette = ThemePalette(
            background = Color(0xFF050609),
            backgroundTop = Color(0xFF101318),
            backgroundBottom = Color(0xFF252A31),
            card = Color(0xFF252B34),
            text = Color(0xFFF7F8FA),
            textSecondary = Color(0xFFCED3DA),
            muted = Color(0xFF929DA9),
            panel = Color(0xFF11151B),
            surface = Color(0xFF1B2028),
            accent = Color(0xFFD9DDE2),
            border = Color(0xFF66717E),
            focus = Color(0xFFD3DCE5),
            selected = Color(0xFF39414C),
            danger = Color(0xFFE77A83),
            success = Color(0xFF72C79D),
            waveOne = Color(0xFF73869A),
            waveTwo = Color(0xFFB5A18A),
        ),
    )

    val allThemes: List<ThemeDefinition> = listOf(
        RoyalNavy,
        CelestialCyan,
        RubyVelvet,
        PearlMarble,
        EmeraldPalace,
        MidnightSapphire,
        CarraraMarble,
        ImperialAmethyst,
        ImperialBronze,
        BlackOnyx,
    ).filter { it.isActive }.sortedBy { it.sortOrder }

    val defaultTheme: ThemeDefinition = RoyalNavy

    private val byId: Map<String, ThemeDefinition> = allThemes.associateBy { it.id }

    /**
     * Resolves a stored theme ID (or atmosphere slug) into a valid [ThemeDefinition],
     * falling back safely to [RoyalNavy] if null, blank, unknown, or corrupted.
     */
    fun resolve(themeId: String?): ThemeDefinition {
        if (themeId.isNullOrBlank()) return defaultTheme
        val normalized = themeId.trim().lowercase()
        return byId[normalized] ?: defaultTheme
    }
}
