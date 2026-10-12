package com.example.multitimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.multitimer.data.BatteryStrategy
import com.example.multitimer.data.TimerRepository
import com.example.multitimer.data.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimerTickService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var isLoopRunning = false

    companion object {
        private const val CHANNEL_ID = "timer_tick_service_channel"
        private const val NOTIFICATION_ID = 2001

        fun startService(context: Context) {
            val intent = Intent(context, TimerTickService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TimerTickService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification("Active Timer Loop Running"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repository = TimerRepository.getInstance(this)

        if (repository.getStrategy() != BatteryStrategy.HIGH_FIDELITY_LOOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isLoopRunning) {
            isLoopRunning = true
            startTickLoop(repository)
        }

        return START_STICKY
    }

    private fun startTickLoop(repository: TimerRepository) {
        serviceScope.launch {
            while (isActive) {
                val timers = repository.getAllTimers()
                val runningCount = timers.count { it.state == TimerState.RUNNING }

                if (runningCount == 0 || repository.getStrategy() != BatteryStrategy.HIGH_FIDELITY_LOOP) {
                    stopSelf()
                    break
                }

                // Update notification text
                val notification = buildForegroundNotification("$runningCount timer(s) running (High-Fi Mode)")
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)

                // Refresh widgets
                repository.notifyWidgets()

                delay(1000L)
            }
        }
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Multi-Timer Active Widget")
            .setContentText(statusText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Timer Tick Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used to render high-fidelity real-time home screen widget countdowns"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
