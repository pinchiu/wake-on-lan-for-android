# Wake-on-LAN Home Phone Remote Control

一個遠端控制系統，使用 Android 手機作為助手，通過行動數據和本地網路，實現對電腦的 Wake-on-LAN (WoL)、關機和重啟。

## 功能特色
- **Wake-on-LAN**：從遠端喚醒電腦（需網路支援）。
- **遠端關機/重啟**：透過行動數據和本地網路控制電腦電源。
- **跨平台電腦**：支援 Windows、Linux、macOS。
- **安全監聽**：使用 UDP 埠，無需公開 IP。

## 先決條件
- **Android 手機**：一支作為助手（在同一 LAN 網路），一支遠端控制（行動數據）。
- **電腦**：安裝 Python 3，且 BIOS/網路卡啟用 WoL（電腦需 Wake-on-LAN 支援）。
- **網路**：助手手機和電腦在同一 Wi-Fi 子網路。關機/重啟支援行動數據路由。

## 安裝與設定

### 1. 安裝電腦端 Python 監聽器
下載 `Python/remote_control_listener.py` 到電腦。
```bash
cd Python
python pc_onoff.py  # 手動測試
```

#### 自動啟動（開機時）
- **Windows**：用 schtasks 加入開機（確保 Python 在 PATH）：
  ```
  schtasks /create /tn "RemoteControlListener" /tr "python \"C:\path\to\remote_control_listener.py\"" /sc onlogon /rl highest /f
  ```
- **Linux/macOS**：參閱 systemd 或 Launch Agents 設定。

### 2. 安裝 Android App
- 將 `Android/` 專案匯入 Android Studio。
- 編譯安裝到助手手機（支援 API 24+）。
- 啟動 App，自動監聽 UDP 埠 9876。

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