# Koin Dependency Injection Setup

## Overview
This project now uses Koin 3.5.6 for dependency injection across the Kotlin Multiplatform codebase. `AudioRecorder` is registered in Koin and ready to inject, while `TimestampUtils` remains available as an `expect`/`actual` utility function outside the DI graph.

## Project Structure

### Core Module
- **`composeApp/src/commonMain/kotlin/org/example/project/di/AppModule.kt`**
  - Shared dependencies used by both Android and iOS
  - Defines the AudioRecorder interface binding

### Android Module
- **`composeApp/src/androidMain/kotlin/org/example/project/di/AndroidModule.kt`**
  - Registers `AudioRecorderAndroid` as the platform implementation of `AudioRecorder`
  
- **`composeApp/src/androidMain/kotlin/org/example/project/App.android.kt`**
  - `VoiceApp` Application class that initializes Koin on startup
  - Configured in `AndroidManifest.xml`

- **`composeApp/src/androidMain/kotlin/org/example/project/BaseActivity.kt`**
  - Injects `AudioRecorder` via property: `protected val audioRecorder: AudioRecorder by inject()`

### iOS Module
- **`composeApp/src/iosMain/kotlin/org/example/project/di/IosModule.kt`**
  - Registers `AudioRecorderIOS` as the platform implementation of `AudioRecorder`

- **`composeApp/src/iosMain/kotlin/org/example/project/App.ios.kt`**
  - `initializeKoin()` function to set up Koin (call early in app lifecycle)

## Current Dependencies Registered

### Android
```kotlin
val androidModule = module {
    single<AudioRecorder> { AudioRecorderAndroid(androidContext()) }
}
```

### iOS
```kotlin
val iosModule = module {
    single<AudioRecorder> { AudioRecorderIOS() }
}
```

## How to Use

### 1. Access AudioRecorder in Activities
Already available in `BaseActivity`:

```kotlin
class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Use audioRecorder directly
        audioRecorder.startRecording()
    }
}
```

### 2. Inject in Composables
```kotlin
@Composable
fun RecordingScreen() {
    val audioRecorder: AudioRecorder = koinInject()
    
    Button(onClick = { audioRecorder.startRecording() }) {
        Text("Start Recording")
    }
}
```

### 3. Inject in Custom Classes
```kotlin
class AudioService(private val audioRecorder: AudioRecorder) {
    fun recordVoiceNote() {
        audioRecorder.startRecording()
    }
}

// Register in AppModule
single { AudioService(get()) }
```

### 4. Using Android Context
For Android dependencies that need Context:

```kotlin
single { MyRepository(androidContext()) }
```

## iOS Setup
In `MainViewController.kt` or your iOS entry point, call:

```kotlin
initializeKoin()
```

Then inject in your Composables:
```kotlin
@Composable
fun App() {
    val audioRecorder: AudioRecorder = koinInject()
    // Use audioRecorder...
}
```

## Dependency Versions
- Koin Core: 3.5.6
- Koin Android: 3.5.6
- Koin Compose: 3.5.6

## File Locations
- Common DI: `composeApp/src/commonMain/kotlin/org/example/project/di/`
- Android DI: `composeApp/src/androidMain/kotlin/org/example/project/di/`
- iOS DI: `composeApp/src/iosMain/kotlin/org/example/project/di/`

## Related Documentation
- **[TIMESTAMP_FORMAT.md](TIMESTAMP_FORMAT.md)** - Audio file timestamps now include timezone information (e.g., `audio_20260405_143022_+0530.mp4`)

## Next Steps
1. ✅ AudioRecorder registered and injectable
2. ✅ Timestamps include timezone information
3. Add more services (repositories, data sources) to modules
4. Implement ViewModel integration for state management
5. Add database or API services to the modules
