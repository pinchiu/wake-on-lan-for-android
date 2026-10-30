# Wake-on-LAN 電腦端電源控制守護程式 (pc_onoff)

本程式為配合 Android 手機端 Wake-on-LAN / Wake-on-WAN 應用之電腦端背景守護程式。以 Rust 語言撰寫，無任何第三方依賴，編譯為單一輕量可執行檔（約 450 KB）。同時完整相容 **Windows** 與 **Linux** 跨平台環境。

---

## 核心亮點

1. **開機免手動選擇，直接於背景掛載常駐**：
   - 執行程式時**預設直接於背景常駐監聽**（不再強制於終端機跳出選單要求手動輸入 `[1]`）。
   - 預設自動啟用開機自動啟動，無需使用者繁瑣設定。
2. **跨平台共通視覺化設定介面 (Local Web UI)**：
   - 內建輕量化本地 Web 設定控制台（預設 `http://127.0.0.1:9878`）。
   - 只要在瀏覽器打開，或執行 `pc_onoff --ui`，即可查看即時狀態、修改連接埠、一鍵切換開機啟動與測試系統控制指令。
   - 純標準庫實作，無論在 Windows 還是無螢幕的 Linux 伺服器/樹莓派上均能 100% 共通運作。
3. **本地即時日誌紀錄**：
   - 自動將每次連線來源 IP、執行時間與系統呼叫紀錄於 `pc_onoff.log`。
   - 可在 Web 控制台中即時觀看通訊日誌。

---

## 跨平台開機自動啟動機制

| 作業系統 | 實作機制 | 權限需求 | 特點 |
| :--- | :--- | :--- | :--- |
| **Windows** | 使用者登錄檔 `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` | 免管理員權限 | 自動複製至 `%LOCALAPPDATA%\pc_onoff\`，開機無黑視窗靜默常駐 |
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

### 1. 預設執行（直接在背景掛載常駐）
* **Windows**：直接滑鼠雙擊 `pc_onoff.exe`。程式會自動加入 Windows 開機啟動並在背景靜默監聽，不留命令提示字元黑視窗。
* **Linux**：執行 `./pc_onoff`。

### 2. 開啟視覺化設定介面
* 於終端機執行：
  ```bash
  pc_onoff --ui
  ```
  系統會自動喚起預設瀏覽器開啟 `http://127.0.0.1:9878` 控制台。
* 亦可直接在任一瀏覽器網址列輸入 `http://localhost:9878` 進入設定。

### 3. 設定檔 (config.ini)
程式於同目錄自動生成 `config.ini`，亦可直接以文字編輯器修改：
```ini
# Wake-on-LAN 電腦端電源控制設定
udp_port=9877
web_port=9878
autostart=true
enable_web_ui=true
```

### 4. 命令列常用參數
* `pc_onoff`：預設啟動（背景常駐 + 自動啟用開機自啟）
* `pc_onoff --ui`：啟動並自動開啟瀏覽器設定頁面
* `pc_onoff --install`：單獨註冊開機自動啟動
* `pc_onoff --uninstall`：解除開機自動啟動
* `pc_onoff --help`：檢視參數說明

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
