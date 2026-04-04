# Whisper.cpp Transcription — Implementation Plan (SQLDelight Version)

## Context

**Why**: Add automatic speech-to-text transcription to every recording. After the user stops recording, the app should transcribe it in the background using Whisper.cpp (Android) and WhisperKit (iOS), show a progress indicator on the recording card, and surface the result with a "View Transcription" button. Retry is available on failure.

**Current state**: KMP project (Compose Multiplatform, Android + iOS). No DB, no ViewModel, no data models — `RecordingScreen.kt` (androidMain only) is raw `remember {}` state, iterating `List<File>` from disk.

**Outcome**: End-to-end transcription flow with **shared UI**, **shared ViewModel**, **shared DB layer** (SQLDelight), platform-specific transcription (Whisper.cpp on Android, WhisperKit on iOS).

## User Decisions
- **Model delivery**: Bundle `ggml-tiny.en-q5_1.bin` (~31 MB) in APK/Xcode bundle
- **iOS scope**: Include iOS (Phases 6–7)
- **Recovery on restart**: Re-enqueue both `PENDING` + `IN_PROGRESS` items
- **DB approach**: **SQLDelight** for true code sharing (not Room + JSON)

---

## Architecture: Testable & Scalable

### Design Principles

1. **Dependency Injection (Koin)** — All dependencies are registered; easy to mock in tests
2. **Repository Pattern** — Data access abstracted behind interface
3. **Orchestrators/Handlers** — Business logic isolated in dedicated classes (`TranscriptionHandler`, `AudioDecoder`)
4. **Helpers** — Reusable utilities for state mapping, validation, etc.
5. **Separation of Concerns** — UI, ViewModel, Repository, Handlers clearly separated
6. **Test Doubles** — Mock repository, mock transcription handler, fake audio decoder

### Folder Structure

```
commonMain/
  ├── data/
  │   ├── RecordingRepository.kt        (interface — contract for data access)
  │   └── RecordingRepositoryImpl.kt     (SQLDelight-backed implementation)
  ├── viewmodel/
  │   ├── RecordingViewModel.kt         (orchestrates UI state + recording)
  │   ├── RecordingUiState.kt           (UI state data classes)
  │   └── helpers/
  │       └── RecordingStateMapper.kt   (maps DB entity → UI state)
  └── ui/
      ├── RecordingScreen.kt           (root composable)
      ├── TranscriptionStatusRow.kt    (status indicator component)
      ├── TranscriptionDialog.kt       (transcription viewer)
      └── helpers/
          └── RecordingCardBuilder.kt  (card layout logic)

androidMain/
  ├── data/
  │   └── (SQLDelight driver setup — see Phase 1.5)
  ├── transcription/
  │   ├── WhisperContext.kt            (JNI wrapper — single responsibility: call Whisper.cpp)
  │   ├── AudioDecoder.kt              (orchestrator: convert MP4 → 16kHz PCM)
  │   ├── ModelManager.kt              (helper: manage model asset bundling)
  │   └── TranscriptionHandler.kt      (orchestrator: manage transcription lifecycle + errors)
  └── cpp/
      ├── CMakeLists.txt
      └── whisper_jni.cpp

iosMain/
  ├── data/
  │   └── (SQLDelight driver setup)
  ├── transcription/
  │   ├── TranscriptionBridge.swift    (bridge to WhisperKit)
  │   ├── AudioDecoderIOS.kt           (AVAssetReader helper)
  │   ├── ModelManagerIOS.kt           (model bundling helper)
  │   └── TranscriptionHandlerIOS.kt   (orchestrator: WhisperKit-based transcription)
```

### Key Classes & Their Responsibilities

| Class | Responsibility | Testable | Reason |
|---|---|---|---|
| `RecordingViewModel` | Orchestrate UI state + recording lifecycle | ✅ | Depends on `RecordingRepository` + `AudioRecorder` (mockable interfaces) |
| `RecordingRepositoryImpl` | Execute DB queries via SQLDelight | ✅ | Can provide in-memory test database |
| `TranscriptionHandler` | Manage background transcription + retries | ✅ | Depends on `Repository` + `AudioDecoder` + `WhisperContext` (all mockable) |
| `AudioDecoder` | Convert audio format + resample | ✅ | Pure function; no side effects; can test with fixtures |
| `RecordingStateMapper` | Map DB entity → UI state | ✅ | Pure mapping function; deterministic |
| `WhisperContext` | Call Whisper.cpp JNI | ⚠️ (requires device/emulator) | JNI call is hard to mock; test via integration test on emulator |

### Testing Strategy

**Unit tests (in `commonTest`)**:
- `RecordingViewModelTest` — mock repository, test state transitions
- `RecordingStateMapperTest` — pure mapping logic
- `RecordingRepositoryImplTest` — test with in-memory SQLDelight database

**Android Integration tests (`androidTest`)**:
- `AudioDecoderTest` — test MP4→PCM conversion with sample file
- `TranscriptionHandlerTest` — test transcription flow with mocked Whisper

**iOS Integration tests (`iosTest`)**:
- `AudioDecoderIOSTest` — test M4A→PCM conversion
- Similar transcription handler test

---

## Phase 1: Shared DB Layer (SQLDelight)

Both Android & iOS use **identical** SQLDelight code. No duplication.

### Checklist

- [ ] **1.1** — `RecordingRepository.kt` (commonMain) — interface defining operations
- [ ] **1.2** — `Recording.sqldelight` (commonMain) — SQL schema + 7 queries
- [ ] **1.3** — `RecordingRepositoryImpl.kt` (commonMain) — wraps SQLDelight `Database`
- [ ] **1.4** — `libs.versions.toml` + `build.gradle.kts` — add SQLDelight plugin + drivers
- [ ] **1.5** — Koin setup — register `AppDatabase` + `RecordingRepositoryImpl` (commonMain)

---

### Phase 1.1 — RecordingRepository Interface

**File**: `composeApp/src/commonMain/kotlin/org/example/project/data/RecordingRepository.kt`

```kotlin
// Phase 1.1 — RecordingRepository: data access contract (Android + iOS)
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

Platform-agnostic. Implemented by `RecordingRepositoryImpl` backed by SQLDelight.

---

### Phase 1.2 — Recording.sqldelight Schema

**File**: `composeApp/src/commonMain/sqldelight/org/example/project/Recording.sqldelight`

```sql
-- Phase 1.2 — Recording schema (shared SQLDelight, compiled to Android + iOS)

CREATE TABLE recording (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    filePath TEXT NOT NULL,
    fileName TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    durationMs INTEGER NOT NULL DEFAULT 0,
    transcriptionText TEXT,
    transcriptionStatus TEXT NOT NULL,
    transcriptionError TEXT
);

-- Insert new recording with PENDING status
insertRecording:
INSERT INTO recording(filePath, fileName, createdAt, transcriptionStatus)
VALUES (?, ?, ?, 'PENDING')
RETURNING id;

-- Update transcription status
updateTranscriptionStatus:
UPDATE recording SET transcriptionStatus = ? WHERE id = ?;

-- Update with transcription result (DONE)
updateTranscriptionResult:
UPDATE recording SET transcriptionText = ?, transcriptionStatus = ? WHERE id = ?;

-- Update with error
updateTranscriptionError:
UPDATE recording SET transcriptionError = ?, transcriptionStatus = ? WHERE id = ?;

-- Get all recordings (newest first)
getAllRecordings:
SELECT * FROM recording ORDER BY createdAt DESC;

-- Get single recording
getRecordingById:
SELECT * FROM recording WHERE id = ? LIMIT 1;

-- Delete recording
deleteRecording:
DELETE FROM recording WHERE id = ?;
```

**Verify**: SQL compiles; 7 queries present; no platform-specific syntax.

---

### Phase 1.3 — RecordingRepositoryImpl

**File**: `composeApp/src/commonMain/kotlin/org/example/project/data/RecordingRepositoryImpl.kt`

```kotlin
// Phase 1.3 — RecordingRepositoryImpl: SQLDelight-backed repository (shared for Android + iOS)
class RecordingRepositoryImpl(private val database: AppDatabase) : RecordingRepository {
    private val queries = database.recordingQueries

    override suspend fun insertRecording(filePath: String, fileName: String): Long =
        withContext(Dispatchers.IO) {
            queries.insertRecording(filePath, fileName, System.currentTimeMillis())
            // SQLDelight RETURNING clause gives us the ID
            queries.getAllRecordings().executeAsOne().id
        }

    override suspend fun markInProgress(id: Long) = withContext(Dispatchers.IO) {
        queries.updateTranscriptionStatus("IN_PROGRESS", id)
    }

    override suspend fun markDone(id: Long, text: String) = withContext(Dispatchers.IO) {
        queries.updateTranscriptionResult(text, "DONE", id)
    }

    override suspend fun markError(id: Long, error: String) = withContext(Dispatchers.IO) {
        queries.updateTranscriptionError(error, "ERROR", id)
    }

    override fun getAllRecordings(): Flow<List<RecordingUiItem>> =
        database.recordingQueries.getAllRecordings()
            .asFlow()
            .mapToList()
            .map { records -> records.map { it.toUiItem() } }

    override suspend fun getRecordingById(id: Long): RecordingUiItem? =
        withContext(Dispatchers.IO) {
            database.recordingQueries.getRecordingById(id).executeAsOneOrNull()?.toUiItem()
        }

    override suspend fun deleteRecording(id: Long) = withContext(Dispatchers.IO) {
        database.recordingQueries.deleteRecording(id)
    }

    // Helper: map SQLDelight Recording → RecordingUiItem
    private fun Recording.toUiItem(): RecordingUiItem = RecordingUiItem(
        id = id,
        fileName = fileName,
        filePath = filePath,
        createdAt = createdAt,
        transcription = when (transcriptionStatus) {
            "IN_PROGRESS" -> TranscriptionUiState.InProgress
            "DONE" -> TranscriptionUiState.Done(transcriptionText ?: "")
            "ERROR" -> TranscriptionUiState.Error(transcriptionError ?: "Unknown error")
            else -> TranscriptionUiState.Pending
        }
    )
}
```

`Recording` is auto-generated by SQLDelight from the schema.

---

### Phase 1.4 — Gradle Configuration

**`gradle/libs.versions.toml`**:
```toml
[versions]
sqldelight = "2.4.1"

[libraries]
sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-android = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-native = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }

[plugins]
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

**`composeApp/build.gradle.kts`**:
```kotlin
plugins {
    alias(libs.plugins.sqldelight)  // add this
    // ... other plugins
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native)
        }
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("org.example.project.data")
            srcDirs.set(listOf("src/commonMain/sqldelight"))
        }
    }
}
```

---

### Phase 1.5 — Koin Setup

**`commonMain/AppModule.kt`**:
```kotlin
// Phase 1.5 — Common data layer registration
val appModule = module {
    single { RecordingRepositoryImpl(get()) }
    bind<RecordingRepository>()  // bind interface to impl
}
```

**`androidMain/AndroidModule.kt`**:
```kotlin
// Phase 1.5 — Android SQLDelight driver
val androidModule = module {
    single {
        AppDatabase(driver = AndroidSqliteDriver(AppDatabase.Schema, androidContext(), "voice_recordings.db"))
    }
}
```

**`iosMain/IosModule.kt`**:
```kotlin
// Phase 1.5 — iOS SQLDelight driver
val iosModule = module {
    single {
        AppDatabase(driver = NativeSqliteDriver(AppDatabase.Schema, "voice_recordings.db"))
    }
}
```

**Verify**: `./gradlew assembleDebug` succeeds; SQLDelight code generation completes; both Android & iOS use the same repository implementation.

---

## Phase 2: Shared ViewModel + UI (same as before)

[ViewModel, UI components, state classes — all in commonMain]

---

## Remaining Phases (3–7)

[Transcription, error handling, retry logic, iOS integration — as documented in original plan]

---

## Benefits of This Architecture

| Aspect | Benefit |
|---|---|
| **Code sharing** | 100% shared DB layer + UI + ViewModel |
| **Testability** | Repository interface; mock in tests; in-memory DB for unit tests |
| **Scalability** | New features just extend `RecordingRepository` interface |
| **Maintainability** | Single schema, single query logic — no duplication |
| **Readability** | Clear separation: repository, ViewModel, UI, handlers, helpers |
| **Type safety** | SQLDelight generates compile-checked queries; no SQL strings |

