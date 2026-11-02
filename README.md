# Wake-on-LAN Home Phone Remote Control
[中文說明 (Traditional Chinese)](README_zh.md)

A complete cross-platform remote control system that turns an Android phone into a home gateway to perform Wake-on-LAN (WoL), shutdown, reboot, sleep, hibernate, and screen lock operations on your computers over mobile data, internet, or local Wi-Fi.

## Features
- **Wake-on-LAN**: Remotely wake up your computer using local UDP broadcast (LAN) or over the internet via TCP or MQTT.
- **Remote Power & Session Control**: Remotely trigger Shutdown, Reboot, Sleep, Hibernate, and Screen Lock.
- **Reliable Phone-to-Phone TCP Connection**: Connect directly from the Remote Phone to the Home Phone over IPv6 or IPv4 on TCP port 9876 with guaranteed delivery and immediate status feedback.
- **Local LAN Mode**: When connected to your home Wi-Fi, the Remote Phone can send WoL packets (UDP port 9) and power commands (UDP port 9877) directly without relaying through the helper phone.
- **MQTT Cloud Mode**: Connect to any MQTT broker (e.g. HiveMQ, EMQX, Adafruit IO) to control your PC without needing a public IP or router port forwarding.
- **Multi-Device Profiles**: Manage multiple target computers (name, MAC address, LAN IP) on the Remote Phone and switch between them effortlessly.
- **Dual Home Screen Widgets**:
  - *Home Phone Widget*: 1-tap local Wake-on-LAN broadcast to wake the default PC.
  - *Remote Phone Widget*: 1-tap remote wake targeting the currently selected profile.
- **Voice Assistant Integration**: Hands-free voice trigger via Google Assistant / Gemini routines using the static app shortcut (`wake_pc_shortcut`).
- **Bilingual Interface**: Seamlessly switch between English and Traditional Chinese in the app settings.
- **Cross-Platform PC Daemon (`pc_onoff`)**: High-performance, zero-dependency standalone daemon written in Rust for Windows, Linux, and Apple macOS.
  - Automatic zero-admin autostart (Windows Registry `HKCU Run`, Linux `systemd --user` / XDG Autostart, macOS `LaunchAgent`).
  - Automatic 5 MB log rotation (`pc_onoff.log`).
  - Rich command-line management interface.

## Downloads

Download the latest pre-built releases from the [GitHub Releases page](https://github.com/pinchiu/wake-on-lan-for-android/releases):

### Android Apps
- `wakeonlan-home-phone.apk`: Install on your spare Android phone stationed at home and connected to your home Wi-Fi. This acts as the gateway/helper.
- `wakeonwan-remote-phone.apk`: Install on the phone you carry with you to send control commands anywhere.

### Computer Power Control Daemon (`pc_onoff`)
- **Windows**: `pc_onoff-windows-x86_64.exe` (or `.zip`) — 64-bit standalone executable, runs immediately with zero dependencies.
- **Linux**: `pc_onoff-linux-x86_64.AppImage` (or standalone binary `pc_onoff-linux-x86_64` / `.tar.gz`) — portable AppImage running on any modern Linux distribution.
- **Apple macOS**: `pc_onoff-macos-universal.zip` (or `.tar.gz` / binary `pc_onoff-macos-universal`) — Universal Binary natively supporting Apple Silicon (M1/M2/M3/M4) and Intel Macs.

## System Architecture & Protocols

```
[Remote Phone (Client)]
       │
       ├─► (WAN: Public IPv6/IPv4) ── TCP Port 9876 ────┐
       ├─► (WAN: Cloud Broker) ────── MQTT (TCP/TLS) ───┼─► [Home Phone Helper]
       │                                                 │         │
       │                                                 │   (Local Wi-Fi Subnet)
       │                                                 │   WoL Broadcast: UDP 9
       │                                                 │   PC Power: UDP 9877
       │                                                 │         │
       └─► (LAN: Same Wi-Fi) ──────── Direct UDP 9/9877 ─┴─────────▼
                                                             [Target Computer]
                                                             (`pc_onoff` daemon)
```

1. **Remote Phone -> Home Phone (WAN TCP Direct)**:
   - Connects via **TCP (Port 9876)** over public IPv6 / IPv4.
   - Guaranteed two-way acknowledgment (returns `SUCCESS: ...` or `ERROR: ...`).
2. **Remote Phone -> Home Phone (MQTT Cloud Mode)**:
   - Subscribes and publishes via an external MQTT broker.
   - Traverses strict NATs and firewalls without port forwarding.
3. **Home Phone -> Target Computer (LAN Relay)**:
   - WoL Wake: Broadcasts Magic Packet via **UDP (Port 9)** to `255.255.255.255`.
   - PC Power Commands: Sends UDP datagram via **UDP (Port 9877)** to the PC's local IP.
4. **Remote Phone -> Target Computer (Local LAN Mode)**:
   - When on the same Wi-Fi network, commands are sent directly to the PC / subnet.

## Setup & Getting Started

### 1. Computer Power Control Daemon (`pc_onoff`)

Download the binary for your operating system from [Releases](https://github.com/pinchiu/wake-on-lan-for-android/releases).

#### Running on Windows
Double-click `pc_onoff-windows-x86_64.exe` (or `pc_onoff.exe`).
- Runs silently in the background listening on UDP 9877 without console window popups.
- Automatically registers Windows autostart (`HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, no admin required).
- Automatically writes event logs to `pc_onoff.log`.
- To allow firewall access automatically, run as administrator: `pc_onoff.exe --firewall`.

#### Running on Linux
- **AppImage (Recommended)**:
  ```bash
  chmod +x pc_onoff-linux-x86_64.AppImage
  ./pc_onoff-linux-x86_64.AppImage
  ```
- **Standalone Binary**:
  ```bash
  chmod +x pc_onoff-linux-x86_64
  ./pc_onoff-linux-x86_64
  ```
- Automatically registers a `systemd --user` service and an XDG autostart entry for persistence across reboots.

#### Running on Apple macOS
Download and extract `pc_onoff-macos-universal.zip`.
```bash
chmod +x pc_onoff
./pc_onoff
```
- Automatically installs and loads a user LaunchAgent at `~/Library/LaunchAgents/com.wakeonlan.pc_onoff.plist`.
- Runs automatically at login without root privileges.
- Safely executes power actions via AppleScript System Events (shutdown, restart), `pmset` (sleep, hibernate), and `CGSession` (lock screen).

#### Daemon CLI Commands
The `pc_onoff` executable provides built-in management options:
```bash
pc_onoff --status             # Check daemon running state and PID
pc_onoff --stop               # Stop running background instance
pc_onoff --restart            # Restart background instance
pc_onoff --test <action>      # Test action locally (shutdown, reboot, sleep, hibernate, lock)
pc_onoff --show-ip            # Display current local IP addresses
pc_onoff --show-log [N]       # View recent N log entries
pc_onoff --autostart-status   # Inspect autostart registration state
pc_onoff --install-autostart  # Re-register login autostart
pc_onoff --uninstall-autostart# Remove login autostart
pc_onoff --foreground         # Run in foreground for debugging
pc_onoff --port <PORT>        # Listen on custom UDP port (default: 9877)
```

### 2. Android Apps Setup

1. Install `wakeonlan-home-phone.apk` on your Home Phone and `wakeonwan-remote-phone.apk` on your Remote Phone.
2. Enable "Install from unknown sources" if prompted by Android.

### 3. Usage Modes

#### A. Direct TCP Mode (Recommended for IPv6)
1. Open **Home Phone App** and tap **Start Service**. The app displays its listening IPv6 and IPv4 addresses on TCP 9876.
2. Open **Remote Phone App**:
   - Add your PC profile (Target MAC and LAN IPv4).
   - Enter the Home Phone's IPv6 address.
   - Tap **WAKE**, **SHUTDOWN**, **REBOOT**, **SLEEP**, **HIBERNATE**, or **LOCK**.
   - The status bar will show real-time confirmation from the Home Phone.

#### B. MQTT Mode
1. Configure your MQTT broker credentials (Host, Port, Username, Password, Topic) in both apps.
2. Send commands using standard syntax:
   - `WAKE:<MAC>`
   - `SHUTDOWN:<IP>`
   - `REBOOT:<IP>`
   - `SLEEP:<IP>`
   - `HIBERNATE:<IP>`
   - `LOCK:<IP>`

#### C. Local LAN Mode
1. When connected to the same Wi-Fi network as your computer, toggle **Local LAN Mode** on the Remote Phone.
2. Commands are sent directly to the local subnet / computer IP without going through the Home Phone.

## Troubleshooting
- **WoL Does Not Wake Computer**:
  - Ensure Wake-on-LAN (or PCIe Power On) is enabled in BIOS / UEFI.
  - In Windows Device Manager, open Network Adapter properties and check "Allow this device to wake the computer" and "Only allow a magic packet to wake the computer".
- **Power Commands Unresponsive**:
  - Verify `pc_onoff` is running: run `pc_onoff --status`.
  - Ensure UDP port 9877 is allowed through firewall:
    - Windows: Run `pc_onoff.exe --firewall` as Administrator or add an inbound UDP 9877 rule in Windows Defender Firewall.
    - Linux: Run `sudo ufw allow 9877/udp` if ufw is active.
    - macOS: Allow incoming connections for `pc_onoff` in System Settings → Network → Firewall if prompted.
