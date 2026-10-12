package com.example.multitimer.util

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.FrameLayout
import android.widget.RemoteViews
import com.example.multitimer.R
import com.example.multitimer.data.TimerRepository
import com.example.multitimer.widget.MultiTimerWidgetProvider
import com.example.multitimer.widget.MultiTimerWidgetService
import com.example.multitimer.widget.SingleTimerWidgetProvider
import java.io.PrintWriter
import java.io.StringWriter

data class DiagnosticResult(
    val title: String,
    val success: Boolean,
    val details: String
)

object WidgetDiagnostics {

    fun runDiagnostics(context: Context): List<DiagnosticResult> {
        val results = mutableListOf<DiagnosticResult>()

        // Test 1: Inflate Multi-Timer List Widget Layout
        results.add(testInflation(context, "Multi-Timer Widget Layout (widget_multi_timer.xml)", R.layout.widget_multi_timer))

        // Test 2: Inflate Single Timer Hero Card Layout
        results.add(testInflation(context, "Hero Timer Card Layout (widget_single_timer.xml)", R.layout.widget_single_timer))

        // Test 3: Inflate Timer List Item Layout
        results.add(testInflation(context, "Timer List Item Layout (widget_timer_item.xml)", R.layout.widget_timer_item))

        // Test 4: Check MultiTimerWidgetService Manifest Export & Permissions
        results.add(checkWidgetService(context))

        // Test 5: Check Active Widget Instances on Home Screen
        results.add(checkActiveWidgets(context))

        return results
    }

    private fun testInflation(context: Context, label: String, layoutResId: Int): DiagnosticResult {
        return try {
            val remoteViews = RemoteViews(context.packageName, layoutResId)
            val parent = FrameLayout(context)
            val inflatedView = remoteViews.apply(context, parent)
            if (inflatedView != null) {
                DiagnosticResult(label, true, "PASS: Inflated successfully without RemoteViews exceptions.")
            } else {
                DiagnosticResult(label, false, "FAIL: Inflater returned null view.")
            }
        } catch (t: Throwable) {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            DiagnosticResult(label, false, "EXCEPTION: ${t.javaClass.simpleName}: ${t.message}\n$sw")
        }
    }

    private fun checkWidgetService(context: Context): DiagnosticResult {
        return try {
            val intent = Intent(context, MultiTimerWidgetService::class.java)
            val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveService(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveService(intent, 0)
            }

            if (resolveInfo?.serviceInfo == null) {
                DiagnosticResult("MultiTimerWidgetService Registration", false, "FAIL: Service not registered in AndroidManifest.xml!")
            } else {
                val serviceInfo = resolveInfo.serviceInfo
                val isExported = serviceInfo.exported
                val perm = serviceInfo.permission

                if (!isExported) {
                    DiagnosticResult(
                        "MultiTimerWidgetService Registration",
                        false,
                        "FAIL: android:exported is FALSE! External launchers (OnePlus Launcher) cannot bind to ListView adapter."
                    )
                } else if (perm != "android.permission.BIND_REMOTEVIEWS") {
                    DiagnosticResult(
                        "MultiTimerWidgetService Registration",
                        false,
                        "WARN: Service permission is '$perm', expected 'android.permission.BIND_REMOTEVIEWS'."
                    )
                } else {
                    DiagnosticResult(
                        "MultiTimerWidgetService Registration",
                        true,
                        "PASS: Service exported=true with permission android.permission.BIND_REMOTEVIEWS."
                    )
                }
            }
        } catch (t: Throwable) {
            DiagnosticResult("MultiTimerWidgetService Registration", false, "EXCEPTION: ${t.message}")
        }
    }

    private fun checkActiveWidgets(context: Context): DiagnosticResult {
        return try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val listIds = appWidgetManager.getAppWidgetIds(ComponentName(context, MultiTimerWidgetProvider::class.java))
            val heroIds = appWidgetManager.getAppWidgetIds(ComponentName(context, SingleTimerWidgetProvider::class.java))

            val sb = StringBuilder()
            sb.append("Multi-Timer List Widgets on Home Screen: ${listIds.size} (IDs: ${listIds.joinToString()})\n")
            sb.append("Timer Hero Cards on Home Screen: ${heroIds.size} (IDs: ${heroIds.joinToString()})\n")

            val total = listIds.size + heroIds.size
            if (total == 0) {
                sb.append("\n⚠️ No widgets placed on home screen yet. Long-press home screen -> Widgets -> Multi-Timer to add one.")
            } else {
                sb.append("\n✅ Widgets detected by system AppWidgetManager.")
            }

            DiagnosticResult("Home Screen Widgets Query", true, sb.toString())
        } catch (t: Throwable) {
            DiagnosticResult("Home Screen Widgets Query", false, "EXCEPTION: ${t.message}")
        }
    }

    fun generateFormattedReport(context: Context): String {
        val results = runDiagnostics(context)
        val sb = StringBuilder()
        sb.append("=== MULTI-TIMER WIDGET DIAGNOSTICS REPORT ===\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
        sb.append("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        sb.append("App Version: 1.0.6 (Code 7)\n\n")

        var allPass = true
        for (r in results) {
            val icon = if (r.success) "✅" else "❌"
            if (!r.success) allPass = false
            sb.append("$icon ${r.title}\n")
            sb.append("   ${r.details.replace("\n", "\n   ")}\n\n")
        }

        sb.append("OVERALL STATUS: ")
        sb.append(if (allPass) "ALL TESTS PASSED ✅" else "ISSUES DETECTED ❌")
        sb.append("\n=============================================")
        return sb.toString()
    }
}
