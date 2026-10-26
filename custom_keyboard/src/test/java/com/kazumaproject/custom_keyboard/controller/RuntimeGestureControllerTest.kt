package com.kazumaproject.custom_keyboard.controller

import android.app.Activity
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.flick.FlickThresholdShape
import com.kazumaproject.core.domain.flick.GestureSessionConfig
import com.kazumaproject.core.domain.flick.GestureSessionConfigSource
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RuntimeGestureControllerTest {

    @Test
    fun normalKeyLongPressUsesConfiguredTimeoutAndSnapshotsItAtDown() {
        var currentConfig = config(thresholdPx = 50f, timeoutMillis = 1_000L, revision = 1L)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val button = Button(activity)
        activity.setContentView(button)
        val events = mutableListOf<String>()
        val controller = TapLongPressInputController(
            GestureSessionConfigSource { currentConfig }
        )
        controller.attach(
            button,
            object : TapLongPressInputController.Listener {
                override fun onPress() {
                    events += "press"
                }

                override fun onTap() {
                    events += "tap"
                }

                override fun onLongPress() {
                    events += "long"
                }

                override fun onUpAfterLongPress() {
                    events += "upAfterLong"
                }

                override fun onLongPressCanceled() {
                    events += "cancelLong"
                }
            }
        )

        button.dispatch(MotionEvent.ACTION_DOWN, x = 10f, y = 10f, downTime = 1L)
        currentConfig = config(thresholdPx = 50f, timeoutMillis = 100L, revision = 2L)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(300L))
        assertFalse(events.contains("long"))
        button.dispatch(MotionEvent.ACTION_UP, x = 10f, y = 10f, downTime = 1L)
        assertTrue(events.contains("tap"))

        events.clear()
        button.dispatch(MotionEvent.ACTION_DOWN, x = 10f, y = 10f, downTime = 10L)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(99L))
        assertFalse(events.contains("long"))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(2L))
        assertTrue(events.contains("long"))
        button.dispatch(MotionEvent.ACTION_UP, x = 10f, y = 10f, downTime = 10L)
        assertEquals(listOf("press", "long", "upAfterLong"), events)
    }

    private fun context(): Context {
        return ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )
    }

    private fun config(
        thresholdPx: Float,
        timeoutMillis: Long,
        revision: Long,
        thresholdShape: FlickThresholdShape = FlickThresholdShape.Radial
    ): GestureSessionConfig {
        return GestureSessionConfig(
            settingsRevision = revision,
            flickSensitivity = 100,
            flickThresholdPx = thresholdPx,
            longPressTimeoutMillis = timeoutMillis,
            flickThresholdShape = thresholdShape
        )
    }

    private fun View.dispatch(action: Int, x: Float, y: Float, downTime: Long) {
        val event = MotionEvent.obtain(
            downTime,
            downTime + 1L,
            action,
            x,
            y,
            0
        )
        dispatchTouchEvent(event)
        event.recycle()
    }
}
