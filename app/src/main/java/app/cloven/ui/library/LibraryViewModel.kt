package app.cloven.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cloven.data.db.BookWithProgress
import app.cloven.data.db.ReadingStatus
import app.cloven.data.pdf.ImportManager
import app.cloven.data.pdf.ImportState
import app.cloven.data.repo.LibraryRepository
import app.cloven.domain.LibraryFilter
import app.cloven.domain.LibraryQuery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class LibraryUiState(
    val loading: Boolean = true,
    val query: String = "",
    val filter: LibraryFilter = LibraryFilter.ALL,
    val totalCount: Int = 0,
    val books: List<BookWithProgress> = emptyList(),
    val continueReading: BookWithProgress? = null,
    val importState: ImportState = ImportState.Idle,
)

class LibraryViewModel(
    private val library: LibraryRepository,
    private val importManager: ImportManager,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(LibraryFilter.ALL)
    private val selectedId = MutableStateFlow<String?>(null)
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    val state: StateFlow<LibraryUiState> = combine(
        library.observeLibrary(), query, filter, importManager.state,
    ) { books, q, f, import ->
        LibraryUiState(
            loading = false,
            query = q,
            filter = f,
            totalCount = books.size,
            books = LibraryQuery.apply(books, q, f),
            continueReading = LibraryQuery.continueReading(books),
            importState = import,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedBook: StateFlow<BookWithProgress?> = selectedId
        .flatMapLatest { id -> if (id == null) flowOf(null) else library.observeBook(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun coverFile(item: BookWithProgress): File? = library.coverFile(item.book)

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterChange(value: LibraryFilter) {
        filter.value = value
    }

    fun import(uri: Uri) = importManager.start(uri)
    fun cancelImport() = importManager.cancel()
    fun dismissImport() = importManager.dismiss()

    fun showDetails(id: String?) {
        selectedId.value = id
    }

    fun setFavorite(id: String, favorite: Boolean) = viewModelScope.launch { library.setFavorite(id, favorite) }

    fun setStatus(id: String, status: ReadingStatus) = viewModelScope.launch { library.setStatus(id, status) }

    fun delete(id: String, title: String) = viewModelScope.launch {
        selectedId.value = null
        val ok = runCatching { library.deleteBook(id) }.getOrDefault(false)
        messageChannel.send(if (ok) "Removed “$title”" else "Couldn't remove “$title”. Please try again.")
    }

    fun exportCopy(id: String, destination: Uri) = viewModelScope.launch {
        val ok = library.exportCopy(id, destination)
        messageChannel.send(if (ok) "Saved a copy of the PDF" else "Couldn't save the copy")
    }
}
