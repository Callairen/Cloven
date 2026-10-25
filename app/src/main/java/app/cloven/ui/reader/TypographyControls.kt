package app.cloven.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloven.data.prefs.ReaderAlignment
import app.cloven.data.prefs.ReaderBackground
import app.cloven.data.prefs.ReaderFont
import app.cloven.data.prefs.ReaderPreferences
import app.cloven.ui.theme.family
import app.cloven.ui.theme.readerPalette
import kotlin.math.roundToInt

/** Typography and background controls for reflowable text; used by the reader and Settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypographyControls(prefs: ReaderPreferences, onChange: (ReaderPreferences) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Text size", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            OutlinedIconButton(
                onClick = { onChange(prefs.copy(fontSizeSp = prefs.fontSizeSp - 1)) },
                enabled = prefs.fontSizeSp > ReaderPreferences.FONT_SIZE_RANGE.first,
            ) { Icon(Icons.Rounded.Remove, contentDescription = "Decrease text size") }
            Text(
                "${prefs.fontSizeSp}",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 44.dp).semantics { contentDescription = "Text size ${prefs.fontSizeSp}" },
            )
            OutlinedIconButton(
                onClick = { onChange(prefs.copy(fontSizeSp = prefs.fontSizeSp + 1)) },
                enabled = prefs.fontSizeSp < ReaderPreferences.FONT_SIZE_RANGE.last,
            ) { Icon(Icons.Rounded.Add, contentDescription = "Increase text size") }
        }

        Text("Typeface", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ReaderFont.entries.forEach { font ->
                FilterChip(
                    selected = prefs.font == font,
                    onClick = { onChange(prefs.copy(font = font)) },
                    label = { Text(font.label, fontFamily = font.family(), fontSize = 15.sp) },
                    shape = RoundedCornerShape(14.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer),
                )
            }
        }
        Text(
            "Tinos is metrically compatible with Times New Roman.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bold text", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Switch(checked = prefs.bold, onCheckedChange = { onChange(prefs.copy(bold = it)) })
        }

        LabeledSlider(
            label = "Line spacing",
            valueText = String.format(java.util.Locale.US, "%.1f×", prefs.lineHeight),
            value = prefs.lineHeight,
            range = ReaderPreferences.LINE_HEIGHT_MIN..ReaderPreferences.LINE_HEIGHT_MAX,
            steps = 10,
            onChange = { onChange(prefs.copy(lineHeight = (it * 10).roundToInt() / 10f)) },
        )
        LabeledSlider(
            label = "Paragraph spacing",
            valueText = String.format(java.util.Locale.US, "%.1f", prefs.paragraphSpacing),
            value = prefs.paragraphSpacing,
            range = ReaderPreferences.PARAGRAPH_MIN..ReaderPreferences.PARAGRAPH_MAX,
            steps = 9,
            onChange = { onChange(prefs.copy(paragraphSpacing = (it * 5).roundToInt() / 5f)) },
        )
        LabeledSlider(
            label = "Margins",
            valueText = "${prefs.marginDp} dp",
            value = prefs.marginDp.toFloat(),
            range = ReaderPreferences.MARGIN_RANGE.first.toFloat()..ReaderPreferences.MARGIN_RANGE.last.toFloat(),
            steps = (ReaderPreferences.MARGIN_RANGE.last - ReaderPreferences.MARGIN_RANGE.first) / 4 - 1,
            onChange = { onChange(prefs.copy(marginDp = ((it / 4).roundToInt() * 4))) },
        )

        Text("Alignment", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ReaderAlignment.entries.forEachIndexed { i, alignment ->
                SegmentedButton(
                    selected = prefs.alignment == alignment,
                    onClick = { onChange(prefs.copy(alignment = alignment)) },
                    shape = SegmentedButtonDefaults.itemShape(i, ReaderAlignment.entries.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) { Text(alignment.label) }
            }
        }

        Text("Background", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ReaderBackground.entries.forEach { background ->
                BackgroundSwatch(background, prefs.background == background) { onChange(prefs.copy(background = background)) }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.secondary,
            ),
            modifier = Modifier.semantics { contentDescription = "$label $valueText" },
        )
    }
}

@Composable
private fun BackgroundSwatch(background: ReaderBackground, selected: Boolean, onClick: () -> Unit) {
    val palette = readerPalette(background)
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(palette.background)
                .border(
                    BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = palette.text, modifier = Modifier.size(20.dp))
            else Text("Aa", color = palette.text, fontSize = 13.sp)
        }
        Spacer(Modifier.width(4.dp))
        Text(background.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
