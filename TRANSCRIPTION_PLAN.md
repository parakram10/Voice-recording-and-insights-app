# Whisper.cpp Transcription — Full Implementation Plan

## Context

**Why**: Add automatic speech-to-text transcription to every recording. After the user stops recording, the app should transcribe it in the background using Whisper.cpp (Android) and WhisperKit (iOS), show a progress indicator on the recording card, and surface the result with a "View Transcription" button. Retry is available on failure.

**Current state**: KMP project (Compose Multiplatform, Android + iOS). No Room DB, no ViewModel, no data models — `RecordingScreen.kt` (androidMain only) is raw `remember {}` state, iterating `List<File>` from disk. iOS shows a placeholder "Click me!" screen. Recording saves MP4 (Android, 44.1kHz AAC) and M4A (iOS).

**Outcome**: End-to-end transcription flow working on both Android and iOS, DB-backed recording list, background processing with proper lifecycle, recovery on crash/app-kill.

## User Decisions
- **Model delivery**: Bundle `ggml-tiny.en-q5_1.bin` (~31 MB) in APK assets / Xcode bundle
- **iOS scope**: Include iOS (Phases 6–7)
- **Recovery on restart**: Re-enqueue both `PENDING` + `IN_PROGRESS` items

---

## Architecture Overview (Compose Multiplatform)

All **UI and ViewModel code is in `commonMain`** so both Android and iOS share the same composables.
Only **platform-specific implementations** (DB, transcription, audio decoding) are in `androidMain`/`iosMain`.

```
commonMain/
  ui/
    RecordingScreen.kt          (Phase 2.3, 5.2, 5.4 — shared UI for Android + iOS)
    TranscriptionStatusRow.kt   (Phase 5.1 — shared UI)
    TranscriptionDialog.kt      (Phase 5.3 — shared UI)
  viewmodel/
    RecordingUiState.kt         (Phase 2.1 — shared state)
    RecordingViewModel.kt       (Phase 2.2, 4.2, 4.3 — shared ViewModel)

androidMain/
  db/
    RecordingEntity.kt          (Phase 1.1)
    RecordingDao.kt             (Phase 1.2)
    AppDatabase.kt              (Phase 1.3)
    RecordingRepository.kt      (Phase 1.4)
  di/
    AndroidModule.kt            (modified: 1.5, 2.3, 3.6, 4.1, 4.2)
  transcription/
    WhisperContext.kt           (Phase 3.4)
    AudioDecoder.kt             (Phase 3.5)
    ModelManager.kt             (Phase 3.6)
    TranscriptionHandler.kt     (Phase 4.1)
  cpp/
    CMakeLists.txt              (Phase 3.1)
    whisper.cpp, whisper.h ...  (Phase 3.1 – vendored)
    whisper_jni.cpp             (Phase 3.2)
  assets/models/
    ggml-tiny.en-q5_1.bin       (Phase 3.6 – binary, download separately)

iosMain/
  db/
    RecordingStorageIOS.kt      (Phase 6.1 — JSON-based storage; iOS doesn't use Room)
  transcription/
    AudioDecoderIOS.kt          (Phase 7.3 — AVAssetReader for M4A decoding)
    TranscriptionHandlerIOS.kt  (Phase 7.4 — WhisperKit integration via callback bridge)
    ModelManagerIOS.kt          (Phase 7.5 — bundle model to NSDocumentDirectory)
  di/
    IosModule.kt                (modified: 6.1, 7.4, 7.5)

iosApp/ (Xcode project)
  TranscriptionBridge.swift     (Phase 7.1 — @objc wrapper around WhisperKit)
  Resources/models/
    ggml-tiny.en-q5_1.bin       (Phase 7.5 — bundled model)
```

**Key difference from previous plan:**
- `RecordingScreen`, `TranscriptionStatusRow`, `TranscriptionDialog`, `RecordingViewModel` are **all in `commonMain`**
- They use dependency injection (Koin) to access platform implementations (`RecordingRepository`, `AudioRecorder`, `TranscriptionHandler`)
- Android and iOS **share the exact same UI code** — no duplication
- Platform-specific code is isolated to `androidMain`/`iosMain`

---

## Sub-Phase Rule

> **Only implement the sub-phase explicitly requested. Do not proceed to the next sub-phase automatically.**
> Each sub-phase must be reviewable and committable independently.
> Every new file must have a `// Phase X.Y — <name>` comment header at the top.

---

## Phase 1: Data Layer Architecture (SQLDelight for Both Platforms)

**Design**: Use **SQLDelight** for shared database implementation across Android + iOS. Single schema, generated queries, no duplication. No Room, no JSON — just SQLite everywhere.

**Why SQLDelight**:
- True code sharing: both platforms use identical DB layer
- Type-safe generated Kotlin code
- Single source of truth (`.sqldelight` schema file)
- Production-ready, no wrapping needed

### Checklist
- [ ] **1.1** — `RecordingRepository.kt` (commonMain) — interface defining domain operations
- [ ] **1.2** — `RecordingSchema.sqldelight` (commonMain/sqldelight) — SQL schema + queries
- [ ] **1.3** — `RecordingRepositoryImpl.kt` (commonMain) — wraps generated `Database` class, implements interface
- [ ] **1.4** — `build.gradle.kts` — add SQLDelight plugin + gradle config
- [ ] **1.5** — `libs.versions.toml` — add SQLDelight version
- [ ] **1.6** — Koin registration — single impl for both Android + iOS

---

### Phase 1.1 — RecordingRepository Interface

**File**: `composeApp/src/commonMain/kotlin/org/example/project/data/RecordingRepository.kt`

```kotlin
// Phase 1.1 — RecordingRepository: common interface for data layer (Android + iOS via SQLDelight)
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

Platform-agnostic interface. SQLDelight `RecordingRepositoryImpl` (Phase 1.3) implements this.

**Verify**: Interface is defined; all 7 methods present; returns `Flow<List<RecordingUiItem>>`.

---

### Phase 1.2 — RecordingEntity (Android-only)

**File**: `composeApp/src/androidMain/kotlin/org/example/project/db/RecordingEntity.kt`

`@Entity(tableName = "recordings")` data class. Fields:
- `id: Long = 0L` — `@PrimaryKey(autoGenerate = true)`
- `filePath: String` — absolute path to .mp4 file
- `fileName: String` — display name
- `createdAt: Long` — epoch millis
- `durationMs: Long = 0L` — placeholder
- `transcriptionText: String?` — null until done
- `transcriptionStatus: String` — one of 4 constants below
- `transcriptionError: String?` — error message on failure

Companion object constants:
```kotlin
const val STATUS_PENDING = "PENDING"
const val STATUS_IN_PROGRESS = "IN_PROGRESS"
const val STATUS_DONE = "DONE"
const val STATUS_ERROR = "ERROR"
```

**Verify**: File compiles; `@Entity`, `@PrimaryKey` annotations are correct; 4 constants exist in companion.

---

### Phase 1.2 — RecordingDao

**File**: `composeApp/src/androidMain/kotlin/org/example/project/db/RecordingDao.kt`

`@Dao` interface with 7 methods:
- `suspend fun insert(recording: RecordingEntity): Long` — `@Insert(onConflict = OnConflictStrategy.REPLACE)`
- `suspend fun updateTranscriptionStatus(id: Long, status: String)` — `@Query("UPDATE recordings SET transcriptionStatus = :status WHERE id = :id")`
- `suspend fun updateTranscriptionResult(id: Long, text: String, status: String)` — `@Query` updating both columns
- `suspend fun updateTranscriptionError(id: Long, error: String, status: String)` — `@Query` updating both columns
- `fun getAllRecordings(): Flow<List<RecordingEntity>>` — `@Query("SELECT * FROM recordings ORDER BY createdAt DESC")` — **NOT suspend**
- `suspend fun getRecordingById(id: Long): RecordingEntity?` — `@Query` with `LIMIT 1`
- `suspend fun deleteRecording(id: Long)` — `@Query("DELETE FROM recordings WHERE id = :id")`

**Verify**: `getAllRecordings()` returns `Flow` not a suspend fun; all column names match Phase 1.1 entity.

---

### Phase 1.4 — AppDatabase (Android-only)

**File**: `composeApp/src/androidMain/kotlin/org/example/project/db/AppDatabase.kt`

```kotlin
@Database(entities = [RecordingEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "voice_recordings.db").build()
    }
}
```

**Verify**: `@Database` lists `RecordingEntity::class` with `version = 1`; `exportSchema = false`.

---

### Phase 1.5 — RecordingRepositoryAndroid (Android Implementation)

**File**: `composeApp/src/androidMain/kotlin/org/example/project/db/RecordingRepositoryAndroid.kt`

```kotlin
// Phase 1.5 — RecordingRepositoryAndroid: Room-based implementation of common RecordingRepository
class RecordingRepositoryAndroid(private val dao: RecordingDao) : RecordingRepository { ... }
```

`class RecordingRepositoryAndroid(private val dao: RecordingDao) : RecordingRepository` implements all interface methods:
- `suspend fun insertRecording(filePath: String, fileName: String): Long` — creates entity with `STATUS_PENDING`, `createdAt = System.currentTimeMillis()`, calls `dao.insert()`, returns generated id
- `suspend fun markInProgress(id: Long)` — calls `dao.updateTranscriptionStatus(id, STATUS_IN_PROGRESS)`
- `suspend fun markDone(id: Long, text: String)` — calls `dao.updateTranscriptionResult(id, text, STATUS_DONE)`
- `suspend fun markError(id: Long, error: String)` — calls `dao.updateTranscriptionError(id, error, STATUS_ERROR)`
- `fun getAllRecordings(): Flow<List<RecordingUiItem>>` — **maps `RecordingEntity` → `RecordingUiItem` internally** using `.map { entities -> entities.map { it.toUiItem() } }`
- `suspend fun getRecordingById(id: Long): RecordingEntity?`
- `suspend fun deleteRecording(id: Long)`

**Important mapping logic** (in the same file as private extension):
```kotlin
private fun RecordingEntity.toUiItem(): RecordingUiItem = RecordingUiItem(
    id = id, fileName = fileName, filePath = filePath, createdAt = createdAt,
    transcription = when (transcriptionStatus) {
        STATUS_IN_PROGRESS -> TranscriptionUiState.InProgress
        STATUS_DONE -> TranscriptionUiState.Done(transcriptionText ?: "")
        STATUS_ERROR -> TranscriptionUiState.Error(transcriptionError ?: "Unknown error")
        else -> TranscriptionUiState.Pending
    }
)
```

This keeps Room entities **internal to androidMain**. The commonMain ViewModel only sees `RecordingUiItem`.

**Verify**: `getAllRecordings()` returns `Flow<List<RecordingUiItem>>`; mapping happens in repository.

---

### Phase 1.6 — Gradle + Koin Registration (Android)

**`gradle/libs.versions.toml`** — add:
```toml
[versions]
androidx-room = "2.7.1"
ksp = "2.3.20-1.0.29"   # must match kotlin = "2.3.20"

[libraries]
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "androidx-room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "androidx-room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "androidx-room" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

**`composeApp/build.gradle.kts`**:
- Add `alias(libs.plugins.ksp)` to `plugins {}` block
- Add to `androidMain.dependencies {}`: `implementation(libs.androidx.room.runtime)` + `implementation(libs.androidx.room.ktx)`
- Add top-level: `add("kspAndroid", libs.androidx.room.compiler)` (KSP for KMP targets `kspAndroid`, not `kapt`)

**`AndroidModule.kt`** — add:
```kotlin
// Phase 1.6 — DB, DAO, and Repository Android implementation
single { AppDatabase.create(androidContext()) }
single { get<AppDatabase>().recordingDao() }
single<RecordingRepository> { RecordingRepositoryAndroid(get()) }  // bind interface to Android impl
```

**Verify**: `./gradlew assembleDebug` succeeds; no KSP errors; `RecordingRepository` interface is injectable (bound to Android impl).

---

## Phase 2: ViewModel + UiState + UI (All Shared in `commonMain`)

### Checklist
- [ ] **2.1** — `RecordingUiState.kt` (commonMain) — sealed `TranscriptionUiState`, `RecordingUiItem`, `RecordingScreenUiState`; `toUiItem()` extension
- [ ] **2.2** — `RecordingViewModel.kt` (commonMain) — `StateFlow`, recording methods, TODO stubs for TranscriptionHandler (Phase 4)
- [ ] **2.3** — `RecordingScreen.kt` (commonMain) — shared UI; uses `koinViewModel()` + `collectAsStateWithLifecycle()`; update `App.kt` to call it

---

### Phase 2.1 — RecordingUiState

**File**: `composeApp/src/commonMain/kotlin/org/example/project/viewmodel/RecordingUiState.kt`

```kotlin
// Phase 2.1 — RecordingUiState
sealed class TranscriptionUiState {
    object Pending : TranscriptionUiState()
    object InProgress : TranscriptionUiState()
    data class Done(val text: String) : TranscriptionUiState()
    data class Error(val message: String) : TranscriptionUiState()
}

data class RecordingUiItem(
    val id: Long,
    val fileName: String,
    val filePath: String,
    val createdAt: Long,
    val transcription: TranscriptionUiState
)

data class RecordingScreenUiState(
    val recordings: List<RecordingUiItem> = emptyList(),
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val permissionGranted: Boolean = false,
    val statusMessage: String = "",
    val isLoading: Boolean = true
)
```

**Important**: The `RecordingRepository` (androidMain) will do the Entity → UiItem mapping internally and return `Flow<List<RecordingUiItem>>` instead of `Flow<List<RecordingEntity>>`. This way, the commonMain ViewModel doesn't need to know about Room entities or the mapper.

**Verify**: `RecordingScreenUiState` contains no platform-specific types; `isLoading = true` default is present.

---

### Phase 2.2 — RecordingViewModel

**File**: `composeApp/src/commonMain/kotlin/org/example/project/viewmodel/RecordingViewModel.kt`

```kotlin
// Phase 2.2 — RecordingViewModel (shared in commonMain, depends on platform impls via Koin)
class RecordingViewModel(
    private val audioRecorder: AudioRecorder,
    private val repository: RecordingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingScreenUiState())
    val uiState: StateFlow<RecordingScreenUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // repository.getAllRecordings() returns Flow<List<RecordingUiItem>> 
            // (mapping done in androidMain RecordingRepository)
            repository.getAllRecordings().collect { uiItems ->
                _uiState.update { it.copy(recordings = uiItems, isLoading = false) }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) { ... }
    fun startRecording() { ... }
    fun pauseRecording() { ... }
    fun resumeRecording() { ... }

    fun stopRecording() {
        viewModelScope.launch {
            val filePath = withContext(Dispatchers.IO) { audioRecorder.stopRecording("") }
            if (filePath != null) {
                val id = withContext(Dispatchers.IO) {
                    repository.insertRecording(filePath, filePath.substringAfterLast("/"))
                }
                _uiState.update { it.copy(isRecording = false) }
                // TODO Phase 4.2: transcriptionHandler.enqueue(id, filePath)
            }
        }
    }

    fun deleteRecording(id: Long, filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            File(filePath).delete()
            repository.deleteRecording(id)
        }
    }

    fun retryTranscription(id: Long, filePath: String) {
        // TODO Phase 4.2: wire TranscriptionHandler
    }
}
```

**Verify**: `_uiState` is `MutableStateFlow`; `stopRecording` calls recorder on `Dispatchers.IO`; two TODO stubs are present and clearly labelled.

---

### Phase 2.3 — RecordingScreen Migration (to Compose Multiplatform)

**Files modified**:

**`composeApp/src/commonMain/kotlin/org/example/project/ui/RecordingScreen.kt`** — SHARED for Android + iOS:
- `val viewModel: RecordingViewModel = koinViewModel()` — Koin injects platform implementations
- `val uiState by viewModel.uiState.collectAsStateWithLifecycle()`
- All buttons delegate to `viewModel.*()` methods
- `SavedFileItem` accepts `RecordingUiItem` (not `File`)
- No `loadSavedFiles()` function — DB is source of truth
- `onDelete = { viewModel.deleteRecording(item.id, item.filePath) }`
- Inside card body: `// TODO Phase 5: transcription UI here`
- MediaPlayer state stays in composable via `remember {}` (lifecycle concern, not business logic)
- **Android-specific permission handling**: wrap permission button in `if (Build.VERSION.SDK_INT >= 23)` or use `expect fun` for cross-platform permission logic

**`composeApp/src/androidMain/kotlin/org/example/project/AndroidApp.kt`** — update to:
```kotlin
@Composable
fun AndroidApp() {
    MaterialTheme {
        RecordingScreen()  // now from commonMain, fully shared
    }
}
```

**`composeApp/src/commonMain/kotlin/org/example/project/App.kt`** — update to call `RecordingScreen()` (for both Android + iOS):
```kotlin
@Composable
fun App() {
    MaterialTheme {
        RecordingScreen()
    }
}
```

**`AndroidModule.kt`** — add:
```kotlin
// Phase 2.3 — ViewModel registration (commonMain ViewModel uses androidMain implementations)
viewModel { RecordingViewModel(get(), get()) }
```

**Verify**: 
- RecordingScreen is in `commonMain/ui/` (not `androidMain/ui/`)
- No `List<File>` anywhere
- Both Android and iOS use the same composable
- Android and iOS both show recording list from their respective implementations

---

## Phase 3: Whisper.cpp JNI Setup (Android)

### Checklist
- [ ] **3.1** — Vendor whisper.cpp source files + `CMakeLists.txt` (pinned commit hash)
- [ ] **3.2** — `whisper_jni.cpp` — 3 JNI functions matching package `org.example.project.transcription` / class `WhisperContext`
- [ ] **3.3** — Wire `externalNativeBuild` + NDK ABI filters (`arm64-v8a`, `x86_64`) into `build.gradle.kts`
- [ ] **3.4** — `WhisperContext.kt` — Kotlin JNI wrapper, `System.loadLibrary("whisper_jni")`, private constructor
- [ ] **3.5** — `AudioDecoder.kt` — `MediaExtractor` + `MediaCodec`; 44.1kHz→16kHz linear-interpolation resample; stereo→mono downmix; normalize to `FloatArray` (÷ 32767.0f)
- [ ] **3.6** — `ggml-tiny.en-q5_1.bin` in `assets/models/` + `ModelManager.kt` (copy-on-first-run to `filesDir/models/`); register in Koin

---

### Phase 3.1 — Vendor Sources + CMakeLists.txt

**Directory**: `composeApp/src/androidMain/cpp/`

Files to copy verbatim from whisper.cpp repo (add pinned commit hash as comment in CMakeLists.txt):
- `whisper.h`, `whisper.cpp`
- `ggml.h`, `ggml.c`
- `ggml-alloc.h`, `ggml-alloc.c`
- `ggml-backend.h`, `ggml-backend.c`

**`CMakeLists.txt`**:
```cmake
cmake_minimum_required(VERSION 3.22.1)
project(whisper_jni)

# Vendor: whisper.cpp @ [PINNED_COMMIT_HASH] — update when upgrading

add_library(whisper STATIC
    ggml.c ggml-alloc.c ggml-backend.c whisper.cpp
)
target_include_directories(whisper PUBLIC .)
target_compile_options(whisper PRIVATE -O3 -ffast-math)

# whisper_jni.cpp added in Phase 3.2
add_library(whisper_jni SHARED whisper_jni.cpp)
target_link_libraries(whisper_jni whisper android log)
```

---

### Phase 3.2 — whisper_jni.cpp

**File**: `composeApp/src/androidMain/cpp/whisper_jni.cpp`

3 `extern "C"` JNI functions. Package: `org.example.project.transcription`, Class: `WhisperContext`:

- `Java_org_example_project_transcription_WhisperContext_createContextFromFile(env, this, jstring modelPath) → jlong`
  - Converts jstring → `std::string`, calls `whisper_init_from_file(path.c_str())`, returns pointer as `jlong`; returns `0L` on null
- `Java_org_example_project_transcription_WhisperContext_freeContext(env, this, jlong contextPtr)`
  - Casts ptr → `whisper_context*`, calls `whisper_free(ctx)`
- `Java_org_example_project_transcription_WhisperContext_transcribeFromFloats(env, this, jlong contextPtr, jfloatArray audioData) → jstring`
  - `whisper_full_params` with `language="en"`, `n_threads=4`, `translate=false`, `print_progress=false`
  - `env->GetFloatArrayElements` paired with `ReleaseFloatArrayElements`
  - Calls `whisper_full()`, loops segments to concatenate text
  - Returns empty string on non-zero `whisper_full` return

---

### Phase 3.3 — Wire CMake into build.gradle.kts

Inside `android {}` block in `composeApp/build.gradle.kts`:
```kotlin
// Phase 3.3 — CMake / NDK
externalNativeBuild {
    cmake {
        path = file("src/androidMain/cpp/CMakeLists.txt")
        version = "3.22.1"
    }
}
```

Inside `android { defaultConfig {} }`:
```kotlin
ndk {
    abiFilters += listOf("arm64-v8a", "x86_64")
}
```

**Verify**: `./gradlew assembleDebug` succeeds; `libwhisper_jni.so` present in `build/intermediates/cmake/`.

---

### Phase 3.4 — WhisperContext.kt

**File**: `composeApp/src/androidMain/kotlin/org/example/project/transcription/WhisperContext.kt`

```kotlin
// Phase 3.4 — WhisperContext: Kotlin JNI wrapper
class WhisperContext private constructor(private val ptr: Long) {

    fun transcribe(audioData: FloatArray): String {
        check(ptr != 0L) { "WhisperContext already freed" }
        return transcribeFromFloats(ptr, audioData)
    }

    fun free() {
        if (ptr != 0L) freeContext(ptr)
    }

    companion object {
        init { System.loadLibrary("whisper_jni") }

        fun createFromFile(modelPath: String): WhisperContext? {
            val ptr = createContextFromFile(modelPath)
            return if (ptr != 0L) WhisperContext(ptr) else null
        }

        @JvmStatic private external fun createContextFromFile(modelPath: String): Long
        @JvmStatic private external fun freeContext(contextPtr: Long)
        @JvmStatic private external fun transcribeFromFloats(contextPtr: Long, audioData: FloatArray): String
    }
}
```

**Verify**: `external` function names exactly match the JNI signatures in Phase 3.2; constructor is private.

---

### Phase 3.5 — AudioDecoder.kt

**File**: `composeApp/src/androidMain/kotlin/org/example/project/transcription/AudioDecoder.kt`

```kotlin
// Phase 3.5 — AudioDecoder: MP4/AAC → 16kHz mono FloatArray for Whisper
object AudioDecoder {
    suspend fun decodeToFloat(filePath: String): FloatArray { ... }
}
```

Implementation:
1. `MediaExtractor` → `setDataSource(filePath)` → find audio track by `mime.startsWith("audio/")`
2. `selectTrack(trackIndex)` → read `KEY_SAMPLE_RATE`, `KEY_CHANNEL_COUNT` from format
3. `MediaCodec.createDecoderByType(mime)` → `configure` → `start`
4. Input/output buffer decode loop → write output bytes to `ByteArrayOutputStream`
5. Convert `ByteArray` → `ShortArray` (16-bit LE PCM) → `FloatArray` (÷ `32767.0f`)
6. If `channelCount > 1`: average adjacent L+R pairs for mono downmix
7. If `sampleRate != 16000`: linear interpolation resample to 16000 Hz
8. `finally`: `codec.stop()`, `codec.release()`, `extractor.release()`

**Verify**: `MediaExtractor` + `MediaCodec` released in `finally`; resampling step is present; mono downmix is present.

---

### Phase 3.6 — Model Asset + ModelManager.kt

**Asset**: Place `ggml-tiny.en-q5_1.bin` at `composeApp/src/androidMain/assets/models/ggml-tiny.en-q5_1.bin`
(Download from: `https://huggingface.co/ggerganov/whisper.cpp` — `ggml-tiny.en-q5_1.bin`, ~31 MB)

**File**: `composeApp/src/androidMain/kotlin/org/example/project/transcription/ModelManager.kt`

```kotlin
// Phase 3.6 — ModelManager: copies model from assets to internal storage on first run
class ModelManager(private val context: Context) {

    suspend fun getModelPath(): String = withContext(Dispatchers.IO) {
        val target = File(context.filesDir, "models/ggml-tiny.en-q5_1.bin")
        if (!target.exists()) {
            target.parentFile?.mkdirs()
            context.assets.open("models/ggml-tiny.en-q5_1.bin").use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
        }
        target.absolutePath
    }
}
```

**`AndroidModule.kt`** — add:
```kotlin
// Phase 3.6 — ModelManager
single { ModelManager(androidContext()) }
```

**Verify**: `getModelPath()` is idempotent (won't copy again if file exists); uses `context.filesDir` (internal storage).

---

## Phase 4: TranscriptionHandler (Android)

### Checklist
- [ ] **4.1** — `TranscriptionHandler.kt` — `SupervisorJob + Dispatchers.IO` scope; `ConcurrentHashMap<Long, Job>` for job tracking; `enqueue/cancel/cancelAll/destroy`; register in Koin
- [ ] **4.2** — Wire into `RecordingViewModel` — add constructor param; replace TODO stubs with real calls; `onCleared()` comment
- [ ] **4.3** — Crash recovery — `recoverInterruptedTranscriptions()` in `init {}` using `.first()` one-shot; re-enqueue both `PENDING` + `IN_PROGRESS`

---

### Phase 4.1 — TranscriptionHandler.kt

**File**: `composeApp/src/androidMain/kotlin/org/example/project/transcription/TranscriptionHandler.kt`

```kotlin
// Phase 4.1 — TranscriptionHandler: background transcription with dedicated coroutine scope
class TranscriptionHandler(
    private val repository: RecordingRepository,
    private val modelManager: ModelManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    fun enqueue(id: Long, filePath: String) {
        if (activeJobs.containsKey(id)) return  // already running

        val job = scope.launch {
            try {
                repository.markInProgress(id)
                val modelPath = modelManager.getModelPath()
                val audioFloats = AudioDecoder.decodeToFloat(filePath)
                val ctx = WhisperContext.createFromFile(modelPath)
                    ?: throw IllegalStateException("Failed to load model: $modelPath")
                val text = try {
                    ctx.transcribe(audioFloats)
                } finally {
                    ctx.free()
                }
                repository.markDone(id, text)
            } catch (e: CancellationException) {
                repository.markError(id, "Transcription cancelled")
                throw e  // must re-throw CancellationException
            } catch (e: Exception) {
                repository.markError(id, e.message ?: "Unknown error")
            } finally {
                activeJobs.remove(id)
            }
        }
        activeJobs[id] = job
    }

    fun cancel(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
    }

    fun cancelAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }

    fun destroy() { scope.cancel() }
}
```

**`AndroidModule.kt`** — add:
```kotlin
// Phase 4.1 — TranscriptionHandler
single { TranscriptionHandler(get(), get()) }
```

**Verify**: `SupervisorJob` used (one failure doesn't cancel others); `CancellationException` is re-thrown; `activeJobs` is `ConcurrentHashMap`.

---

### Phase 4.2 — Wire into RecordingViewModel

**File modified**: `composeApp/src/androidMain/kotlin/org/example/project/viewmodel/RecordingViewModel.kt`

Add `private val transcriptionHandler: TranscriptionHandler` constructor param (3rd param).

Replace `stopRecording()` TODO stub:
```kotlin
// Phase 4.2 — auto-trigger transcription after recording is saved
transcriptionHandler.enqueue(id, filePath)
```

Replace `retryTranscription()` TODO stub:
```kotlin
// Phase 4.2 — retry: mark in-progress immediately for instant UI feedback
viewModelScope.launch(Dispatchers.IO) {
    repository.markInProgress(id)
    transcriptionHandler.enqueue(id, filePath)
}
```

Add `onCleared()`:
```kotlin
override fun onCleared() {
    super.onCleared()
    // Phase 4.2 — Do NOT cancel TranscriptionHandler here.
    // It is a Koin singleton with its own scope so in-flight transcriptions
    // survive ViewModel destruction (e.g. user navigates away mid-transcription).
}
```

Update Koin in `AndroidModule.kt`:
```kotlin
// Phase 4.2 — ViewModel with TranscriptionHandler
viewModel { RecordingViewModel(get(), get(), get()) }
```

**Verify**: `enqueue` called after `repository.insertRecording` succeeds, not before; retry calls `markInProgress` first.

---

### Phase 4.3 — Crash Recovery

**File modified**: `RecordingViewModel.kt`

Add to `init {}` (after the Flow collector launch):
```kotlin
// Phase 4.3 — recover PENDING + IN_PROGRESS transcriptions interrupted by crash or app-kill
viewModelScope.launch(Dispatchers.IO) {
    repository.getAllRecordings().first()
        .filter {
            it.transcriptionStatus == RecordingEntity.STATUS_PENDING ||
            it.transcriptionStatus == RecordingEntity.STATUS_IN_PROGRESS
        }
        .forEach { transcriptionHandler.enqueue(it.id, it.filePath) }
}
```

**Verify**: Uses `.first()` (one-shot), not `.collect()` (ongoing); only `PENDING` + `IN_PROGRESS` are re-enqueued; runs on `Dispatchers.IO`.

---

## Phase 5: UI Updates (Android)

### Checklist
- [ ] **5.1** — `TranscriptionStatusRow.kt` — pure composable; exhaustive `when` over all 4 `TranscriptionUiState` cases (no `else`)
- [ ] **5.2** — Wire `TranscriptionStatusRow` into `SavedFileItem`; add `onRetry` + `onViewTranscription` lambda params
- [ ] **5.3** — `TranscriptionDialog.kt` — `AlertDialog` with `SelectionContainer` for copy-paste
- [ ] **5.4** — Wire dialog into `RecordingScreen` with local `var transcriptionDialogItem` state (outside `LazyColumn`)

---

### Phase 5.1 — TranscriptionStatusRow.kt

**File**: `composeApp/src/androidMain/kotlin/org/example/project/ui/TranscriptionStatusRow.kt`

```kotlin
// Phase 5.1 — TranscriptionStatusRow: shows transcription state on a recording card
@Composable
fun TranscriptionStatusRow(
    state: TranscriptionUiState,
    onRetry: () -> Unit,
    onViewTranscription: () -> Unit
) {
    when (state) {
        is TranscriptionUiState.Pending -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
            Spacer(Modifier.width(6.dp))
            Text("Transcription pending...", style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is TranscriptionUiState.InProgress -> Column {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Transcribing...", style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is TranscriptionUiState.Done -> TextButton(onClick = onViewTranscription) {
            Text("View Transcription")
        }
        is TranscriptionUiState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.message, style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
```

**Verify**: All 4 cases handled with no `else` (compiler enforces exhaustiveness); `LinearProgressIndicator()` with no value argument = indeterminate.

---

### Phase 5.2 — Wire into SavedFileItem

**File modified**: `RecordingScreen.kt`

Add `onRetry: () -> Unit` and `onViewTranscription: () -> Unit` to `SavedFileItem` signature.

Replace `// TODO Phase 5.2: transcription UI here` with:
```kotlin
// Phase 5.2 — transcription status row
TranscriptionStatusRow(
    state = item.transcription,
    onRetry = onRetry,
    onViewTranscription = onViewTranscription
)
```

At `SavedFileItem` call site, add:
```kotlin
onRetry = { viewModel.retryTranscription(item.id, item.filePath) },
onViewTranscription = { /* TODO Phase 5.4 */ }
```

**Verify**: `SavedFileItem` has 2 new lambda params; transcription row appears inside each card.

---

### Phase 5.3 — TranscriptionDialog.kt

**File**: `composeApp/src/androidMain/kotlin/org/example/project/ui/TranscriptionDialog.kt`

```kotlin
// Phase 5.3 — TranscriptionDialog: view full transcription text with copy-paste support
@Composable
fun TranscriptionDialog(
    text: String,
    fileName: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(fileName) },
        text = {
            SelectionContainer {
                Text(text)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = null
    )
}
```

**Verify**: `SelectionContainer` wraps the text (enables long-press copy); `dismissButton = null` explicitly set; zero composable state.

---

### Phase 5.4 — Wire Dialog into RecordingScreen

**File modified**: `RecordingScreen.kt`

Add at top of `RecordingScreen` composable:
```kotlin
// Phase 5.4 — dialog state
var transcriptionDialogItem by remember { mutableStateOf<RecordingUiItem?>(null) }
```

Replace `onViewTranscription` no-op lambda:
```kotlin
onViewTranscription = { transcriptionDialogItem = item }
```

After the `LazyColumn` closing brace:
```kotlin
// Phase 5.4 — transcription dialog shown outside LazyColumn
transcriptionDialogItem?.let { dialogItem ->
    val text = (dialogItem.transcription as? TranscriptionUiState.Done)?.text ?: ""
    TranscriptionDialog(
        text = text,
        fileName = dialogItem.fileName,
        onDismiss = { transcriptionDialogItem = null }
    )
}
```

**Verify**: Dialog shown outside `LazyColumn`; dismissed by setting state to null; text is cast from `Done` with empty-string fallback.

---

## Phase 6: iOS Data Layer Implementation

> **Note**: iOS doesn't need its own RecordingScreen composable because **Phase 2.3 created a shared one in commonMain** that both Android and iOS use. Phase 6 implements the iOS-specific data layer (`RecordingRepositoryIOS`) so the shared ViewModel and UI work on iOS.

### Checklist
- [ ] **6.1** — `RecordingRepositoryIOS.kt` — JSON-based implementation of common `RecordingRepository` interface
- [ ] **6.2** — `IosModule.kt` — register `RecordingRepositoryIOS` bound to `RecordingRepository` interface
- [ ] **6.3** — Update `MainViewController.kt` to show the shared `RecordingScreen()` composable (no more placeholder)

---

### Phase 6.1 — RecordingRepositoryIOS.kt

**File**: `composeApp/src/iosMain/kotlin/org/example/project/data/RecordingRepositoryIOS.kt`

```kotlin
// Phase 6.1 — RecordingRepositoryIOS: JSON-based implementation of common RecordingRepository
class RecordingRepositoryIOS : RecordingRepository {
    private var recordings = mutableListOf<RecordingUiItem>()
    
    override suspend fun insertRecording(filePath: String, fileName: String): Long { ... }
    override suspend fun markInProgress(id: Long) { ... }
    override suspend fun markDone(id: Long, text: String) { ... }
    override suspend fun markError(id: Long, error: String) { ... }
    override fun getAllRecordings(): Flow<List<RecordingUiItem>> { ... }
    override suspend fun getRecordingById(id: Long): RecordingUiItem? { ... }
    override suspend fun deleteRecording(id: Long) { ... }
}
```

Stores/loads from `NSDocumentDirectory/recordings.json` on each change. Uses Kotlinx Serialization to convert `RecordingUiItem` to/from JSON.

---

### Phase 6.2 — Register in IosModule.kt

**File**: `composeApp/src/iosMain/kotlin/org/example/project/di/IosModule.kt`

```kotlin
// Phase 6.2 — iOS data layer registration
val iosModule = module {
    single<RecordingRepository> { RecordingRepositoryIOS() }
    // ... other iOS registrations (TranscriptionHandler, etc. come later in Phase 7)
}
```

---

### Phase 6.3 — Update iOS Entry Point

**File**: `composeApp/src/iosMain/kotlin/org/example/project/MainViewController.kt`

Update the composable to show the shared `RecordingScreen()`:
```kotlin
fun MainViewController() = ComposeUIViewController {
    initializeKoin()
    MaterialTheme {
        RecordingScreen()  // shared from commonMain
    }
}
```

**Verify**: iOS app shows the same recording screen as Android; recordings persist to JSON; ViewModel uses the iOS repository.

---

## Phase 7: iOS Whisper Integration (WhisperKit)

> WhisperKit (Swift Package by Argmax) is used instead of whisper.cpp C++ cinterop.
> Reasons: Core ML acceleration on Apple Silicon; simpler integration; no raw C++ bridging needed.

### Checklist
- [ ] **7.1** — Add WhisperKit SPM package to Xcode; create `TranscriptionBridge.swift` (`@objc` class with callback-based API)
- [ ] **7.2** — Kotlin/Native cinterop `.def` file + `build.gradle.kts` cinterop config
- [ ] **7.3** — `AudioDecoderIOS.kt` — `AVAssetReader` for M4A → 16kHz mono `FloatArray` *(may be skippable if WhisperKit handles format conversion natively — evaluate during implementation)*
- [ ] **7.4** — `TranscriptionHandlerIOS.kt` — mirrors Android handler; bridges callback to `suspendCoroutine`; register in Koin
- [ ] **7.5** — Bundle `ggml-tiny.en-q5_1.bin` in Xcode target resources; `ModelManagerIOS.kt` copies to `NSDocumentDirectory` on first run

---

### Phase 7.1 — WhisperKit + TranscriptionBridge.swift

**Xcode setup**: File → Add Package Dependency → `https://github.com/argmaxinc/WhisperKit` → `WhisperKit` product → `iosApp` target.

**File**: `iosApp/iosApp/TranscriptionBridge.swift`

```swift
// Phase 7.1 — TranscriptionBridge: @objc wrapper around WhisperKit for Kotlin/Native cinterop
import WhisperKit
import Foundation

@objc public class TranscriptionBridge: NSObject {
    @objc public static func transcribeFile(
        path: String,
        modelPath: String,
        completion: @escaping (String?, Error?) -> Void
    ) {
        Task {
            do {
                let whisperKit = try await WhisperKit(modelFolder: modelPath)
                let results = try await whisperKit.transcribe(audioPath: path)
                completion(results.map { $0.text }.joined(separator: " "), nil)
            } catch {
                completion(nil, error)
            }
        }
    }
}
```

---

### Phase 7.2 — Kotlin/Native cinterop

**File**: `composeApp/src/iosMain/cinterop/TranscriptionBridge.def`

```
# Phase 7.2 — cinterop definition for TranscriptionBridge ObjC class
language = Objective-C
headers = TranscriptionBridge.h
```

**`build.gradle.kts`** — add cinterop to `iosArm64` and `iosSimulatorArm64` targets:
```kotlin
// Phase 7.2 — cinterop for TranscriptionBridge
compilations["main"].cinterops {
    create("TranscriptionBridge") {
        defFile(project.file("src/iosMain/cinterop/TranscriptionBridge.def"))
    }
}
```

---

### Phase 7.3 — AudioDecoderIOS.kt

**File**: `composeApp/src/iosMain/kotlin/org/example/project/transcription/AudioDecoderIOS.kt`

```kotlin
// Phase 7.3 — AudioDecoderIOS: M4A → 16kHz mono FloatArray via AVAssetReader
// Note: evaluate if WhisperKit handles this natively before implementing
object AudioDecoderIOS {
    suspend fun decodeToFloat(filePath: String): FloatArray { ... }
}
```

Uses `AVAssetReader` + `AVAssetReaderAudioMixOutput` with settings:
- `AVFormatIDKey = kAudioFormatLinearPCM`
- `AVSampleRateKey = 16000`
- `AVNumberOfChannelsKey = 1`
- `AVLinearPCMBitDepthKey = 32`
- `AVLinearPCMIsFloatKey = true`

---

### Phase 7.4 — TranscriptionHandlerIOS.kt

**File**: `composeApp/src/iosMain/kotlin/org/example/project/transcription/TranscriptionHandlerIOS.kt`

```kotlin
// Phase 7.4 — TranscriptionHandlerIOS: mirrors Android TranscriptionHandler using WhisperKit bridge
class TranscriptionHandlerIOS(
    private val storage: RecordingStorageIOS,
    private val modelManager: ModelManagerIOS
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeJobs = mutableMapOf<Long, Job>()  // Kotlin/Native: no ConcurrentHashMap, use mutex

    fun enqueue(id: Long, filePath: String) { ... }
    fun cancel(id: Long) { ... }
    fun destroy() { scope.cancel() }
}
```

Bridge callback to coroutine via `suspendCoroutine<String> { cont -> TranscriptionBridge.transcribeFile(filePath, modelPath) { text, error -> ... } }`.

Register in `IosModule.kt`:
```kotlin
// Phase 7.4 — TranscriptionHandlerIOS
single { TranscriptionHandlerIOS(get(), get()) }
```

---

### Phase 7.5 — iOS Model Bundle + ModelManagerIOS.kt

**Xcode**: Add `ggml-tiny.en-q5_1.bin` to Xcode project → target → Build Phases → Copy Bundle Resources.

**File**: `composeApp/src/iosMain/kotlin/org/example/project/transcription/ModelManagerIOS.kt`

```kotlin
// Phase 7.5 — ModelManagerIOS: copies bundled model to NSDocumentDirectory on first run
class ModelManagerIOS {
    suspend fun getModelPath(): String {
        // Target: NSDocumentDirectory/models/ggml-tiny.en-q5_1.bin
        // Copy from NSBundle.mainBundle if not present
        // Return target path
    }
}
```

Register in `IosModule.kt`:
```kotlin
// Phase 7.5 — ModelManagerIOS
single { ModelManagerIOS() }
```

---

---

## Compose Multiplatform Architecture Summary

**Shared Code (commonMain)** — Write once, run on both Android & iOS:
- `RecordingUiState.kt` — data classes for UI state
- `RecordingRepository` interface — common data contract
- `RecordingViewModel` — all business logic
- `RecordingScreen.kt` — all UI composables
- `TranscriptionStatusRow.kt`, `TranscriptionDialog.kt` — UI components

**Android Implementation (androidMain)**:
- `RecordingRepositoryAndroid` — Room-based implementation
- `RecordingEntity`, `RecordingDao`, `AppDatabase` — Room schema + access
- `WhisperContext.kt` — JNI wrapper for Whisper.cpp
- `AudioDecoder.kt` — MP4 → 16kHz PCM conversion
- `ModelManager.kt` — bundle model to filesDir
- `TranscriptionHandler.kt` — background transcription

**iOS Implementation (iosMain)**:
- `RecordingRepositoryIOS` — JSON-based implementation (Phase 6)
- `AudioDecoderIOS.kt` — M4A → 16kHz PCM conversion
- `ModelManagerIOS.kt` — bundle model to NSDocumentDirectory
- `TranscriptionHandlerIOS.kt` — WhisperKit integration

**Koin wiring**: Register platform-specific `RecordingRepositoryAndroid` (or `RecordingRepositoryIOS`) bound to the common `RecordingRepository` interface. ViewModel depends on the interface, not the implementation.

---

## Key Architectural Decisions

| Decision | Rationale |
|---|---|
| **Common `RecordingRepository` interface** | Both Android (Room) and iOS (JSON) implement the same interface. ViewModel depends on the interface, not the concrete impl. Enables true code sharing. |
| **KSP not KAPT for Room** | KAPT is deprecated for KMP. `kspAndroid` applies annotation processing only to Android source set. |
| **TranscriptionHandler is Koin singleton** | Transcription must outlive ViewModel. In-flight jobs survive user navigation. |
| **WhisperContext created per-transcription** | Context holds ~100 MB RAM. Freed immediately after use. Acceptable for a memo app. |
| **AudioDecoder resamples 44.1kHz→16kHz** | Recorder uses 44.1kHz AAC; Whisper requires 16kHz PCM. Decode at transcription time. |
| **WhisperKit for iOS** | Core ML acceleration on Apple Silicon; far simpler than raw whisper.cpp C++ cinterop. |
| **JSON storage for iOS (Phase 6)** | Room is Android-only. SQLDelight adds scope. JSON is a simple interim for Phase 6. |
| **PENDING + IN_PROGRESS recovered on restart** | PENDING can get stuck if killed between insert and enqueue. Recovering both avoids silent failures. |

---

## Verification Checkpoints

| After phase | What to verify |
|---|---|
| Phase 1 | `./gradlew assembleDebug` with no KSP errors; `RecordingRepository` interface and Android impl injectable |
| Phase 2 | Recording list loads from DB; record/stop/delete work; shared RecordingScreen in commonMain |
| Phase 3 | `libwhisper_jni.so` in build outputs; `WhisperContext.createFromFile()` returns non-null |
| Phase 4 | Stop recording auto-triggers transcription; crash → restart → jobs re-enqueued |
| Phase 5 | Cards show correct state; retry works; dialog opens with selectable text |
| Phase 6 | iOS app shows same recording screen as Android (from commonMain); iOS recordings persist to JSON; ViewModel works on iOS |
| Phase 7 | iOS transcription runs end-to-end via WhisperKit |
