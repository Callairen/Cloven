package app.cloven

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.cloven.data.db.ClovenDatabase
import app.cloven.data.files.BookStorage
import app.cloven.data.pdf.ImportManager
import app.cloven.data.pdf.PdfImporter
import app.cloven.data.pdf.PdfTextExtractor
import app.cloven.data.prefs.PreferencesRepository
import app.cloven.data.repo.ActivityRepository
import app.cloven.data.repo.LibraryRepository
import app.cloven.data.repo.ReadingRepository
import app.cloven.domain.ClovenClock
import app.cloven.domain.SystemClovenClock
import app.cloven.widget.WidgetUpdater
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

private val Context.settingsDataStore by preferencesDataStore(name = "cloven_settings")

/** Manual dependency container; one instance per process, owned by [ClovenApp]. */
class AppContainer(app: Application) {
    /** Outlives screens: imports and final session writes must finish after navigation. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: ClovenClock = SystemClovenClock

    val database = ClovenDatabase.build(app)
    val storage = BookStorage(app.filesDir)
    val preferences = PreferencesRepository(app.settingsDataStore)
    val library = LibraryRepository(database, storage, app.contentResolver, clock)
    val reading = ReadingRepository(database, clock)
    val activity = ActivityRepository(database.sessionDao(), preferences, clock)
    val widgetUpdater = WidgetUpdater(app, appScope, currentStreak = { activity.currentStreak() })
    val importManager = ImportManager(
        PdfImporter(
            contentResolver = app.contentResolver,
            storage = storage,
            bookDao = database.bookDao(),
            extractor = PdfTextExtractor(File(app.cacheDir, "pdfbox")),
            clock = clock,
        ),
        appScope,
    )

    init {
        PDFBoxResourceLoader.init(app)
    }

    fun onAppStart() {
        appScope.launch { runCatching { library.sweepOrphans() } }
        // While the process lives, every streak change (session writes, midnight) reaches the widget.
        appScope.launch { activity.observeCurrentStreak().collect { widgetUpdater.requestUpdate() } }
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as ClovenApp).container
