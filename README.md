# Wake-on-LAN Home Phone Remote Control
[中文說明 (Traditional Chinese)](README_zh.md)

A remote control system that uses an Android phone as a helper to perform Wake-on-LAN (WoL), shutdown, reboot, sleep, and hibernate operations on a computer over mobile data, internet, and a local network.

## Features
- **Wake-on-LAN**: Remotely wake up your computer using local UDP broadcast (LAN) or TCP / MQTT (WAN).
- **Remote Shutdown/Reboot/Sleep/Hibernate/Lock**: Control computer power state and lock screen via local network or remote helper commands.
- **Reliable Phone-to-Phone Direct Connection (TCP)**: Connect directly from Remote Phone to Home Phone over IPv6/IPv4 using TCP (port 9876) with instant two-way confirmation and status feedback.
- **Local LAN Mode**: When connected to the same Wi-Fi network, the Remote Phone can directly broadcast Magic Packets via UDP (port 9) without going through the helper phone.
- **MQTT Support**: Connects to any MQTT broker (e.g., Adafruit IO, HiveMQ, EMQX) for true remote control across firewalls without port forwarding.
- **Cross-Platform PC Daemon**: High-performance, zero-dependency standalone daemon written in Rust, supporting Windows, Linux (AppImage & binary), and Apple macOS (Universal Binary for Apple Silicon and Intel).

## Downloads

You can download the latest pre-built files from the [GitHub Releases page](https://github.com/pinchiu/wake-on-lan-for-android/releases):

### Android Apps
- `wakeonlan-home-phone.apk`: Install this on the phone connected to the same home Wi-Fi network as your computer. This phone acts as the "helper / server".
- `wakeonwan-remote-phone.apk`: Install this on the phone you carry with you to send commands from anywhere. This is your "remote controller".

### Computer Power Control Daemon (`pc_onoff`)
- **Windows**: `pc_onoff-windows-x86_64.exe` (or `pc_onoff-windows-x86_64.zip`) — standalone 64-bit Windows executable, zero installation needed.
- **Linux**: `pc_onoff-linux-x86_64.AppImage` (or standalone binary `pc_onoff-linux-x86_64` / `pc_onoff-linux-x86_64.tar.gz`) — portable AppImage executable on any modern Linux distribution without root.
- **Apple macOS**: `pc_onoff-macos-universal.zip` (or `pc_onoff-macos-universal.tar.gz` / standalone binary `pc_onoff-macos-universal`) — Universal Binary supporting both Apple Silicon (M1/M2/M3/M4) and Intel Macs.

## Architecture and Protocols

1. **Remote Phone -> Home Phone (WAN / Internet Direct Mode)**:
   - Uses **TCP (Port 9876)** over public IPv6 / IPv4.
   - Provides guaranteed delivery and two-way status acknowledgment (ACK).
2. **Remote Phone -> Home Phone (MQTT Mode)**:
   - Uses **MQTT (TCP/TLS)** via an external broker.
   - Bypasses NAT and router firewalls without requiring a public IP.
3. **Remote Phone -> Computer (Local LAN Mode)**:
   - Direct Wake-on-LAN: Broadcasts Magic Packet via **UDP (Port 9)** to `255.255.255.255`.
   - Direct PC Control: Sends command via **UDP (Port 9877)** to the computer's local IP.
4. **Home Phone -> Computer (LAN WoL & Control)**:
   - WoL: Broadcasts Magic Packet via **UDP (Port 9)**.
   - PC Power Commands: Sends command via **UDP (Port 9877)** to the computer running `pc_onoff`.

## Prerequisites
- **Android Phones**: One phone acting as a helper (on the same LAN as the computer) and another for remote control (using mobile data or external Wi-Fi).
- **Computer**: Wake-on-LAN enabled in BIOS / Network Card settings.
- **Network**: Helper phone and computer must be on the same Wi-Fi subnet.

## Installation and Setup

### 1. Computer Power Control Daemon (pc_onoff)
Download the pre-built binary for your operating system from the [GitHub Releases page](https://github.com/pinchiu/wake-on-lan-for-android/releases).

#### Running
- **Windows**:
  Double-click `pc_onoff-windows-x86_64.exe` (or `pc_onoff.exe`)!
  - Runs in the background immediately listening on UDP 9877 (no console window, no unnecessary GUI popups).
  - Automatically configures Windows autostart (registered in HKCU Run, no administrator privileges required).
  - Automatically logs local IP, connection source, and all power commands in `pc_onoff.log`.
  - Runs quietly in the background upon system reboot without manual intervention.
- **Linux**:
  - **AppImage (Recommended)**:
    ```bash
    chmod +x pc_onoff-linux-x86_64.AppImage
    ./pc_onoff-linux-x86_64.AppImage
    ```
  - **Standalone Binary**: Run `./pc_onoff` in terminal. It automatically registers a `systemd --user` service and XDG Autostart to run on user login.
- **Apple macOS**:
  - Download and extract `pc_onoff-macos-universal.zip` (or `.tar.gz`).
  - Run in terminal:
    ```bash
    chmod +x pc_onoff
    ./pc_onoff
    ```
  - Automatically installs and loads a user LaunchAgent at `~/Library/LaunchAgents/com.wakeonlan.pc_onoff.plist`, ensuring it starts on login without requiring root privileges.
  - Controls macOS power safely via AppleScript System Events (shutdown, restart), `pmset` (sleep, hibernate), and `CGSession` (screen lock).

### 2. Install the Android Apps
Download the pre-built APKs from the [GitHub Releases page](https://github.com/pinchiu/wake-on-lan-for-android/releases):
- `wakeonlan-home-phone.apk`
- `wakeonwan-remote-phone.apk`

*(Enable "Install from unknown sources" in Android settings if prompted.)*

### 3. Usage Modes

#### A. Direct TCP Connection Mode (Recommended for IPv6)
1. Open **Home Phone App** and start the service. The app will display its current listening IPv6 / IPv4 addresses on TCP port 9876.
2. Open **Remote Phone App**:
   - Enter the Home Phone's IPv6 address.
   - Enter the Target Computer's MAC address and local IPv4.
   - Tap **WAKE**, **SHUTDOWN**, **REBOOT**, **SLEEP**, or **HIBERNATE**.
   - The status bar will show the instant execution feedback returned by the Home Phone.

#### B. MQTT Mode
1. Configure the MQTT broker settings (Host, Port, Username, Password, Topic) in both apps.
2. Send commands to the configured topic:
   - `WAKE:<MAC>`
   - `SHUTDOWN:<IP>`
   - `REBOOT:<IP>`
   - `SLEEP:<IP>`
   - `HIBERNATE:<IP>`

#### C. Local LAN Mode
1. Toggle **Local LAN Mode** on the Remote Phone when connected to home Wi-Fi.
2. Commands are sent directly to the local broadcast / PC without passing through the helper phone.

## Troubleshooting
- **WoL Does Not Wake PC**: Ensure Wake-on-LAN is enabled in BIOS / UEFI and in Windows Network Adapter Properties (enable "Magic Packet").
- **Power Commands Unresponsive**: Ensure the firewall allows UDP port 9877:
  - **Windows**: Windows Defender Firewall must allow UDP 9877. Run `pc_onoff.exe --firewall` as Administrator to add the rule automatically.
  - **Linux**: If using `ufw`, run `sudo ufw allow 9877/udp`.
  - **macOS**: Check System Settings → Network → Firewall and allow incoming connections for `pc_onoff` if prompted.
