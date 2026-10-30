# Wake-on-LAN Home Phone Remote Control
[中文說明 (Traditional Chinese)](README_zh.md)

A remote control system that uses an Android phone as a helper to perform Wake-on-LAN (WoL), shutdown, reboot, sleep, and hibernate operations on a computer over mobile data, internet, and a local network.

## Features
- **Wake-on-LAN**: Remotely wake up your computer using local UDP broadcast (LAN) or TCP / MQTT (WAN).
- **Remote Shutdown/Reboot/Sleep/Hibernate**: Control computer power state via local network or remote helper commands.
- **Reliable Phone-to-Phone Direct Connection (TCP)**: Connect directly from Remote Phone to Home Phone over IPv6/IPv4 using TCP (port 9876) with instant two-way confirmation and status feedback.
- **Local LAN Mode**: When connected to the same Wi-Fi network, the Remote Phone can directly broadcast Magic Packets via UDP (port 9) without going through the helper phone.
- **MQTT Support**: Connects to any MQTT broker (e.g., Adafruit IO, HiveMQ, EMQX) for true remote control across firewalls without port forwarding.
- **Cross-Platform PC Daemon**: High-performance, zero-dependency standalone daemon written in Rust, supporting Windows and Linux without any runtime installation.

## Downloads

You can download the latest pre-built files from the [GitHub Releases page](https://github.com/pinchiu/wake-on-lan-for-android/releases):

- `wakeonlan-home-phone.apk`: Install this on the phone connected to the same home Wi-Fi network as your computer. This phone acts as the "helper / server".
- `wakeonwan-remote-phone.apk`: Install this on the phone you carry with you to send commands from anywhere. This is your "remote controller".
- `pc_onoff.exe`: Target computer power control daemon (standalone executable for Windows, no installation needed).

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
Download `pc_onoff.exe` from `computer/` (or compile `pc_onoff` on Linux).

#### Running
- **Windows**:
  Simply double-click `pc_onoff.exe`!
  - Runs in the background immediately listening on UDP 9877 (no console window, no unnecessary GUI popups).
  - Automatically configures Windows autostart (registered in HKCU Run, no admin privileges required).
  - Automatically logs local IP, connection source, and all power commands in `pc_onoff.log`.
  - Runs quietly in the background upon system reboot without manual intervention.
- **Linux**:
  Run `./pc_onoff` in terminal, which automatically registers a `systemd --user` service and XDG Autostart.

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
- **Power Commands Unresponsive**: Ensure Windows Defender Firewall allows UDP 9877. You can test through the Web UI or run `pc_onoff --firewall` as Administrator to add the rule.
