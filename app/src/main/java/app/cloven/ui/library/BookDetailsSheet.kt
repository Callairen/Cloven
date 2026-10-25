package app.cloven.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cloven.data.db.BookWithProgress
import app.cloven.data.db.ExtractionStatus
import app.cloven.data.db.ReadingStatus
import app.cloven.ui.components.BookCover
import app.cloven.ui.components.ProgressLine
import app.cloven.ui.components.formatBytes
import app.cloven.ui.theme.Literata
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsSheet(
    item: BookWithProgress,
    coverFile: File?,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onFavorite: (Boolean) -> Unit,
    onStatus: (ReadingStatus) -> Unit,
    onDelete: () -> Unit,
    onExport: (Uri) -> Unit,
) {
    val book = item.book
    var confirmDelete by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let(onExport)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                BookCover(book.title, coverFile, Modifier.width(96.dp).aspectRatio(2f / 3f))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(book.title, fontFamily = Literata, style = MaterialTheme.typography.titleLarge)
                    Text(book.author ?: "Unknown author", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${book.pageCount} pages · ${formatBytes(book.fileSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Added ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(book.importedAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconToggleButton(checked = book.isFavorite, onCheckedChange = onFavorite) {
                    if (book.isFavorite) Icon(Icons.Rounded.Favorite, contentDescription = "Remove from favorites")
                    else Icon(Icons.Rounded.FavoriteBorder, contentDescription = "Add to favorites")
                }
            }

            val percent = item.progressPercent ?: 0f
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProgressLine(percent / 100f)
                Text("${percent.roundToInt()}% read", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (book.extractionStatus != ExtractionStatus.READY || book.processingError != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            book.processingError ?: "Reflowable text isn't available for this book.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Text("Reading status", style = MaterialTheme.typography.labelLarge)
            val statuses = listOf(ReadingStatus.NOT_STARTED to "Not started", ReadingStatus.READING to "Reading", ReadingStatus.FINISHED to "Finished")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                statuses.forEachIndexed { index, (status, label) ->
                    SegmentedButton(
                        selected = book.status == status,
                        onClick = { onStatus(status) },
                        shape = SegmentedButtonDefaults.itemShape(index, statuses.size),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) { Text(label, maxLines = 1) }
                }
            }

            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) {
                Text(if (book.status == ReadingStatus.NOT_STARTED) "Start reading" else "Open book")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { exporter.launch("${book.title.take(80)}.pdf") }) {
                    Icon(Icons.Rounded.SaveAlt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Save a copy")
                }
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Remove")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove “${book.title}”?") },
            text = {
                Text(
                    "This deletes Cloven's private copy of the PDF (${formatBytes(book.fileSizeBytes)}), " +
                        "its reading progress and bookmarks. Your reading streak and history are kept. " +
                        "The original file you imported from is not affected.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
