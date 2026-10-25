package app.cloven.data.pdf

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface ImportState {
    data object Idle : ImportState
    data class Running(val progress: ImportProgress) : ImportState
    data class Succeeded(val bookId: String, val title: String, val note: String?) : ImportState
    data class Failed(val message: String) : ImportState
}

/**
 * Runs one import at a time in the application scope, so leaving the library screen does
 * not abandon an import. The user can cancel; cancellation leaves no partial book behind.
 */
class ImportManager(
    private val importer: PdfImporter,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private var job: Job? = null

    fun start(uri: Uri) {
        if (job?.isActive == true) return
        _state.value = ImportState.Running(ImportProgress(ImportProgress.Stage.COPYING))
        job = scope.launch {
            try {
                val book = importer.import(uri) { progress -> _state.value = ImportState.Running(progress) }
                _state.value = ImportState.Succeeded(book.id, book.title, book.processingError)
            } catch (e: CancellationException) {
                _state.value = ImportState.Idle
                throw e
            } catch (e: ImportException) {
                _state.value = ImportState.Failed(e.userMessage)
            } catch (e: Exception) {
                _state.value = ImportState.Failed("Import failed unexpectedly. Please try again.")
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = ImportState.Idle
    }

    fun dismiss() {
        if (_state.value !is ImportState.Running) _state.value = ImportState.Idle
    }
}
