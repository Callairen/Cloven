package app.cloven.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloven.R
import app.cloven.data.prefs.ThemeMode

/** Cloven brand tokens. */
object ClovenColors {
    val Buttercream = Color(0xFFFFF1D2)
    val OatBeige = Color(0xFFE9DAB6)
    val SoftSage = Color(0xFFBFD0B3)
    val BotanicalGreen = Color(0xFF7F9C86)
    val ForestCharcoal = Color(0xFF2F3E35)
    val CloverGreen = Color(0xFF588157)

    /** Clover green deepened slightly so cream labels on filled controls reach 4.5:1. */
    val CloverGreenDeep = Color(0xFF4A6F49)
}

private val LightColors = lightColorScheme(
    primary = ClovenColors.CloverGreenDeep,
    onPrimary = Color(0xFFFFF8EA),
    primaryContainer = ClovenColors.SoftSage,
    onPrimaryContainer = Color(0xFF1E2B22),
    secondary = ClovenColors.BotanicalGreen,
    onSecondary = Color(0xFF14231A),
    secondaryContainer = Color(0xFFDDE7D3),
    onSecondaryContainer = ClovenColors.ForestCharcoal,
    tertiary = Color(0xFF8A6D45),
    onTertiary = Color(0xFFFFF8EA),
    tertiaryContainer = ClovenColors.OatBeige,
    onTertiaryContainer = Color(0xFF3B2F1C),
    background = ClovenColors.Buttercream,
    onBackground = ClovenColors.ForestCharcoal,
    surface = ClovenColors.Buttercream,
    onSurface = ClovenColors.ForestCharcoal,
    surfaceVariant = ClovenColors.OatBeige,
    onSurfaceVariant = Color(0xFF545F53),
    surfaceContainerLowest = Color(0xFFFFF8E8),
    surfaceContainerLow = Color(0xFFFCEDCB),
    surfaceContainer = Color(0xFFF7E7C3),
    surfaceContainerHigh = Color(0xFFF1E0BA),
    surfaceContainerHighest = ClovenColors.OatBeige,
    surfaceTint = Color.Transparent,
    outline = Color(0xFF9F947A),
    outlineVariant = Color(0xFFDDCEA9),
    error = Color(0xFFA2453A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF6DACF),
    onErrorContainer = Color(0xFF581C14),
    inverseSurface = ClovenColors.ForestCharcoal,
    inverseOnSurface = Color(0xFFF7EBCF),
    inversePrimary = Color(0xFFA8C9A0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C9A0),
    onPrimary = Color(0xFF16301A),
    primaryContainer = Color(0xFF3B5A3B),
    onPrimaryContainer = Color(0xFFD8EBCF),
    secondary = Color(0xFF9FBBA5),
    onSecondary = Color(0xFF13271A),
    secondaryContainer = Color(0xFF34473A),
    onSecondaryContainer = Color(0xFFD7E6D0),
    tertiary = Color(0xFFD8BF8E),
    onTertiary = Color(0xFF3A2C12),
    tertiaryContainer = Color(0xFF4A3F2A),
    onTertiaryContainer = Color(0xFFF3E2BE),
    background = Color(0xFF1B221D),
    onBackground = Color(0xFFF1E6CC),
    surface = Color(0xFF1B221D),
    onSurface = Color(0xFFF1E6CC),
    surfaceVariant = ClovenColors.ForestCharcoal,
    onSurfaceVariant = Color(0xFFC8C2AC),
    surfaceContainerLowest = Color(0xFF151B17),
    surfaceContainerLow = Color(0xFF1F2822),
    surfaceContainer = Color(0xFF243028),
    surfaceContainerHigh = Color(0xFF29362D),
    surfaceContainerHighest = ClovenColors.ForestCharcoal,
    surfaceTint = Color.Transparent,
    outline = Color(0xFF7E8A7F),
    outlineVariant = Color(0xFF3A483F),
    error = Color(0xFFF0A99B),
    onError = Color(0xFF4A140C),
    errorContainer = Color(0xFF6B2A20),
    onErrorContainer = Color(0xFFFFDAD2),
    inverseSurface = Color(0xFFF1E6CC),
    inverseOnSurface = ClovenColors.ForestCharcoal,
    inversePrimary = ClovenColors.CloverGreenDeep,
)

@OptIn(ExperimentalTextApi::class)
private fun variable(resource: Int, weight: FontWeight) =
    Font(resource, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Interface typeface (OFL). */
val Nunito = FontFamily(
    variable(R.font.nunito, FontWeight.Normal),
    variable(R.font.nunito, FontWeight.Medium),
    variable(R.font.nunito, FontWeight.SemiBold),
    variable(R.font.nunito, FontWeight.Bold),
    variable(R.font.nunito, FontWeight.ExtraBold),
)

/** Reading serif (OFL). */
val Literata = FontFamily(
    variable(R.font.literata, FontWeight.Normal),
    variable(R.font.literata, FontWeight.Medium),
    variable(R.font.literata, FontWeight.SemiBold),
    variable(R.font.literata, FontWeight.Bold),
)

/** Times New Roman–metric-compatible serif (OFL). */
val Tinos = FontFamily(
    Font(R.font.tinos_regular, FontWeight.Normal),
    Font(R.font.tinos_bold, FontWeight.Bold),
)

private val Base = Typography()

private fun TextStyle.nunito(weight: FontWeight, letterSpacing: Float? = null) =
    copy(fontFamily = Nunito, fontWeight = weight, letterSpacing = letterSpacing?.sp ?: this.letterSpacing)

val ClovenTypography = Typography(
    displaySmall = Base.displaySmall.nunito(FontWeight.Bold),
    headlineLarge = Base.headlineLarge.nunito(FontWeight.Bold),
    headlineMedium = Base.headlineMedium.nunito(FontWeight.Bold),
    headlineSmall = Base.headlineSmall.nunito(FontWeight.Bold),
    titleLarge = Base.titleLarge.nunito(FontWeight.Bold),
    titleMedium = Base.titleMedium.nunito(FontWeight.Bold, 0.1f),
    titleSmall = Base.titleSmall.nunito(FontWeight.Bold),
    bodyLarge = Base.bodyLarge.nunito(FontWeight.Normal),
    bodyMedium = Base.bodyMedium.nunito(FontWeight.Normal),
    bodySmall = Base.bodySmall.nunito(FontWeight.Medium),
    labelLarge = Base.labelLarge.nunito(FontWeight.Bold, 0.2f),
    labelMedium = Base.labelMedium.nunito(FontWeight.SemiBold),
    labelSmall = Base.labelSmall.nunito(FontWeight.SemiBold),
)

val ClovenShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Extra semantic colors not covered by Material's scheme. */
@Immutable
data class ClovenExtras(
    val progressTrack: Color,
    val progressFill: Color,
    val brandMark: Color,
    val coverFallback: Color,
    val coverFallbackText: Color,
    val isDark: Boolean,
)

val LocalClovenExtras = staticCompositionLocalOf {
    ClovenExtras(ClovenColors.OatBeige, ClovenColors.BotanicalGreen, ClovenColors.CloverGreen, ClovenColors.SoftSage, ClovenColors.ForestCharcoal, false)
}

@Composable
fun isClovenDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun ClovenTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val extras = if (darkTheme) {
        ClovenExtras(Color(0xFF34443A), Color(0xFF9FBBA5), Color(0xFF9CC198), Color(0xFF34473A), Color(0xFFE3EBD9), true)
    } else {
        ClovenExtras(ClovenColors.OatBeige, ClovenColors.BotanicalGreen, ClovenColors.CloverGreen, ClovenColors.SoftSage, ClovenColors.ForestCharcoal, false)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalClovenExtras provides extras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = ClovenTypography,
            shapes = ClovenShapes,
            content = content,
        )
    }
}
