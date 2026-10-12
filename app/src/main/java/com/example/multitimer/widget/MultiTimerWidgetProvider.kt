package com.example.multitimer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.example.multitimer.R
import com.example.multitimer.data.BatteryStrategy
import com.example.multitimer.data.TimerRepository
import com.example.multitimer.data.TimerState
import com.example.multitimer.ui.MainActivity

class MultiTimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val repository = TimerRepository.getInstance(context)
        val timers = repository.getAllTimers()
        val runningCount = timers.count { it.state == TimerState.RUNNING }
        val currentStrategy = repository.getStrategy()

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_multi_timer)

            // Subtitle status
            val countText = if (runningCount == 1) "1 running" else "$runningCount running"
            views.setTextViewText(R.id.widget_subtitle, countText)

            // Strategy Button Label
            if (currentStrategy == BatteryStrategy.NATIVE_CHRONOMETER) {
                views.setTextViewText(R.id.btn_toggle_strategy, "⚡ Battery Saver")
                views.setTextColor(R.id.btn_toggle_strategy, 0xFF38BDF8.toInt()) // Cyan
            } else {
                views.setTextViewText(R.id.btn_toggle_strategy, "✨ High-Fi Loop")
                views.setTextColor(R.id.btn_toggle_strategy, 0xFF10B981.toInt()) // Emerald
            }

            // Header Tap -> Launch Main App
            val appIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val appPendingIntent = PendingIntent.getActivity(
                context,
                0,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.header_title_layout, appPendingIntent)

            // Strategy Toggle Click -> Broadcast
            val toggleIntent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = WidgetActionReceiver.ACTION_TOGGLE_STRATEGY
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context,
                101,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_toggle_strategy, togglePendingIntent)

            // Quick Add Button -> Broadcast
            val addIntent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = WidgetActionReceiver.ACTION_ADD_QUICK
            }
            val addPendingIntent = PendingIntent.getBroadcast(
                context,
                102,
                addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_add, addPendingIntent)

            // ListView Adapter Service Intent
            val serviceIntent = Intent(context, MultiTimerWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("widget://multitimer/list/$appWidgetId")
            }
            views.setRemoteAdapter(R.id.timer_list_view, serviceIntent)
            views.setEmptyView(R.id.timer_list_view, R.id.empty_view)

            // Pending Intent Template for List Items
            val itemClickIntent = Intent(context, WidgetActionReceiver::class.java)
            val itemClickPendingIntent = PendingIntent.getBroadcast(
                context,
                201,
                itemClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.timer_list_view, itemClickPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            if (ids != null && ids.isNotEmpty()) {
                onUpdate(context, appWidgetManager, ids)
            }
        }
    }
}
