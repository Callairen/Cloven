package app.cloven.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import app.cloven.R
import app.cloven.ui.theme.ClovenColors
import app.cloven.ui.theme.LocalClovenExtras
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cloven.data.db.BookWithProgress
import app.cloven.data.db.ReadingStatus
import app.cloven.data.pdf.ImportProgress
import app.cloven.data.pdf.ImportState
import app.cloven.domain.LibraryFilter
import app.cloven.ui.components.BookCover
import app.cloven.ui.components.MessageState
import app.cloven.ui.components.ProgressLine
import app.cloven.ui.theme.Literata
import kotlin.math.roundToInt

@Composable
fun LibraryScreen(vm: LibraryViewModel, onOpenBook: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val selected by vm.selectedBook.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::import) // null = user cancelled; nothing is created
    }
    val launchPicker = { picker.launch(arrayOf("application/pdf")) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.totalCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = launchPicker,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Import PDF") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(20.dp),
                )
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 108.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 12.dp, bottom = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            fullWidth("header") { LibraryHeader() }

            if (state.importState !is ImportState.Idle) {
                fullWidth("import") {
                    ImportStatusCard(
                        state = state.importState,
                        onCancel = vm::cancelImport,
                        onDismiss = vm::dismissImport,
                        onOpen = { id -> vm.dismissImport(); onOpenBook(id) },
                    )
                }
            }

            when {
                state.loading -> fullWidth("loading") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                state.totalCount == 0 && state.importState is ImportState.Running -> Unit // the import card says it all
                state.totalCount == 0 -> fullWidth("empty") {
                    MessageState(
                        title = "Your shelf is waiting",
                        body = "Import a PDF e-book from your phone. Cloven keeps a private copy, so it stays readable offline.",
                    ) {
                        Button(onClick = launchPicker, shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Import a PDF")
                        }
                    }
                }
                else -> {
                    fullWidth("search") { SearchField(state.query, vm::onQueryChange) }
                    fullWidth("filters") { FilterRow(state.filter, vm::onFilterChange) }
                    val continueBook = state.continueReading
                    if (continueBook != null && state.query.isBlank() && state.filter == LibraryFilter.ALL) {
                        fullWidth("continue") {
                            ContinueReadingCard(
                                item = continueBook,
                                coverFile = vm.coverFile(continueBook),
                                onContinue = { onOpenBook(continueBook.book.id) },
                                onDetails = { vm.showDetails(continueBook.book.id) },
                            )
                        }
                    }
                    fullWidth("shelf-title") {
                        val label = if (state.query.isBlank() && state.filter == LibraryFilter.ALL) "Your shelf" else "Results"
                        Text(
                            "$label · ${state.books.size}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    if (state.books.isEmpty()) {
                        fullWidth("no-results") {
                            MessageState(
                                title = "No books found",
                                body = if (state.query.isNotBlank()) "Nothing matches “${state.query.trim()}”. Try part of a title or author name."
                                else "No books in “${state.filter.label}” yet.",
                            )
                        }
                    } else {
                        items(state.books, key = { it.book.id }) { item ->
                            BookCard(
                                item = item,
                                coverFile = vm.coverFile(item),
                                onOpen = { onOpenBook(item.book.id) },
                                onDetails = { vm.showDetails(item.book.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    selected?.let { item ->
        BookDetailsSheet(
            item = item,
            coverFile = vm.coverFile(item),
            onDismiss = { vm.showDetails(null) },
            onOpen = { vm.showDetails(null); onOpenBook(item.book.id) },
            onFavorite = { vm.setFavorite(item.book.id, it) },
            onStatus = { vm.setStatus(item.book.id, it) },
            onDelete = { vm.delete(item.book.id, item.book.title) },
            onExport = { uri -> vm.exportCopy(item.book.id, uri) },
        )
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

/**
 * Brand header: the combined logo + wordmark (inapp_logo.png) as a single image. The supplied
 * artwork has no tagline, so the tagline is the only separately rendered text.
 */
@Composable
private fun LibraryHeader() {
    val wordmark = painterResource(R.drawable.inapp_logo)
    // The wordmark's letters are dark forest green on transparency; on the dark theme they sit on a
    // buttercream plate so they stay legible without altering the artwork.
    val plate = if (LocalClovenExtras.current.isDark) {
        Modifier.clip(RoundedCornerShape(14.dp)).background(ClovenColors.Buttercream).padding(horizontal = 10.dp, vertical = 4.dp)
    } else {
        Modifier
    }
    Column(Modifier.padding(top = 4.dp)) {
        Image(
            painter = wordmark,
            contentDescription = "Cloven",
            contentScale = ContentScale.Fit,
            modifier = plate
                .height(44.dp)
                .aspectRatio(wordmark.intrinsicSize.width / wordmark.intrinsicSize.height)
                .semantics { heading() },
        )
        Text(
            "Your quiet little library",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Lines the text up with the wordmark's "C" (the artwork has a small transparent margin).
            modifier = Modifier.padding(start = 5.dp, top = 2.dp),
        )
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Search titles and authors") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") }
            }
        },
        shape = RoundedCornerShape(22.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun FilterRow(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(LibraryFilter.entries) { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
                shape = RoundedCornerShape(14.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = filter == selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.secondary,
                ),
            )
        }
    }
}

@Composable
private fun ContinueReadingCard(item: BookWithProgress, coverFile: java.io.File?, onContinue: () -> Unit, onDetails: () -> Unit) {
    val percent = item.progressPercent ?: 0f
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        onClick = onContinue,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(item.book.title, coverFile, Modifier.width(78.dp).aspectRatio(2f / 3f), corner = 12.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Continue reading", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(item.book.title, fontFamily = Literata, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.book.author?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(10.dp))
                ProgressLine(percent / 100f)
                Spacer(Modifier.height(4.dp))
                Text("${percent.roundToInt()}% read", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onContinue, shape = RoundedCornerShape(16.dp)) { Text("Continue") }
                    TextButton(onClick = onDetails) { Text("Details") }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(item: BookWithProgress, coverFile: java.io.File?, onOpen: () -> Unit, onDetails: () -> Unit) {
    val book = item.book
    val percent = item.progressPercent ?: 0f
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClickLabel = "Open book", onLongClickLabel = "Book details", onClick = onOpen, onLongClick = onDetails)
            .semantics { customActions = listOf(CustomAccessibilityAction("Book details") { onDetails(); true }) }
            // Keeps the last text line clear of the rounded clip's bottom corners.
            .padding(bottom = 8.dp),
    ) {
        Box {
            BookCover(book.title, coverFile, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            if (book.isFavorite) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Favorite, contentDescription = "Favorite", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                book.author?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onDetails, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Details for ${book.title}", modifier = Modifier.size(20.dp))
            }
        }
        when (book.status) {
            ReadingStatus.READING -> {
                Spacer(Modifier.height(4.dp))
                ProgressLine(percent / 100f, height = 5.dp)
                Text("${percent.roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ReadingStatus.FINISHED -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Finished", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ReadingStatus.NOT_STARTED -> Text("New", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ImportStatusCard(state: ImportState, onCancel: () -> Unit, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val (container, content) = when (state) {
        is ImportState.Failed -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = container, contentColor = content)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                is ImportState.Running -> {
                    val p = state.progress
                    val label = when (p.stage) {
                        ImportProgress.Stage.COPYING -> "Copying PDF…"
                        ImportProgress.Stage.CHECKING -> "Checking the document…"
                        ImportProgress.Stage.READING_TEXT -> "Reading text · page ${p.done} of ${p.total}"
                        ImportProgress.Stage.SAVING -> "Adding to your shelf…"
                    }
                    Text("Importing", style = MaterialTheme.typography.titleSmall)
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    if (p.stage == ImportProgress.Stage.READING_TEXT && p.total > 0) {
                        LinearProgressIndicator(progress = { p.done.toFloat() / p.total }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
                }
                is ImportState.Succeeded -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Added “${state.title}”", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    state.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) { Text("Dismiss") }
                        OutlinedButton(onClick = { onOpen(state.bookId) }, shape = RoundedCornerShape(16.dp)) { Text("Open") }
                    }
                }
                is ImportState.Failed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import failed", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("OK") }
                }
                ImportState.Idle -> Unit
            }
        }
    }
}
