package com.example.kronos.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.example.kronos.MainActivity
import com.example.kronos.R
import com.example.kronos.data.SettingsStore
import com.example.kronos.data.ThemeMode
import com.example.kronos.data.local.DatabaseProvider
import com.example.kronos.lib.currentRotationWeek
import com.example.kronos.lib.parseUserColor
import com.example.kronos.lib.formatDisplayTime
import com.example.kronos.notifications.AlarmManagerGateway
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TimetableWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAsync(context.applicationContext, ids)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateAsync(context.applicationContext, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == actionRefresh(context) || intent.action == actionAlarm(context)) {
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            updateAsync(
                context.applicationContext,
                ids,
                if (intent.action == actionRefresh(context)) 200 else 0
            )
        }
    }

    override fun onDisabled(context: Context) {
        AlarmManagerGateway(context).cancelWidget(WIDGET_ALARM_REQUEST_CODE)
    }

    private fun updateAsync(context: Context, ids: IntArray?, delayMillis: Long = 0) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (delayMillis > 0) delay(delayMillis)
                val manager = AppWidgetManager.getInstance(context)
                val widgetIds = ids ?: manager.getAppWidgetIds(ComponentName(context, javaClass))
                if (widgetIds.isEmpty()) return@launch
                val database = DatabaseProvider.get(context)
                val settings = SettingsStore(context)
                val clock = Clock.systemDefaultZone()
                val today = LocalDate.now(clock)
                val week = currentRotationWeek(today, settings.semesterStart, settings.rotationLength)
                val sessions = database.classSessionDao().getAllWithSubjects().filter {
                    it.dayOfWeek == today.dayOfWeek.value &&
                        (it.weekNumber == 0 || it.weekNumber == week)
                }
                val holiday = database.holidayDao().getAll().firstOrNull {
                    !today.isBefore(it.startDate) && !today.isAfter(it.endDate)
                }
                widgetIds.forEach { widgetId ->
                    val options = manager.getAppWidgetOptions(widgetId)
                    val maxRows = rowsForHeight(options.getInt(
                        AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110
                    ))
                    val content = buildWidgetContent(sessions, holiday?.name, clock, maxRows)
                    manager.updateAppWidget(widgetId, render(context, content, today, week, settings))
                }
                AlarmManagerGateway(context).scheduleWidget(
                    WIDGET_ALARM_REQUEST_CODE,
                    nextWidgetBoundary(sessions, clock).atZone(clock.zone).toInstant().toEpochMilli()
                )
            } finally {
                pending.finish()
            }
        }
    }

    private fun render(
        context: Context,
        content: WidgetContent,
        date: LocalDate,
        week: Int,
        settings: SettingsStore
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_timetable)
        val dark = settings.themeMode == ThemeMode.DARK ||
            (settings.themeMode == ThemeMode.AUTO_BATTERY &&
                (context.resources.configuration.uiMode and 0x30) == 0x20)
        val background = if (settings.pureBlack) {
            Color.BLACK
        } else if (dark) {
            context.getColor(R.color.widget_dark_background)
        } else {
            context.getColor(R.color.widget_light_background)
        }
        val foreground = if (dark || settings.pureBlack) {
            if (settings.pureBlack) Color.WHITE else context.getColor(R.color.widget_dark_primary)
        } else {
            context.getColor(R.color.widget_light_primary)
        }
        val secondary = if (dark || settings.pureBlack) {
            if (settings.pureBlack) Color.rgb(190, 185, 195) else context.getColor(R.color.widget_dark_secondary)
        } else {
            context.getColor(R.color.widget_light_secondary)
        }
        val backgroundResource = if (settings.pureBlack) {
            R.drawable.widget_background_black
        } else if (dark) {
            R.drawable.widget_background_dark
        } else {
            R.drawable.widget_background_light
        }
        views.setInt(R.id.widgetRoot, "setBackgroundResource", backgroundResource)
        views.setTextColor(
            R.id.widgetHeader,
            if (dark || settings.pureBlack) {
                context.getColor(R.color.widget_dark_accent)
            } else {
                context.getColor(R.color.widget_light_accent)
            }
        )
        views.setTextColor(R.id.widgetWeek, secondary)
        views.setTextColor(R.id.widgetEmpty, secondary)
        views.setTextColor(R.id.widgetHoliday, secondary)
        views.setTextColor(R.id.widgetMore, secondary)
        views.setTextViewText(
            R.id.widgetHeader,
            DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(date)
        )
        views.setTextViewText(R.id.widgetWeek, "Week $week")
        val rowIds = intArrayOf(R.id.widgetRow1, R.id.widgetRow2, R.id.widgetRow3, R.id.widgetRow4, R.id.widgetRow5)
        val markerIds = intArrayOf(R.id.widgetMarker1, R.id.widgetMarker2, R.id.widgetMarker3, R.id.widgetMarker4, R.id.widgetMarker5)
        val timeIds = intArrayOf(R.id.widgetTime1, R.id.widgetTime2, R.id.widgetTime3, R.id.widgetTime4, R.id.widgetTime5)
        val endTimeIds = intArrayOf(R.id.widgetEndTime1, R.id.widgetEndTime2, R.id.widgetEndTime3, R.id.widgetEndTime4, R.id.widgetEndTime5)
        val subjectIds = intArrayOf(R.id.widgetSubject1, R.id.widgetSubject2, R.id.widgetSubject3, R.id.widgetSubject4, R.id.widgetSubject5)
        val roomIds = intArrayOf(R.id.widgetRoom1, R.id.widgetRoom2, R.id.widgetRoom3, R.id.widgetRoom4, R.id.widgetRoom5)
        rowIds.forEach { views.setViewVisibility(it, View.GONE) }
        views.setViewVisibility(R.id.widgetEmpty, View.GONE)
        views.setViewVisibility(R.id.widgetHoliday, View.GONE)
        views.setViewVisibility(R.id.widgetMore, View.GONE)
        if (content.holidayName != null) {
            views.setViewVisibility(R.id.widgetHoliday, View.VISIBLE)
            views.setTextColor(R.id.widgetHoliday, secondary)
            views.setTextViewText(R.id.widgetHoliday, content.holidayName)
        } else if (content.empty) {
            views.setViewVisibility(R.id.widgetEmpty, View.VISIBLE)
            views.setTextColor(R.id.widgetEmpty, secondary)
            views.setTextViewText(R.id.widgetEmpty, context.getString(R.string.widget_no_classes))
        } else {
            content.rows.forEachIndexed { index, row ->
                val session = row.session
                views.setViewVisibility(rowIds[index], View.VISIBLE)
                val subjectColor = parseUserColor(session.subjectColor, Color.GRAY)
                views.setTextViewText(markerIds[index], "●")
                views.setTextColor(
                    markerIds[index],
                    visibleMarkerColor(subjectColor, dark || settings.pureBlack)
                )
                val primaryText = if (row.ended && !row.highlighted) withAlpha(foreground, 115) else foreground
                val secondaryText = if (row.ended && !row.highlighted) withAlpha(secondary, 115) else secondary
                views.setTextColor(timeIds[index], primaryText)
                views.setTextColor(endTimeIds[index], if (row.highlighted) subjectColor else secondaryText)
                views.setTextColor(subjectIds[index], primaryText)
                views.setTextColor(roomIds[index], secondaryText)
                val timeFormat = settings.timeFormat
                views.setTextViewText(
                    timeIds[index],
                    formatDisplayTime(LocalTime.parse(session.startTime), timeFormat)
                )
                views.setTextViewText(
                    endTimeIds[index],
                    formatDisplayTime(LocalTime.parse(session.endTime), timeFormat)
                )
                views.setTextViewText(subjectIds[index], session.subjectName)
                views.setTextViewText(roomIds[index], session.room.orEmpty())
                views.setInt(
                    rowIds[index],
                    "setBackgroundResource",
                    rowBorderBackground(dark, settings.pureBlack)
                )
            }

            if (content.moreCount > 0) {
                views.setViewVisibility(R.id.widgetMore, View.VISIBLE)
                views.setTextColor(R.id.widgetMore, secondary)
                views.setTextViewText(R.id.widgetMore, "+${content.moreCount} more")
            }
        }
        val launch = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(MainActivity.EXTRA_REMINDER_DAY, date.dayOfWeek.value)
        }
        views.setOnClickPendingIntent(
            R.id.widgetRoot,
            android.app.PendingIntent.getActivity(
                context, 8101, launch,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        )
        return views
    }

    private fun rowsForHeight(heightDp: Int): Int =
        ((heightDp - 58) / 46).coerceIn(1, 5)

    private fun visibleMarkerColor(color: Int, darkBackground: Boolean): Int {
        val luminance = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
        return when {
            darkBackground && luminance < 45 -> Color.rgb(130, 130, 130)
            !darkBackground && luminance > 235 -> Color.rgb(150, 150, 150)
            else -> color
        }
    }

    private fun rowBorderBackground(
        dark: Boolean,
        pureBlack: Boolean
    ): Int = when {
        pureBlack -> R.drawable.widget_row_border_black
        dark -> R.drawable.widget_row_border_dark
        else -> R.drawable.widget_row_border_light
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    companion object {
        private const val ACTION_REFRESH_SUFFIX = ".action.WIDGET_REFRESH"
        private const val ACTION_ALARM_SUFFIX = ".action.WIDGET_ALARM"
        const val WIDGET_ALARM_REQUEST_CODE = Int.MIN_VALUE + 8101

        fun actionRefresh(context: Context): String =
            context.packageName + ACTION_REFRESH_SUFFIX

        fun actionAlarm(context: Context): String =
            context.packageName + ACTION_ALARM_SUFFIX

        fun requestUpdate(context: Context) {
            val component = ComponentName(context, TimetableWidgetProvider::class.java)
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(component)
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, TimetableWidgetProvider::class.java)
                    .setAction(actionRefresh(context))
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }
}
