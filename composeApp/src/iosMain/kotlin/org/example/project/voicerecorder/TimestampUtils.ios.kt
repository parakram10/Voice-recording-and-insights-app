package org.example.project.voicerecorder

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

private val timestampFormatter = NSDateFormatter().apply {
    dateFormat = "yyyyMMdd_HHmmss"
}

actual fun getCurrentTimestamp(): String {
    return timestampFormatter.stringFromDate(NSDate())
}
