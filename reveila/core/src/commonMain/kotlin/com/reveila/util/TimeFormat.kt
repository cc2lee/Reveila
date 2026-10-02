package com.reveila.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.jvm.JvmStatic

object TimeFormat {

    /**
     * Formats a timestamp in milliseconds to a human-readable string.
     * Example: "2026-05-20 23:32:15.482"
     */
    @JvmStatic
    fun timestamp(ms: Long): String {
        val instant = Instant.fromEpochMilliseconds(ms)
        val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val year = ldt.year.toString().padStart(4, '0')
        val month = ldt.monthNumber.toString().padStart(2, '0')
        val day = ldt.dayOfMonth.toString().padStart(2, '0')
        val hour = ldt.hour.toString().padStart(2, '0')
        val minute = ldt.minute.toString().padStart(2, '0')
        val second = ldt.second.toString().padStart(2, '0')
        val millis = (ms % 1000).toString().padStart(3, '0')
        return "$year-$month-$day $hour:$minute:$second.$millis"
    }

    /**
     * Formats a duration in milliseconds to a human-readable string.
     * Example: "01:23:45.678"
     */
    @JvmStatic
    fun duration(ms: Long): String {
        val totalSecs = ms / 1000
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        val millis = ms % 1000

        val sb = StringBuilder(12)
        if (hours < 10) sb.append('0')
        sb.append(hours).append(':')
        if (minutes < 10) sb.append('0')
        sb.append(minutes).append(':')
        if (seconds < 10) sb.append('0')
        sb.append(seconds).append('.')
        if (millis < 100) sb.append('0')
        if (millis < 10) sb.append('0')
        sb.append(millis)

        return sb.toString()
    }
}
