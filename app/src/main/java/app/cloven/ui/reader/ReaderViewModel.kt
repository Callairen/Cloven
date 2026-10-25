package app.cloven.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cloven.AppContainer
import app.cloven.data.db.BookEntity
import app.cloven.data.db.BookmarkEntity
import app.cloven.data.db.ExtractionStatus
import app.cloven.data.db.ReadingMode
import app.cloven.data.db.ReadingStatus
import app.cloven.data.prefs.ReaderPreferences
import app.cloven.data.repo.ReaderDocument
import app.cloven.domain.BookStatusRules
import app.cloven.domain.PositionValidator
import app.cloven.domain.ProgressMath
import app.cloven.tracking.ReadingSessionRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.roundToInt

/** One-off navigation command for the active reading surface. */
data class JumpRequest(val id: Long, val textOffset: Long? = null, val pdfPage: Int? = null)

data class ReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val book: BookEntity? = null,
    val pdfFile: File? = null,
    val document: ReaderDocument? = null,
    val mode: ReadingMode = ReadingMode.TEXT,
    val textOffset: Long = 0L,
    val textPercent: Float = 0f,
    val pdfPage: Int = 0,
    val prefs: ReaderPreferences = ReaderPreferences(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val jump: JumpRequest? = null,
) {
    val canReflow: Boolean get() = document != null && document.blocks.isNotEmpty()
    val pageCount: Int get() = book?.pageCount ?: 0
    val pdfPercent: Float get() = ProgressMath.pdfPercent(pdfPage, pageCount)
    val percent: Float get() = if (mode == ReadingMode.TEXT) textPercent else pdfPercent

    /** Whether a bookmark already exists at (or very near) the current position. */
    val currentBookmark: BookmarkEntity? get() = bookmarks.firstOrNull { b ->
        b.mode == mode && when (mode) {
            ReadingMode.TEXT -> b.textCharOffset != null && kotlin.math.abs(b.textCharOffset - textOffset) <= 40
            ReadingMode.PDF -> b.pdfPageIndex == pdfPage
        }
    }
}

class ReaderViewModel(
    private val bookId: String,
    private val container: AppContainer,
) : ViewModel() {

    private val reading = container.reading
    private val library = container.library

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    private var status = ReadingStatus.NOT_STARTED
    private var saveJob: Job? = null
    private var jumpCounter = 0L
    private val saveMutex = Mutex()

    private val recorder = ReadingSessionRecorder(
        bookId = bookId,
        store = container.activity,
        clock = container.clock,
        scope = container.appScope,
        qualifyingMs = container.activity.qualifyingMs,
        onStreakMayHaveChanged = { container.widgetUpdater.requestUpdate() },
    )

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            container.preferences.readerPreferences.collect { prefs -> _state.update { it.copy(prefs = prefs) } }
        }
        viewModelScope.launch {
            reading.observeBookmarks(bookId).collect { list -> _state.update { it.copy(bookmarks = list) } }
        }
    }

    private suspend fun load() {
        val book = library.getBook(bookId)
        if (book == null) {
            _state.update { it.copy(loading = false, error = "This book is no longer in your library.") }
            return
        }
        val file = library.bookFile(book)
        if (!file.exists()) {
            _state.update {
                it.copy(loading = false, book = book, error = "The stored PDF for this book is missing. Remove the book and import it again.")
            }
            return
        }
        val document = if (book.extractionStatus == ExtractionStatus.READY) {
            runCatching { reading.loadDocument(bookId) }.getOrNull()?.takeIf { it.blocks.isNotEmpty() }
        } else {
            null
        }
        val progress = reading.getProgress(bookId)
        val offset = PositionValidator.clampTextOffset(progress?.textCharOffset, document?.totalChars ?: 0L) ?: 0L
        val page = PositionValidator.clampPage(progress?.pdfPageIndex, book.pageCount) ?: 0
        val mode = if (document == null || progress?.lastMode == ReadingMode.PDF) ReadingMode.PDF else ReadingMode.TEXT

        status = BookStatusRules.onOpened(book.status)
        library.markOpened(book)
        _state.update {
            it.copy(
                loading = false,
                book = book,
                pdfFile = file,
                document = document,
                mode = mode,
                textOffset = offset,
                textPercent = ProgressMath.textPercent(offset, document?.totalChars ?: 0L),
                pdfPage = page,
            )
        }
        if (document == null) {
            messageChannel.send(book.processingError ?: "Reflowable text isn't available for this book, so it opens as the original PDF.")
        }
    }

    // ---- Lifecycle -----------------------------------------------------------------------

    fun onReaderVisible() = recorder.onForeground()

    fun onReaderHidden() {
        recorder.onBackground()
        flushProgress()
    }

    fun setOverlayOpen(open: Boolean) = recorder.setBlockedByUi(open)

    fun onInteraction() = recorder.onInteraction()

    override fun onCleared() {
        flushProgress()
        recorder.close()
    }

    // ---- Position --------------------------------------------------------------------------

    fun onTextPosition(offset: Long, atEnd: Boolean) {
        val document = _state.value.document ?: return
        val percent = if (atEnd) 100f else ProgressMath.textPercent(offset, document.totalChars)
        _state.update { it.copy(textOffset = offset, textPercent = percent) }
        scheduleSave()
    }

    fun onPdfPage(page: Int) {
        val count = _state.value.pageCount
        if (count <= 0) return
        _state.update { it.copy(pdfPage = page.coerceIn(0, count - 1)) }
        scheduleSave()
    }

    fun consumeJump(id: Long) {
        _state.update { if (it.jump?.id == id) it.copy(jump = null) else it }
    }

    fun switchMode() {
        val s = _state.value
        val document = s.document
        if (document == null) {
            messageChannel.trySend("Reflowable text isn't available for this PDF.")
            return
        }
        if (s.mode == ReadingMode.TEXT) {
            val page = PositionValidator.clampPage(document.pageOf(s.textOffset), s.pageCount) ?: 0
            _state.update { it.copy(mode = ReadingMode.PDF, pdfPage = page, jump = nextJump(pdfPage = page)) }
            messageChannel.trySend("Original PDF · page ${page + 1}. Zoom enlarges the page; text settings apply to reflowed text only.")
        } else {
            val offset = document.offsetForPage(s.pdfPage)
            _state.update {
                it.copy(
                    mode = ReadingMode.TEXT,
                    textOffset = offset,
                    textPercent = ProgressMath.textPercent(offset, document.totalChars),
                    jump = nextJump(textOffset = offset),
                )
            }
            messageChannel.trySend("Reflowed text · near page ${s.pdfPage + 1}")
        }
        scheduleSave()
    }

    fun jumpToFraction(fraction: Float) {
        val s = _state.value
        val f = fraction.coerceIn(0f, 1f)
        when (s.mode) {
            ReadingMode.TEXT -> s.document?.let { jumpToTextOffset((it.totalChars * f).toLong()) }
            ReadingMode.PDF -> if (s.pageCount > 0) jumpToPage((f * (s.pageCount - 1)).roundToInt())
        }
    }

    fun jumpToBlock(blockIndex: Int) {
        val document = _state.value.document ?: return
        if (_state.value.mode != ReadingMode.TEXT) _state.update { it.copy(mode = ReadingMode.TEXT) }
        jumpToTextOffset(document.index.startOf(blockIndex))
    }

    private fun jumpToTextOffset(offset: Long) {
        val document = _state.value.document ?: return
        val clamped = offset.coerceIn(0L, document.totalChars)
        _state.update {
            it.copy(textOffset = clamped, textPercent = ProgressMath.textPercent(clamped, document.totalChars), jump = nextJump(textOffset = clamped))
        }
        scheduleSave()
    }

    fun jumpToPage(page: Int) {
        val count = _state.value.pageCount
        if (count <= 0) return
        val p = page.coerceIn(0, count - 1)
        _state.update { it.copy(pdfPage = p, jump = nextJump(pdfPage = p)) }
        scheduleSave()
    }

    private fun nextJump(textOffset: Long? = null, pdfPage: Int? = null) = JumpRequest(++jumpCounter, textOffset, pdfPage)

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persist(_state.value)
        }
    }

    /** Writes the current position on the app scope so it completes even as the screen closes. */
    private fun flushProgress() {
        saveJob?.cancel()
        val snapshot = _state.value
        container.appScope.launch { persist(snapshot) }
    }

    private suspend fun persist(s: ReaderUiState) = saveMutex.withLock {
        if (s.book == null || s.loading || s.error != null) return@withLock
        status = reading.saveProgress(bookId, s.textOffset, s.pdfPage, s.mode, s.percent, status)
    }

    // ---- Bookmarks ---------------------------------------------------------------------------

    fun toggleBookmark() {
        val s = _state.value
        s.currentBookmark?.let { existing ->
            viewModelScope.launch {
                reading.deleteBookmark(existing.id)
                messageChannel.send("Bookmark removed")
            }
            return
        }
        viewModelScope.launch {
            when (s.mode) {
                ReadingMode.TEXT -> {
                    val document = s.document ?: return@launch
                    reading.addBookmark(bookId, ReadingMode.TEXT, s.textOffset, document.pageOf(s.textOffset), document.snippetAt(s.textOffset))
                }
                ReadingMode.PDF -> reading.addBookmark(bookId, ReadingMode.PDF, null, s.pdfPage, "Page ${s.pdfPage + 1}")
            }
            messageChannel.send("Bookmark added")
        }
    }

    fun openBookmark(bookmark: BookmarkEntity) {
        val s = _state.value
        val valid = PositionValidator.isValidBookmark(
            bookmark.mode, bookmark.textCharOffset, bookmark.pdfPageIndex, s.document?.totalChars ?: 0L, s.pageCount,
        )
        if (!valid) {
            messageChannel.trySend("This bookmark no longer matches the book's content.")
            return
        }
        when (bookmark.mode) {
            ReadingMode.TEXT -> {
                if (s.mode != ReadingMode.TEXT) _state.update { it.copy(mode = ReadingMode.TEXT) }
                jumpToTextOffset(bookmark.textCharOffset ?: 0L)
            }
            ReadingMode.PDF -> {
                if (s.mode != ReadingMode.PDF) _state.update { it.copy(mode = ReadingMode.PDF) }
                jumpToPage(bookmark.pdfPageIndex ?: 0)
            }
        }
    }

    fun deleteBookmark(id: Long) = viewModelScope.launch { reading.deleteBookmark(id) }

    fun updateBookmarkNote(id: Long, note: String) = viewModelScope.launch { reading.updateBookmarkNote(id, note) }

    // ---- Typography --------------------------------------------------------------------------

    fun updatePrefs(prefs: ReaderPreferences) {
        _state.update { it.copy(prefs = prefs.normalized()) }
        viewModelScope.launch { container.preferences.setReaderPreferences(prefs) }
    }

    fun resetPrefs() = viewModelScope.launch { container.preferences.resetReaderPreferences() }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 1_200L
    }
}
