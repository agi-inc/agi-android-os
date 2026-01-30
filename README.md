# AGI Android OS

A custom Android GSI (Generic System Image) that provides system-level agent control capabilities, including headless virtual display sessions for parallel agent operation.

## Overview

This project creates a modified Android 13 (API 33) GSI with a built-in `AgentSystemService` that enables:

- **Headless Sessions**: Create virtual displays that render off-screen, allowing agents to operate apps without affecting the physical display
- **Parallel Agents**: Run multiple agent sessions simultaneously, each with their own isolated display
- **System-Level Access**: Full control over apps, permissions, settings, filesystem, and input injection
- **Physical Display Control**: Optionally control the primary display alongside or instead of headless sessions

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      AGI-Android OS                              │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Physical Display (display_id=0)                             ││
│  │ - User interacts normally                                   ││
│  │ - OR agent controls via AgentSession(headless=false)        ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Virtual Display 1 (display_id=N, off-screen)                ││
│  │ - Headless agent session                                    ││
│  │ - Apps render to ImageReader surface                        ││
│  │ - Screenshot capture + input injection                      ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Virtual Display 2...N (off-screen)                          ││
│  │ - Additional parallel sessions                              ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ AgentSystemService (runs in system_server)                  ││
│  │                                                              ││
│  │  SessionManager ─── VirtualDisplayManager                   ││
│  │       │                    │                                 ││
│  │       │              ScreenCapturer                         ││
│  │       │                    │                                 ││
│  │       └──────────── InputInjector                           ││
│  │                           │                                  ││
│  │                    SystemExecutor                           ││
│  │                    (install, permissions, shell, etc.)      ││
│  └─────────────────────────────────────────────────────────────┘│
│                              ↕                                   │
│                         AIDL Binder                              │
│                              ↕                                   │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ AgentOS SDK (client library)                                ││
│  │ - Used by apps/agents to interact with AgentSystemService   ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

## Key Capabilities

### Session Management
- Create/destroy headless or physical display sessions
- Each session is isolated with its own display, input, and app state
- Query running sessions and their states

### Display Control
- **Headless**: VirtualDisplay renders to ImageReader (off-screen)
- **Physical**: Control the actual device screen
- Configurable resolution and DPI per session

### Input Injection
- Tap, long press, double tap
- Drag/swipe gestures
- Text input
- Hardware keys (home, back, recents, volume, power)
- All input targeted to specific display ID

### Screen Capture
- High-performance capture via ImageReader surface
- PNG or raw RGBA output
- Per-session capture (each virtual display independent)

### App Control
- Launch apps/activities on specific displays
- Kill apps
- Get foreground app info
- Install/uninstall APKs

### System Access
- Grant/revoke permissions
- Modify system settings
- Execute shell commands
- Read notifications
- File system access

## Documentation

| Document | Description |
|----------|-------------|
| [PROGRESS.md](PROGRESS.md) | Current implementation status and next steps |
| [docs/big-picture.md](docs/big-picture.md) | Vision, architecture overview, and key concepts |
| [docs/architecture.md](docs/architecture.md) | Detailed technical architecture |
| [docs/driver-integration.md](docs/driver-integration.md) | How the agi-driver binary integrates |
| [docs/building.md](docs/building.md) | Build environment setup |
| [docs/flashing.md](docs/flashing.md) | How to flash GSI to devices |
| [docs/sdk-api.md](docs/sdk-api.md) | SDK API reference |

## Project Structure

```
agi-android-os/
├── README.md                  # This file
├── PROGRESS.md                # Implementation progress tracker
├── docs/
│   ├── big-picture.md         # Vision and architecture overview
│   ├── architecture.md        # Detailed technical architecture
│   ├── driver-integration.md  # Driver binary integration
│   ├── building.md            # Build environment setup and compilation
│   ├── flashing.md            # How to flash GSI to devices
│   └── sdk-api.md             # SDK API reference
│
├── system-service/            # AgentSystemService implementation
│   ├── Android.bp             # Build config
│   ├── AndroidManifest.xml
│   └── src/com/agi/os/
│       ├── service/
│       │   ├── AgentSystemService.kt
│       │   └── AgentServiceImpl.kt
│       ├── display/
│       │   └── VirtualDisplayManager.kt
│       ├── input/
│       │   └── InputInjector.kt
│       ├── screen/
│       │   └── ScreenCapturer.kt
│       └── session/
│           ├── Session.kt
│           └── SessionManager.kt
│
├── sdk/                       # Client SDK for apps
│   ├── Android.bp
│   └── src/com/agi/os/sdk/
│       ├── AgentOS.kt         # Main entry point
│       ├── AgentSession.kt    # Session interface
│       └── internal/
│           └── AgentOSClient.kt
│
├── aidl/                      # AIDL interface definitions
│   └── com/agi/os/
│       ├── IAgentService.aidl
│       └── IAgentSession.aidl
│
├── aosp/                      # AOSP build integration
│   ├── device/agi/os/         # Device config
│   │   ├── AndroidProducts.mk
│   │   ├── agi_os_arm64.mk
│   │   └── BoardConfig.mk
│   └── patches/               # Patches to AOSP source
│       └── frameworks_base/
│           └── 0001-add-agent-system-service.patch
│
└── tools/
    ├── setup-build-env.sh     # Set up AOSP build environment
    ├── build.sh               # Build the GSI
    ├── flash.sh               # Flash to device
    └── test-emulator.sh       # Test in Android emulator
```

## Quick Start

See [docs/building.md](docs/building.md) for detailed instructions.

```bash
# 1. Set up build environment (one-time)
./tools/setup-build-env.sh

# 2. Build GSI
./tools/build.sh

# 3. Flash to device (or test in emulator)
./tools/flash.sh /dev/sdX  # Replace with your device
# OR
./tools/test-emulator.sh
```

## SDK Usage

```kotlin
// Get AgentOS instance
val agentOS = AgentOS.getInstance(context)

// Create a headless session
val session = agentOS.createSession(
    SessionConfig(
        width = 1080,
        height = 1920,
        dpi = 420,
        headless = true
    )
)

// Launch an app in the session
session.launchApp("com.twitter.android")

// Wait for app to load
Thread.sleep(2000)

// Capture screenshot
val screenshot: ByteArray = session.captureScreen()

// Interact
session.click(540, 960)
session.type("Hello world")
session.pressBack()

// Clean up
session.destroy()
```

## Target Devices

This builds a GSI (Generic System Image) compatible with any device supporting Project Treble (most devices from 2018+). Tested on:

- Google Pixel devices (4+)
- Samsung Galaxy S/A series (Android 13+)
- OnePlus devices
- Generic ARM64 devices with unlocked bootloader

## Related Projects

| Project | Description |
|---------|-------------|
| [agi-api-driver](../agi-api-driver) | Agent driver binary (branch: `jacob/agent-driver-module`) |
| [agi-api](../agi-api) | AGI agent server |
| [agi-android](../agi-android) | Original Android agent app (accessibility-based, legacy) |
| [agi-python](../agi-python) | Python SDK |
| [agi-node](../agi-node) | Node.js SDK |
| [agi-csharp](../agi-csharp) | C# SDK |
| [agi-cli](../agi-cli) | Command-line interface |

## Current Status

**AOSP sync in progress.** Monitor with:
```bash
tail -f ~/aosp-sync.log
```

See [PROGRESS.md](PROGRESS.md) for detailed status.

## License

Proprietary - Internal use only.
