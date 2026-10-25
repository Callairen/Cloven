package app.cloven.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import app.cloven.data.prefs.ReaderBackground
import app.cloven.data.prefs.ReaderFont

/** Colors for one reading background; independent of the app-wide light/dark theme. */
@Immutable
data class ReaderPalette(
    val background: Color,
    val text: Color,
    val secondaryText: Color,
    val chrome: Color,
    val accent: Color,
    val track: Color,
    val isDark: Boolean,
)

fun readerPalette(background: ReaderBackground): ReaderPalette = when (background) {
    ReaderBackground.WARM_PAPER -> ReaderPalette(
        background = Color(0xFFFBF3E3), text = Color(0xFF2D2A24), secondaryText = Color(0xFF6A6355),
        chrome = Color(0xFFF4E9D2), accent = Color(0xFF4A6F49), track = Color(0xFFE6DAC0), isDark = false,
    )
    ReaderBackground.CREAM -> ReaderPalette(
        background = ClovenColors.Buttercream, text = ClovenColors.ForestCharcoal, secondaryText = Color(0xFF5E675B),
        chrome = Color(0xFFF6E5C0), accent = Color(0xFF4A6F49), track = ClovenColors.OatBeige, isDark = false,
    )
    ReaderBackground.SEPIA -> ReaderPalette(
        background = Color(0xFFF0DFBF), text = Color(0xFF45341F), secondaryText = Color(0xFF735E44),
        chrome = Color(0xFFE6D2AC), accent = Color(0xFF6B5A2E), track = Color(0xFFD9C49C), isDark = false,
    )
    ReaderBackground.DARK_ESPRESSO -> ReaderPalette(
        background = Color(0xFF2A211B), text = Color(0xFFEADCC5), secondaryText = Color(0xFFB8A892),
        chrome = Color(0xFF342922), accent = Color(0xFFB7CFA9), track = Color(0xFF4A3D33), isDark = true,
    )
    ReaderBackground.NEAR_BLACK -> ReaderPalette(
        background = Color(0xFF111312), text = Color(0xFFCCC9C0), secondaryText = Color(0xFF8F8D86),
        chrome = Color(0xFF1B1E1C), accent = Color(0xFF9CC198), track = Color(0xFF2C302D), isDark = true,
    )
}

fun ReaderFont.family(): FontFamily = when (this) {
    ReaderFont.LITERATA -> Literata
    ReaderFont.TIMES -> Tinos
    ReaderFont.SERIF -> FontFamily.Serif
    ReaderFont.NUNITO -> Nunito
    ReaderFont.SANS -> FontFamily.SansSerif
}
