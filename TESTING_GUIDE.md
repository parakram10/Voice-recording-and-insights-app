# Audio Recording Testing Guide

## Android Testing UI

A dedicated testing UI has been created for Android to test the audio recording functionality without needing to integrate it into your main app UI.

### Location
- **RecordingScreen**: `composeApp/src/androidMain/kotlin/org/example/project/ui/RecordingScreen.kt`
- **AndroidApp**: `composeApp/src/androidMain/kotlin/org/example/project/AndroidApp.kt`
- **MainActivity**: Updated to show RecordingScreen

### Features

The testing UI provides the following buttons:

#### 1. **Start Recording** 🎤
- Initiates audio recording
- Automatically generates timestamp-based filename with timezone
- Filename format: `audio_20260405_143022_+0530.mp4`
- Button is disabled while recording

#### 2. **Pause Recording** ⏸️
- Pauses the ongoing recording (requires Android 7.0+, API 24)
- Only visible and enabled on API 24+
- Button is disabled if not recording or already paused

#### 3. **Resume Recording** ▶️
- Resumes a paused recording (requires Android 7.0+, API 24)
- Only visible and enabled on API 24+
- Button is disabled if not recording or not paused

#### 4. **Stop Recording** ⏹️
- Stops recording and saves the file
- Shows the saved file path in the UI
- Button is disabled when not recording

#### 5. **Reset** 🔄
- Clears the UI state and status
- Useful for restarting the test

### Status Indicators

The UI displays:
- **Real-time status message**: Shows current recording state and any errors
- **Recording indicator**: 🔴 "Recording in progress" when active
- **Paused indicator**: ⏸️ "Recording paused" when paused
- **Saved file path**: Shows the filename of the last saved recording (without directory)

### API Level Support

- **Pause/Resume**: Only available on Android 7.0+ (API 24+)
  - On older devices, a warning message is shown
  - Attempting to pause/resume throws `UnsupportedOperationException`

### Error Handling

All operations display errors in the status message:
- Permission errors
- Device errors
- File I/O errors
- API compatibility issues

Example: `Status: Error: RECORD_AUDIO permission not granted`

### Usage Workflow

1. **Start Recording**
   ```
   Tap "Start Recording" → Status: "Recording started"
   ```

2. **Optional: Pause & Resume** (API 24+)
   ```
   Tap "Pause Recording" → Status: "Recording paused"
   Tap "Resume Recording" → Status: "Recording resumed"
   ```

3. **Stop Recording**
   ```
   Tap "Stop Recording" → Status: "Recording stopped"
   Shows: "Saved: audio_20260405_143022_+0530.mp4"
   ```

4. **Reset for Next Test**
   ```
   Tap "Reset" → Status: "Ready to record"
   ```

### File Storage

Audio files are saved to:
- **Android**: `Context.getExternalFilesDir(null)`
  - Example: `/data/user/0/org.example.project/files/`
  - Accessible via Android File Explorer in Android Studio

### Testing Checklist

- [ ] Start recording (should succeed)
- [ ] Verify 🔴 Recording indicator appears
- [ ] Pause recording (if API 24+)
- [ ] Resume recording (if API 24+)
- [ ] Stop recording
- [ ] Verify saved file path is displayed
- [ ] Check file exists in device file system
- [ ] Verify filename format: `audio_YYYYMMDD_HHmmss_±HHMM.mp4`
- [ ] Verify timezone offset is correct
- [ ] Test on multiple API levels (if available)

### Permissions

The RECORD_AUDIO permission is requested by:
- `BaseActivity.requestRecordAudioPermission()`
- Called via the UI or programmatically
- Uses Android's new permission request API

### Integration into Main App

When ready to integrate into your main app:

1. Remove the temporary `RecordingScreen` from MainActivity
2. Create your own UI component for recording
3. Inject `AudioRecorder` via Koin:
   ```kotlin
   val audioRecorder: AudioRecorder = koinInject()
   ```
4. Call the recorder methods as needed

### Example Integration

```kotlin
@Composable
fun MyRecordingFeature() {
    val audioRecorder: AudioRecorder = koinInject()
    
    Button(onClick = { audioRecorder.startRecording() }) {
        Text("Start")
    }
}
```

## Common Issues

### "RECORD_AUDIO permission not granted"
- The app needs to request the permission first
- The OS permission dialog should appear
- Grant the permission and try again

### File not found after stopping
- Check if the file was actually saved
- Look in `getExternalFilesDir(null)` directory
- Check the status message for any error details

### Pause/Resume not working
- Check Android version (requires API 24+)
- The UI will show a warning on older devices
- Use only on Android 7.0+

### No audio in saved file
- Ensure RECORD_AUDIO permission is granted
- Check device microphone is not muted
- Verify file was saved (check status message)
