package app.cloven.ui.reader

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cloven.data.db.ReadingMode
import app.cloven.ui.theme.ReaderPalette
import app.cloven.ui.theme.readerPalette
import kotlin.math.roundToInt

private enum class ReaderOverlay { NONE, TYPOGRAPHY, NAVIGATION, PDF_INFO }

@Composable
fun ReaderScreen(vm: ReaderViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val palette = readerPalette(state.prefs.background)
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var overlay by rememberSaveable { mutableStateOf(ReaderOverlay.NONE) }
    val snackbar = remember { SnackbarHostState() }

    // Session time counts only while this screen is resumed (not locked / backgrounded).
    LifecycleResumeEffect(vm) {
        vm.onReaderVisible()
        onPauseOrDispose { vm.onReaderHidden() }
    }
    LaunchedEffect(overlay) { vm.setOverlayOpen(overlay != ReaderOverlay.NONE) }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    SystemBarAppearance(darkContent = !palette.isDark)

    val systemBars = WindowInsets.systemBars.asPaddingValues()
    val contentPadding = PaddingValues(
        top = systemBars.calculateTopPadding() + 8.dp,
        bottom = systemBars.calculateBottomPadding() + 16.dp,
    )

    Box(Modifier.fillMaxSize().background(palette.background)) {
        val book = state.book
        val document = state.document
        val file = state.pdfFile
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = palette.accent)
            state.error != null || book == null -> ReaderError(state.error ?: "Book not found.", palette, onBack)
            state.mode == ReadingMode.TEXT && document != null -> ReflowableReader(
                document = document,
                title = book.title,
                author = book.author,
                prefs = state.prefs,
                palette = palette,
                initialOffset = state.textOffset,
                jump = state.jump,
                onJumpConsumed = vm::consumeJump,
                onPosition = vm::onTextPosition,
                onUserScroll = {
                    chromeVisible = false
                    vm.onInteraction()
                },
                onTap = {
                    chromeVisible = !chromeVisible
                    vm.onInteraction()
                },
                contentPadding = contentPadding,
            )
            file != null -> PdfReader(
                file = file,
                initialPage = state.pdfPage,
                jump = state.jump,
                onJumpConsumed = vm::consumeJump,
                onPage = vm::onPdfPage,
                onUserScroll = { chromeVisible = false },
                onInteraction = vm::onInteraction,
                onTap = {
                    chromeVisible = !chromeVisible
                    vm.onInteraction()
                },
                palette = palette,
                contentPadding = contentPadding,
            )
        }

        // Keeps text from running under the clock and status icons while the controls are hidden.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(palette.background.copy(alpha = 0.94f)),
        )

        if (book != null && state.error == null && !state.loading) {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                ReaderTopBar(
                    title = book.title,
                    subtitle = when (state.mode) {
                        ReadingMode.TEXT -> "${state.textPercent.roundToInt()}% · Reflowed text"
                        ReadingMode.PDF -> "Page ${state.pdfPage + 1} of ${state.pageCount} · Original PDF"
                    },
                    bookmarked = state.currentBookmark != null,
                    palette = palette,
                    onBack = onBack,
                    onBookmark = vm::toggleBookmark,
                )
            }
            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ReaderBottomBar(
                    state = state,
                    palette = palette,
                    onSeek = vm::jumpToFraction,
                    onTypography = { overlay = if (state.mode == ReadingMode.TEXT) ReaderOverlay.TYPOGRAPHY else ReaderOverlay.PDF_INFO },
                    onNavigation = { overlay = ReaderOverlay.NAVIGATION },
                    onSwitchMode = vm::switchMode,
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 120.dp))
    }

    when (overlay) {
        ReaderOverlay.TYPOGRAPHY -> TypographySheet(
            prefs = state.prefs,
            onChange = vm::updatePrefs,
            onReset = { vm.resetPrefs() },
            onDismiss = { overlay = ReaderOverlay.NONE },
        )
        ReaderOverlay.NAVIGATION -> NavigationSheet(
            document = state.document,
            bookmarks = state.bookmarks,
            onHeading = { overlay = ReaderOverlay.NONE; vm.jumpToBlock(it) },
            onBookmark = { overlay = ReaderOverlay.NONE; vm.openBookmark(it) },
            onDeleteBookmark = { vm.deleteBookmark(it) },
            onEditNote = { id, note -> vm.updateBookmarkNote(id, note) },
            onDismiss = { overlay = ReaderOverlay.NONE },
        )
        ReaderOverlay.PDF_INFO -> AlertDialog(
            onDismissRequest = { overlay = ReaderOverlay.NONE },
            title = { Text("Original PDF") },
            text = {
                Text(
                    "This view shows the PDF exactly as published, with its own fonts and layout. " +
                        "Pinch or double-tap to zoom — zoom enlarges the page, it doesn't restyle the text.\n\n" +
                        "For adjustable typeface, size and spacing, switch to reflowed text" +
                        (if (state.canReflow) "." else " (not available for this PDF because it has no extractable text)."),
                )
            },
            confirmButton = { TextButton(onClick = { overlay = ReaderOverlay.NONE }) { Text("Got it") } },
        )
        ReaderOverlay.NONE -> Unit
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    subtitle: String,
    bookmarked: Boolean,
    palette: ReaderPalette,
    onBack: () -> Unit,
    onBookmark: () -> Unit,
) {
    Surface(color = palette.chrome, contentColor = palette.text, shape = MaterialTheme.shapes.large.copy(topStart = androidx.compose.foundation.shape.CornerSize(0.dp), topEnd = androidx.compose.foundation.shape.CornerSize(0.dp))) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to library") }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = palette.secondaryText, maxLines = 1)
            }
            IconButton(onClick = onBookmark) {
                if (bookmarked) Icon(Icons.Rounded.Bookmark, contentDescription = "Remove bookmark here", tint = palette.accent)
                else Icon(Icons.Outlined.BookmarkAdd, contentDescription = "Add bookmark here")
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    state: ReaderUiState,
    palette: ReaderPalette,
    onSeek: (Float) -> Unit,
    onTypography: () -> Unit,
    onNavigation: () -> Unit,
    onSwitchMode: () -> Unit,
) {
    val isText = state.mode == ReadingMode.TEXT
    val current = if (isText) state.textPercent / 100f
    else if (state.pageCount > 1) state.pdfPage.toFloat() / (state.pageCount - 1) else 0f
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: current
    val label = if (isText) "${(shown * 100).roundToInt()}%"
    else "${(shown * (state.pageCount - 1)).roundToInt() + 1} / ${state.pageCount}"

    Surface(
        color = palette.chrome,
        contentColor = palette.text,
        shape = MaterialTheme.shapes.large.copy(bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp), bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.widthIn(min = 64.dp))
                Slider(
                    value = shown,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let(onSeek)
                        dragging = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = palette.accent,
                        activeTrackColor = palette.accent,
                        inactiveTrackColor = palette.track,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CompositionLocalProvider(LocalContentColor provides palette.text) {
                    BarAction(Icons.AutoMirrored.Rounded.List, "Contents", onNavigation)
                    if (isText) BarAction(Icons.Rounded.FormatSize, "Style", onTypography)
                    else BarAction(Icons.Rounded.Info, "About", onTypography)
                    if (isText) BarAction(Icons.Rounded.PictureAsPdf, "Original PDF", onSwitchMode)
                    else BarAction(Icons.AutoMirrored.Rounded.Article, "Reflow text", onSwitchMode, enabled = state.canReflow)
                }
            }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val color = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.4f)
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .widthIn(min = 72.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
private fun ReaderError(message: String, palette: ReaderPalette, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Can't open this book", style = MaterialTheme.typography.titleLarge, color = palette.text)
        Spacer(Modifier.padding(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryText, textAlign = TextAlign.Center)
        Spacer(Modifier.padding(12.dp))
        Button(onClick = onBack) { Text("Back to library") }
    }
}

/** Keeps status/navigation bar icons legible against the reading background. */
@Composable
private fun SystemBarAppearance(darkContent: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(darkContent) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = darkContent
        controller?.isAppearanceLightNavigationBars = darkContent
        onDispose {
            previousStatus?.let { controller.isAppearanceLightStatusBars = it }
            previousNav?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }
}
