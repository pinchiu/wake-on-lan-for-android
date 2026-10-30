# Wake-on-LAN 電腦端電源控制守護程式 (pc_onoff)

本程式為配合 Android 手機端 Wake-on-LAN / Wake-on-WAN 應用之電腦端常駐守護程式。以 Rust 語言撰寫，無任何外部依賴，編譯為單一極輕量可執行檔（約 420 KB）。完整支援 **Windows** 與 **Linux** 跨平台環境。

---

## 核心特色

1. **零干擾背景常駐（無黑視窗、無多餘 GUI）**：
   * 雙擊 `pc_onoff.exe` 直接於背景安靜運行，不會彈出任何黑色命令提示字元視窗或多餘的 GUI 介面。
2. **預設自動啟用開機啟動**：
   * 預設在啟動時確保開機自啟設定（Windows 註冊至 `HKCU Run`，完全免管理員權限）。
   * 開機登入系統後自動於背景常駐運行，無需使用者手動介入。
   * 可由 `config.ini` 的 `autostart=false` 或 `--uninstall` 停用。
3. **單實例守護**：
   * Windows 使用具名互斥鎖、Linux 使用 PID 鎖定檔，多次啟動不會導致連接埠衝突或重複執行。
4. **本地即時日誌紀錄**：
   * 每次連線來源 IP、執行時間戳記與系統電源操作均自動記錄於 `pc_onoff.log`。
   * 日誌超過 5 MB 會自動輪替為 `pc_onoff.log.1`，不會無限制成長。
   * 遠端封包中的換行等控制字元會被清理，避免偽造日誌行。

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

* 大小寫不拘（`SHUTDOWN`、`Shutdown` 皆可）。
* 相容 Android 端送出的 `SHUTDOWN:<IP>`、`SLEEP,<IP>` 形式；`:<IP>` 部分會被忽略。
* 另接受別名：`poweroff`、`restart`、`suspend`、`standby`。

---

## 設定檔

程式會依下列順序選擇 `config.ini` 位置：

1. 執行檔所在目錄（若已有檔案或目錄可寫入）。
2. Windows：`%LOCALAPPDATA%\pc_onoff\config.ini`
3. Linux：`~/.config/pc_onoff/config.ini`

```ini
# Wake-on-LAN 電腦端電源控制設定
udp_port=9877
autostart=true
```

| 鍵 | 預設值 | 說明 |
| :--- | :--- | :--- |
| `udp_port` | `9877` | UDP 監聽連接埠，需與手機端設定一致。 |
| `autostart` | `true` | 啟動時是否確保開機自動啟動。 |

未知的鍵與格式錯誤的值會被忽略，舊版設定檔（含 `start_minimized`）仍可安全載入。

---

## 使用方式

### Windows 使用方式
1. 直接在檔案總管雙擊執行 `pc_onoff.exe`。
2. 程式將自動於背景開始監聽 UDP 9877，並依設定註冊開機自動啟動。
3. 可隨時開啟同目錄下的 `pc_onoff.log` 檢視本機 IP、運作狀態與遠端控制紀錄。

### Linux 使用方式
* 於終端機執行 `./pc_onoff`，自動註冊 `systemd --user` 服務與 XDG Autostart 並於背景監聽。

### 命令列常用管理參數
* `pc_onoff.exe`：預設在背景執行並依設定註冊開機啟動
* `pc_onoff.exe --status`：檢視背景服務、本機 IP、連接埠與開機啟動設定
* `pc_onoff.exe --log`：檢視最新 25 行日誌
* `pc_onoff.exe --stop`：停止正在背景運行的服務
* `pc_onoff.exe --install`：啟用開機自動啟動（並同步寫入 `config.ini`）
* `pc_onoff.exe --uninstall`：解除開機自動啟動（並同步寫入 `config.ini`）
* `pc_onoff.exe --help`：檢視參數說明

---

## 專案結構（深模組）

核心業務收斂在 `dispatch`：呼叫端只需 `Dispatcher::dispatch(raw)`，語法解析與系統電源操作都藏在介面之後。`power::PowerController` 是唯一的系統電源 seam，測試以假實作注入，因此不需要真的關機。

| 檔案 | 職責 |
| :--- | :--- |
| `src/action.rs` | 指令語法解析（大小寫、空白、`:`／`,`、別名）與 `PowerAction`。 |
| `src/dispatch.rs` | **深模組**：解析 → 執行 → 結構化 `DispatchError`，不寫日誌、不碰 socket。 |
| `src/power.rs` | `PowerController` seam 與 Windows／Linux 系統實作。 |
| `src/server.rs` | UDP 9877 傳輸適配器；將封包交給 dispatcher 並記錄結果。 |
| `src/config.rs` | `config.ini` 純函式解析與原子寫入。 |
| `src/logging.rs` | 主控台＋檔案日誌、5 MB 輪替、控制字元清理。 |
| `src/autostart.rs` | Windows 登錄檔／Linux systemd 與 XDG 開機自啟。 |
| `src/instance.rs` | 單一實例守護與 `--stop` 實作。 |
| `src/paths.rs` | 設定檔／日誌路徑選擇（不產生探測副作用）。 |
| `src/cli.rs` | 命令列解析與行程生命週期。 |
| `src/main.rs` | 極薄的程式入口。 |

---

## 編譯與建置說明

* **Windows 本地編譯**：
  ```powershell
  cd computer
  cargo fmt --all -- --check
  cargo clippy --all-targets -- -D warnings
  cargo test
  cargo build --release
  ```
* **Linux 本地/遠端編譯**：
  ```bash
  cd computer
  cargo fmt --all -- --check
  cargo clippy --all-targets -- -D warnings
  cargo test
  cargo build --release
  ```

> [!NOTE]
> 本專案不引入任何第三方 crate；Win32 API 以 `extern "system"` 直接宣告，
> 其餘平台差異以 `#[cfg(windows)]`／`#[cfg(not(windows))]` 收斂於各模組內。
