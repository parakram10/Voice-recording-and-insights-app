// iOS implementation of time and timezone utilities

package org.example.project.util

import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.secondsFromGMT
import platform.Foundation.timeIntervalSince1970

actual fun getCurrentTimeMillis(): Long {
    val timeIntervalSeconds = NSDate().timeIntervalSince1970
    return (timeIntervalSeconds * 1000).toLong()
}

actual fun getTimeZoneOffsetMinutes(): Int {
    val localTz = NSTimeZone.localTimeZone
    return (localTz.secondsFromGMT() / 60).toInt()  // Convert seconds to minutes
}

actual fun getTimeZoneId(): String {
    return NSTimeZone.localTimeZone().name
}
