# Wake-on-LAN Home Phone Remote Control
[🇨🇳 中文說明 (Chinese)](README_zh.md)


A remote control system that uses an Android phone as a helper to perform Wake-on-LAN (WoL), shutdown, and reboot operations on a computer over mobile data and a local network.

## Features
- **Wake-on-LAN**: Remotely wake up your computer using UDP (LAN) or MQTT (WAN).
- **Remote Shutdown/Reboot**: Control your power state via local network or MQTT commands.
- **MQTT Support**: Connects to any MQTT broker (e.g., Adafruit IO, HiveMQ) for true remote control over the internet without port forwarding.
- **Cross-Platform PC Script**: Python script supports Windows, Linux, and macOS.
- **Secure Listening**: Uses local UDP ports and encrypted MQTT connections (SSL/TLS).

## Downloads

You can download the latest APKs from the [GitHub Releases page](https://github.com/pinchiu/wake-on-lan-for-andoiriod/releases/tag/v20250930).

- `wakeonlan-home-phone.apk`: The app to be installed on the phone connected to the same network as the computer.
- `wakeonwan-remote-phone.apk`: The app to be installed on the phone that will be used for remote control.

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

To have the Python script run automatically when you log in to your computer, follow these instructions.

- **Windows**:
  You can use the Windows Task Scheduler to create a new task. Open a Command Prompt as an administrator and run the following command. Make sure to replace `C:\path\to\pc_onoff.py` with the actual path to the `pc_onoff.py` file on your computer.
  ```
  schtasks /create /tn "RemoteControlListener" /tr "pythonw \"C:\path\to\pc_onoff.py\"" /sc onlogon /rl highest /f
  ```
  *Note: We use `pythonw.exe` instead of `python.exe` to run the script without a visible console window.*

- **Linux (Untested)**:
  > **Note**: This script has not been fully verified on Linux. Use at your own risk.
  You can use `cron` to run the script at startup. Open your crontab by running `crontab -e` and add the following line at the end. Make sure to replace `/path/to/pc_onoff.py` with the actual path to the file.
  ```
  @reboot /usr/bin/python3 /path/to/pc_onoff.py
  ```

- **macOS (Untested)**:
  > **Note**: This script has not been fully verified on macOS. Use at your own risk.
  You can use `launchd` to create a Launch Agent. Create a new file named `com.remotecontrol.listener.plist` in `~/Library/LaunchAgents/` with the following content. Make sure to replace `/path/to/pc_onoff.py` with the actual path to the file.
  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
  <plist version="1.0">
  <dict>
      <key>Label</key>
      <string>com.remotecontrol.listener</string>
      <key>ProgramArguments</key>
      <array>
          <string>/usr/bin/python3</string>
          <string>/path/to/pc_onoff.py</string>
      </array>
      <key>RunAtLoad</key>
      <true/>
  </dict>
  </plist>
  ```
  Then, load the agent by running `launchctl load ~/Library/LaunchAgents/com.remotecontrol.listener.plist`.

### 2. Install the Android Apps

You can download the pre-built APK files from the [GitHub Releases page](https://github.com/pinchiu/wake-on-lan-for-andoiriod/releases/tag/v20250930).

- **`wakeonlan-home-phone.apk`**: Install this on the phone that is connected to the same Wi-Fi network as your computer. This phone will act as the "helper".
- **`wakeonwan-remote-phone.apk`**: Install this on the phone you will use to send the commands from anywhere. This is your "remote".

You will need to enable "Install from unknown sources" on your Android phone to install the APK files.

### 3. Configure the App (MQTT & Settings)
1. Open the app and click the **Gear Icon (⚙️)**.
2. You will see the **Settings Menu**:
   - **MQTT Settings**: Configure your broker connection.
   - **App Update**: Check for and install the latest version of the app.
3. **MQTT Configuration**:
   - **Broker/Host**: Address of your MQTT broker (e.g., `io.adafruit.com`).
   - **Port**: Usually `1883` (TCP) or `8883` (SSL).
   - **Username/Password**: Your broker credentials.
   - **Topic**: The topic to listen on (e.g., `home/pc/control`).
   - **Target MAC**: (Optional) Default MAC address for simple "WAKE" commands.

### 4. Get Network Information
- **Computer IP address**: e.g., `192.168.1.100`.
- **Computer MAC address**: e.g., use `ipconfig /all` on Windows or `ifconfig` on macOS/Linux.

## Usage

### Local Control (UDP)
The app listens on IPv6 port 9876. You can send UDP packets from another device on the LAN.

### Remote Control (MQTT)
Send messages to your configured MQTT Topic:
- **Wake**: Payload `WAKE` (uses configured Target MAC) or `WAKE,AA:BB:CC:DD:EE:FF`.
- **Shutdown**: Payload `SHUTDOWN,192.168.1.100` (requires PC script).
- **Reboot**: Payload `REBOOT,192.168.1.100` (requires PC script).
- **Sleep**: Payload `SLEEP,192.168.1.100`.
- **Hibernate**: Payload `HIBERNATE,192.168.1.100`.

Check the **Live Logs** on the main screen to verify commands are received.

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