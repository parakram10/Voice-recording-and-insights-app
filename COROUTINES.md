# COROUTINES.md — Threading Model & Async Patterns

**Goal**: Move heavy work off main thread → smooth UI, no ANRs, learn coroutine concepts.

**Non-Engineering Principle**: Use coroutines where they solve a real problem (blocking operations, long-running tasks, reactive updates). Don't use for trivial operations.

---

## Threading Model: Where Each Operation Lives

### Main Thread
- UI rendering (Compose recomposition)
- UI event handlers (button clicks, etc.)
- State updates (StateFlow emissions)

### IO Thread (`Dispatchers.IO`)
- Database reads/writes (repository queries)
- File I/O (audio decoding, model copying)
- Network calls (if added in future)

### Default Thread (`Dispatchers.Default`)
- CPU-intensive computation (audio resampling, if heavy)
- Transcription (Whisper.cpp inference)

### `viewModelScope` (Main dispatcher)
- ViewModel-scoped coroutines
- Auto-cancels when ViewModel cleared
- Safe for UI state updates

### Custom Scope (e.g., `TranscriptionHandler`)
- Long-lived background jobs (transcription)
- Survives ViewModel destruction
- Must manually manage cancellation

---

## Pattern 1: Repository Operations (IO Thread)

### ❌ **Bad** — Blocks main thread
```kotlin
// DON'T DO THIS
fun stopRecording() {
    val filePath = audioRecorder.stopRecording("")  // ← blocking call!
    val id = repository.insertRecording(filePath, "audio.mp4")  // ← blocking DB write!
}
```

### ✅ **Good** — IO thread via coroutine
```kotlin
// Phase 2.2 — ViewModel
fun stopRecording() {
    viewModelScope.launch {  // launch on Main (default)
        val filePath = withContext(Dispatchers.IO) {  // switch to IO
            audioRecorder.stopRecording("")  // blocking; safe on IO thread
        }
        if (filePath != null) {
            val id = withContext(Dispatchers.IO) {  // switch to IO again
                repository.insertRecording(filePath, filePath.substringAfterLast("/"))
            }
            _uiState.update { it.copy(isRecording = false) }  // back to Main
        }
    }
}
```

**Why this works**:
- `viewModelScope.launch` — Launches on Main (safe for UI state)
- `withContext(Dispatchers.IO)` — Switches to IO thread for blocking call
- Auto-returns to Main after the block (no manual switching back)
- Entire coroutine cancelled if ViewModel destroyed

---

## Pattern 2: Reactive Data with Flow (Main Thread Updates)

### ❌ **Bad** — Manual state management, no reactivity
```kotlin
// DON'T DO THIS
var recordings: List<File> = emptyList()

fun loadRecordings() {
    viewModelScope.launch {
        recordings = File(dir).listFiles().toList()  // loses type, not reactive
        // UI must manually call this function; doesn't auto-update if DB changes
    }
}
```

### ✅ **Good** — Flow-based, auto-updates
```kotlin
// Phase 2.2 — ViewModel (in init block)
init {
    viewModelScope.launch {
        repository.getAllRecordings()  // returns Flow<List<RecordingUiItem>>
            .collect { uiItems ->
                _uiState.update { it.copy(recordings = uiItems, isLoading = false) }
            }
    }
}
```

**Why this works**:
- `Flow` emits data asynchronously
- `collect` receives each emission and updates state
- UI observes `StateFlow<RecordingScreenUiState>` → auto-recomposes
- If DB is updated, Flow emits again → UI auto-updates (no manual refresh needed)
- Coroutine cancelled if ViewModel destroyed (Flow stopped)

### Learning: `collect` vs `collectAsStateWithLifecycle`

**In ViewModel** (Phase 2.2):
```kotlin
viewModelScope.launch {
    repository.getAllRecordings()
        .collect { uiItems ->
            _uiState.update { it.copy(recordings = uiItems) }
        }
}
```
- Uses `viewModelScope` (ViewModel lifecycle)
- Updates state once per emission
- Safe to launch multiple collectors

**In Composable** (Phase 2.3):
```kotlin
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```
- `collectAsStateWithLifecycle` respects Compose lifecycle
- Cancels when composable leaves scope
- No manual launch needed

---

## Pattern 3: Background Jobs (Custom Scope)

### ❌ **Bad** — No scope management
```kotlin
// DON'T DO THIS
class TranscriptionHandler {
    fun enqueue(id: Long, filePath: String) {
        // No scope = job runs forever, even if app is killed
        // Not cancellable
        GlobalScope.launch {
            transcribe(filePath)
        }
    }
}
```

### ✅ **Good** — Managed scope + job tracking
```kotlin
// Phase 4.1 — TranscriptionHandler
class TranscriptionHandler(private val repository: RecordingRepository, ...) {
    // Own scope: SupervisorJob (one failure doesn't cancel others)
    // IO dispatcher: transcription is CPU-bound, but audio decode is IO
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Track jobs by recording ID
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    fun enqueue(id: Long, filePath: String) {
        if (activeJobs.containsKey(id)) return  // already running
        
        val job = scope.launch {
            try {
                repository.markInProgress(id)
                
                // Step 1: Decode audio (IO-bound)
                val floats = AudioDecoder.decodeToFloat(filePath)
                
                // Step 2: Transcribe (CPU-bound; Whisper.cpp does its own threading)
                val text = transcribeWithWhisper(floats)
                
                // Step 3: Update DB (IO-bound)
                repository.markDone(id, text)
                
            } catch (e: CancellationException) {
                // Job cancelled (e.g., user tapped cancel)
                repository.markError(id, "Cancelled")
                throw e  // must rethrow per Kotlin spec
            } catch (e: Exception) {
                // Error occurred (network, file not found, etc.)
                repository.markError(id, e.message ?: "Unknown error")
                // don't rethrow — job ends gracefully
            } finally {
                activeJobs.remove(id)  // cleanup
            }
        }
        
        activeJobs[id] = job
    }

    fun cancel(id: Long) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
    }

    fun destroy() {
        scope.cancel()  // cancel all jobs in this handler
    }
}
```

**Why this works**:
- **SupervisorJob**: One failing job doesn't cancel the whole handler
- **ConcurrentHashMap**: Track jobs by ID; idempotent (can't enqueue twice)
- **try/catch/finally**: Proper error handling + cleanup
- **CancellationException**: Must rethrow (Kotlin coroutine contract)
- **Manual destroy()**: Called on app exit or before GC

**Learning**: Scope vs Job
- **Scope** owns multiple jobs; `cancel()` cancels all
- **Job** is individual work; `cancel()` cancels one
- When ViewModel destroyed, `viewModelScope.cancel()` auto-called
- Custom scope (like `TranscriptionHandler.scope`) must be manually managed

---

## Pattern 4: Sequencing Async Work

### ❌ **Bad** — Callback hell
```kotlin
// DON'T DO THIS
fun transcribeAfterDecode(filePath: String) {
    audioDecoder.decodeAsync(filePath) { floats, error ->
        if (error != null) {
            handleError(error)
            return@decodeAsync
        }
        whisperContext.transcribeAsync(floats) { text, error ->
            if (error != null) {
                handleError(error)
                return@transcribeAsync
            }
            repository.markDone(text) { error ->
                if (error != null) {
                    handleError(error)
                }
            }
        }
    }
}
```

### ✅ **Good** — Sequential coroutines
```kotlin
// Phase 4.1 — TranscriptionHandler
scope.launch {
    try {
        // Step 1: Decode (IO)
        val floats = withContext(Dispatchers.IO) {
            AudioDecoder.decodeToFloat(filePath)
        }
        
        // Step 2: Transcribe (Default/CPU)
        val text = withContext(Dispatchers.Default) {
            whisperContext.transcribe(floats)
        }
        
        // Step 3: Persist (IO)
        withContext(Dispatchers.IO) {
            repository.markDone(id, text)
        }
        
    } catch (e: Exception) {
        // All errors caught in one place
        repository.markError(id, e.message)
    }
}
```

**Why this is better**:
- Linear, readable flow (no nesting)
- Single error handler
- Each step's dispatcher clear (`withContext` name tells you where it runs)
- Automatic cancellation on parent scope cancel

---

## Pattern 5: Parallel Async Work (if needed)

### Example: Decode multiple files at once
```kotlin
// If you need to decode multiple files in parallel
viewModelScope.launch {
    val deferreds = filePaths.map { filePath ->
        async(Dispatchers.IO) {
            AudioDecoder.decodeToFloat(filePath)
        }
    }
    
    val results = deferreds.awaitAll()  // wait for all to complete
    results.forEach { floats -> 
        // process each result
    }
}
```

**Gotchas**:
- Only use `async` + `awaitAll` if you have true parallel work
- For sequential operations (like transcription), use `launch` + `withContext` (simpler, clearer)
- Don't over-parallelize — unnecessary context switching hurts performance

**For our app**: We don't need this. Transcription is sequential: decode → transcribe → persist.

---

## Pattern 6: Flow-Based Retry (Learning: Flow Operators)

### Example: Retry on error
```kotlin
// Phase 4.3 — Recovery in ViewModel.init
viewModelScope.launch {
    repository.getAllRecordings()
        .catch { e ->
            // Flow threw an error
            _uiState.update { it.copy(statusMessage = "DB error: ${e.message}") }
        }
        .collect { uiItems ->
            _uiState.update { it.copy(recordings = uiItems, isLoading = false) }
        }
}
```

### Retry with exponential backoff (advanced, optional):
```kotlin
// NOT NEEDED for this app, but educational
fun <T> Flow<T>.retryWithBackoff(
    maxRetries: Int = 3,
    delayMillis: Long = 100
): Flow<T> = flow {
    repeat(maxRetries) { attempt ->
        try {
            emitAll(this@retryWithBackoff)
            return@flow  // success, stop
        } catch (e: Exception) {
            if (attempt == maxRetries - 1) throw e  // last attempt, rethrow
            delay(delayMillis * (1 shl attempt))  // exponential backoff
        }
    }
}
```

**For our app**: Keep it simple. Repository operations are fast (local DB). Just use `catch {}` for error handling.

---

## Dispatcher Selection Guide

| Operation | Dispatcher | Why | Example |
|---|---|---|---|
| UI state update | Main | UI update must be on Main | `_uiState.update {}` |
| Database query | IO | Blocking disk I/O | `repository.insertRecording()` |
| File I/O | IO | Blocking disk access | `AudioDecoder.decode()` |
| CPU-intensive | Default | Math, loops, heavy compute | (not needed in this app) |
| Audio decode | IO + resampling | Decode is IO, resample can be Default | `MediaCodec` on IO; resample on Default |
| Whisper transcribe | Default | CPU-bound, Whisper.cpp owns threading | JNI call on Default |
| Compose collection | Main (via collectAsStateWithLifecycle) | UI rendering must be on Main | UI `val state by flow.collect...` |

---

## ViewModel Pattern (Full Example)

```kotlin
// Phase 2.2 — RecordingViewModel (complete with threading)

class RecordingViewModel(
    private val audioRecorder: AudioRecorder,
    private val repository: RecordingRepository,
    private val transcriptionHandler: TranscriptionHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingScreenUiState())
    val uiState: StateFlow<RecordingScreenUiState> = _uiState.asStateFlow()

    init {
        // Load recordings from DB reactively
        viewModelScope.launch {
            repository.getAllRecordings()
                .catch { e ->
                    _uiState.update { it.copy(statusMessage = "Error loading recordings") }
                }
                .collect { uiItems ->
                    _uiState.update { it.copy(recordings = uiItems, isLoading = false) }
                }
        }

        // Recover interrupted transcriptions on startup
        viewModelScope.launch {
            repository.getAllRecordings().first()
                .filter { it.transcription is TranscriptionUiState.InProgress || 
                         it.transcription is TranscriptionUiState.Pending }
                .forEach { item ->
                    transcriptionHandler.enqueue(item.id, item.filePath)
                }
        }
    }

    fun startRecording() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {  // recording is not IO, but audio ops are system-intensive
                    audioRecorder.startRecording()
                }
                _uiState.update { it.copy(isRecording = true, statusMessage = "Recording...") }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "Start failed: ${e.message}") }
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            try {
                // Stop recording (blocking, must be off Main)
                val filePath = withContext(Dispatchers.IO) {
                    audioRecorder.stopRecording("")
                }
                
                if (filePath != null) {
                    // Insert DB row (blocking IO)
                    val recordingId = withContext(Dispatchers.IO) {
                        repository.insertRecording(filePath, filePath.substringAfterLast("/"))
                    }
                    
                    _uiState.update { it.copy(isRecording = false) }
                    
                    // Auto-trigger transcription (runs in background via TranscriptionHandler)
                    // This call is async; returns immediately
                    transcriptionHandler.enqueue(recordingId, filePath)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "Stop failed: ${e.message}") }
            }
        }
    }

    fun deleteRecording(id: Long, filePath: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    File(filePath).delete()  // file delete
                    repository.deleteRecording(id)  // DB delete
                }
                // UI state auto-updates via Flow subscription (recordings list changes)
            } catch (e: Exception) {
                _uiState.update { it.copy(statusMessage = "Delete failed: ${e.message}") }
            }
        }
    }

    fun retryTranscription(id: Long, filePath: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.markInProgress(id)  // optimistic UI update
            }
            transcriptionHandler.enqueue(id, filePath)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // TranscriptionHandler NOT cancelled here — it survives ViewModel destruction
        // Only clear if you added manual scope management
    }
}
```

**What's happening under the hood**:

1. **`init` block** 
   - Collects `getAllRecordings()` Flow on IO thread
   - Updates `_uiState` on Main thread (automatic via Flow)
   - Recovery: one-shot query to find interrupted jobs

2. **`startRecording()`**
   - Launch on Main via `viewModelScope`
   - `withContext(Dispatchers.IO)` switches to IO (audio recording can block briefly)
   - State update happens back on Main (automatic)

3. **`stopRecording()`**
   - Two `withContext` switches: IO for recorder, IO for DB insert
   - After each block, execution returns to Main context
   - `transcriptionHandler.enqueue()` call returns immediately (launches background job)

4. **`deleteRecording()`**
   - File delete + DB delete both on IO thread (single `withContext` block)
   - State update via Flow auto-triggers (no manual state update needed)

5. **Cancellation**
   - Entire coroutine cancelled if ViewModel cleared
   - TranscriptionHandler keeps running (different scope)

---

## TranscriptionHandler Dispatcher Choices

```kotlin
// Phase 4.1 — TranscriptionHandler with dispatcher reasoning

class TranscriptionHandler(...) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Start on IO because many operations are IO (model loading, file reading)

    fun enqueue(id: Long, filePath: String) {
        val job = scope.launch {  // launches on Dispatchers.IO
            try {
                repository.markInProgress(id)
                // ^ IO operation, already on Dispatchers.IO
                
                val floats = AudioDecoder.decodeToFloat(filePath)
                // ^ File I/O (MediaExtractor) + resampling (CPU)
                //   AudioDecoder internally uses withContext if needed
                
                val text = whisperContext.transcribe(floats)
                // ^ Whisper.cpp is CPU-bound, JNI call
                //   Consider withContext(Dispatchers.Default) if Whisper call takes >10ms
                
                repository.markDone(id, text)
                // ^ IO operation, already on Dispatchers.IO
                
            } catch (e: Exception) {
                repository.markError(id, e.message)
            }
        }
        activeJobs[id] = job
    }
}
```

**Alternative**: If transcription takes 3+ seconds, switch dispatcher:

```kotlin
val text = withContext(Dispatchers.Default) {
    whisperContext.transcribe(floats)
    // Whisper doesn't care which thread, but Default is better for pure CPU work
    // IO thread pool should remain available for other DB/file operations
}
```

---

## Testing Coroutines (Learning)

### ❌ **Bad** — Tests hang/fail randomly
```kotlin
@Test
fun testStopRecordingInserts() = runTest {  // runTest is from kotlinx-coroutines-test
    val viewModel = RecordingViewModel(mockRecorder, mockRepo, mockHandler)
    viewModel.stopRecording()
    // Test finishes before coroutine completes!
}
```

### ✅ **Good** — Wait for coroutine
```kotlin
@Test
fun testStopRecordingInserts() = runTest {
    val mockRepo = mockk<RecordingRepository>()
    coEvery { mockRepo.insertRecording(any(), any()) } returns 1L
    
    val viewModel = RecordingViewModel(mockRecorder, mockRepo, mockHandler)
    viewModel.stopRecording()
    
    advanceUntilIdle()  // wait for all coroutines to finish
    
    coVerify { mockRepo.insertRecording(any(), any()) }
}
```

**Key test concepts**:
- `runTest { }` — Creates test dispatcher (runs synchronously, no real delays)
- `advanceUntilIdle()` — Runs all pending coroutines until no work left
- `coEvery` / `coVerify` — For suspend functions (mockk library)

---

## Common Mistakes to Avoid

| Mistake | Why Bad | How to Fix |
|---|---|---|
| Blocking on Main thread | ANR dialog after 5 sec | Use `withContext(Dispatchers.IO)` |
| `GlobalScope.launch` | Untrackable jobs; never cancelled | Use scoped: `viewModelScope`, custom scope |
| `Thread.sleep()` | Blocks entire thread | Use `delay()` (suspend function) |
| Not handling `CancellationException` | Job won't cancel cleanly | Don't catch it; or rethrow after cleanup |
| Mixing `launch` and `async` unnecessarily | Harder to read | Use `launch` + `withContext` for sequential; `async` + `awaitAll` only for parallel |
| Flow without `catch` | Errors crash app | Add `.catch { }` operator |
| Updating UI from wrong thread | Compose crashes | Ensure state updates happen on Main (automatic with Flow + StateFlow) |

---

## Concurrency Diagram: Where Code Runs

```
User taps "Stop Recording"
    ↓
viewModelScope.launch {              ← Main thread (UI safe)
    val filePath = withContext(Dispatchers.IO) {  ← Switch to IO
        audioRecorder.stopRecording("")            ← Blocking call
    }                                              ← Auto-return to Main
    
    val id = withContext(Dispatchers.IO) {       ← Switch to IO
        repository.insertRecording(...)           ← Blocking DB write
    }                                             ← Auto-return to Main
    
    _uiState.update { ... }                       ← Main thread (update state)
    transcriptionHandler.enqueue(id, path)       ← Main thread (non-blocking, returns immediately)
}

↓ (transcriptionHandler runs in background)

TranscriptionHandler.scope.launch {              ← Dispatchers.IO
    AudioDecoder.decodeToFloat(path)             ← File I/O
    withContext(Dispatchers.Default) {           ← Switch to Default (optional)
        whisperContext.transcribe(floats)        ← CPU-intensive
    }
    withContext(Dispatchers.IO) {                ← Switch back to IO
        repository.markDone(id, text)            ← DB write
    }
}

↓ (repository updates DB)

Flow<List<RecordingUiItem>>                      ← Emits on IO
    .collect {                                   ← Main thread (via collectAsStateWithLifecycle)
        _uiState.update { ... }                  ← Update UI state
    }

↓ Compose recomposition (automatic, Main thread)

UI updates with new transcription status
```

---

## Learning Path: Concepts to Understand

1. **Dispatcher** — `Main`, `IO`, `Default`, `Unconfined` (where code runs)
2. **Scope** — `viewModelScope`, custom scope (lifetime, cancellation)
3. **withContext** — Switch dispatcher, return result (for sequential work)
4. **launch** — Fire-and-forget coroutine (no result)
5. **async/await** — Parallel work with results (advanced, not needed here)
6. **Flow** — Cold stream of data (lazy, multi-emission, reactive)
7. **StateFlow** — Hot state container (immediate value, UI observation)
8. **Structured Concurrency** — Parent scope owns child coroutines (automatic cancellation)

---

## Summary: Threading in This App

| Component | Dispatcher | Why | Example |
|---|---|---|---|
| ViewModel init | Main/IO | Collect Flow on IO, update state on Main | `viewModelScope.launch { repository.getAllRecordings().collect {} }` |
| Stop recording | IO | Blocking recorder call | `withContext(Dispatchers.IO) { audioRecorder.stopRecording() }` |
| Insert DB | IO | Blocking DB write | `repository.insertRecording()` inside `withContext(Dispatchers.IO)` |
| TranscriptionHandler | IO + Default | Audio decode on IO, Whisper on Default | `scope.launch` on IO; `withContext(Dispatchers.Default)` for Whisper |
| UI state update | Main | Compose requirement | `_uiState.update {}` (automatic via Flow) |
| Composable observation | Main | Compose requirement | `collectAsStateWithLifecycle()` |

---

## References & Learning

**Official Kotlin Docs**:
- https://kotlinlang.org/docs/coroutines-overview.html
- https://kotlinlang.org/docs/flow.html

**Key Concepts You'll Master**:
1. `launch` vs `async` — when to use each
2. `withContext` — switching dispatchers cleanly
3. `viewModelScope` — automatic cancellation
4. `Flow.collect` — reactive data
5. `CancellationException` — proper cleanup

**In This App You'll See**:
- Phase 2.2: ViewModel with `launch`, `withContext`, Flow
- Phase 4.1: TranscriptionHandler with custom scope, SupervisorJob
- Phase 4.3: Recovery with `.first()` (one-shot Flow)
- Phase 5: Composable with `collectAsStateWithLifecycle()`

---

**Key Takeaway**: 
- Move anything blocking to IO thread
- Keep Main thread for UI state updates only
- Use Flow for reactive, auto-updating data
- Use custom scope only for background jobs that outlive ViewModel
- Avoid over-complication; use `launch` + `withContext` for sequential work

