package app.cloven

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cloven.widget.StreakWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Inflates the widget's RemoteViews exactly as a launcher would and checks what it shows. */
@RunWith(AndroidJUnit4::class)
class StreakWidgetTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun render(streak: Int, compact: Boolean = false): View {
        var view: View? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = StreakWidget.layout(context, streak, compact).apply(context, FrameLayout(context))
        }
        return view!!
    }

    private fun texts(view: View): List<String> =
        if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else listOfNotNull((view as? TextView)?.text?.toString())

    private fun check(streak: Int, expectedDescription: String) {
        val view = render(streak)
        assertEquals(listOf("Your", "Reading Strike", streak.toString()), texts(view))
        assertNotNull(view.findViewById<ImageView>(R.id.widget_logo).drawable)
        assertEquals(expectedDescription, view.findViewById<View>(R.id.widget_root).contentDescription)
    }

    @Test fun zeroStreak() = check(0, "Your reading streak: 0 days")

    @Test fun oneDayStreak() = check(1, "Your reading streak: 1 day")

    @Test fun multiDayStreak() = check(27, "Your reading streak: 27 days")

    @Test fun labelLinesNeverWrap() {
        val view = render(3)
        for (id in listOf(R.id.widget_line_your, R.id.widget_line_title)) {
            val line = view.findViewById<TextView>(id)
            assertEquals(1, line.maxLines)
        }
        // The two label lines are separate views, stacked vertically.
        val your = view.findViewById<TextView>(R.id.widget_line_your)
        val title = view.findViewById<TextView>(R.id.widget_line_title)
        assertTrue(your.parent === title.parent)
    }

    @Test fun narrowInstanceFallsBackToCloverAndNumber() {
        val view = render(5, compact = true)
        assertEquals(listOf("5"), texts(view))
        assertNotNull(view.findViewById<ImageView>(R.id.widget_logo).drawable)
        assertEquals("Your reading streak: 5 days", view.findViewById<View>(R.id.widget_root).contentDescription)
    }

    @Test fun responsiveViewsBuildForEveryApiLevel() {
        // Builds the launcher-facing RemoteViews (size-mapped on API 31+) without throwing.
        assertNotNull(StreakWidget.buildViews(context, 2))
        assertNotNull(StreakWidget.buildViews(context, 2, widthDp = 70))
    }
}
