package com.example.multitimer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.multitimer.R
import com.example.multitimer.data.TimerRepository
import com.example.multitimer.ui.MainActivity

class TimerAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_TIMER_ID = "extra_timer_id"
        const val EXTRA_TIMER_NAME = "extra_timer_name"
        private const val CHANNEL_ID = "timer_alarm_channel"
        private const val CHANNEL_NAME = "Timer Alarms"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val timerId = intent.getStringExtra(EXTRA_TIMER_ID) ?: return
        val timerName = intent.getStringExtra(EXTRA_TIMER_NAME) ?: "Timer"

        val repository = TimerRepository.getInstance(context)
        repository.markFinished(timerId)
        repository.notifyWidgets()

        triggerAlert(context, timerName, timerId.hashCode())
    }

    private fun triggerAlert(context: Context, timerName: String, notificationId: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when a timer finishes"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tap notification to open main app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Timer Finished!")
            .setContentText("'$timerName' has reached 00:00.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)

        // Direct hardware vibration
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), -1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 400, 200, 400, 200, 400), -1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
