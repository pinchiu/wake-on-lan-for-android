# Global Project Development & Maintenance Specification (AGENTS.md)

This document is the global specification and architectural guidelines for this project (supporting Linux Foundation / Agentic AI standards, as well as AI development agents such as Antigravity, Claude Code, Cursor, Windsurf, and human developers). It details the core system architecture, deep module design, command dispatch pipeline, file responsibilities, and **cross-platform (Linux, macOS, Windows) build, test, and verification standard operating procedures (SOP)**.

---

## 1. System Architecture & Core Modules

The system consists of three core subsystems:

* [`wakeonlanhomephone/`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonlanhomephone/): **Home Phone Helper Android Application**. Stationed at home and permanently connected to the local Wi-Fi network, serving as the gateway/bridge from WAN into the local LAN. Broadcasts Wake-on-LAN UDP Magic Packets (port 9), listens for incoming WAN TCP connections (port 9876), subscribes to MQTT brokers, receives voice shortcuts / routines, and handles home screen widgets, converging all commands through `PcActionDispatcher`.
* [`wakeonwanremotephone/`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonwanremotephone/): **Remote Phone Controller Android Application**. Carried by the user to send control commands to the Home Phone helper or directly to target computers via TCP (IPv6/IPv4), MQTT, or local LAN broadcast. Supports multi-host profile management and a 1-tap desktop wake-up widget.
* [`computer/`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/computer/): **Target Computer Daemon (Rust Cross-Platform Service)**. Runs in the background on Windows, Linux, and Apple macOS listening on UDP port 9877. Receives power commands (`shutdown`, `reboot`, `sleep`, `hibernate`, `lock`) and executes safe OS transitions via native system calls (Win32 API / Linux systemd-logind / macOS AppleScript & pmset). Includes zero-admin autostart registration (Windows Registry HKCU Run / Linux systemd & XDG / macOS LaunchAgent) and automatic 5 MB log rotation.

---

## 2. Core Deep Module Architecture: PcActionDispatcher

To eliminate tight coupling between network transport protocols and domain business logic, the Home Phone helper adopts a **Deep Module** architecture:

```
[Transport Adapters]
  ├── TCP Listener (WolListenerService: 9876)
  ├── MQTT Subscriber (MqttWolService: wakeonlan/#)
  ├── Voice / Shortcut (VoiceWakeActivity: Routine / Shortcut)
  └── Desktop Widget (WakePcWidgetProvider: App Widget)
                │
                ▼ (Unified Ingestion)
┌─────────────────────────────────────────────────────────────┐
│          PcActionDispatcher (Deep Module / Domain Core)       │
│                                                             │
│  Interface: suspend fun dispatch(rawCommand, source):        │
│                         PcActionResult                      │
│                                                             │
│  Responsibilities:                                          │
│    1. Command syntax parsing & normalization (:, delimiters)│
│    2. Parameter validation (MAC formatting, IPv4 parsing)   │
│    3. Business operation execution:                         │
│       - WAKE: Broadcasts WoL UDP Magic Packet (Port 9)      │
│       - SHUTDOWN/REBOOT/SLEEP/HIBERNATE/LOCK:               │
│         Sends power control UDP packet to target_ip:9877    │
│    4. Structured result encapsulation (PcActionResult)      │
└─────────────────────────────────────────────────────────────┘
                │
                ▼ (Return Result)
        PcActionResult (Success / Failure)
                │
                ├─► TCP Adapter: Returns toProtocolString() ("SUCCESS: ..." / "ERROR: ...")
                ├─► MQTT / Voice Adapter: Records to AppLogger & system notifications
                └─► Widget Adapter: Shows Toast & updates widget timestamp
```

### 2.1 Interface & Result Contract

* **Entry Interface**:
  ```kotlin
  suspend fun dispatch(rawCommand: String, source: String = "local"): PcActionResult
  ```
* **Result Type**:
  ```kotlin
  sealed class PcActionResult {
      data class Success(val message: String, val details: String? = null) : PcActionResult()
      data class Failure(val errorMessage: String, val cause: Throwable? = null) : PcActionResult()
      fun toProtocolString(): String
  }
  ```
  `toProtocolString()` generates protocol-compliant wire formats: `SUCCESS: <message>` or `ERROR: <reason>`.

### 2.2 Supported Command Syntax

`PcActionDispatcher` supports the following command formats (case-insensitive, supporting `:` and `,` delimiters):

| Command Format | Example | Description |
| :--- | :--- | :--- |
| `WAKE` | `WAKE` | Broadcasts WoL packet using the default configured MAC |
| `WAKE:<MAC>` or `WAKE,<MAC>` | `WAKE:AA:BB:CC:DD:EE:FF` | Broadcasts WoL packet to the specified MAC address |
| `<MAC>` (pure MAC) | `AA:BB:CC:DD:EE:FF` | Automatically detected as MAC and broadcasts WoL packet |
| `SHUTDOWN:<IP>` or `SHUTDOWN,<IP>` | `SHUTDOWN:192.168.1.100` | Sends UDP command `shutdown` to `192.168.1.100:9877` |
| `REBOOT:<IP>` or `REBOOT,<IP>` | `REBOOT:192.168.1.100` | Sends UDP command `reboot` to `192.168.1.100:9877` |
| `SLEEP:<IP>` or `SLEEP,<IP>` | `SLEEP:192.168.1.100` | Sends UDP command `sleep` to `192.168.1.100:9877` |
| `HIBERNATE:<IP>` or `HIBERNATE,<IP>` | `HIBERNATE:192.168.1.100` | Sends UDP command `hibernate` to `192.168.1.100:9877` |
| `LOCK:<IP>` or `LOCK,<IP>` | `LOCK:192.168.1.100` | Sends UDP command `lock` to `192.168.1.100:9877` |

### 2.3 Symmetrical PC Daemon Deep Module (Rust `Dispatcher`)

[`computer/src/dispatch.rs`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/computer/src/dispatch.rs) implements the exact symmetrical deep module pattern on the PC daemon side:

```rust
pub trait PowerController {
    fn execute(&self, action: PowerAction) -> Result<(), PowerError>;
}

pub struct Dispatcher<P: PowerController> { /* ... */ }

impl<P: PowerController> Dispatcher<P> {
    pub fn dispatch(&self, raw: &str) -> Result<PowerAction, DispatchError>;
}
```

* **Small Interface**: Callers only need `dispatch(raw)` returning `Result<PowerAction, DispatchError>`.
* **Deep Implementation**: `action.rs` handles casing, whitespace, delimiters, and aliases; `power.rs` encapsulates platform differences (Win32 API on Windows, `systemctl`/`loginctl` on Linux, AppleScript `osascript`/`pmset`/`CGSession` on macOS); `server.rs` only handles UDP transport and logging.
* **Testability**: `PowerController` is the single system seam, allowing unit tests to inject mock controllers without triggering actual shutdowns.
* **Unidirectional Dependency**: `power → action`, `dispatch → {action, power}`, `server → dispatch`, `cli → all`.

---

## 3. Remote Phone Multi-Host Profile Management & Widgets

### 3.1 Device Profile Management (`DeviceProfileManager`)
* **Data Structure**: `DeviceProfile(id, name, mac, ip)`.
* **Persistence**: Stored as a JSON array in `SharedPreferences` via Gson.
* **Backwards Compatibility**: Automatically migrates legacy single MAC / IP preferences to "Default PC" on first launch.
* **State Synchronization**: When switching active profiles, broadcasts an update to refresh the desktop widget (`RemoteWakeWidgetProvider`).

### 3.2 Dual Desktop Widgets (App Widgets)
* **Home Phone Widget** ([`WakePcWidgetProvider.kt`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WakePcWidgetProvider.kt)):
  1-tap button to broadcast a local WoL packet to wake the default PC, showing status and execution timestamp.
* **Remote Phone Widget** ([`RemoteWakeWidgetProvider.kt`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteWakeWidgetProvider.kt)):
  Displays the currently selected target PC name and sends a remote wake command via the helper or direct connection.

---

## 4. File Responsibilities Table

| Component | File Path | Core Responsibility |
| :--- | :--- | :--- |
| **Command Dispatch Core (Deep Module)** | [`wakeonlanhomephone/.../PcActionDispatcher.kt`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt) | Command parsing, parameter validation, WoL Magic Packet & UDP 9877 delivery, structured `PcActionResult`. Pure Kotlin/JVM, decoupled from Android frameworks. |
| **Unit Test Suite** | [`wakeonlanhomephone/.../PcActionDispatcherTest.kt`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonlanhomephone/app/src/test/java/com/example/wakeonlanhomephone/PcActionDispatcherTest.kt) | 100% comprehensive unit test coverage for all command formats, boundary conditions, and error cases. |
| **TCP Service Adapter** | [`wakeonlanhomephone/.../WolListenerService.kt`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt) | Listens on TCP 9876, directs stream input to `dispatcher.dispatch()`, and writes back `toProtocolString()`. |
| **MQTT Service Adapter** | [`wakeonlanhomephone/.../MqttWolService.kt`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttWolService.kt) | Subscribes to MQTT topic, forwarding payload strings to `dispatcher.dispatch()`. |
| **Voice / Shortcut Adapter** | [`wakeonlanhomephone/.../VoiceWakeActivity.kt`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/VoiceWakeActivity.kt) | Invisible entry activity for Google Assistant / App Shortcuts; invokes `dispatcher.dispatch("WAKE")` asynchronously and exits immediately. |
| **Home Phone Widget** | [`wakeonlanhomephone/.../WakePcWidgetProvider.kt`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WakePcWidgetProvider.kt) | Android desktop widget for 1-tap local WoL packet broadcasting. |
| **Low-Level WoL Broadcaster** | [`wakeonlanhomephone/.../WolUtil.kt`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolUtil.kt) | Constructs 102-byte Magic Packet binary frames and sends UDP datagrams. |
| **Configuration Manager** | [`wakeonlanhomephone/.../MqttConfigManager.kt`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttConfigManager.kt) | Manages target MAC and MQTT broker settings in SharedPreferences. |
| **Static Shortcut Declaration** | [`wakeonlanhomephone/.../res/xml/shortcuts.xml`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/res/xml/shortcuts.xml) | Declares static `wake_pc_shortcut` for Google Assistant and launcher long-press. |
| **Application Logger** | [`wakeonlanhomephone/.../AppLogger.kt`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/AppLogger.kt) | Provides in-memory real-time log ring buffer for UI display. |
| **Remote Profile Manager** | [`wakeonwanremotephone/.../DeviceProfileManager.kt`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/DeviceProfileManager.kt) | Manages multi-host configuration (name, MAC, IP) and active target selection on the remote controller. |
| **Remote Wake Widget** | [`wakeonwanremotephone/.../RemoteWakeWidgetProvider.kt`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteWakeWidgetProvider.kt) | Desktop widget for 1-tap remote waking of the selected PC. |
| **Remote Network Utilities** | [`wakeonwanremotephone/.../RemoteNetworkUtil.kt`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt) | Network command wrappers (TCP 9876, LAN WoL broadcast, UDP 9877 direct). |
| **PC Daemon Dispatcher (Rust Deep Module)** | [`computer/src/dispatch.rs`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/computer/src/dispatch.rs) | `Dispatcher<P: PowerController>::dispatch(raw)`: Parse → execute → structured `DispatchError`; completely testable with mock controller. |
| **PC Daemon Command Syntax** | [`computer/src/action.rs`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/computer/src/action.rs) | `PowerAction` enum and fault-tolerant parsing (casing, whitespace, delimiters, aliases). |
| **PC Daemon Power Seam** | [`computer/src/power.rs`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/computer/src/power.rs) | `PowerController` trait; Win32 API on Windows, `systemctl`/`loginctl` on Linux, AppleScript `osascript`/`pmset`/`CGSession` on macOS. |
| **PC Daemon Autostart** | [`computer/src/autostart.rs`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/computer/src/autostart.rs) | Zero-admin autostart management (Windows HKCU Run, Linux systemd user & XDG, macOS `~/Library/LaunchAgents/com.wakeonlan.pc_onoff.plist`). |
| **PC Daemon Path Resolution** | [`computer/src/paths.rs`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/computer/src/paths.rs) | Cross-platform config/log path resolution (Windows `%LOCALAPPDATA%`, Linux `~/.config`, macOS `~/Library/Application Support`). |
| **PC Daemon UDP Server** | [`computer/src/server.rs`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/computer/src/server.rs) | Listens on UDP 9877, passes datagrams to dispatcher, logs events, resolves local IPv4. |
| **PC Daemon Support Modules** | [`computer/src/`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/computer/src/) | `config` (atomic INI persistence), `logging` (5 MB rotation), `instance` (single-instance mutex & stop), `cli` (CLI args & status). |
| **PC Daemon Python Reference** | [`computer/pc_onoff.py`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/computer/pc_onoff.py) | Reference implementation supporting Windows, Linux, and macOS; checked for syntax in CI. |

---

## 5. Voice Wake-up (Gemini / Google Assistant) Execution Chain

1. **Voice Trigger**: User says "Hey Google, turn on my computer" to the Home Phone.
2. **Assistant Matching**: Google Assistant Routine matches the phrase and launches App Shortcut `wake_pc_shortcut`.
3. **Headless Entry**: System starts transparent activity [`VoiceWakeActivity`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/VoiceWakeActivity.kt) (no visible UI).
4. **Delegate to Deep Module**: Calls `PcActionDispatcher.dispatch("WAKE", "VoiceWakeActivity")`.
5. **Magic Packet Broadcast**: Dispatcher reads target MAC and sends UDP Magic Packet broadcast to `255.255.255.255:9`.
6. **Lifecycle End**: Records to `AppLogger`, shows Toast feedback, and calls `finish()` immediately.

---

## 6. Cross-Platform Build, Test, and Verification SOP

### 6.1 Native Command Reference Table

#### A. Run Deep Module Unit Tests (Must Pass 100%)
* **Linux / macOS**:
  ```bash
  cd wakeonlanhomephone && ./gradlew testDebugUnitTest
  ```
* **Windows (PowerShell)**:
  ```powershell
  cd wakeonlanhomephone; .\gradlew.bat testDebugUnitTest
  ```

#### B. Kotlin Compilation Check
* **Linux / macOS**:
  ```bash
  cd wakeonlanhomephone && ./gradlew compileDebugKotlin
  cd ../wakeonwanremotephone && ./gradlew compileDebugKotlin
  ```
* **Windows (PowerShell)**:
  ```powershell
  cd wakeonlanhomephone; .\gradlew.bat compileDebugKotlin
  cd ..\wakeonwanremotephone; .\gradlew.bat compileDebugKotlin
  ```

#### C. Local Release APK Build & Sync to Root
* **Linux / macOS**:
  ```bash
  cd wakeonlanhomephone && ./gradlew assembleRelease && cp app/build/outputs/apk/release/app-release.apk ../wakeonlan-home-phone.apk
  cd ../wakeonwanremotephone && ./gradlew assembleRelease && cp app/build/outputs/apk/release/app-release.apk ../wakeonwan-remote-phone.apk
  ```
* **Windows (PowerShell)**:
  ```powershell
  cd wakeonlanhomephone; .\gradlew.bat assembleRelease; Copy-Item -Path app\build\outputs\apk\release\app-release.apk -Destination ..\wakeonlan-home-phone.apk -Force
  cd ..\wakeonwanremotephone; .\gradlew.bat assembleRelease; Copy-Item -Path app\build\outputs\apk\release\app-release.apk -Destination ..\wakeonwan-remote-phone.apk -Force
  ```

#### D. PC Python Daemon Syntax Validation
* **Linux / macOS**:
  ```bash
  python3 -m py_compile computer/pc_onoff.py
  ```
* **Windows (PowerShell)**:
  ```powershell
  python -m py_compile computer\pc_onoff.py
  ```

#### E. PC Rust Daemon Testing & Linting (Must Pass 100%)
* **Linux / macOS**:
  ```bash
  cd computer && cargo fmt --all -- --check && cargo clippy --all-targets -- -D warnings && cargo test
  ```
* **Windows (PowerShell)**:
  ```powershell
  cd computer; cargo fmt --all -- --check; cargo clippy --all-targets -- -D warnings; cargo test
  ```

---

## 7. GitHub Actions CI/CD & Release Workflows

### 7.1 CI/CD Pipeline (`ci.yml`)
* **Workflow Path**: [`.github/workflows/ci.yml`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/.github/workflows/ci.yml)
* **Trigger Conditions**: `push` or `pull_request` to `master` / `main` (ignoring `**.md` and `.gitignore`), or manual `workflow_dispatch`.
* **Subsystem Path Filtering & Conditional Execution**:
  Uses `dorny/paths-filter` to isolate subsystems so **unchanged components are never compiled redundantly**:
  1. `detect-changes`: Inspects `home-phone` (`wakeonlanhomephone/**`), `remote-phone` (`wakeonwanremotephone/**`), and `computer` (`computer/**`).
  2. `build-home-phone`: Runs JDK 21 unit tests and APK packaging only when `wakeonlanhomephone/**` changes (skipped otherwise).
  3. `build-remote-phone`: Builds Release APK only when `wakeonwanremotephone/**` changes (skipped otherwise).
  4. `build-computer`: Runs `cargo fmt --check`, `cargo clippy`, `cargo test`, Linux release build, and Python syntax check only when `computer/**` changes (skipped otherwise).

### 7.2 Multi-Platform Release Pipeline (`release.yml`)
* **Workflow Path**: [`.github/workflows/release.yml`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/.github/workflows/release.yml)
* **Trigger Conditions**: Tag push `v*`, or manual `workflow_dispatch` (with options to force Rust or Android builds).
* **Multi-Platform Build Matrix**:
  1. `build-windows`: Builds 64-bit standalone executable `pc_onoff-windows-x86_64.exe` and `pc_onoff-windows-x86_64.zip`.
  2. `build-linux`: Packages portable `pc_onoff-linux-x86_64.AppImage` via `appimagetool` and standalone binary `pc_onoff-linux-x86_64` / `.tar.gz`.
  3. `build-macos`: Compiles Apple Silicon (`aarch64-apple-darwin`) and Intel (`x86_64-apple-darwin`) on a macOS runner, merging into Universal Binary `pc_onoff-macos-universal` (`.zip` and `.tar.gz`).
  4. `build-android`: Builds Release APKs (`wakeonlan-home-phone.apk`, `wakeonwan-remote-phone.apk`).
  5. `publish-release`: Publishes all generated assets to GitHub Releases via `softprops/action-gh-release@v2`.

### 7.3 Mandatory CI/CD Verification Loop (Post-Push SOP)

> [!IMPORTANT]
> **Mandatory Post-Push Verification**:
> Whenever an AI Agent or developer pushes commits via `git push origin <branch>`, **never conclude the task immediately**. Always follow this procedure:
> 1. **Monitor CI Status**: Run `gh run list -L 1` to find the newly triggered Run ID, and track it with `gh run watch <RunID>`.
> 2. **Verify Results**: Ensure all executed jobs succeed (green status).
> 3. **Fix and Re-push**: If any job fails, inspect failure logs (`gh run view <RunID> --log-failed`), resolve root causes, verify locally, commit, push, and monitor until 100% green.

---

## 8. Extension and Modification Guidelines

### 8.1 Adding a New Power Command (e.g. Screen Lock)
1. **Modify [`PcActionDispatcher.kt`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt)**:
   * Add prefix matching: `rawUpper.startsWith("LOCK:") || rawUpper.startsWith("LOCK,")`.
   * Call `sendUdpCommand(ip, 9877, "lock")`.
2. **Modify PC Daemon [`action.rs`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/computer/src/action.rs)**:
   * Add new variant to `PowerAction` enum, `keyword()`, `label()`, and match arms in `parse()`.
3. **Modify PC Daemon [`power.rs`](file:///home/scott/Documents/opencode/wake-on-lan-for-android/computer/src/power.rs)**:
   * Implement system calls for Windows, Linux, and macOS.
4. **Add Unit Tests**:
   * Add test case in `PcActionDispatcherTest.kt`.
   * Add parsing and dispatch test cases in `computer/src/action.rs` and `computer/src/dispatch.rs`.
5. **Transport Channels Work Automatically**: TCP, MQTT, Voice, and Widgets will immediately support the new command without changing transport code.

### 8.2 Subnet Broadcast & Multi-PC Wake-up
* `PcActionDispatcher.dispatch("WAKE:<MAC>")` dynamically supports passing target MAC addresses.
* Subnet-directed broadcasts can be configured by passing custom broadcast IP addresses to `sendUdpPacket()`.
