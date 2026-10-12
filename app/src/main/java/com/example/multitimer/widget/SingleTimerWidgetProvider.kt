package com.example.multitimer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.example.multitimer.R
import com.example.multitimer.data.BatteryStrategy
import com.example.multitimer.data.TimerModel
import com.example.multitimer.data.TimerRepository
import com.example.multitimer.data.TimerState
import com.example.multitimer.ui.MainActivity
import java.util.Locale

class SingleTimerWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_HERO_PLAY_PAUSE = "com.example.multitimer.ACTION_HERO_PLAY_PAUSE"
        const val ACTION_HERO_RESET = "com.example.multitimer.ACTION_HERO_RESET"
        const val ACTION_HERO_NEXT = "com.example.multitimer.ACTION_HERO_NEXT"
        const val ACTION_HERO_PREV = "com.example.multitimer.ACTION_HERO_PREV"
        private const val PREFS_HERO = "prefs_hero_widget"
        private const val KEY_INDEX_PREFIX = "hero_index_"

        fun getSelectedTimerIndex(context: Context, appWidgetId: Int): Int {
            val prefs = context.getSharedPreferences(PREFS_HERO, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_INDEX_PREFIX + appWidgetId, 0)
        }

        fun setSelectedTimerIndex(context: Context, appWidgetId: Int, index: Int) {
            val prefs = context.getSharedPreferences(PREFS_HERO, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_INDEX_PREFIX + appWidgetId, index).apply()
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val repository = TimerRepository.getInstance(context)
        val timers = repository.getAllTimers()
        val strategy = repository.getStrategy()

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_single_timer)

            if (timers.isEmpty()) {
                views.setTextViewText(R.id.hero_timer_name, "No Timers")
                views.setTextViewText(R.id.hero_sound_name, "Open app to add one")
                views.setTextViewText(R.id.hero_status_label, "EMPTY")
                appWidgetManager.updateAppWidget(appWidgetId, views)
                continue
            }

            var index = getSelectedTimerIndex(context, appWidgetId)
            if (index !in timers.indices) {
                index = 0
                setSelectedTimerIndex(context, appWidgetId, 0)
            }
            val timer = timers[index]

            views.setTextViewText(R.id.hero_timer_name, timer.name)
            if (timer.isYouTubeMusic) {
                views.setTextViewText(R.id.hero_sound_name, "▶️ " + (timer.youTubeQuery ?: timer.soundName))
            } else {
                views.setTextViewText(R.id.hero_sound_name, "🎵 " + timer.soundName)
            }

            // Tap root / title -> Launch Main App
            val appIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val appPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.hero_timer_name, appPendingIntent)

            val remainingMs = when (timer.state) {
                TimerState.RUNNING -> (timer.endRealtimeMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                TimerState.PAUSED -> timer.remainingMs
                TimerState.FINISHED -> 0L
                TimerState.IDLE -> timer.totalDurationMs
            }

            val progress = if (timer.totalDurationMs > 0) {
                val elapsed = (timer.totalDurationMs - remainingMs).coerceAtLeast(0L)
                (elapsed.toFloat() / timer.totalDurationMs).coerceIn(0f, 1f)
            } else 0f

            val isRunning = timer.state == TimerState.RUNNING

            // Render Fluid Liquid Wave Background for Hero Widget
            val wavePhase = (SystemClock.elapsedRealtime() % 3200L) / 3200f * (2 * Math.PI).toFloat()
            val waveBitmap = WidgetWaveRenderer.drawWave(
                context = context,
                widthDp = 260,
                heightDp = 180,
                progress = progress,
                isRunning = isRunning,
                isYouTube = timer.isYouTubeMusic,
                phase = wavePhase
            )
            views.setImageViewBitmap(R.id.hero_wave_bg, waveBitmap)

            // Render Circular Progress Ring with Glowing Circle Ending Bead
            val ringBitmap = WidgetRingRenderer.drawProgressRing(
                context = context,
                progress = progress,
                isRunning = isRunning,
                isYouTube = timer.isYouTubeMusic,
                sizeDp = 150,
                strokeWidthDp = 5.5f,
                beadRadiusDp = 5.5f
            )
            views.setImageViewBitmap(R.id.hero_ring_progress, ringBitmap)


            // Setup Timer Display
            if (strategy == BatteryStrategy.NATIVE_CHRONOMETER) {
                views.setViewVisibility(R.id.hero_chronometer, View.VISIBLE)
                views.setViewVisibility(R.id.hero_timer_text, View.GONE)
                views.setChronometerCountDown(R.id.hero_chronometer, true)

                when (timer.state) {
                    TimerState.RUNNING -> {
                        views.setChronometer(R.id.hero_chronometer, timer.endRealtimeMs, null, true)
                        views.setTextViewText(R.id.hero_status_label, "RUNNING (0% Battery)")
                        views.setImageViewResource(R.id.btn_hero_play_pause, R.drawable.ic_pause)
                    }
                    TimerState.PAUSED -> {
                        val stoppedBase = SystemClock.elapsedRealtime() + timer.remainingMs
                        views.setChronometer(R.id.hero_chronometer, stoppedBase, null, false)
                        views.setTextViewText(R.id.hero_status_label, "PAUSED")
                        views.setImageViewResource(R.id.btn_hero_play_pause, R.drawable.ic_play)
                    }
                    TimerState.FINISHED -> {
                        views.setChronometer(R.id.hero_chronometer, SystemClock.elapsedRealtime(), null, false)
                        views.setTextViewText(R.id.hero_status_label, "FINISHED")
                        views.setImageViewResource(R.id.btn_hero_play_pause, R.drawable.ic_play)
                    }
                    TimerState.IDLE -> {
                        val base = SystemClock.elapsedRealtime() + timer.totalDurationMs
                        views.setChronometer(R.id.hero_chronometer, base, null, false)
                        views.setTextViewText(R.id.hero_status_label, "READY")
                        views.setImageViewResource(R.id.btn_hero_play_pause, R.drawable.ic_play)
                    }
                }
            } else {
                views.setViewVisibility(R.id.hero_chronometer, View.GONE)
                views.setViewVisibility(R.id.hero_timer_text, View.VISIBLE)
                views.setTextViewText(R.id.hero_timer_text, formatMillis(remainingMs))

                when (timer.state) {
                    TimerState.RUNNING -> {
                        views.setTextViewText(R.id.hero_status_label, "TICKING LOOP")
                        views.setImageViewResource(R.id.btn_hero_play_pause, R.drawable.ic_pause)
                    }
                    TimerState.PAUSED -> {
                        views.setTextViewText(R.id.hero_status_label, "PAUSED")
                        views.setImageViewResource(R.id.btn_hero_play_pause, R.drawable.ic_play)
                    }
                    TimerState.FINISHED -> {
                        views.setTextViewText(R.id.hero_status_label, "FINISHED")
                        views.setImageViewResource(R.id.btn_hero_play_pause, R.drawable.ic_play)
                    }
                    TimerState.IDLE -> {
                        views.setTextViewText(R.id.hero_status_label, "READY")
                        views.setImageViewResource(R.id.btn_hero_play_pause, R.drawable.ic_play)
                    }
                }
            }


            // Play/Pause Action
            val playIntent = Intent(context, SingleTimerWidgetProvider::class.java).apply {
                action = ACTION_HERO_PLAY_PAUSE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(WidgetActionReceiver.EXTRA_TIMER_ID, timer.id)
            }
            val playPI = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 1,
                playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_hero_play_pause, playPI)

            // Reset Action
            val resetIntent = Intent(context, SingleTimerWidgetProvider::class.java).apply {
                action = ACTION_HERO_RESET
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(WidgetActionReceiver.EXTRA_TIMER_ID, timer.id)
            }
            val resetPI = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 2,
                resetIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_hero_reset, resetPI)

            // Next Action
            val nextIntent = Intent(context, SingleTimerWidgetProvider::class.java).apply {
                action = ACTION_HERO_NEXT
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val nextPI = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 3,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_hero_next, nextPI)

            // Prev Action
            val prevIntent = Intent(context, SingleTimerWidgetProvider::class.java).apply {
                action = ACTION_HERO_PREV
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val prevPI = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 4,
                prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_hero_prev, prevPI)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val repository = TimerRepository.getInstance(context)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val timerId = intent.getStringExtra(WidgetActionReceiver.EXTRA_TIMER_ID)

        when (intent.action) {
            ACTION_HERO_PLAY_PAUSE -> {
                if (timerId != null) {
                    val timer = repository.getTimer(timerId)
                    if (timer != null) {
                        if (timer.state == TimerState.RUNNING) {
                            repository.pauseTimer(timerId)
                        } else {
                            repository.startTimer(timerId)
                        }
                    }
                }
            }
            ACTION_HERO_RESET -> {
                if (timerId != null) {
                    repository.resetTimer(timerId)
                }
            }
            ACTION_HERO_NEXT -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val timers = repository.getAllTimers()
                    if (timers.isNotEmpty()) {
                        val current = getSelectedTimerIndex(context, appWidgetId)
                        val next = (current + 1) % timers.size
                        setSelectedTimerIndex(context, appWidgetId, next)
                        triggerUpdate(context, appWidgetId)
                    }
                }
            }
            ACTION_HERO_PREV -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val timers = repository.getAllTimers()
                    if (timers.isNotEmpty()) {
                        val current = getSelectedTimerIndex(context, appWidgetId)
                        val prev = if (current - 1 < 0) timers.size - 1 else current - 1
                        setSelectedTimerIndex(context, appWidgetId, prev)
                        triggerUpdate(context, appWidgetId)
                    }
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun triggerUpdate(context: Context, appWidgetId: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
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
}
