package com.example.multitimer.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import com.example.multitimer.R
import com.example.multitimer.service.TimerAlarmReceiver
import com.example.multitimer.service.TimerTickService
import com.example.multitimer.widget.MultiTimerWidgetProvider
import org.json.JSONArray
import java.util.UUID

class TimerRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "multi_timer_prefs"
        private const val KEY_TIMERS = "saved_timers"
        private const val KEY_STRATEGY = "battery_strategy"

        @Volatile
        private var instance: TimerRepository? = null

        fun getInstance(context: Context): TimerRepository {
            return instance ?: synchronized(this) {
                instance ?: TimerRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        // Seed initial default presets if empty
        if (getAllTimers().isEmpty()) {
            val defaults = listOf(
                TimerModel(UUID.randomUUID().toString(), "Deep Work Sprint", 25 * 60 * 1000L),
                TimerModel(UUID.randomUUID().toString(), "Coffee / Tea Steep", 4 * 60 * 1000L),
                TimerModel(UUID.randomUUID().toString(), "Workout Rest", 90 * 1000L),
                TimerModel(UUID.randomUUID().toString(), "Quick Refresh", 10 * 60 * 1000L)
            )
            saveAllTimers(defaults)
        }
    }

    @Synchronized
    fun getAllTimers(): List<TimerModel> {
        val jsonString = prefs.getString(KEY_TIMERS, null) ?: return emptyList()
        val list = mutableListOf<TimerModel>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                list.add(TimerModel.fromJson(array.getJSONObject(i)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    @Synchronized
    fun getTimer(id: String): TimerModel? {
        return getAllTimers().find { it.id == id }
    }

    @Synchronized
    fun saveTimer(timer: TimerModel) {
        val current = getAllTimers().toMutableList()
        val index = current.indexOfFirst { it.id == timer.id }
        if (index >= 0) {
            current[index] = timer
        } else {
            current.add(timer)
        }
        saveAllTimers(current)
        syncState(timer)
    }

    @Synchronized
    fun deleteTimer(id: String) {
        cancelAlarm(id)
        val current = getAllTimers().filter { it.id != id }
        saveAllTimers(current)
        notifyWidgets()
        evaluateTickService()
    }

    @Synchronized
    fun startTimer(id: String) {
        val timer = getTimer(id) ?: return
        if (timer.state == TimerState.RUNNING) return

        if (timer.remainingMs <= 0) {
            timer.remainingMs = timer.totalDurationMs
        }

        timer.state = TimerState.RUNNING
        timer.endRealtimeMs = SystemClock.elapsedRealtime() + timer.remainingMs
        saveTimer(timer)

        scheduleAlarm(timer)
        evaluateTickService()
    }

    @Synchronized
    fun pauseTimer(id: String) {
        val timer = getTimer(id) ?: return
        if (timer.state != TimerState.RUNNING) return

        val nowRealtime = SystemClock.elapsedRealtime()
        val remaining = (timer.endRealtimeMs - nowRealtime).coerceAtLeast(0L)
        timer.remainingMs = remaining
        timer.state = TimerState.PAUSED
        timer.endRealtimeMs = 0L
        saveTimer(timer)

        cancelAlarm(id)
        evaluateTickService()
    }

    @Synchronized
    fun resetTimer(id: String) {
        val timer = getTimer(id) ?: return
        timer.remainingMs = timer.totalDurationMs
        timer.state = TimerState.IDLE
        timer.endRealtimeMs = 0L
        saveTimer(timer)

        cancelAlarm(id)
        evaluateTickService()
    }

    @Synchronized
    fun markFinished(id: String) {
        val timer = getTimer(id) ?: return
        timer.remainingMs = 0L
        timer.state = TimerState.FINISHED
        timer.endRealtimeMs = 0L
        saveTimer(timer)
        evaluateTickService()
    }

    fun getStrategy(): BatteryStrategy {
        val name = prefs.getString(KEY_STRATEGY, BatteryStrategy.NATIVE_CHRONOMETER.name)
        return try {
            BatteryStrategy.valueOf(name ?: BatteryStrategy.NATIVE_CHRONOMETER.name)
        } catch (e: Exception) {
            BatteryStrategy.NATIVE_CHRONOMETER
        }
    }

    fun setStrategy(strategy: BatteryStrategy) {
        prefs.edit().putString(KEY_STRATEGY, strategy.name).apply()
        notifyWidgets()
        evaluateTickService()
    }

    fun toggleStrategy(): BatteryStrategy {
        val next = if (getStrategy() == BatteryStrategy.NATIVE_CHRONOMETER) {
            BatteryStrategy.HIGH_FIDELITY_LOOP
        } else {
            BatteryStrategy.NATIVE_CHRONOMETER
        }
        setStrategy(next)
        return next
    }

    private fun saveAllTimers(timers: List<TimerModel>) {
        val array = JSONArray()
        timers.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_TIMERS, array.toString()).apply()
        notifyWidgets()
    }

    private fun syncState(timer: TimerModel) {
        notifyWidgets()
    }

    fun notifyWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, MultiTimerWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        // Request list data refresh
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.timer_list_view)

        // Trigger onUpdate to re-render header & counts
        val intent = Intent(context, MultiTimerWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        }
        context.sendBroadcast(intent)
    }

    private fun scheduleAlarm(timer: TimerModel) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAtMillis = System.currentTimeMillis() + timer.remainingMs

        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            putExtra(TimerAlarmReceiver.EXTRA_TIMER_ID, timer.id)
            putExtra(TimerAlarmReceiver.EXTRA_TIMER_NAME, timer.name)
            putExtra(TimerAlarmReceiver.EXTRA_SOUND_URI, timer.soundUri)
            putExtra(TimerAlarmReceiver.EXTRA_SOUND_NAME, timer.soundName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            timer.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelAlarm(id: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TimerAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun evaluateTickService() {
        val isHighFi = getStrategy() == BatteryStrategy.HIGH_FIDELITY_LOOP
        val hasRunningTimers = getAllTimers().any { it.state == TimerState.RUNNING }

        if (isHighFi && hasRunningTimers) {
            TimerTickService.startService(context)
        } else {
            TimerTickService.stopService(context)
        }
    }
}
