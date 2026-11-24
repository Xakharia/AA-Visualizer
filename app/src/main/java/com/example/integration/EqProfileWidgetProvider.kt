package com.example.integration

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.AAVisualizerApp
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EqProfileWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as? AAVisualizerApp
        val profileName = app?.container?.activeProfile?.value?.name ?: "Default"
        val deviceName = app?.container?.outputMonitor?.currentDevice?.value?.shortLabel ?: "Audio Ready"
        appWidgetIds.forEach { id ->
            updateSingleWidget(context, appWidgetManager, id, profileName, deviceName)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CYCLE_EQ) {
            val app = context.applicationContext as? AAVisualizerApp ?: return
            CoroutineScope(Dispatchers.Default).launch {
                val list = app.container.profiles.value
                if (list.isNotEmpty()) {
                    val idx = list.indexOfFirst { it.id == app.container.activeProfileId.value }
                    val next = list[(idx + 1) % list.size]
                    app.container.repository.setActiveProfileId(next.id)
                    notifyDataChanged(
                        context,
                        next.name,
                        app.container.outputMonitor.currentDevice.value.shortLabel
                    )
                }
            }
        }
    }

    companion object {
        const val ACTION_CYCLE_EQ = "com.example.ACTION_CYCLE_EQ_WIDGET"

        fun notifyDataChanged(context: Context, profileName: String, deviceShort: String) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, EqProfileWidgetProvider::class.java))
            ids.forEach { id ->
                updateSingleWidget(context, mgr, id, profileName, deviceShort)
            }
        }

        private fun updateSingleWidget(
            context: Context,
            mgr: AppWidgetManager,
            widgetId: Int,
            profileName: String,
            deviceShort: String
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_eq_profile)
            views.setTextViewText(R.id.widget_profile_name, profileName)
            views.setTextViewText(R.id.widget_subtitle, "Output: $deviceShort")

            val cycleIntent = Intent(context, EqProfileWidgetProvider::class.java).apply {
                action = ACTION_CYCLE_EQ
            }
            val cyclePending = PendingIntent.getBroadcast(
                context,
                10,
                cycleIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_btn_cycle, cyclePending)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_START_TAB, "equalizer")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context,
                11,
                openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_btn_open, openPending)
            views.setOnClickPendingIntent(R.id.widget_root, openPending)

            mgr.updateAppWidget(widgetId, views)
        }
    }
}
