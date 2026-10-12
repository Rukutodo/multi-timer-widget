package com.example.multitimer.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.multitimer.data.BatteryStrategy
import com.example.multitimer.data.TimerModel
import com.example.multitimer.data.TimerRepository
import com.example.multitimer.data.TimerState
import java.util.UUID

class WidgetActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.multitimer.ACTION_PLAY_PAUSE"
        const val ACTION_RESET = "com.example.multitimer.ACTION_RESET"
        const val ACTION_TOGGLE_STRATEGY = "com.example.multitimer.ACTION_TOGGLE_STRATEGY"
        const val ACTION_ADD_QUICK = "com.example.multitimer.ACTION_ADD_QUICK"

        const val EXTRA_TIMER_ID = "extra_timer_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val repository = TimerRepository.getInstance(context)

        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID) ?: return
                val timer = repository.getTimer(timerId) ?: return

                if (timer.state == TimerState.RUNNING) {
                    repository.pauseTimer(timerId)
                } else {
                    repository.startTimer(timerId)
                }
            }

            ACTION_RESET -> {
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID) ?: return
                repository.resetTimer(timerId)
            }

            ACTION_TOGGLE_STRATEGY -> {
                val nextStrategy = repository.toggleStrategy()
                val label = if (nextStrategy == BatteryStrategy.NATIVE_CHRONOMETER) {
                    "Switched to: ⚡ Battery Saver (Native Chronometer)"
                } else {
                    "Switched to: ✨ High-Fi Loop (Progress Bar)"
                }
                Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
            }

            ACTION_ADD_QUICK -> {
                val newTimer = TimerModel(
                    id = UUID.randomUUID().toString(),
                    name = "Quick Timer",
                    totalDurationMs = 5 * 60 * 1000L
                )
                repository.saveTimer(newTimer)
                Toast.makeText(context, "Added 'Quick Timer (5m)'", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
