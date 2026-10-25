package app.cloven

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cloven.widget.StreakWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Inflates the widget's RemoteViews exactly as a launcher would and checks what it shows. */
@RunWith(AndroidJUnit4::class)
class StreakWidgetTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun render(streak: Int, minWidthDp: Int, maxHeightDp: Int): View {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minWidthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, maxHeightDp)
        }
        var view: View? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = StreakWidget.buildViews(context, streak, options).apply(context, FrameLayout(context))
        }
        return view!!
    }

    private fun texts(view: View): List<String> =
        if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else listOfNotNull((view as? TextView)?.text?.toString())

    private fun check(streak: Int, view: View, expectedDescription: String) {
        // Exactly one piece of text — the streak — plus the clover image. Nothing else.
        assertEquals(listOf(streak.toString()), texts(view))
        assertNotNull(view.findViewById<ImageView>(R.id.widget_logo).drawable)
        assertEquals(expectedDescription, view.findViewById<View>(R.id.widget_root).contentDescription)
    }

    @Test fun zeroStreakCompact() = check(0, render(0, 56, 56), "Cloven reading streak: 0 days")

    @Test fun oneDayStreak() = check(1, render(1, 80, 80), "Cloven reading streak: 1 day")

    @Test fun multiDayStreakWideUsesHorizontalLayout() {
        val view = render(27, 180, 70)
        check(27, view, "Cloven reading streak: 27 days")
        assertEquals(LinearLayout.HORIZONTAL, (view as LinearLayout).orientation)
    }

    @Test fun compactUsesVerticalLayout() =
        assertEquals(LinearLayout.VERTICAL, (render(4, 60, 60) as LinearLayout).orientation)
}
