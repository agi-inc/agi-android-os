# Driver Integration

This document describes how the `agi-driver` binary integrates with AGI-Android OS.

## Overview

The `agi-driver` is a standalone binary that contains the agent intelligence. It:
- Receives screenshots
- Calls Claude API to decide actions
- Returns actions to execute

The driver communicates via JSON lines over stdin/stdout, making it easy to embed.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    AGI-Android OS                                │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                    DriverBridge                           │  │
│  │  - Spawns agi-driver process                             │  │
│  │  - Reads events from stdout                              │  │
│  │  - Writes commands to stdin                              │  │
│  │  - Translates events to session actions                  │  │
│  └──────────────────────────────────────────────────────────┘  │
│           ↕ stdin/stdout (JSON lines)                           │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              agi-driver (native binary)                   │  │
│  │  - Agent state machine                                   │  │
│  │  - Claude API calls                                      │  │
│  │  - Tool definitions                                      │  │
│  └──────────────────────────────────────────────────────────┘  │
│           ↕ HTTPS                                               │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                   Claude API                              │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Driver Binary

### Source Location
```
~/Code/agi-api-driver/src/agi_driver/
├── __main__.py          # Entry point
├── executor.py          # Main execution loop
├── state_machine.py     # State management
├── agent/
│   ├── base.py          # Base agent class
│   └── desktop_agent.py # Desktop implementation
├── llm/
│   └── ...              # LLM client
└── protocol/
    ├── commands.py      # Command types
    ├── events.py        # Event types
    └── jsonl.py         # JSON line I/O
```

### Build Workflow
The driver is built with Nuitka (Python → native binary):
```
.github/workflows/build-agi-driver.yml
```

Current targets:
- `darwin-arm64` - macOS Apple Silicon
- `darwin-x64` - macOS Intel
- `linux-x64` - Linux x86_64
- `windows-x64` - Windows

**TODO**: Add `linux-arm64` for Android.

### Building for Android

Android uses Linux ARM64, so we need to add to the build matrix:

```yaml
- os: ubuntu-22.04
  target: linux-arm64
  python: '3.11'
```

And install cross-compiler:
```bash
sudo apt-get install gcc-aarch64-linux-gnu g++-aarch64-linux-gnu
```

Then build:
```bash
python -m nuitka \
  --standalone \
  --onefile \
  --output-filename=agi-driver \
  --target=linux-arm64 \
  ...
```

## Protocol

### Events (driver → bridge)

```jsonl
{"event":"ready","version":"0.1.0","protocol":"jsonl","step":0}
{"event":"state_change","state":"running","step":0}
{"event":"thinking","text":"I see a login form...","step":1}
{"event":"action","action":{"type":"click","x":500,"y":750},"step":1}
{"event":"confirm","action":{},"reason":"Click Delete?","step":2}
{"event":"ask_question","question":"What is your email?","question_id":"q1","step":3}
{"event":"finished","reason":"completed","summary":"Task done","success":true,"step":10}
{"event":"error","message":"API error","code":"api_error","recoverable":true,"step":5}
```

### Commands (bridge → driver)

```jsonl
{"command":"start","session_id":"abc","goal":"Open settings","screenshot":"base64...","screen_width":1080,"screen_height":1920,"platform":"android","model":"claude-sonnet"}
{"command":"screenshot","data":"base64...","screen_width":1080,"screen_height":1920}
{"command":"pause"}
{"command":"resume"}
{"command":"stop","reason":"user cancelled"}
{"command":"confirm","approved":true,"message":""}
{"command":"answer","text":"user@example.com","question_id":"q1"}
```

### State Machine

```
IDLE → RUNNING → PAUSED → RUNNING → FINISHED
         ↓         ↓
    WAITING_*   STOPPED
         ↓
      RUNNING
```

## DriverBridge Implementation

```kotlin
// system-service/src/com/agi/os/driver/DriverBridge.kt

class DriverBridge(
    private val context: Context,
    private val session: Session
) {
    private var process: Process? = null
    private var stdin: BufferedWriter? = null
    private var stdout: BufferedReader? = null
    private var readerJob: Job? = null

    // Event callbacks
    var onThinking: ((String) -> Unit)? = null
    var onAction: ((DriverAction) -> Unit)? = null
    var onConfirm: ((String) -> Boolean)? = null
    var onAskQuestion: ((String, String) -> String)? = null
    var onFinished: ((Boolean, String) -> Unit)? = null
    var onError: ((String, String, Boolean) -> Unit)? = null

    fun start(goal: String): Boolean {
        // Start driver process
        val driverPath = "/system/bin/agi-driver"
        val pb = ProcessBuilder(driverPath)
        pb.environment()["ANTHROPIC_API_KEY"] = getApiKey()

        process = pb.start()
        stdin = process!!.outputStream.bufferedWriter()
        stdout = process!!.inputStream.bufferedReader()

        // Start reader coroutine
        readerJob = CoroutineScope(Dispatchers.IO).launch {
            readLoop()
        }

        // Wait for ready event
        // ...

        // Send start command
        val screenshot = session.captureScreen()
        sendCommand(StartCommand(
            sessionId = session.id,
            goal = goal,
            screenshot = Base64.encodeToString(screenshot, Base64.NO_WRAP),
            screenWidth = session.config.width,
            screenHeight = session.config.height,
            platform = "android",
            model = "claude-sonnet"
        ))

        return true
    }

    suspend fun step() {
        // Capture screenshot and send
        val screenshot = session.captureScreen()
        sendCommand(ScreenshotCommand(
            data = Base64.encodeToString(screenshot, Base64.NO_WRAP),
            screenWidth = session.config.width,
            screenHeight = session.config.height
        ))
    }

    private suspend fun readLoop() {
        while (process?.isAlive == true) {
            val line = stdout?.readLine() ?: break
            if (line.isBlank()) continue

            try {
                val event = parseEvent(line)
                handleEvent(event)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing event: $line", e)
            }
        }
    }

    private suspend fun handleEvent(event: DriverEvent) {
        when (event) {
            is ThinkingEvent -> onThinking?.invoke(event.text)
            is ActionEvent -> executeAction(event.action)
            is ConfirmEvent -> handleConfirm(event)
            is AskQuestionEvent -> handleQuestion(event)
            is FinishedEvent -> {
                onFinished?.invoke(event.success, event.summary)
                stop()
            }
            is ErrorEvent -> {
                onError?.invoke(event.message, event.code, event.recoverable)
                if (!event.recoverable) stop()
            }
        }
    }

    private fun executeAction(action: DriverAction) {
        // Convert normalized coords (0-1000) to pixels
        val x = (action.x / 1000.0 * session.config.width).toFloat()
        val y = (action.y / 1000.0 * session.config.height).toFloat()

        when (action.type) {
            "click" -> session.click(x, y)
            "double_click" -> session.doubleClick(x, y)
            "drag" -> {
                val endX = (action.endX / 1000.0 * session.config.width).toFloat()
                val endY = (action.endY / 1000.0 * session.config.height).toFloat()
                session.drag(x, y, endX, endY)
            }
            "type" -> session.type(action.text)
            "press_home" -> session.pressHome()
            "press_back" -> session.pressBack()
            // ... other action types
        }

        onAction?.invoke(action)
    }

    private fun sendCommand(command: Any) {
        val json = Gson().toJson(command)
        stdin?.write(json)
        stdin?.newLine()
        stdin?.flush()
    }

    fun stop(reason: String? = null) {
        sendCommand(StopCommand(reason = reason))
        readerJob?.cancel()
        process?.destroy()
        process = null
    }
}
```

## Including Driver in System Image

### Option 1: Prebuilt Binary

Copy pre-built binary to system image:

```makefile
# aosp/device/agi/os/agi_os_arm64.mk

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/bin/agi-driver:$(TARGET_COPY_OUT_SYSTEM)/bin/agi-driver
```

### Option 2: Build from Source

Include driver build in AOSP:

```
# aosp/external/agi-driver/Android.mk

include $(CLEAR_VARS)
LOCAL_MODULE := agi-driver
LOCAL_MODULE_CLASS := EXECUTABLES
LOCAL_MODULE_PATH := $(TARGET_OUT)/bin
LOCAL_SRC_FILES := agi-driver-linux-arm64
LOCAL_MODULE_STEM := agi-driver
include $(BUILD_PREBUILT)
```

## API Key Management

The driver needs an Anthropic API key. Options:

1. **Environment Variable**: Set `ANTHROPIC_API_KEY` when spawning process
2. **Config File**: Read from `/data/local/tmp/agi-driver.conf`
3. **SDK Parameter**: Pass via SDK when creating agent session

For security, recommend option 1 or 3, not storing key on filesystem.

## Platform-Specific Considerations

### Desktop vs Android

| Aspect | Desktop Driver | Android Integration |
|--------|----------------|---------------------|
| Coordinates | Normalized 0-1000 | Same |
| Screenshot | JPEG base64 | PNG or JPEG base64 |
| Platform string | `"desktop"` | `"android"` |
| Actions | Mouse-based | Touch-based |

### Android-Specific Tools

The driver may need Android-specific tools:

```python
# Additional tools for Android
ANDROID_TOOLS = [
    {"name": "press_home", "description": "Press home button"},
    {"name": "press_back", "description": "Press back button"},
    {"name": "open_app", "description": "Open app by package name", "parameters": {...}},
    {"name": "open_notifications", "description": "Open notification shade"},
]
```

## Testing

### Unit Test Driver Bridge

```kotlin
@Test
fun testDriverBridge() {
    val mockSession = MockSession()
    val bridge = DriverBridge(context, mockSession)

    bridge.onAction = { action ->
        assertEquals("click", action.type)
    }

    bridge.start("Click the button")
    // Simulate driver response...
}
```

### Integration Test

```bash
# On device
adb shell

# Run driver directly
/system/bin/agi-driver << EOF
{"command":"start","session_id":"test","goal":"test","screenshot":"...","screen_width":1080,"screen_height":1920,"platform":"android","model":"claude-sonnet"}
EOF
```

## Troubleshooting

### Driver Won't Start
- Check binary exists: `adb shell ls -la /system/bin/agi-driver`
- Check permissions: `adb shell chmod +x /system/bin/agi-driver`
- Check logs: `adb logcat -s DriverBridge`

### API Errors
- Verify `ANTHROPIC_API_KEY` is set
- Check network connectivity
- Review driver stderr for API errors

### Action Not Executing
- Verify coordinate conversion (0-1000 → pixels)
- Check session is still valid
- Verify input injection works: `adb shell input tap X Y`
