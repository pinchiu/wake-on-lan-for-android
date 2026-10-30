# Wake-on-LAN 電腦端電源控制守護程式 (pc_onoff)

本程式為配合 Android 手機端 Wake-on-LAN / Wake-on-WAN 應用之電腦端守護程式。以 Rust 語言撰寫，無任何第三方依賴，編譯為單一輕量可執行檔（約 440 KB）。同時完整相容 **Windows** 與 **Linux** 跨平台環境。

---

## 核心特色

1. **原生 Windows 桌面 GUI 視窗與系統匣 (System Tray)**：
   * 雙擊 `pc_onoff.exe` **立即開啟原生桌面 GUI 控制視窗**，無任何黑色命令提示字元視窗干擾。
   * 可直接在視窗內修改 UDP 監聽連接埠、切換「開機自動啟動」、查看本機 IP、測試鎖定螢幕與防火牆規則。
   * 內建即時通訊日誌框，收到封包時即時更新。
   * 點擊「縮小至系統匣」或視窗右上角關閉 (X) 時，自動縮小至右下角系統匣（托盤圖示），背景靜默持續監聽。
2. **開機免手動選擇，預設自動啟用自啟**：
   * 視窗內「開機自動啟動」**預設即為打勾啟用**，免手動選擇或繁瑣設定。
   * 開機啟動時自動於系統匣背景常駐，完全不影響桌面操作。
3. **本地即時日誌紀錄**：
   * 自動將每次連線來源 IP、執行時間與系統呼叫紀錄於 `pc_onoff.log`。
   * 視窗內提供「開啟完整日誌」按鈕，可直接喚起 Windows 記事本開啟檢視。

---

## 跨平台開機自動啟動機制

| 作業系統 | 實作機制 | 權限需求 | 特點 |
| :--- | :--- | :--- | :--- |
| **Windows** | 使用者登錄檔 `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` | 免管理員權限 | 自動複製至 `%LOCALAPPDATA%\pc_onoff\`，開機常駐系統匣 |
| **Linux** | `systemd --user` 服務檔 + XDG Autostart (`~/.config/autostart/`) | 免 sudo 權限 | 同時支援各主流桌面環境 (GNOME/KDE) 與無桌面純伺服器模式 |

---

## 支援的遠端控制指令 (UDP 9877)

| 指令 | 說明 | Windows 呼叫方式 | Linux 呼叫方式 |
| :--- | :--- | :--- | :--- |
| `shutdown` | 關機 | `shutdown /s /t 0` | `systemctl poweroff` |
| `reboot` | 重新開機 | `shutdown /r /t 0` | `systemctl reboot` |
| `sleep` | 進入睡眠 | 鎖定工作站並進入 S3 睡眠狀態 | `systemctl suspend` |
| `hibernate` | 進入休眠 | 鎖定工作站並進入 S4 休眠狀態 | `systemctl hibernate` |
| `lock` | 鎖定螢幕 | `user32.dll,LockWorkStation` | `loginctl lock-session` |

---

## 使用方式

### Windows 使用方式
1. 直接在檔案總管雙擊執行 `pc_onoff.exe`。
2. 畫面會直接彈出「**Wake-on-LAN 電腦端電源控制設定**」原生 GUI 視窗：
   * **基本設定**：可修改 UDP 連接埠（預設 9877），「開機自動啟動」預設已打勾。
   * **連線與服務狀態**：顯示本機區網 IP（方便於手機端輸入）、測試鎖定螢幕與設定防火牆。
   * **即時日誌紀錄**：下方即時顯示所有收到的指令封包與時間。
3. 點擊「縮小至系統匣」或視窗右上角關閉 (X)，程式會隱藏至螢幕右下角工作列系統匣，並在背景持續監聽。
4. 於系統匣圖示點擊右鍵可隨時「開啟控制視窗」、「檢視完整日誌」或「結束程式」。

### Linux 使用方式
* 於終端機執行 `./pc_onoff`，自動註冊 `systemd --user` 服務與 XDG Autostart 並於背景監聽。

### 命令列常用參數
* `pc_onoff.exe`：開啟原生 GUI 控制視窗
* `pc_onoff.exe --tray`：於背景靜默啟動並常駐於系統匣
* `pc_onoff.exe --install`：單獨註冊開機自動啟動
* `pc_onoff.exe --uninstall`：解除開機自動啟動
* `pc_onoff.exe --help`：檢視參數說明

---

## 編譯與建置說明

* **Windows 本地編譯**：
  ```powershell
  cd computer
  cargo test
  cargo build --release
  ```
* **Linux 本地/遠端編譯**：
  ```bash
  cd computer
  cargo test
  cargo build --release
  ```
