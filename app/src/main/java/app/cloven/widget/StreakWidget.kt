package app.cloven.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
 * Compact home-screen widget: the clover mark and the current streak — nothing else.
 *
 * Implemented with plain RemoteViews so each update renders synchronously inside the update
 * broadcast. (A Glance version was tried first; after a reboot its WorkManager-backed render
 * job could sit in JobScheduler for minutes, leaving a loading spinner on the home screen.)
 * The streak comes from the same Room session data as the Reading Activity screen.
 */
object StreakWidget {

    /** Widgets at least this wide (and wider than tall) show the clover beside the number. */
    private const val WIDE_MIN_WIDTH_DP = 100

    fun contentDescription(streak: Int): String =
        if (streak == 1) "Cloven reading streak: 1 day" else "Cloven reading streak: $streak days"

    fun buildViews(context: Context, streak: Int, options: Bundle?): RemoteViews {
        val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val maxHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        val wide = minWidth >= WIDE_MIN_WIDTH_DP && (maxHeight == 0 || minWidth > maxHeight * 1.3f)
        val layout = if (wide) R.layout.widget_streak_wide else R.layout.widget_streak
        return RemoteViews(context.packageName, layout).apply {
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
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, streak, manager.getAppWidgetOptions(id))) }
    }
}

class StreakWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

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
