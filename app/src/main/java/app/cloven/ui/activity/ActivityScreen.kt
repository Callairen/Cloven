package app.cloven.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cloven.data.repo.ActivityRepository
import app.cloven.data.repo.ActivitySnapshot
import app.cloven.domain.DayActivity
import app.cloven.ui.components.CloverMark
import app.cloven.ui.components.ProgressLine
import app.cloven.ui.components.formatMinutes
import app.cloven.ui.theme.LocalClovenExtras
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.format.TextStyle
import java.util.Locale

private val BAR_MAX_HEIGHT = 80.dp

class ActivityViewModel(activity: ActivityRepository) : ViewModel() {
    val snapshot: StateFlow<ActivitySnapshot?> =
        activity.observeSnapshot().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val qualifyingMinutes: Int = (activity.qualifyingMs / 60_000L).toInt()
}

@Composable
fun ActivityScreen(vm: ActivityViewModel) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val data = snapshot
    if (data == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Reading activity", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        StreakCard(data)
        TodayCard(data, vm.qualifyingMinutes)
        WeekCard(data.week, data.dailyGoalMinutes)
        Text(
            "A day counts toward your streak after ${vm.qualifyingMinutes} minutes of active reading. " +
                "Only time with a book open on screen is counted; menus, settings and idle time are not.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StreakCard(data: ActivitySnapshot) {
    val streak = data.stats.currentStreak
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(24.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CloverMark(size = 64.dp)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("$streak", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.displaySmall)
                Text(if (streak == 1) "day in a row" else "days in a row", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Longest ${data.stats.longestStreak} · ${data.stats.qualifyingDays} reading days in total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TodayCard(data: ActivitySnapshot, qualifyingMinutes: Int) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            Text(
                "${formatMinutes(data.stats.todayMs)} of ${data.dailyGoalMinutes} min goal",
                style = MaterialTheme.typography.bodyLarge,
            )
            ProgressLine(data.goalFraction, height = 10.dp)
            val message = when {
                data.goalReached -> "Daily goal reached. Lovely."
                data.stats.todayQualified -> "Today counts toward your streak."
                else -> "Read ${qualifyingMinutes - data.todayMinutes.coerceAtMost(qualifyingMinutes)} more minutes to count today."
            }
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeekCard(week: List<DayActivity>, goalMinutes: Int) {
    val extras = LocalClovenExtras.current
    val maxMs = maxOf(goalMinutes * 60_000L, week.maxOfOrNull { it.activeMs } ?: 0L).coerceAtLeast(1L)
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                week.forEach { day ->
                    val dayName = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    val description = "$dayName: ${formatMinutes(day.activeMs)}" + if (day.qualified) ", counted" else ""
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clearAndSetSemantics { contentDescription = description },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        val fraction = (day.activeMs.toFloat() / maxMs).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .width(18.dp)
                                .height((BAR_MAX_HEIGHT * fraction).coerceAtLeast(4.dp))
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (day.qualified) extras.progressFill else extras.progressTrack),
                        )
                        Spacer(Modifier.height(6.dp))
                        // Qualifying days carry a clover glyph, so status isn't conveyed by color alone.
                        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                            if (day.qualified) CloverMark(size = 14.dp)
                        }
                        Text(dayName.take(2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
