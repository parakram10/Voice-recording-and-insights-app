package org.example.project.voicerecorder

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

private val timestampFormatter = NSDateFormatter().apply {
    dateFormat = "yyyyMMdd_HHmmss"
    timeZone = NSTimeZone.localTimeZone
}

private val timezoneFormatter = NSDateFormatter().apply {
    dateFormat = "Z"
    timeZone = NSTimeZone.localTimeZone
}

actual fun getCurrentTimestamp(): String {
    val currentDate = NSDate()
    val timestamp = timestampFormatter.stringFromDate(currentDate)
    val timezone = timezoneFormatter.stringFromDate(currentDate)
    return "${timestamp}_${timezone}"
}
