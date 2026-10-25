package app.cloven.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cloven.BuildConfig
import app.cloven.data.prefs.AppSettings
import app.cloven.data.prefs.PreferencesRepository
import app.cloven.data.prefs.ReaderPreferences
import app.cloven.data.prefs.ThemeMode
import app.cloven.data.repo.LibraryRepository
import app.cloven.data.repo.StorageInfo
import app.cloven.ui.components.CloverMark
import app.cloven.ui.components.formatBytes
import app.cloven.ui.reader.TypographyControls
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SettingsViewModel(
    private val preferences: PreferencesRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    val appSettings: StateFlow<AppSettings> =
        preferences.appSettings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val readerPreferences: StateFlow<ReaderPreferences> =
        preferences.readerPreferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderPreferences())

    private val _storage = MutableStateFlow<StorageInfo?>(null)
    val storage: StateFlow<StorageInfo?> = _storage.asStateFlow()

    init {
        viewModelScope.launch { _storage.value = runCatching { library.storageInfo() }.getOrNull() }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { preferences.setTheme(mode) }
    fun setDailyGoal(minutes: Int) = viewModelScope.launch { preferences.setDailyGoal(minutes) }
    fun setReader(prefs: ReaderPreferences) = viewModelScope.launch { preferences.setReaderPreferences(prefs) }
    fun resetReader() = viewModelScope.launch { preferences.resetReaderPreferences() }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.appSettings.collectAsStateWithLifecycle()
    val reader by vm.readerPreferences.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })

        Section("Appearance") {
            Text("App theme", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = settings.theme == mode,
                        onClick = { vm.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) { Text(mode.label) }
                }
            }
            Text(
                "The reading background is chosen separately below, so you can read on paper tones with a dark app or vice versa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section("Daily reading goal") {
            // Local slider state so dragging doesn't write to DataStore on every frame.
            var draft by remember(settings.dailyGoalMinutes) { mutableStateOf(settings.dailyGoalMinutes.toFloat()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Goal", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("${draft.roundToInt()} min per day", style = MaterialTheme.typography.titleSmall)
            }
            Slider(
                value = draft,
                onValueChange = { draft = (it / 5).roundToInt() * 5f },
                onValueChangeFinished = { vm.setDailyGoal(draft.roundToInt()) },
                valueRange = AppSettings.GOAL_RANGE.first.toFloat()..AppSettings.GOAL_RANGE.last.toFloat(),
                steps = (AppSettings.GOAL_RANGE.last - AppSettings.GOAL_RANGE.first) / 5 - 1,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.semantics { contentDescription = "Daily goal ${draft.roundToInt()} minutes" },
            )
            Text(
                "Your streak counts any day with at least 5 minutes of reading; the goal is a separate target.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section("Default reading style") {
            Text(
                "Applies to reflowed text in every book. Original PDF pages keep their own fonts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TypographyControls(reader, vm::setReader)
            OutlinedButton(onClick = { vm.resetReader() }, shape = RoundedCornerShape(16.dp)) { Text("Restore default reading style") }
        }

        Section("Storage & privacy") {
            val info = storage
            Text(
                if (info == null) "Calculating…" else "${info.bookCount} books · ${formatBytes(info.bytes)} on this device",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Everything stays on this phone: Cloven has no account, no cloud sync, no analytics and no internet permission. " +
                    "Books are stored as private copies inside the app and are not encrypted beyond Android's own app sandbox. " +
                    "Cloud backup is turned off, so uninstalling Cloven deletes your library and reading history — " +
                    "use “Save a copy” in a book's details to keep a PDF elsewhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section("About") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CloverMark(size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Cloven", style = MaterialTheme.typography.titleMedium)
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "An offline reader for PDF e-books. Fonts: Literata, Tinos and Nunito (SIL Open Font License). " +
                    "PDF text extraction: PdfBox-Android (Apache License 2.0).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}
