// Shared time and timezone utilities with platform-specific implementations

package org.example.project.util

/**
 * Get current time in milliseconds since epoch.
 *
 * Platform-specific implementations:
 * - Android: System.currentTimeMillis()
 * - iOS: NSDate().timeIntervalSince1970 * 1000
 */
expect fun getCurrentTimeMillis(): Long

/**
 * Get current timezone offset from UTC in minutes.
 *
 * Platform-specific implementations:
 * - Android: TimeZone.getDefault().rawOffset / 60000
 * - iOS: NSTimeZone.localTimeZone().secondsFromGMT() / 60
 *
 * @return Timezone offset in minutes (positive for east of UTC, negative for west)
 */
expect fun getTimeZoneOffsetMinutes(): Int

/**
 * Get current timezone identifier (e.g., "Asia/Kolkata", "America/New_York").
 *
 * @return Timezone ID as string
 */
expect fun getTimeZoneId(): String
