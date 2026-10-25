package app.cloven

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import app.cloven.data.prefs.AppSettings
import app.cloven.ui.ClovenRoot
import app.cloven.ui.theme.ClovenColors
import app.cloven.ui.theme.ClovenTheme
import app.cloven.ui.theme.isClovenDark

class MainActivity : ComponentActivity() {

    /** Destination requested by the widget; consumed once by the nav host. */
    private val pendingDestination = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingDestination.value = intent?.getStringExtra(EXTRA_DESTINATION)
        val container = appContainer
        setContent {
            val settings by container.preferences.appSettings.collectAsState(initial = AppSettings())
            val dark = isClovenDark(settings.theme)
            DisposableEffect(dark) {
                val scrim = if (dark) ClovenColors.ForestCharcoal.toArgb() else ClovenColors.Buttercream.toArgb()
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(scrim, scrim) { dark },
                    navigationBarStyle = SystemBarStyle.auto(scrim, scrim) { dark },
                )
                onDispose {}
            }
            ClovenTheme(darkTheme = dark) {
                ClovenRoot(
                    container = container,
                    requestedDestination = pendingDestination.value,
                    onDestinationConsumed = { pendingDestination.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_DESTINATION)?.let { pendingDestination.value = it }
    }

    override fun onStart() {
        super.onStart()
        appContainer.widgetUpdater.requestUpdate()
    }

    companion object {
        const val EXTRA_DESTINATION = "app.cloven.extra.DESTINATION"
        const val DESTINATION_ACTIVITY = "activity"
    }
}
