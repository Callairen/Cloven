package app.cloven.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import app.cloven.MainActivity
import app.cloven.R
import app.cloven.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Home-screen widget: the standalone clover (logo.png), a fixed two-line label
 * ("Your" / "Reading Strike") and the current streak number.
 *
 * Implemented with plain RemoteViews so each update renders synchronously inside the update
 * broadcast. (A Glance version was tried first; after a reboot its WorkManager-backed render
 * job could sit in JobScheduler for minutes, leaving a loading spinner on the home screen.)
 * The streak comes from the same Room session data as the Reading Activity screen.
 */
object StreakWidget {

    /** Narrower instances (e.g. a 1×1 widget placed by an older version) use the compact layout. */
    const val LABEL_MIN_WIDTH_DP = 170

    fun contentDescription(streak: Int): String =
        if (streak == 1) "Your reading streak: 1 day" else "Your reading streak: $streak days"

    /**
     * On Android 12+ both layouts are handed to the launcher keyed by size, and the launcher picks
     * the one that fits the widget's real size (no dependency on size options, which can still be
     * empty right after boot). Older versions choose from [widthDp]; 0 (unknown) gets the full layout.
     */
    fun buildViews(context: Context, streak: Int, widthDp: Int = 0): RemoteViews =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RemoteViews(
                mapOf(
                    SizeF(40f, 40f) to layout(context, streak, compact = true),
                    SizeF(LABEL_MIN_WIDTH_DP.toFloat(), 40f) to layout(context, streak, compact = false),
                ),
            )
        } else {
            layout(context, streak, compact = widthDp in 1 until LABEL_MIN_WIDTH_DP)
        }

    /** One concrete layout: the full "Your / Reading Strike" row, or the compact clover-and-number. */
    fun layout(context: Context, streak: Int, compact: Boolean): RemoteViews {
        val id = if (compact) R.layout.widget_streak_compact else R.layout.widget_streak
        return RemoteViews(context.packageName, id).apply {
            setTextViewText(R.id.widget_streak, streak.toString())
            setContentDescription(R.id.widget_root, contentDescription(streak))
            setOnClickPendingIntent(R.id.widget_root, openActivityIntent(context))
        }
    }

    private fun openActivityIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Pushes [streak] to every placed Cloven widget. */
    fun pushToAll(context: Context, streak: Int) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, StreakWidgetReceiver::class.java))
        ids.forEach { id ->
            val width = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            manager.updateAppWidget(id, buildViews(context, streak, width))
        }
    }
}

class StreakWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    /** Resizing can cross the compact/label threshold. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        refresh(context)

    /** Reads the streak from Room off the main thread while keeping the broadcast alive. */
    private fun refresh(context: Context) {
        val pending = goAsync()
        val container = context.appContainer
        container.appScope.launch {
            try {
                val streak = runCatching { container.activity.currentStreak() }.getOrDefault(0)
                StreakWidget.pushToAll(context, streak)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Time zone or clock changes can move "today", which can change the streak. */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.appContainer.widgetUpdater.requestUpdate()
    }
}

/**
 * Keeps placed widgets in sync while the app process is alive: it follows the live streak
 * Flow (so session writes and midnight roll-overs show up immediately) and coalesces explicit
 * refresh requests so bursts never cause more than one update per [minIntervalMs].
 */
class WidgetUpdater(
    private val context: Context,
    private val scope: CoroutineScope,
    private val currentStreak: suspend () -> Int,
    private val minIntervalMs: Long = 2_000L,
) {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        scope.launch {
            requests.collect {
                runCatching { StreakWidget.pushToAll(context, currentStreak()) }
                delay(minIntervalMs)
            }
        }
    }

    fun requestUpdate() {
        requests.tryEmit(Unit)
    }
}
