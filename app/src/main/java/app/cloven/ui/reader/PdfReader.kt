package app.cloven.ui.reader

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cloven.data.pdf.PdfPageRenderer
import app.cloven.ui.theme.ReaderPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface RendererState {
    data object Loading : RendererState
    data class Ready(val renderer: PdfPageRenderer) : RendererState
    data class Failed(val message: String) : RendererState
}

/** Bounded in-memory cache of rendered pages, sized by bitmap bytes. */
private class PageCache : LruCache<Int, Bitmap>(
    minOf(Runtime.getRuntime().maxMemory() / 5, 96L * 1024 * 1024).toInt(),
) {
    override fun sizeOf(key: Int, value: Bitmap): Int = value.allocationByteCount
}

@Composable
fun PdfReader(
    file: File,
    initialPage: Int,
    jump: JumpRequest?,
    onJumpConsumed: (Long) -> Unit,
    onPage: (Int) -> Unit,
    onUserScroll: () -> Unit,
    onInteraction: () -> Unit,
    onTap: () -> Unit,
    palette: ReaderPalette,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val state by produceState<RendererState>(RendererState.Loading, file) {
        val renderer = try {
            withContext(Dispatchers.IO) { PdfPageRenderer.open(file) }
        } catch (e: SecurityException) {
            value = RendererState.Failed("This PDF is password-protected and can't be displayed.")
            return@produceState
        } catch (e: Exception) {
            value = RendererState.Failed("Cloven couldn't open the original PDF. The file may be damaged.")
            return@produceState
        }
        value = RendererState.Ready(renderer)
        awaitDispose { renderer.close() }
    }
    val surround = if (palette.isDark) Color(0xFF0D0F0E) else Color(0xFFE4DCCB)

    Box(modifier.fillMaxSize().background(surround)) {
        when (val s = state) {
            RendererState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = palette.accent)
            is RendererState.Failed -> Text(
                s.message,
                color = palette.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.8f),
            )
            is RendererState.Ready -> PdfPages(
                renderer = s.renderer,
                initialPage = initialPage,
                jump = jump,
                onJumpConsumed = onJumpConsumed,
                onPage = onPage,
                onUserScroll = onUserScroll,
                onInteraction = onInteraction,
                onTap = onTap,
                accent = palette.accent,
                contentPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun PdfPages(
    renderer: PdfPageRenderer,
    initialPage: Int,
    jump: JumpRequest?,
    onJumpConsumed: (Long) -> Unit,
    onPage: (Int) -> Unit,
    onUserScroll: () -> Unit,
    onInteraction: () -> Unit,
    onTap: () -> Unit,
    accent: Color,
    contentPadding: PaddingValues,
) {
    val count = renderer.pageCount
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage.coerceIn(0, count - 1))
    val cache = remember(renderer) { PageCache() }
    val aspects by produceState<List<Float>?>(null, renderer) {
        value = runCatching { renderer.pageSizes().map { (w, h) -> if (w > 0) h.toFloat() / w else 1.414f } }.getOrNull()
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var settled by remember { mutableStateOf(false) }

    LaunchedEffect(jump?.id) {
        val request = jump ?: return@LaunchedEffect
        request.pdfPage?.let { listState.scrollToItem(it.coerceIn(0, count - 1)) }
        onJumpConsumed(request.id)
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val index = listState.firstVisibleItemIndex
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            if (first != null && listState.firstVisibleItemScrollOffset > first.size / 2 && index + 1 < count) index + 1 else index
        }.distinctUntilChanged().collect { page ->
            onPage(page)
            if (settled) {
                onUserScroll()
                onInteraction()
            }
            settled = true
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth
        val quality = if (scale > 1.5f) 2 else 1
        // Extra bottom room so the end of the document is reachable while zoomed in.
        val zoomPadding = with(density) { (constraints.maxHeight * (1f - 1f / scale)).toDp() }
        val layoutDirection = LocalLayoutDirection.current

        fun clampOffset(value: Float, s: Float): Float {
            val limit = widthPx * (s - 1f) / 2f
            return value.coerceIn(-limit, limit)
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 8.dp + zoomPadding,
            ),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.count { it.pressed }
                            if (pressed >= 2) {
                                val newScale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                                scale = newScale
                                offsetX = clampOffset(offsetX + event.calculatePan().x, newScale)
                                event.changes.forEach { it.consume() }
                                onInteraction()
                            } else if (scale > 1f) {
                                offsetX = clampOffset(offsetX + event.calculatePan().x, scale)
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = {
                            scale = if (scale > 1.05f) 1f else 2.5f
                            offsetX = 0f
                            onInteraction()
                        },
                    )
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
        ) {
            items(count, key = { it }) { index ->
                val aspect = aspects?.getOrNull(index) ?: aspects?.firstOrNull() ?: 1.414f
                PdfPage(renderer, index, aspect, widthPx, quality, cache, accent)
            }
        }
    }
}

@Composable
private fun PdfPage(
    renderer: PdfPageRenderer,
    index: Int,
    aspect: Float,
    widthPx: Int,
    quality: Int,
    cache: PageCache,
    accent: Color,
) {
    val key = index * 4 + quality
    val bitmap by produceState(cache.get(key) ?: cache.get(index * 4 + 1), key, widthPx) {
        val cached = cache.get(key)
        if (cached != null) {
            value = cached
        } else {
            renderer.render(index, widthPx * quality)?.let {
                cache.put(key, it)
                value = it
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f / aspect)
            .background(Color.White)
            .semantics { contentDescription = "Page ${index + 1}" },
    ) {
        val image = bitmap
        if (image != null) {
            Image(image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        } else {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), color = accent, strokeWidth = 2.dp)
        }
    }
}
