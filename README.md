# Wake-on-LAN Home Phone Remote Control
[中文說明 (Traditional Chinese)](README_zh.md)

A remote control system that uses an Android phone as a helper to perform Wake-on-LAN (WoL), shutdown, reboot, sleep, and hibernate operations on a computer over mobile data, internet, and a local network.

## Features
- **Wake-on-LAN**: Remotely wake up your computer using local UDP broadcast (LAN) or TCP / MQTT (WAN).
- **Remote Shutdown/Reboot/Sleep/Hibernate**: Control computer power state via local network or remote helper commands.
- **Reliable Phone-to-Phone Direct Connection (TCP)**: Connect directly from Remote Phone to Home Phone over IPv6/IPv4 using TCP (port 9876) with instant two-way confirmation and status feedback.
- **Local LAN Mode**: When connected to the same Wi-Fi network, the Remote Phone can directly broadcast Magic Packets via UDP (port 9) without going through the helper phone.
- **MQTT Support**: Connects to any MQTT broker (e.g., Adafruit IO, HiveMQ, EMQX) for true remote control across firewalls without port forwarding.
- **Cross-Platform PC Script**: Python listener script supporting Windows, Linux, and macOS.

## Downloads

You can download the latest pre-built APKs from the [GitHub Releases page](https://github.com/pinchiu/wake-on-lan-for-android/releases).

- `wakeonlan-home-phone.apk`: Install this on the phone connected to the same home Wi-Fi network as your computer. This phone acts as the "helper / server".
- `wakeonwan-remote-phone.apk`: Install this on the phone you carry with you to send commands from anywhere. This is your "remote controller".

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
   - PC Power Commands: Sends command via **UDP (Port 9877)** to the computer running `pc_onoff.py`.

## Prerequisites
- **Android Phones**: One phone acting as a helper (on the same LAN as the computer) and another for remote control (using mobile data or external Wi-Fi).
- **Computer**: Python 3 installed, and Wake-on-LAN enabled in BIOS / Network Card settings.
- **Network**: Helper phone and computer must be on the same Wi-Fi subnet.

## Installation and Setup

### 1. Install the Python Listener on the Computer
Download `computer/pc_onoff.py` to your computer. You can test it manually first:

```bash
cd computer
python pc_onoff.py
```

#### Automatic Startup (on Boot / Login)

- **Windows**:
  Use Windows Task Scheduler to create a startup task. Open Command Prompt as Administrator:
  ```cmd
  schtasks /create /tn "RemoteControlListener" /tr "pythonw \"C:\path\to\pc_onoff.py\"" /sc onlogon /rl highest /f
  ```
  *(Note: `pythonw.exe` runs the script in the background without a visible console window.)*

- **Linux (Untested)**:
  Add an entry to `crontab -e`:
  ```bash
  @reboot /usr/bin/python3 /path/to/pc_onoff.py
  ```

- **macOS (Untested)**:
  Create `~/Library/LaunchAgents/com.remotecontrol.listener.plist` and load it via `launchctl`.

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
- **TCP Connection Timed Out / Refused**: Check if your home Wi-Fi router firewall permits inbound traffic on TCP port 9876, or ensure both devices have routable IPv6 connectivity.
- **PC Commands Not Working**: Verify that `pc_onoff.py` is running on the target PC and that Windows Firewall allows UDP port 9877.

## License
This project is licensed under the MIT License.
