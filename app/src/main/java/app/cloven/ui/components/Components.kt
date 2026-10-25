package app.cloven.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloven.R
import app.cloven.ui.theme.Literata
import app.cloven.ui.theme.LocalClovenExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The standalone clover (logo.png, see docs/LOGO.md), drawn in its original colours.
 * Pass [tint] only for a single-colour silhouette; that works because the artwork has a
 * transparent background (tinting an opaque image would fill its whole square).
 */
@Composable
fun CloverMark(modifier: Modifier = Modifier, size: Dp = 28.dp, tint: Color? = null, contentDescription: String? = null) {
    val painter = painterResource(R.drawable.logo)
    if (tint != null) {
        Icon(painter, contentDescription, modifier.size(size), tint = tint)
    } else {
        Image(painter, contentDescription, modifier.size(size), contentScale = ContentScale.Fit)
    }
}

/** Rounded progress bar with an accessible range description. */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier, height: Dp = 6.dp, fill: Color? = null, track: Color? = null) {
    val extras = LocalClovenExtras.current
    val value = progress.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track ?: extras.progressTrack)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) },
    ) {
        if (value > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value)
                    .clip(CircleShape)
                    .background(fill ?: extras.progressFill),
            )
        }
    }
}

private object CoverCache {
    private val cache = LruCache<String, ImageBitmap>(64)

    fun load(file: File): ImageBitmap? {
        val key = file.path + ":" + file.lastModified()
        cache.get(key)?.let { return it }
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.path)?.asImageBitmap()?.also { cache.put(key, it) }
    }
}

/**
 * Book cover: the rendered first page when available, otherwise a clearly generated
 * placeholder (title on a sage card with the clover mark) that can't be mistaken for
 * the book's real cover.
 */
@Composable
fun BookCover(title: String, coverFile: File?, modifier: Modifier = Modifier, corner: Dp = 14.dp) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, coverFile?.path) {
        value = coverFile?.let { withContext(Dispatchers.IO) { CoverCache.load(it) } }
    }
    val shape = RoundedCornerShape(corner)
    val extras = LocalClovenExtras.current
    Box(modifier.clip(shape).background(extras.coverFallback)) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                Modifier.fillMaxSize().padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = title,
                    fontFamily = Literata,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = extras.coverFallbackText,
                    textAlign = TextAlign.Center,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                CloverMark(size = 18.dp, tint = extras.coverFallbackText.copy(alpha = 0.55f))
            }
        }
    }
}

/** Centered message used for empty, no-results and error states. */
@Composable
fun MessageState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { CloverMark(size = 40.dp) }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(6.dp))
            action()
        }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024L -> String.format(java.util.Locale.US, "%d KB", bytes / 1024)
    else -> "$bytes B"
}

fun formatMinutes(ms: Long): String {
    val minutes = ms / 60_000L
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}
