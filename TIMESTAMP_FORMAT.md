# Timestamp Format with Timezone

## Overview
The app now generates timestamps with timezone information. This ensures that audio recordings are timestamped according to the device's local timezone, making it easier to correlate recordings with local events.

## Format
`yyyyMMdd_HHmmss_±HHMM`

### Examples:
- `20260405_143022_+0530` - April 5, 2026, 2:30:22 PM IST (UTC+5:30)
- `20260405_143022_-0800` - April 5, 2026, 2:30:22 PM PST (UTC-8:00)
- `20260405_143022_+0000` - April 5, 2026, 2:30:22 PM UTC
- `20260405_143022_+0530` - April 5, 2026, 2:30:22 PM in India Standard Time

## Filename Examples

### Android (MP4 format)
`audio_20260405_143022_+0530.mp4`

### iOS (M4A format)
`audio_20260405_143022_+0530.m4a`

## Implementation

### Android
Located in `composeApp/src/androidMain/kotlin/org/example/project/voicerecorder/TimestampUtils.android.kt`

Uses:
- `SimpleDateFormat` with `TimeZone.getDefault()` for date/time formatting
- `SimpleDateFormat` with format "Z" for timezone offset extraction

### iOS
Located in `composeApp/src/iosMain/kotlin/org/example/project/voicerecorder/TimestampUtils.ios.kt`

Uses:
- `NSDateFormatter` with `NSTimeZone.localTimeZone` for date/time formatting
- `NSDateFormatter` with format "Z" for timezone offset extraction

## Common Interface
Located in `composeApp/src/commonMain/kotlin/org/example/project/voicerecorder/TimestampUtils.kt`

```kotlin
expect fun getCurrentTimestamp(): String
```

Returns a timestamp string with timezone offset based on the device's local timezone settings.

## Timezone Offset Format
The timezone offset is in ISO 8601 format:
- `+HHMM` for timezones ahead of UTC
- `-HHMM` for timezones behind UTC
- `+0000` for UTC

## Benefits
1. **Local Time Context** - Timestamps reflect the local time when recording was made
2. **Easy Correlation** - Can easily match recordings with events in the same timezone
3. **Multi-Timezone Support** - If used across multiple devices in different timezones, the offset is always clear
4. **Standard Format** - ISO 8601 format is widely recognized and parseable by tools

## Parsing Timestamps
To parse these timestamps in other tools:

### Python Example
```python
from datetime import datetime

timestamp_str = "20260405_143022_+0530"
dt = datetime.strptime(timestamp_str, "%Y%m%d_%H%M%S_%z")
print(dt)  # 2026-04-05 14:30:22+05:30
```

### JavaScript Example
```javascript
const timestamp = "20260405_143022_+0530";
const dateStr = timestamp.slice(0, 15);
const tzStr = timestamp.slice(16);
const date = new Date(dateStr.replace(/(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})/, '$1-$2-$3T$4:$5:$6') + tzStr);
```

## Future Enhancements
- Add option to customize timestamp format
- Add option to use UTC timestamps instead of local
- Add human-readable format option (e.g., "April_5_2026_2_30_PM")
