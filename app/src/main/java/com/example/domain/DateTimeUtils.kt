package com.example.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeUtils {

    private fun isoFormatter(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun time12hFormatter(): SimpleDateFormat =
        SimpleDateFormat("h:mm a", Locale.getDefault())

    private fun displayDateFormatter(): SimpleDateFormat =
        SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    private fun monthYearFormatter(): SimpleDateFormat =
        SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    fun todayIso(nowMillis: Long = System.currentTimeMillis()): String =
        isoFormatter().format(Date(nowMillis))

    fun dateIsoFromMillis(millis: Long): String =
        isoFormatter().format(Date(millis))

    fun parseIsoToCalendar(dateIso: String): Calendar {
        val parsed = runCatching { isoFormatter().parse(dateIso) }.getOrNull()
        return Calendar.getInstance().apply {
            if (parsed != null) time = parsed
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    fun formatTime(millis: Long): String =
        time12hFormatter().format(Date(millis))

    fun formatMinutesOfDay(minutesOfDay: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, (minutesOfDay / 60).coerceIn(0, 23))
            set(Calendar.MINUTE, (minutesOfDay % 60).coerceIn(0, 59))
        }
        return time12hFormatter().format(cal.time)
    }

    fun formatDisplayDate(dateIso: String): String {
        val cal = parseIsoToCalendar(dateIso)
        return displayDateFormatter().format(cal.time)
    }

    fun formatMonthYear(year: Int, monthZeroBased: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return monthYearFormatter().format(cal.time)
    }

    /**
     * Resolves the target bedtime timestamp in millis for a given dateIso and targetBedtimeMinutes.
     * If targetBedtimeMinutes is in early morning (00:00 - 05:59), treats it as the night ending that day (next calendar morning).
     */
    fun bedtimeMillisForDate(dateIso: String, targetBedtimeMinutes: Int): Long {
        val cal = parseIsoToCalendar(dateIso)
        val hour = (targetBedtimeMinutes / 60).coerceIn(0, 23)
        val minute = (targetBedtimeMinutes % 60).coerceIn(0, 59)
        if (hour < 6) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun combineDateAndTime(dateIso: String, hourOfDay: Int, minute: Int): Long {
        val cal = parseIsoToCalendar(dateIso)
        cal.set(Calendar.HOUR_OF_DAY, hourOfDay.coerceIn(0, 23))
        cal.set(Calendar.MINUTE, minute.coerceIn(0, 59))
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun hourAndMinuteFromMillis(millis: Long): Pair<Int, Int> {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE)
    }

    fun shiftDateIso(dateIso: String, deltaDays: Int): String {
        val cal = parseIsoToCalendar(dateIso)
        cal.add(Calendar.DAY_OF_MONTH, deltaDays)
        return isoFormatter().format(cal.time)
    }

    fun previousDateIso(dateIso: String): String = shiftDateIso(dateIso, -1)

    fun formatDurationMinutes(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        val h = safe / 60
        val m = safe % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    fun formatDurationHours(hours: Double): String {
        if (hours <= 0.05) return "Cleared"
        val totalMinutes = (hours * 60).toInt()
        return formatDurationMinutes(totalMinutes)
    }

    /**
     * Generates calendar cells for a month grid (Sunday-first, null for leading padding).
     */
    fun buildMonthCells(year: Int, monthZeroBased: Int): List<String?> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val firstDayOffset = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = mutableListOf<String?>()
        repeat(firstDayOffset) { cells.add(null) }
        for (day in 1..daysInMonth) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            cells.add(isoFormatter().format(cal.time))
        }
        return cells
    }

    fun dateIsoToUtcMidnightMillis(dateIso: String): Long {
        val utcFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        return runCatching { utcFmt.parse(dateIso)?.time }.getOrNull() ?: System.currentTimeMillis()
    }

    fun utcMidnightMillisToDateIso(utcMillis: Long): String {
        val utcFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        return utcFmt.format(Date(utcMillis))
    }
}
