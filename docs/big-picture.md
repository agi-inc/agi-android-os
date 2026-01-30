# Big Picture: AGI Android OS

## Vision

A custom Android OS that provides system-level infrastructure for computer automation agents. Unlike the existing `agi-android` app which runs as a user application with accessibility service limitations, this OS provides:

1. **Virtual Display Sessions** - Create headless off-screen displays for parallel agent operation
2. **System-Level Access** - Direct input injection, app control, permissions management
3. **Driver Integration** - Native integration with the `agi-driver` binary for agent logic
4. **SDK for Apps** - Any Android app can programmatically control sessions

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           AGI-Android OS                                     │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                    System Layer (AOSP + Patches)                       │ │
│  │  - AgentSystemService (in system_server)                               │ │
│  │  - Virtual Display management                                          │ │
│  │  - Input injection                                                     │ │
│  │  - Screen capture                                                      │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                              ↕ AIDL Binder                                   │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                         SDK Layer                                       │ │
│  │  - AgentOS client library                                              │ │
│  │  - Session interface                                                   │ │
│  │  - Available to any Android app                                        │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                              ↕ stdio (JSON lines)                            │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                    Driver Layer (agi-driver binary)                     │ │
│  │  - Agent logic (Claude API)                                            │ │
│  │  - State machine                                                       │ │
│  │  - Tool execution                                                      │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  Session 0 (Physical)     Session 1 (Virtual)    Session N (Virtual)       │
│  ┌─────────────────┐      ┌─────────────────┐    ┌─────────────────┐       │
│  │  User or Agent  │      │  Headless Agent │    │  Headless Agent │       │
│  │  Display 0      │      │  Display 1      │    │  Display N      │       │
│  └─────────────────┘      └─────────────────┘    └─────────────────┘       │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Key Concepts

### Sessions (Infrastructure)
A session is a controllable display context. Sessions are **agent-agnostic** - they're pure automation primitives.

- **Headless Session**: Virtual display that renders off-screen. Apps run here without affecting the physical screen.
- **Physical Session**: Control of the actual device display (display 0).

Multiple sessions can run in parallel. Each session has:
- Screen capture (PNG or raw RGBA)
- Input injection (tap, drag, type, keys)
- App launching and control
- Independent lifecycle

### Driver (Agent Logic)
The `agi-driver` binary contains the agent intelligence:

- Receives screenshots
- Calls Claude API for decisions
- Returns actions to execute
- Manages conversation state

The driver communicates via **JSON lines over stdin/stdout**:
```
SDK → stdin → agi-driver → stdout → SDK
```

### Separation of Concerns

```
Session (Infrastructure)           Driver (Intelligence)
├── Create virtual displays        ├── See screenshots
├── Capture screenshots            ├── Decide what to do
├── Inject input                   ├── Call Claude API
├── Launch apps                    └── Return actions
└── Manage lifecycle

Session knows HOW to interact      Driver knows WHAT to do
```

An app can:
- Use sessions without any driver (manual automation, testing)
- Use multiple drivers on one session
- Use one driver across multiple sessions
- Use the HTTP API instead of the local driver

## Related Projects

| Project | Description | Role |
|---------|-------------|------|
| `agi-android-os` | This repo - Custom Android OS | Infrastructure |
| `agi-api-driver` | Agent driver binary + server | Intelligence |
| `agi-android` | Original Android agent app | Legacy approach |
| `agi-python` | Python SDK | Client library |
| `agi-node` | Node.js SDK | Client library |
| `agi-csharp` | C# SDK | Client library |
| `agi-cli` | Command-line interface | User tool |

## Communication Protocols

### SDK ↔ Session (AIDL/Binder)

```kotlin
// Create session
val session = agentOS.createSession(SessionConfig(headless = true))

// Capture and inject
val screenshot = session.captureScreen()
session.click(540f, 960f)
session.type("hello")
session.launchApp("com.example.app")
```

### Session ↔ Driver (JSON lines over stdio)

```jsonl
← {"event":"ready","version":"0.1.0","step":0}
→ {"command":"start","goal":"Order pizza","screenshot":"base64...","screen_width":1080,"screen_height":1920}
← {"event":"state_change","state":"running","step":0}
← {"event":"thinking","text":"I see the DoorDash app...","step":1}
← {"event":"action","action":{"type":"click","x":540,"y":960},"step":1}
→ {"command":"screenshot","data":"base64..."}
← {"event":"action","action":{"type":"type","text":"pepperoni"},"step":2}
...
← {"event":"finished","success":true,"summary":"Order placed","step":10}
```

### Driver ↔ Claude API (HTTP)

Standard Anthropic API with tool use. The driver handles:
- Message history management
- Visual memory (recent screenshots)
- Token management
- Tool call parsing

## Coordinate Systems

All coordinates use pixels from top-left origin:

```
(0, 0) ─────────────────── (width, 0)
  │                            │
  │                            │
  │                            │
(0, height) ────────── (width, height)
```

The driver uses **normalized 0-1000 coordinates** for device independence:
```kotlin
// Driver returns normalized
val normalizedX = 500  // middle

// Convert to pixels
val pixelX = (normalizedX / 1000.0 * screenWidth).toInt()
```

## Build & Deployment

```
AOSP Source (~100GB)
      +
agi-android-os modules (~50 files)
      ↓
    Build
      ↓
system.img (GSI)
      ↓
   Flash to device
```

The GSI (Generic System Image) works on any Project Treble device (2018+).

## Current Status

See [PROGRESS.md](PROGRESS.md) for detailed progress tracking.

**Summary:**
- ✅ Architecture designed
- ✅ System service skeleton created
- ✅ SDK interface defined
- ✅ Build configuration ready
- ⏳ AOSP sync in progress
- ⏳ Driver ARM64 build needed
- ⏳ Integration testing pending
