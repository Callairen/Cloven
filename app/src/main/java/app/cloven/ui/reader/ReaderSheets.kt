package app.cloven.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cloven.data.db.BookmarkEntity
import app.cloven.data.db.ReadingMode
import app.cloven.data.prefs.ReaderPreferences
import app.cloven.data.repo.ReaderDocument
import app.cloven.ui.components.MessageState
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypographySheet(prefs: ReaderPreferences, onChange: (ReaderPreferences) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reading style", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onReset) { Text("Restore defaults") }
            }
            TypographyControls(prefs, onChange)
            Spacer(Modifier.padding(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationSheet(
    document: ReaderDocument?,
    bookmarks: List<BookmarkEntity>,
    onHeading: (Int) -> Unit,
    onBookmark: (BookmarkEntity) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onEditNote: (Long, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val headings = document?.headings.orEmpty()
    var tab by rememberSaveable { mutableIntStateOf(if (headings.isEmpty()) 1 else 0) }
    var editing by remember { mutableStateOf<BookmarkEntity?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Contents") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Bookmarks · ${bookmarks.size}") })
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(min = 240.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (tab == 0) {
                if (headings.isEmpty()) {
                    item {
                        MessageState(
                            title = "No chapters detected",
                            body = if (document == null) "Contents come from reflowable text, which isn't available for this PDF."
                            else "Cloven couldn't find chapter headings in this book's text. Use the progress slider to move around.",
                        )
                    }
                }
                items(headings, key = { it.first }) { (blockIndex, title) ->
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable { onHeading(blockIndex) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    )
                }
            } else {
                if (bookmarks.isEmpty()) {
                    item {
                        MessageState(title = "No bookmarks yet", body = "Tap the bookmark button at the top of the reader to save your place.")
                    }
                }
                items(bookmarks, key = { it.id }) { bookmark ->
                    BookmarkRow(bookmark, onOpen = { onBookmark(bookmark) }, onEdit = { editing = bookmark }, onDelete = { onDeleteBookmark(bookmark.id) })
                }
            }
        }
    }

    editing?.let { bookmark ->
        var note by remember(bookmark.id) { mutableStateOf(bookmark.note.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Bookmark note") },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 280) note = it },
                    placeholder = { Text("A short note (optional)") },
                    shape = RoundedCornerShape(14.dp),
                    minLines = 2,
                )
            },
            confirmButton = { TextButton(onClick = { onEditNote(bookmark.id, note); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BookmarkRow(bookmark: BookmarkEntity, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 24.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(bookmark.snippet.ifBlank { "Bookmark" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            bookmark.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val where = when (bookmark.mode) {
                ReadingMode.TEXT -> "Text" + (bookmark.pdfPageIndex?.let { " · near page ${it + 1}" } ?: "")
                ReadingMode.PDF -> "Original PDF"
            }
            Text(
                "$where · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(bookmark.createdAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) { Icon(Icons.Rounded.EditNote, contentDescription = "Edit note") }
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete bookmark") }
    }
}
