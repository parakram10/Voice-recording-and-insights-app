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
    // Format timezone as +0530 or -0800 (Z format produces +0530)
    val tzFormatted = timezone.substring(0, 3) + timezone.substring(3)
    return "${timestamp}_${tzFormatted}"
}
