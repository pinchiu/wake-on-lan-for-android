# Wake-on-LAN 電腦端電源控制守護程式 (pc_onoff)

本程式為配合 Android 手機端 Wake-on-LAN / Wake-on-WAN 應用之電腦端常駐守護程式。以 Rust 語言撰寫，無任何肥大依賴，編譯為單一極輕量可執行檔（約 420 KB）。完整支援 **Windows** 與 **Linux** 跨平台環境。

---

## 核心特色

1. **零干擾背景常駐（無黑視窗、無多餘 GUI）**：
   * 雙擊 `pc_onoff.exe` 直接於背景安靜運行，不會彈出任何黑色命令提示字元視窗或多餘的 GUI 介面。
2. **預設自動啟用開機啟動**：
   * 雙擊執行檔即**預設自動完成開機啟動設定**（Windows 註冊至 `HKCU Run`，完全免管理員權限）。
   * 開機登入系統後自動於背景常駐運行，無需使用者手動介入或選擇。
3. **單實例守護**：
   * 具備系統級互斥鎖機制，多次雙擊不會導致連接埠衝突或重複執行，自動確保常駐服務正常運作。
4. **本地即時日誌紀錄**：
   * 每次連線來源 IP、執行時間戳記與系統電源操作均自動記錄於 `pc_onoff.log`，方便隨時追蹤。

---

## 跨平台開機自動啟動機制

| 作業系統 | 實作機制 | 權限需求 | 特點 |
| :--- | :--- | :--- | :--- |
| **Windows** | 使用者登錄檔 `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` | 免管理員權限 | 雙擊自動完成註冊，重啟電腦後自動背景執行 |
| **Linux** | `systemd --user` 服務檔 + XDG Autostart (`~/.config/autostart/`) | 免 sudo 權限 | 同時支援主流桌面環境 (GNOME/KDE) 與純伺服器模式 |

---

## 支援的遠端控制指令 (UDP 9877)

| 指令 | 說明 | Windows 呼叫方式 | Linux 呼叫方式 |
| :--- | :--- | :--- | :--- |
| `shutdown` | 關機 | `shutdown /s /t 0` (靜默無黑視窗) | `systemctl poweroff` |
| `reboot` | 重新開機 | `shutdown /r /t 0` (靜默無黑視窗) | `systemctl reboot` |
| `sleep` | 進入睡眠 | 鎖定工作站並調用原生 `SetSuspendState(0, 0, 0)`（保留 WoL 喚醒能力） | `systemctl suspend` |
| `hibernate` | 進入休眠 | 鎖定工作站並執行休眠 | `systemctl hibernate` |
| `lock` | 鎖定螢幕 | 原生調用 `LockWorkStation` | `loginctl lock-session` |

---

## 使用方式

### Windows 使用方式
1. 直接在檔案總管雙擊執行 `pc_onoff.exe`。
2. 程式將自動於背景開始監聽 UDP 9877，並已完成開機自動啟動註冊。
3. 可隨時開啟同目錄下的 `pc_onoff.log` 檢視本機 IP、運作狀態與遠端控制紀錄。

### Linux 使用方式
* 於終端機執行 `./pc_onoff`，自動註冊 `systemd --user` 服務與 XDG Autostart 並於背景監聽。

### 命令列常用管理參數
* `pc_onoff.exe`：預設在背景執行並自動設定開機啟動
* `pc_onoff.exe --status`：檢視目前狀態、本機 IP 與開機啟動設定
* `pc_onoff.exe --log`：檢視最新日誌記錄
* `pc_onoff.exe --stop`：終止正在背景運行的服務
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
