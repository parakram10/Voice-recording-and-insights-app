// Android implementation of time and timezone utilities

package org.example.project.util

import java.util.TimeZone

actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()

actual fun getTimeZoneOffsetMinutes(): Int {
    val tz = TimeZone.getDefault()
    return tz.rawOffset / (1000 * 60)  // Convert milliseconds to minutes
}

actual fun getTimeZoneId(): String {
    return TimeZone.getDefault().id
}
