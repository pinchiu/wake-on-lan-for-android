# Wake-on-LAN Home Phone Remote Control
[🇺🇸 English README](README.md)

一個遠端控制系統，使用 Android 手機作為助手，通過行動數據和本地網路，實現對電腦的 Wake-on-LAN (WoL)、關機和重啟。

## 功能特色
- **Wake-on-LAN**: 透過 UDP (區域網路) 或 MQTT (網際網路) 遠端喚醒電腦。
- **遠端關機/重啟**: 透過區域網路或 MQTT 指令控制電源狀態。
- **MQTT 支援**: 連接至任何 MQTT Broker (如 Adafruit IO, HiveMQ)，無需設定連接埠轉發即可真正遠端控制。
- **跨平台電腦腳本**: Python 腳本支援 Windows, Linux, 和 macOS。
- **安全監聽**: 使用本地 UDP 埠與加密 MQTT 連線 (SSL/TLS)。

## 下載

您可以從 [GitHub 發行頁面](https://github.com/pinchiu/wake-on-lan-for-andoiriod/releases/tag/v20250930) 下載預先建置的 APK 檔案。

- **`wakeonlan-home-phone.apk`**：將此應用程式安裝在與您電腦連接到相同 Wi-Fi 網路的手機上。這支手機將作為「助手」。
- **`wakeonwan-remote-phone.apk`**：將此應用程式安裝在您要從任何地方傳送指令的手機上。這是您的「遙控器」。

## 先決條件
- **Android 手機**：一支作為助手（在同一 LAN 網路），一支遠端控制（行動數據）。
- **電腦**：安裝 Python 3，且 BIOS/網路卡啟用 WoL（電腦需 Wake-on-LAN 支援）。
- **網路**：助手手機和電腦在同一 Wi-Fi 子網路。關機/重啟支援行動數據路由。

## 安裝與設定

### 1. 安裝電腦端 Python 監聽器
下載 `computer/pc_onoff.py` 到電腦。您可以手動測試它。

```bash
cd computer
python pc_onoff.py
```

#### 自動啟動（開機時）

為了讓 Python 腳本在您登入電腦時自動運行，請按照以下說明操作。

- **Windows**：
  您可以使用 Windows 工作排程器來建立新工作。以系統管理員身分開啟命令提示字元，然後執行以下命令。請務必將 `C:\path\to\pc_onoff.py` 取代為您電腦上 `pc_onoff.py` 檔案的實際路徑。
  ```
  schtasks /create /tn "RemoteControlListener" /tr "pythonw \"C:\path\to\pc_onoff.py\"" /sc onlogon /rl highest /f
  ```
  *注意：我們使用 `pythonw.exe` 而非 `python.exe` 來執行腳本，這樣就不會顯示主控台視窗。*

- **Linux**：
  您可以使用 `cron` 在啟動時執行腳本。執行 `crontab -e` 來開啟您的 crontab，然後在檔案結尾新增以下這一行。請務必將 `/path/to/pc_onoff.py` 取代為檔案的實際路徑。
  ```
  @reboot /usr/bin/python3 /path/to/pc_onoff.py
  ```

- **macOS**：
  您可以使用 `launchd` 來建立啟動代理。在 `~/Library/LaunchAgents/` 中建立一個名為 `com.remotecontrol.listener.plist` 的新檔案，並填入以下內容。請務必將 `/path/to/pc_onoff.py` 取代為檔案的實際路徑。
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
  然後，執行 `launchctl load ~/Library/LaunchAgents/com.remotecontrol.listener.plist` 來載入代理。

### 2. 安裝 Android 應用程式

您可以從 [GitHub 發行頁面](https://github.com/pinchiu/wake-on-lan-for-andoiriod/releases/tag/v20250930) 下載預先建置的 APK 檔案。

- **`wakeonlan-home-phone.apk`**：將此應用程式安裝在與您電腦連接到相同 Wi-Fi 網路的手機上。這支手機將作為「助手」。
- **`wakeonwan-remote-phone.apk`**：將此應用程式安裝在您要從任何地方傳送指令的手機上。這是您的「遙控器」。

您需要在您的 Android 手機上啟用「安裝未知來源的應用程式」才能安裝 APK 檔案。

### 3. 設定 App (MQTT 與 設定選項)
1. 開啟 App 並點擊右上角的 **齒輪圖示 (⚙️)**。
2. 您會看到 **設定選單**：
   - **MQTT 設定**：設定您的 MQTT Broker 連線。
   - **App 更新**：檢查並安裝最新版本的 App。
3. **MQTT 設定內容**：
   - **Broker/Host**：您的 MQTT Broker 位址 (例如 `io.adafruit.com`)。
   - **Port**：通常是 `1883` (TCP) 或 `8883` (SSL)。
   - **Username/Password**：您的 Broker 帳號密碼。
   - **Topic**：要監聽的主題 (例如 `home/pc/control`)。
   - **Target MAC**：(選填) 用於簡易 "WAKE" 指令的預設 MAC 位址。

### 4. 取得網路資訊
- 電腦 IP（e.g., 192.168.1.100）。
- 電腦 MAC 位址（e.g., `ipconfig /all` on Windows）。

## 使用方法

### 本地控制 (UDP)
App 監聽 IPv6 port 9876。您可以從區域網路內的其他裝置發送 UDP 封包。

### 遠端控制 (MQTT)
發送訊息到您設定的 MQTT Topic：
- **喚醒 (Wake)**: 內容 `WAKE` (使用設定的目標 MAC) 或 `WAKE,AA:BB:CC:DD:EE:FF`。
- **關機 (Shutdown)**: 內容 `SHUTDOWN,192.168.1.100` (需搭配電腦腳本)。
- **重啟 (Reboot)**: 內容 `REBOOT,192.168.1.100` (需搭配電腦腳本)。
- **睡眠 (Sleep)**: 內容 `SLEEP,192.168.1.100`。
- **休眠 (Hibernate)**: 內容 `HIBERNATE,192.168.1.100`。

請檢查主畫面的 **即時日誌 (Live Logs)** 確認是否收到指令。

## 佔用資源
- **網路**：助手手機監聽 IPv6:9876，發送 UDP 到電腦:9877 (WoL broadcast)。
- **權限**：手機需網路權限，電腦腳本需關機權限。
- **條件**：依電腦設定，可能需管理員。

## 疑難排解
- **WoL 不動**：檢查 BIOS 啟用 WoL，網路廣播支援。
- **UDP 失敗**：確保手機 IPv6 可達，電腦防火牆開放埠。
- **錯誤訊息**：查看 App 日誌或 Python console。

## 貢獻
歡迎提交 Issue 或 Pull Request！請遵守 MIT Licence。

## 授權
MIT Licence。詳見 [LICENSE](LICENSE)。
