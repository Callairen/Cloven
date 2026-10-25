package app.cloven.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.cloven.data.db.BlockKind
import app.cloven.data.prefs.ReaderAlignment
import app.cloven.data.prefs.ReaderPreferences
import app.cloven.data.repo.ReaderDocument
import app.cloven.ui.components.CloverMark
import app.cloven.ui.theme.ReaderPalette
import app.cloven.ui.theme.family
import kotlin.math.max
import kotlin.math.roundToInt

private const val HEADER_ITEMS = 1

/**
 * Holds the logical reading position outside of Compose state. When typography or the
 * viewport changes, [suppress] blocks position reports until the list has been re-anchored
 * to [anchor], so a re-layout can never overwrite the reader's place.
 */
private class AnchorTracker(var anchor: Long) {
    var suppress = true
    var restored = false
    var layoutKey: Any? = null
}

@Composable
fun ReflowableReader(
    document: ReaderDocument,
    title: String,
    author: String?,
    prefs: ReaderPreferences,
    palette: ReaderPalette,
    initialOffset: Long,
    jump: JumpRequest?,
    onJumpConsumed: (Long) -> Unit,
    onPosition: (offset: Long, atEnd: Boolean) -> Unit,
    onUserScroll: () -> Unit,
    onTap: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val tracker = remember(document) { AnchorTracker(initialOffset) }

    BoxWithConstraints(modifier.fillMaxSize().background(palette.background)) {
        val layoutKey = listOf(
            prefs.font, prefs.fontSizeSp, prefs.bold, prefs.lineHeight, prefs.paragraphSpacing,
            prefs.marginDp, prefs.alignment, maxWidth, maxHeight,
        )
        if (tracker.layoutKey != layoutKey) {
            tracker.layoutKey = layoutKey
            tracker.suppress = true
        }

        // Initial restore, and re-anchoring after any typography / size change.
        LaunchedEffect(document, layoutKey) {
            listState.scrollToTextOffset(document, tracker.anchor)
            tracker.restored = true
            tracker.suppress = false
        }

        LaunchedEffect(jump?.id) {
            val request = jump ?: return@LaunchedEffect
            val target = request.textOffset
            if (target != null) {
                tracker.suppress = true
                tracker.anchor = target
                listState.scrollToTextOffset(document, target)
                tracker.suppress = false
            }
            onJumpConsumed(request.id)
        }

        LaunchedEffect(listState, document) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, scrollOffset) ->
                    if (tracker.suppress || !tracker.restored) return@collect
                    val first = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return@collect
                    val blockIndex = index - HEADER_ITEMS
                    val offset = when {
                        blockIndex < 0 -> 0L
                        blockIndex >= document.blocks.size -> document.totalChars
                        else -> document.index.offsetOf(blockIndex, scrollOffset.toFloat() / max(1, first.size))
                    }
                    tracker.anchor = offset
                    onPosition(offset, !listState.canScrollForward)
                    // Layout settling also emits here; only a real drag/fling counts as reading activity.
                    if (listState.isScrollInProgress) onUserScroll()
                }
        }

        val density = LocalDensity.current
        val family = prefs.font.family()
        val bodyStyle = remember(prefs, palette) {
            TextStyle(
                fontFamily = family,
                fontSize = prefs.fontSizeSp.sp,
                fontWeight = if (prefs.bold) FontWeight.SemiBold else FontWeight.Normal,
                lineHeight = prefs.lineHeight.em,
                color = palette.text,
                textAlign = if (prefs.alignment == ReaderAlignment.JUSTIFY) TextAlign.Justify else TextAlign.Start,
                hyphens = if (prefs.alignment == ReaderAlignment.JUSTIFY) Hyphens.Auto else Hyphens.None,
                lineBreak = LineBreak.Paragraph,
            )
        }
        val headingStyle = remember(bodyStyle) {
            bodyStyle.copy(
                fontSize = (prefs.fontSizeSp * 1.3f).sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 1.3f.em,
                textAlign = TextAlign.Start,
                hyphens = Hyphens.None,
            )
        }
        val paragraphGap = with(density) { (prefs.fontSizeSp * prefs.paragraphSpacing).sp.toDp() }
        val margin = prefs.marginDp.dp

        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
        ) {
            item(key = "header") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = margin).padding(top = 24.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        title,
                        style = headingStyle.copy(fontSize = (prefs.fontSizeSp * 1.45f).sp, textAlign = TextAlign.Center),
                        modifier = Modifier.semantics { heading() },
                    )
                    author?.let { Text(it, style = bodyStyle.copy(color = palette.secondaryText, textAlign = TextAlign.Center)) }
                    CloverMark(size = 18.dp, tint = palette.accent.copy(alpha = 0.7f))
                }
            }
            items(document.blocks, key = { it.blockIndex }) { block ->
                if (block.kind == BlockKind.HEADING) {
                    Text(
                        block.text,
                        style = headingStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = margin)
                            .padding(top = paragraphGap + 12.dp, bottom = paragraphGap + 4.dp)
                            .semantics { heading() },
                    )
                } else {
                    Text(
                        block.text,
                        style = bodyStyle,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = margin).padding(bottom = paragraphGap),
                    )
                }
            }
            item(key = "footer") {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CloverMark(size = 28.dp, tint = palette.accent)
                    Text("The end", style = bodyStyle.copy(color = palette.secondaryText, textAlign = TextAlign.Center))
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * Scrolls so that [offset] is at the top of the viewport: first to its block, then — once the
 * block has been measured with the current typography — by the same fraction within the block.
 */
private suspend fun LazyListState.scrollToTextOffset(document: ReaderDocument, offset: Long) {
    if (offset <= 0L) {
        scrollToItem(0)
        return
    }
    val location = document.index.locate(offset) ?: return
    val index = location.blockIndex + HEADER_ITEMS
    scrollToItem(index)
    if (location.fraction <= 0f) return
    repeat(6) {
        val size = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size
        if (size != null && size > 0) {
            scrollToItem(index, (size * location.fraction).roundToInt())
            return
        }
        withFrameNanos { }
    }
}
