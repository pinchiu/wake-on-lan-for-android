# Wake-on-LAN Home Phone Remote Control

A remote control system that uses an Android phone as a helper to perform Wake-on-LAN (WoL), shutdown, and reboot operations on a computer over mobile data and a local network.

## Features
- **Wake-on-LAN**: Remotely wake up your computer (requires network support).
- **Remote Shutdown/Reboot**: Control your computer's power using mobile data and the local network.
- **Cross-Platform**: The computer-side script supports Windows, Linux, and macOS.
- **Secure Listening**: Uses a UDP port without needing a public IP address.

## Prerequisites
- **Android Phones**: One phone to act as a helper (on the same LAN as the computer) and another for remote control (using mobile data).
- **Computer**: Python 3 installed, and WoL enabled in the BIOS/network card (the computer must support Wake-on-LAN).
- **Network**: The helper phone and the computer must be on the same Wi-Fi subnet.

## Installation and Setup

### 1. Install the Python Listener on the Computer
Download `computer/pc_onoff.py` to your computer. You can test it manually first.

```bash
cd computer
python pc_onoff.py
```

#### Automatic Startup (on boot)
- **Windows**: Use `schtasks` to add it to startup (make sure Python is in your PATH):
  ```
  schtasks /create /tn "RemoteControlListener" /tr "python "C:\path\to\pc_onoff.py"" /sc onlogon /rl highest /f
  ```
- **Linux/macOS**: Refer to `systemd` or `Launch Agents` for setup.

### 2. Install the Android App
- Import the `wakeonlanhomephone/` project into Android Studio.
- Compile and install it on the helper phone (supports API 24+).
- Start the app, and it will automatically listen on UDP port 9876.

### 3. Get Network Information
- **Computer IP address**: e.g., `192.168.1.100`.
- **Computer MAC address**: e.g., use `ipconfig /all` on Windows or `ifconfig` on macOS/Linux.

## Usage
1. Start the Android app service on the helper phone.
2. From the remote phone, send a UDP packet to the helper phone's public IPv6 address on port 9876 with one of the following commands:
   - **WoL**: `WAKE:YOUR_MAC_ADDRESS` (e.g., `WAKE:AA:BB:CC:DD:EE:FF`)
   - **Shutdown**: `SHUTDOWN:COMPUTER_IP` (e.g., `SHUTDOWN:192.168.1.100`)
   - **Reboot**: `REBOOT:COMPUTER_IP`
3. Check the phone's logcat or the app's logs to confirm execution.

## Resource Usage
- **Network**: The helper phone listens on IPv6 port 9876 and sends UDP packets to the computer on port 9877 (WoL broadcast).
- **Permissions**: The phone requires network permissions, and the computer script requires shutdown permissions.
- **Conditions**: Depending on the computer's configuration, administrator privileges may be required for the Python script.

## Troubleshooting
- **WoL Doesn't Work**: Check that WoL is enabled in the BIOS and that your network supports broadcast packets.
- **UDP Fails**: Make sure the helper phone's IPv6 is reachable from the remote phone and that the computer's firewall allows UDP traffic on port 9877.
- **Error Messages**: Check the app's logs or the Python console for errors.

## Contributing
Contributions are welcome! Please submit an Issue or Pull Request and adhere to the MIT License.

## License
This project is licensed under the MIT License.
