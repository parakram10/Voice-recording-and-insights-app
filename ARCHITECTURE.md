# Voice Recording & Insights — Architecture (Testable & Scalable)

## Overview: Complete Data & Control Flow

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                          UI Layer (commonMain)                               │
│  RecordingScreen → RecordingViewModel ← RecordingRepository (interface)     │
│                                                                              │
│  User records → stopRecording() → insertRecording() → DB updated            │
│  User sees cards from getAllRecordings() → Flow updates UI in real-time     │
│  User taps "View Transcription" → dialog shows text                         │
└──────────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌──────────────────────────────────────────────────────────────────────────────┐
│                    Business Logic Layer (commonMain)                         │
│  TranscriptionHandler (orchestrator) ← depends on:                          │
│    - RecordingRepository (get recording, update status)                     │
│    - AudioDecoder (helper: convert audio format)                            │
│    - WhisperContext / TranscriptionBridge (JNI/Swift: call transcriber)    │
│    - ModelManager (helper: manage model asset)                              │
│    - ErrorRecoveryOrchestrator (helper: handle crashes, retry logic)        │
│                                                                              │
│  On stopRecording():                                                        │
│    1. Insert recording with status=PENDING                                 │
│    2. TranscriptionHandler.enqueue(id, filePath) — auto-triggers           │
│    3. Mark status=IN_PROGRESS                                              │
│    4. AudioDecoder.decode(filePath) → 16kHz PCM FloatArray                 │
│    5. WhisperContext.transcribe(floats) → text                             │
│    6. repository.markDone(id, text) → UI updates                           │
│    On error: repository.markError(id, error) → Retry button appears        │
│    On app kill: Phase 4.3 recovery re-enqueues PENDING + IN_PROGRESS       │
└──────────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌──────────────────────────────────────────────────────────────────────────────┐
│                       Data Layer (commonMain + platform)                     │
│  RecordingRepositoryImpl ← SQLDelight (shared schema + queries)              │
│                                                                              │
│  Android: AndroidSqliteDriver (SQLite on device)                            │
│  iOS: NativeSqliteDriver (SQLite on device)                                 │
│                                                                              │
│  Database Schema:                                                           │
│    recording(id, filePath, fileName, createdAt, durationMs,                │
│              transcriptionText, transcriptionStatus, transcriptionError)    │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Architecture Layers

### 1. **Presentation Layer** (commonMain/ui)

**Purpose**: Display data, handle user interactions, delegate to ViewModel

**Classes**:
- `RecordingScreen` (composable root)
- `SavedFileItem` (card component)
- `TranscriptionStatusRow` (status indicator)
- `TranscriptionDialog` (fullscreen dialog)

**State Management**:
- All state in `RecordingViewModel` (single source of truth)
- UI observes `StateFlow<RecordingScreenUiState>`
- UI is **pure** — no logic, no side effects

**Testing**: Mock ViewModel, test Compose state changes with `ComposeTestRule`

---

### 2. **ViewModel / State Orchestration Layer** (commonMain/viewmodel)

**Purpose**: Orchestrate recording, coordinate with data layer, expose UI state as Flow

**Classes**:

#### `RecordingViewModel`
- **Responsibility**: Manage UI state, orchestrate recording lifecycle, delegate to repo
- **Dependencies** (injected):
  - `AudioRecorder` (platform impl: MediaRecorder/AVAudioRecorder)
  - `RecordingRepository` (interface; SQLDelight impl at runtime)
  - `TranscriptionHandler` (orchestrator; platform-specific)
- **Key methods**:
  - `startRecording()` — calls `audioRecorder.startRecording()`
  - `stopRecording()` — calls `audioRecorder.stopRecording()`, inserts DB row, **triggers `transcriptionHandler.enqueue()`**
  - `deleteRecording(id)` — deletes file + DB row
  - `retryTranscription(id)` — re-enqueues with `transcriptionHandler`

**State Classes** (data classes):
- `TranscriptionUiState` (sealed: Pending, InProgress, Done, Error)
- `RecordingUiItem` (id, fileName, filePath, createdAt, transcription state)
- `RecordingScreenUiState` (recordings list, recording/paused flags, loading state, etc.)

**Helpers**:
- `RecordingStateMapper` — maps DB entity → RecordingUiItem (pure function)

**Testing**:
```kotlin
@Test
fun testStopRecordingAutoTriggersTranscription() {
    val mockRepo = mockk<RecordingRepository>()
    val mockHandler = mockk<TranscriptionHandler>()
    val viewModel = RecordingViewModel(mockAudioRecorder, mockRepo, mockHandler)
    
    // when
    viewModel.stopRecording()
    
    // then
    verify { mockHandler.enqueue(any(), any()) }  // assert transcription was triggered
}
```

---

### 3. **Business Logic / Orchestration Layer** (commonMain + platform)

**Purpose**: Coordinate complex operations like transcription, error recovery, retries

#### **TranscriptionHandler** (orchestrator)

**Responsibility**: Manage background transcription lifecycle (enqueue, execute, retry, error handling)

**Dependencies**:
- `RecordingRepository` (update status/results)
- `AudioDecoder` (convert audio)
- `WhisperContext` / `TranscriptionBridge` (call transcriber)
- `ModelManager` (get model path)

**Architecture**:
```kotlin
class TranscriptionHandler(
    private val repository: RecordingRepository,
    private val audioDecoder: AudioDecoder,
    private val modelManager: ModelManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    fun enqueue(id: Long, filePath: String) {
        if (activeJobs.containsKey(id)) return  // already running
        
        val job = scope.launch {
            try {
                repository.markInProgress(id)
                
                // Step 1: Convert audio
                val floatArray = audioDecoder.decodeToFloat(filePath)
                
                // Step 2: Load model
                val modelPath = modelManager.getModelPath()
                val context = WhisperContext.createFromFile(modelPath) 
                    ?: throw ModelLoadException("Model not found at $modelPath")
                
                // Step 3: Transcribe
                val text = try {
                    context.transcribe(floatArray)
                } finally {
                    context.free()
                }
                
                // Step 4: Persist result
                repository.markDone(id, text)
                
            } catch (e: CancellationException) {
                repository.markError(id, "Cancelled")
                throw e  // must rethrow
            } catch (e: Exception) {
                repository.markError(id, e.message ?: "Unknown error")
            } finally {
                activeJobs.remove(id)
            }
        }
        activeJobs[id] = job
    }

    fun cancel(id: Long) { ... }
    fun destroy() { scope.cancel() }
}
```

**Why separate from ViewModel**:
- ViewModel is tied to UI lifecycle (cleared when user navigates away)
- TranscriptionHandler needs to outlive ViewModel — job must continue in background
- Koin singleton scope: handler survives ViewModel destruction

**Testing**:
```kotlin
@Test
fun testTranscriptionErrorIsPersistedToDb() {
    val mockRepo = mockk<RecordingRepository>()
    val mockDecoder = mockk<AudioDecoder>() 
    mockDecoder.decodeToFloat returns FloatArray(0)  // empty audio
    
    val handler = TranscriptionHandler(mockRepo, mockDecoder, mockModelManager)
    
    // when error occurs
    handler.enqueue(1, "/path/to/audio.mp4")
    
    // then error is persisted
    verify { mockRepo.markError(1, any()) }
}
```

#### **ErrorRecoveryOrchestrator** (helper)

**Responsibility**: Detect and recover from transcription failures on app restart

**Key logic** (in ViewModel.init{}):
```kotlin
// Recover interrupted transcriptions on app restart
viewModelScope.launch(Dispatchers.IO) {
    repository.getAllRecordings().first()
        .filter { it.status == "IN_PROGRESS" || it.status == "PENDING" }
        .forEach { transcriptionHandler.enqueue(it.id, it.filePath) }
}
```

**Scenarios handled**:
1. App killed mid-transcription (status = IN_PROGRESS) → re-start
2. App killed between insert & enqueue (status = PENDING) → start
3. User manually taps Retry (status = ERROR) → `retryTranscription()` in ViewModel

---

### 4. **Helper / Utility Layer** (commonMain + platform)

**Purpose**: Provide reusable, testable utilities for common operations

#### **AudioDecoder** (helper orchestrator)

**Responsibility**: Convert audio format (MP4/AAC @ 44.1kHz → 16kHz PCM mono)

**Architecture**:
```kotlin
object AudioDecoder {
    suspend fun decodeToFloat(filePath: String): FloatArray = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor().apply { setDataSource(filePath) }
        val codec = MediaCodec.createDecoderByType(mime)
        
        // 1. Decode MP4 → PCM bytes
        val pcmBytes = decodeToBytes(extractor, codec)
        
        // 2. Convert bytes → ShortArray (16-bit LE)
        val shorts = pcmBytes.toShortArray()
        
        // 3. Downsample stereo → mono (if needed)
        val mono = if (channelCount > 1) downmixStereo(shorts) else shorts
        
        // 4. Resample 44.1kHz → 16kHz (if needed)
        val resampled = if (sampleRate != 16000) linearResample(mono, sampleRate) else mono
        
        // 5. Normalize to FloatArray [-1.0, 1.0]
        resampled.map { it / 32767.0f }.toFloatArray()
    }
}
```

**Testing**:
```kotlin
@Test
fun testAudioDecoderResamplesCorrectly() {
    val floats = AudioDecoder.decodeToFloat("test_44100Hz.mp4")
    
    // Verify output is valid
    assert(floats.isNotEmpty())
    assert(floats.all { it >= -1.0f && it <= 1.0f })  // normalized
}
```

#### **ModelManager** (helper)

**Responsibility**: Bundle model asset → device storage on first run

```kotlin
class ModelManager(private val context: Context) {
    suspend fun getModelPath(): String = withContext(Dispatchers.IO) {
        val target = File(context.filesDir, "models/ggml-tiny.en-q5_1.bin")
        if (!target.exists()) {
            target.parentFile?.mkdirs()
            context.assets.open("models/ggml-tiny.en-q5_1.bin")
                .copyTo(FileOutputStream(target))
        }
        target.absolutePath
    }
}
```

**Testable**: Mock filesystem, verify file is copied once.

#### **RecordingStateMapper** (helper)

**Responsibility**: Pure mapping from DB entity → UI state

```kotlin
object RecordingStateMapper {
    fun toUiItem(record: Recording): RecordingUiItem = RecordingUiItem(
        id = record.id,
        fileName = record.fileName,
        transcription = when (record.transcriptionStatus) {
            "IN_PROGRESS" -> TranscriptionUiState.InProgress
            "DONE" -> TranscriptionUiState.Done(record.transcriptionText ?: "")
            "ERROR" -> TranscriptionUiState.Error(record.transcriptionError ?: "Unknown")
            else -> TranscriptionUiState.Pending
        }
    )
}
```

**Testing**: Pure function, trivial to unit test.

---

### 5. **Data Layer** (commonMain + platform)

**Purpose**: Abstract all persistence behind interface; SQLDelight provides shared impl

#### `RecordingRepository` (interface)

```kotlin
interface RecordingRepository {
    suspend fun insertRecording(filePath: String, fileName: String): Long
    suspend fun markInProgress(id: Long)
    suspend fun markDone(id: Long, text: String)
    suspend fun markError(id: Long, error: String)
    fun getAllRecordings(): Flow<List<RecordingUiItem>>
    suspend fun getRecordingById(id: Long): RecordingUiItem?
    suspend fun deleteRecording(id: Long)
}
```

#### `RecordingRepositoryImpl` (SQLDelight impl in commonMain)

```kotlin
class RecordingRepositoryImpl(database: AppDatabase) : RecordingRepository {
    private val queries = database.recordingQueries
    
    override fun getAllRecordings(): Flow<List<RecordingUiItem>> =
        queries.getAllRecordings()
            .asFlow()
            .mapToList()
            .map { records -> records.map { it.toUiItem() } }
    // ... other methods ...
}
```

**Testing**: Replace `AppDatabase` with in-memory test database; verify queries.

---

### 6. **Platform-Specific Layer** (androidMain / iosMain)

#### **Android: JNI Bridge**

- `WhisperContext.kt` — wraps Whisper.cpp via JNI
- `whisper_jni.cpp` — C++ side of bridge
- `CMakeLists.txt` — builds libwhisper_jni.so
- `AndroidSqliteDriver` — SQLite driver

#### **iOS: Swift Bridge**

- `TranscriptionBridge.swift` — wraps WhisperKit
- `AudioDecoderIOS.kt` — AVAssetReader helper
- `NativeSqliteDriver` — SQLite driver

---

## Dependency Injection & Composition

### Koin Modules

**commonMain/AppModule.kt**:
```kotlin
val appModule = module {
    // Repositories (shared)
    single<RecordingRepository> { RecordingRepositoryImpl(get()) }
    
    // ViewModels (commonMain, depends on interfaces)
    viewModel { RecordingViewModel(get(), get(), get()) }
}
```

**androidMain/AndroidModule.kt**:
```kotlin
val androidModule = module {
    // Platform implementations
    single { AudioRecorderAndroid(androidContext()) }
    single { AudioDecoder }  // helper
    single { ModelManager(androidContext()) }
    single { WhisperContext }  // JNI wrapper
    single { TranscriptionHandler(get(), get(), get()) }
    
    // SQLDelight driver
    single { AppDatabase(driver = AndroidSqliteDriver(...)) }
}
```

**iosMain/IosModule.kt**:
```kotlin
val iosModule = module {
    single { AudioRecorderIOS() }
    single { AudioDecoderIOS }
    single { ModelManagerIOS() }
    single { TranscriptionBridge }  // Swift wrapper
    single { TranscriptionHandlerIOS(get(), get(), get()) }
    
    single { AppDatabase(driver = NativeSqliteDriver(...)) }
}
```

**Key principle**: Interfaces injected in commonMain; implementations injected in platform modules.

---

## Testability Matrix

| Component | Type | How to Test | Mock Dependencies |
|---|---|---|---|
| `RecordingViewModel` | ViewModel | Unit + mock repo | `RecordingRepository`, `TranscriptionHandler` |
| `TranscriptionHandler` | Orchestrator | Unit + mock repo + mock decoder | `Repository`, `AudioDecoder`, `WhisperContext` |
| `AudioDecoder` | Helper | Integration (needs audio file) | None (pure function on floats) |
| `RecordingStateMapper` | Helper | Unit | None (pure function) |
| `RecordingRepositoryImpl` | Repository | Unit (in-memory DB) | SQLDelight test DB |
| `RecordingScreen` | Composable | UI test with `ComposeTestRule` | Mock ViewModel |
| `WhisperContext` | JNI wrapper | Integration (requires emulator) | None (JNI call) |

---

## Scalability: How to Add Features

### Example 1: Add Recording Duration Tracking

1. **DB**: Add migration to `Recording.sqldelight`:
   ```sql
   -- Compute duration from start-stop timestamps
   SELECT fileName, (stopTime - startTime) as durationMs FROM recording
   ```

2. **ViewModel**: No change (repo interface unchanged)

3. **UI**: Add duration display in `SavedFileItem`

4. **Tests**: No impact on existing tests

### Example 2: Add Transcription Progress (streaming results)

1. **Handler**: Extend `TranscriptionHandler` to support callbacks:
   ```kotlin
   fun enqueueWithProgress(id: Long, filePath: String, onProgress: (String) -> Unit) { ... }
   ```

2. **ViewModel**: No change (repo interface unchanged)

3. **UI**: Show partial transcription in real-time

4. **Tests**: Mock progress callback

---

## Clean Code Principles Applied

| Principle | How Implemented |
|---|---|
| **Single Responsibility** | Each class has ONE reason to change (e.g., `AudioDecoder` only handles format conversion) |
| **Open/Closed** | New transcription strategies extend `TranscriptionHandler` without modifying existing code |
| **Liskov Substitution** | `RecordingRepository` interface allows any impl (Room, SQLDelight, mock) |
| **Interface Segregation** | Small, focused interfaces (e.g., `AudioRecorder`, `RecordingRepository`) |
| **Dependency Inversion** | ViewModel depends on `RecordingRepository` interface, not concrete SQLDelight |
| **DRY** | Helpers like `RecordingStateMapper`, `ModelManager` prevent duplication |

---

## Folder Structure (Final)

```
composeApp/
├── src/
│   ├── commonMain/
│   │   ├── kotlin/org/example/project/
│   │   │   ├── data/
│   │   │   │   ├── RecordingRepository.kt        (interface)
│   │   │   │   └── RecordingRepositoryImpl.kt     (SQLDelight impl)
│   │   │   ├── viewmodel/
│   │   │   │   ├── RecordingViewModel.kt
│   │   │   │   ├── RecordingUiState.kt
│   │   │   │   └── helpers/
│   │   │   │       └── RecordingStateMapper.kt
│   │   │   ├── ui/
│   │   │   │   ├── RecordingScreen.kt
│   │   │   │   ├── SavedFileItem.kt
│   │   │   │   ├── TranscriptionStatusRow.kt
│   │   │   │   └── TranscriptionDialog.kt
│   │   │   ├── transcription/
│   │   │   │   ├── AudioDecoder.kt               (shared; expect/actual for platform)
│   │   │   │   ├── TranscriptionHandler.kt       (shared; platform-agnostic orchestrator)
│   │   │   │   ├── ModelManager.kt               (shared; expect/actual)
│   │   │   │   └── ErrorRecoveryOrchestrator.kt
│   │   │   └── di/
│   │   │       └── AppModule.kt
│   │   └── sqldelight/org/example/project/
│   │       └── Recording.sqldelight
│   │
│   ├── androidMain/
│   │   ├── kotlin/org/example/project/
│   │   │   ├── voicerecorder/
│   │   │   │   └── AudioRecorder.android.kt
│   │   │   ├── transcription/
│   │   │   │   ├── WhisperContext.kt             (JNI wrapper)
│   │   │   │   ├── AudioDecoder.android.kt       (actual impl)
│   │   │   │   ├── ModelManager.android.kt       (actual impl)
│   │   │   │   └── TranscriptionHandler.kt
│   │   │   ├── cpp/
│   │   │   │   ├── CMakeLists.txt
│   │   │   │   └── whisper_jni.cpp
│   │   │   ├── di/
│   │   │   │   └── AndroidModule.kt
│   │   │   └── MainActivity.kt
│   │   └── assets/models/
│   │       └── ggml-tiny.en-q5_1.bin
│   │
│   └── iosMain/
│       ├── kotlin/org/example/project/
│       │   ├── voicerecorder/
│       │   │   └── AudioRecorder.ios.kt
│       │   ├── transcription/
│       │   │   ├── TranscriptionBridge.swift
│       │   │   ├── AudioDecoder.ios.kt           (actual impl: AVAssetReader)
│       │   │   ├── ModelManager.ios.kt           (actual impl)
│       │   │   └── TranscriptionHandler.ios.kt
│       │   └── di/
│       │       └── IosModule.kt
│       └── Resources/models/
│           └── ggml-tiny.en-q5_1.bin
│
└── gradle/libs.versions.toml

tests/
├── commonTest/
│   └── RecordingViewModelTest.kt
│   └── RecordingRepositoryImplTest.kt
│   └── RecordingStateMapperTest.kt
├── androidTest/
│   └── AudioDecoderTest.kt
│   └── TranscriptionHandlerTest.kt
└── iosTest/
    └── AudioDecoderIOSTest.kt
```

---

## Key Takeaways

✅ **Testable**: Mock interfaces, in-memory DB, no hard dependencies  
✅ **Scalable**: Add features by extending repos/handlers, not modifying  
✅ **Readable**: Clear separation of concerns, named helpers, pure functions  
✅ **Maintainable**: Single DB schema, no duplication, DI everywhere  
✅ **Debuggable**: Flow through orchestrators is explicit, not hidden in callbacks  

