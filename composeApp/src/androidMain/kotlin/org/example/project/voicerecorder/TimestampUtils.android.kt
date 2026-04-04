package org.example.project.voicerecorder

import java.text.SimpleDateFormat
import java.util.Locale

private val timestampFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

actual fun getCurrentTimestamp(): String {
    return timestampFormatter.format(System.currentTimeMillis())
}
