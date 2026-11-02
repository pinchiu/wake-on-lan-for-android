# Wake-on-LAN Computer Power Control Daemon (pc_onoff)

A cross-platform background daemon designed to pair with the Android Wake-on-LAN / Wake-on-WAN mobile applications. Written in pure Rust with zero third-party crate dependencies, compiled into a single lightweight binary (~420 KB). Fully supports **Windows**, **Apple macOS**, and **Linux**.

---

## Key Features

1. **Zero-Distraction Background Daemon (No Console Windows, No Popups)**:
   * Windows: Double-click `pc_onoff.exe` to run quietly in the background without any console window or intrusive GUI.
   * macOS / Linux: Run `./pc_onoff` to listen quietly in the background.
2. **Automatic Autostart Configuration by Default**:
   * Registers automatic startup on user login:
     - Windows: Registered in user registry `HKCU Run` (requires no administrator privileges).
     - macOS: Automatically creates a `launchd` LaunchAgent (`~/Library/LaunchAgents/com.wakeonlan.pc_onoff.plist`).
     - Linux: Automatically registers `systemd --user` service and XDG Autostart (`~/.config/autostart`).
   * Runs automatically upon system reboot/login without manual intervention.
   * Can be disabled via `autostart=false` in `config.ini` or with `--uninstall`.
3. **Single-Instance Protection**:
   * Windows uses named mutexes (`Local\WakeOnLanPcOnOffMutex`); macOS and Linux use PID lockfiles to prevent duplicate instances or port conflicts.
4. **Local Event Logging with Automatic Rotation**:
   * Connection source IP, timestamps, and system power operations are automatically recorded in `pc_onoff.log`.
   * Automatically rotates to `pc_onoff.log.1` when exceeding 5 MB to prevent unbounded growth.
   * Control characters in remote packets are sanitized to prevent log injection.

---

## Cross-Platform Autostart Mechanisms

| OS | Mechanism | Permissions | Highlights |
| :--- | :--- | :--- | :--- |
| **Windows** | User registry `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` | No admin required | Auto-registers on double-click; automatically runs in background after reboot |
| **macOS (Apple)** | `launchd` LaunchAgent (`~/Library/LaunchAgents/com.wakeonlan.pc_onoff.plist`) | No root required | Managed by launchd upon user login; controllable via `launchctl` |
| **Linux** | `systemd --user` service unit + XDG Autostart (`~/.config/autostart/`) | No sudo required | Supports both mainstream desktop environments (GNOME/KDE) and headless user sessions |

---

## Supported Remote Commands (UDP 9877)

| Command | Action | Windows Invocation | macOS Invocation | Linux Invocation |
| :--- | :--- | :--- | :--- | :--- |
| `shutdown` | Power off | `shutdown /s /f /t 0` (silent) | `osascript` (System Events) / `shutdown -h now` | `systemctl poweroff` |
| `reboot` | Restart | `shutdown /r /f /t 0` (silent) | `osascript` (System Events) / `shutdown -r now` | `systemctl reboot` |
| `sleep` | Suspend to RAM | Locks workstation, calls native `SetSuspendState(0, 0, 0)` (preserves WoL) | `pmset sleepnow` / `osascript` (System Events) | `systemctl suspend` |
| `hibernate` | Suspend to Disk | Locks workstation, triggers hibernation | `pmset sleepnow` (follows system sleepmode) | `systemctl hibernate` |
| `lock` | Lock screen | Native `LockWorkStation` | `CGSession -suspend` / `pmset displaysleepnow` | `loginctl lock-session` |

* Case-insensitive (`SHUTDOWN`, `Shutdown`, etc.).
* Tolerates `SHUTDOWN:<IP>` or `SLEEP,<IP>` wire formats sent by Android apps; the `:<IP>` segment is safely ignored.
* Accepts common aliases: `poweroff`, `restart`, `suspend`, `standby`.

---

## Configuration File

The daemon resolves `config.ini` in the following order:

1. Directory containing the executable (if file exists or directory is writable).
2. Windows: `%LOCALAPPDATA%\pc_onoff\config.ini`
3. macOS: `~/Library/Application Support/pc_onoff/config.ini`
4. Linux: `~/.config/pc_onoff/config.ini`

```ini
# Wake-on-LAN Computer Power Control Configuration
udp_port=9877
autostart=true
```

| Key | Default | Description |
| :--- | :--- | :--- |
| `udp_port` | `9877` | UDP listening port, matching the mobile app setting. |
| `autostart` | `true` | Whether to ensure autostart registration on launch. |

Unknown keys and formatting errors are safely ignored; older configs (including `start_minimized`) load cleanly.

---

## Usage

### Windows
1. Double-click `pc_onoff-windows-x86_64.exe` (or `pc_onoff.exe`).
2. The program runs quietly in the background on UDP port 9877 and registers autostart.
3. Check `pc_onoff.log` in the same directory or application data directory for local IP, operational status, and incoming commands.

### macOS (Apple)
* Run in terminal: `./pc_onoff`. Automatically registers a user LaunchAgent (`~/Library/LaunchAgents/com.wakeonlan.pc_onoff.plist`) and listens on UDP 9877.

### Linux
* Run AppImage:
  ```bash
  chmod +x pc_onoff-linux-x86_64.AppImage
  ./pc_onoff-linux-x86_64.AppImage
  ```
* Or run standalone binary: `./pc_onoff`. Automatically registers `systemd --user` service and XDG Autostart.

### CLI Options
* `pc_onoff` (Windows: `pc_onoff.exe`): Run as background daemon and register autostart (default)
* `pc_onoff --status`: View background service, local IP, port, and autostart configuration
* `pc_onoff --log`: View latest 25 log lines
* `pc_onoff --stop`: Stop running background daemon
* `pc_onoff --install`: Enable autostart on boot (and persist to `config.ini`)
* `pc_onoff --uninstall`: Disable autostart on boot (and persist to `config.ini`)
* `pc_onoff --help`: Show command usage

---

## Architecture (Deep Module)

The core business logic converges in `dispatch`: callers only need `Dispatcher::dispatch(raw)`. Parsing, alias normalization, and power execution are encapsulated behind the interface. `power::PowerController` is the single system power seam, allowing unit tests to inject mocks without performing actual power operations.

| File | Responsibility |
| :--- | :--- |
| `src/action.rs` | Command syntax parsing (casing, whitespace, delimiters, aliases) and `PowerAction`. |
| `src/dispatch.rs` | **Deep module**: parse → execute → structured `DispatchError`; does not touch sockets or write logs. |
| `src/power.rs` | `PowerController` seam and Windows / macOS / Linux OS implementations. |
| `src/server.rs` | UDP 9877 transport adapter; passes incoming packets to dispatcher and records logs. |
| `src/config.rs` | `config.ini` pure parsing and atomic persistence. |
| `src/logging.rs` | Console + file logging, 5 MB size rotation, control character sanitization. |
| `src/autostart.rs` | Windows HKCU Run / macOS launchd / Linux systemd & XDG autostart. |
| `src/instance.rs` | Single-instance mutual exclusion and `--stop` implementation. |
| `src/paths.rs` | Config / log path resolution without side-effect probes. |
| `src/cli.rs` | Command line argument parsing and process lifecycle. |
| `src/main.rs` | Thin program entry point. |

---

## Building and Testing

```bash
cd computer
cargo fmt --all -- --check
cargo clippy --all-targets -- -D warnings
cargo test
cargo build --release
```

---

## Automated Packaging & GitHub Releases

The project includes automated GitHub Actions release workflows (`.github/workflows/release.yml`). When a version tag is pushed (e.g. `git tag v1.6.1 && git push origin v1.6.1`) or manually dispatched, artifacts are compiled across native runners:

| OS / Platform | Release Artifact Format | Description |
| :--- | :--- | :--- |
| **Windows** | `pc_onoff-windows-x86_64.exe`<br>`pc_onoff-windows-x86_64.zip` | Standalone executable and archive with README |
| **Linux** | `pc_onoff-linux-x86_64.AppImage`<br>`pc_onoff-linux-x86_64`<br>`pc_onoff-linux-x86_64.tar.gz` | Portable zero-install AppImage and standalone server binary |
| **Apple macOS** | `pc_onoff-macos-universal`<br>`pc_onoff-macos-universal.zip`<br>`pc_onoff-macos-universal.tar.gz` | Universal Binary via lipo for Apple Silicon (M1-M4) and Intel Macs |
| **Android** | `wakeonlan-home-phone.apk`<br>`wakeonwan-remote-phone.apk` | Helper and remote mobile app Release APKs |

> [!NOTE]
> This daemon does not depend on any third-party crates; Win32 APIs are declared via `extern "system"`, and platform variations are cleanly handled via `#[cfg(windows)]`, `#[cfg(target_os = "macos")]`, and `#[cfg(all(unix, not(target_os = "macos")))]`.
