package app.cloven.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class ReaderFont(val label: String) {
    LITERATA("Literata"),
    TIMES("Tinos"),
    SERIF("System serif"),
    NUNITO("Nunito"),
    SANS("System sans"),
}

enum class ReaderBackground(val label: String, val isDark: Boolean) {
    WARM_PAPER("Warm paper", false),
    CREAM("Cream", false),
    SEPIA("Sepia", false),
    DARK_ESPRESSO("Espresso", true),
    NEAR_BLACK("Night", true),
}

enum class ReaderAlignment(val label: String) { START("Left"), JUSTIFY("Justified") }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dailyGoalMinutes: Int = DEFAULT_GOAL_MINUTES,
) {
    companion object {
        const val DEFAULT_GOAL_MINUTES = 20
        val GOAL_RANGE = 5..120
    }
}

/** Typography for reflowable text. Values are clamped to sensible ranges. */
data class ReaderPreferences(
    val font: ReaderFont = ReaderFont.LITERATA,
    val fontSizeSp: Int = 19,
    val bold: Boolean = false,
    val lineHeight: Float = 1.55f,
    val paragraphSpacing: Float = 0.8f,
    val marginDp: Int = 24,
    val alignment: ReaderAlignment = ReaderAlignment.START,
    val background: ReaderBackground = ReaderBackground.WARM_PAPER,
) {
    fun normalized(): ReaderPreferences = copy(
        fontSizeSp = fontSizeSp.coerceIn(FONT_SIZE_RANGE),
        lineHeight = lineHeight.coerceIn(LINE_HEIGHT_MIN, LINE_HEIGHT_MAX),
        paragraphSpacing = paragraphSpacing.coerceIn(PARAGRAPH_MIN, PARAGRAPH_MAX),
        marginDp = marginDp.coerceIn(MARGIN_RANGE),
    )

    companion object {
        val FONT_SIZE_RANGE = 12..34
        const val LINE_HEIGHT_MIN = 1.1f
        const val LINE_HEIGHT_MAX = 2.2f
        const val PARAGRAPH_MIN = 0f
        const val PARAGRAPH_MAX = 2f
        val MARGIN_RANGE = 8..56
    }
}

class PreferencesRepository(private val dataStore: DataStore<Preferences>) {

    val appSettings: Flow<AppSettings> = dataStore.data.map { p ->
        AppSettings(
            theme = enumOr(p[THEME], ThemeMode.SYSTEM),
            dailyGoalMinutes = (p[DAILY_GOAL] ?: AppSettings.DEFAULT_GOAL_MINUTES).coerceIn(AppSettings.GOAL_RANGE),
        )
    }.distinctUntilChanged()

    val readerPreferences: Flow<ReaderPreferences> = dataStore.data.map { p ->
        val defaults = ReaderPreferences()
        ReaderPreferences(
            font = enumOr(p[FONT], defaults.font),
            fontSizeSp = p[FONT_SIZE] ?: defaults.fontSizeSp,
            bold = p[BOLD] ?: defaults.bold,
            lineHeight = p[LINE_HEIGHT] ?: defaults.lineHeight,
            paragraphSpacing = p[PARAGRAPH] ?: defaults.paragraphSpacing,
            marginDp = p[MARGIN] ?: defaults.marginDp,
            alignment = enumOr(p[ALIGN], defaults.alignment),
            background = enumOr(p[BACKGROUND], defaults.background),
        ).normalized()
    }.distinctUntilChanged()

    suspend fun setTheme(mode: ThemeMode) {
        dataStore.edit { it[THEME] = mode.name }
    }

    suspend fun setDailyGoal(minutes: Int) {
        dataStore.edit { it[DAILY_GOAL] = minutes.coerceIn(AppSettings.GOAL_RANGE) }
    }

    suspend fun setReaderPreferences(prefs: ReaderPreferences) {
        val p = prefs.normalized()
        dataStore.edit {
            it[FONT] = p.font.name
            it[FONT_SIZE] = p.fontSizeSp
            it[BOLD] = p.bold
            it[LINE_HEIGHT] = p.lineHeight
            it[PARAGRAPH] = p.paragraphSpacing
            it[MARGIN] = p.marginDp
            it[ALIGN] = p.alignment.name
            it[BACKGROUND] = p.background.name
        }
    }

    suspend fun resetReaderPreferences() = setReaderPreferences(ReaderPreferences())

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: fallback

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DAILY_GOAL = intPreferencesKey("daily_goal_minutes")
        val FONT = stringPreferencesKey("reader_font")
        val FONT_SIZE = intPreferencesKey("reader_font_size")
        val BOLD = booleanPreferencesKey("reader_bold")
        val LINE_HEIGHT = floatPreferencesKey("reader_line_height")
        val PARAGRAPH = floatPreferencesKey("reader_paragraph_spacing")
        val MARGIN = intPreferencesKey("reader_margin")
        val ALIGN = stringPreferencesKey("reader_alignment")
        val BACKGROUND = stringPreferencesKey("reader_background")
    }
}
