package org.example.project.voicerecorder

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val timestampFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
    timeZone = TimeZone.getDefault()
}

private val timezoneFormatter = SimpleDateFormat("Z", Locale.US).apply {
    timeZone = TimeZone.getDefault()
}

actual fun getCurrentTimestamp(): String {
    val timestamp = timestampFormatter.format(System.currentTimeMillis())
    val timezone = timezoneFormatter.format(System.currentTimeMillis())
    // Format timezone as +0530 or -0800
    val tzFormatted = timezone.substring(0, 3) + timezone.substring(3)
    return "${timestamp}_${tzFormatted}"
}
