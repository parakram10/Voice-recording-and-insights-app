package org.example.project.voicerecorder

/**
 * Generates a timestamp string with timezone information in the format: yyyyMMdd_HHmmss_±HHMM
 *
 * Examples:
 * - 20260405_143022_+0530 (for IST - UTC+5:30)
 * - 20260405_143022_-0800 (for PST - UTC-8:00)
 * - 20260405_143022_+0000 (for UTC)
 *
 * The timezone offset is based on the device's local timezone settings.
 *
 * @return Timestamp string with timezone offset
 */
expect fun getCurrentTimestamp(): String
