package com.example.multitimer.widget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.multitimer.R
import com.example.multitimer.data.BatteryStrategy
import com.example.multitimer.data.TimerModel
import com.example.multitimer.data.TimerRepository
import com.example.multitimer.data.TimerState
import java.util.Locale

class MultiTimerWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return MultiTimerRemoteViewsFactory(applicationContext)
    }
}

class MultiTimerRemoteViewsFactory(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {

    private val repository = TimerRepository.getInstance(context)
    private var timers: List<TimerModel> = emptyList()

    override fun onCreate() {
        onDataSetChanged()
    }

    override fun onDataSetChanged() {
        val identityToken = android.os.Binder.clearCallingIdentity()
        try {
            timers = repository.getAllTimers()
        } finally {
            android.os.Binder.restoreCallingIdentity(identityToken)
        }
    }

    override fun onDestroy() {
        timers = emptyList()
    }

    override fun getCount(): Int = timers.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position !in timers.indices) return null
        val timer = timers[position]
        val strategy = repository.getStrategy()

        val views = RemoteViews(context.packageName, R.layout.widget_timer_item)
        views.setTextViewText(R.id.timer_name, timer.name)

        if (strategy == BatteryStrategy.NATIVE_CHRONOMETER) {
            // Show Chronometer, hide Text & ProgressBar
            views.setViewVisibility(R.id.chronometer_view, View.VISIBLE)
            views.setViewVisibility(R.id.timer_text, View.GONE)
            views.setViewVisibility(R.id.timer_progress, View.GONE)

            views.setChronometerCountDown(R.id.chronometer_view, true)

            when (timer.state) {
                TimerState.RUNNING -> {
                    views.setChronometer(R.id.chronometer_view, timer.endRealtimeMs, null, true)
                    views.setTextViewText(R.id.timer_status_label, "RUNNING (0% Battery)")
                    views.setImageViewResource(R.id.btn_play_pause, R.drawable.ic_pause)
                }
                TimerState.PAUSED -> {
                    val stoppedBase = SystemClock.elapsedRealtime() + timer.remainingMs
                    views.setChronometer(R.id.chronometer_view, stoppedBase, null, false)
                    views.setTextViewText(R.id.timer_status_label, "PAUSED")
                    views.setImageViewResource(R.id.btn_play_pause, R.drawable.ic_play)
                }
                TimerState.FINISHED -> {
                    views.setChronometer(R.id.chronometer_view, SystemClock.elapsedRealtime(), null, false)
                    views.setTextViewText(R.id.timer_status_label, "FINISHED")
                    views.setImageViewResource(R.id.btn_play_pause, R.drawable.ic_play)
                }
                TimerState.IDLE -> {
                    val base = SystemClock.elapsedRealtime() + timer.totalDurationMs
                    views.setChronometer(R.id.chronometer_view, base, null, false)
                    views.setTextViewText(R.id.timer_status_label, "READY")
                    views.setImageViewResource(R.id.btn_play_pause, R.drawable.ic_play)
                }
            }
        } else {
            // Show Text & ProgressBar, hide Chronometer
            views.setViewVisibility(R.id.chronometer_view, View.GONE)
            views.setViewVisibility(R.id.timer_text, View.VISIBLE)
            views.setViewVisibility(R.id.timer_progress, View.VISIBLE)

            val remainingMs = when (timer.state) {
                TimerState.RUNNING -> (timer.endRealtimeMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                TimerState.PAUSED -> timer.remainingMs
                TimerState.FINISHED -> 0L
                TimerState.IDLE -> timer.totalDurationMs
            }

            views.setTextViewText(R.id.timer_text, formatMillis(remainingMs))

            val progress = if (timer.totalDurationMs > 0) {
                val elapsed = (timer.totalDurationMs - remainingMs).coerceAtLeast(0L)
                ((elapsed.toDouble() / timer.totalDurationMs) * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }
            views.setProgressBar(R.id.timer_progress, 100, progress, false)

            when (timer.state) {
                TimerState.RUNNING -> {
                    views.setTextViewText(R.id.timer_status_label, "TICKING LOOP: $progress%")
                    views.setImageViewResource(R.id.btn_play_pause, R.drawable.ic_pause)
                }
                TimerState.PAUSED -> {
                    views.setTextViewText(R.id.timer_status_label, "PAUSED AT $progress%")
                    views.setImageViewResource(R.id.btn_play_pause, R.drawable.ic_play)
                }
                TimerState.FINISHED -> {
                    views.setTextViewText(R.id.timer_status_label, "COMPLETED")
                    views.setImageViewResource(R.id.btn_play_pause, R.drawable.ic_play)
                }
                TimerState.IDLE -> {
                    views.setTextViewText(R.id.timer_status_label, "READY")
                    views.setImageViewResource(R.id.btn_play_pause, R.drawable.ic_play)
                }
            }
        }

        // Fill-in Intent for Play/Pause
        val playPauseFillIn = Intent().apply {
            action = WidgetActionReceiver.ACTION_PLAY_PAUSE
            putExtra(WidgetActionReceiver.EXTRA_TIMER_ID, timer.id)
        }
        views.setOnClickFillInIntent(R.id.btn_play_pause, playPauseFillIn)

        // Fill-in Intent for Reset
        val resetFillIn = Intent().apply {
            action = WidgetActionReceiver.ACTION_RESET
            putExtra(WidgetActionReceiver.EXTRA_TIMER_ID, timer.id)
        }
        views.setOnClickFillInIntent(R.id.btn_reset, resetFillIn)

        return views
    }

    private fun formatMillis(millis: Long): String {
        val totalSeconds = (millis + 999) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1 // Single unified layout prevents any view recycling crashes
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
