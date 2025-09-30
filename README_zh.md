# Wake-on-LAN Home Phone Remote Control

一個遠端控制系統，使用 Android 手機作為助手，通過行動數據和本地網路，實現對電腦的 Wake-on-LAN (WoL)、關機和重啟。

## 功能特色
- **Wake-on-LAN**：從遠端喚醒電腦（需網路支援）。
- **遠端關機/重啟**：透過行動數據和本地網路控制電腦電源。
- **跨平台電腦**：支援 Windows、Linux、macOS。
- **安全監聽**：使用 UDP 埠，無需公開 IP。

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

### 3. 取得網路資訊
- 電腦 IP（e.g., 192.168.1.100）。
- 電腦 MAC 位址（e.g., `ipconfig /all` on Windows）。

## 使用方法
1. 啟動 Android App 服務。
2. 從遠端手機發送 UDP 資料到助手手機的 IPv6:9876：
   - WoL： `WAKE:你的MAC位址` (e.g., `WAKE:AA:BB:CC:DD:EE:FF`)
   - 關機： `SHUTDOWN:電腦IP` (e.g., `SHUTDOWN:192.168.1.100`)
   - 重啟： `REBOOT:電腦IP`

3. 檢查手機 logcat 或 App 日誌，確認執行。

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
