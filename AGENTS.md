# 跨工具專案全域開發與維護規範 (AGENTS.md)

本文件為專案之全域規範設定檔（支援 Linux Foundation / Agentic AI 標準，以及 Antigravity、Claude Code、Cursor、Windsurf 等所有 AI 開發代理工具與人工開發者）。詳細記錄專案之核心架構、深模組設計、指令分派管線、各檔案職責，以及**跨平台（Linux、macOS、Windows）建置、測試與驗證作業程序**。

---

## 1. 專案整體架構與核心模組

本專案由三大核心子系統組成：

* [`wakeonlanhomephone/`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonlanhomephone/)：**家用助手端 Android 應用程式**。放置於家中常駐連線 Wi-Fi，作為外網進入區域網路之控制樞紐。負責在區域網路發送 UDP WoL 廣播、監聽外網 TCP (Port 9876)、訂閱 MQTT Broker、接收語音捷徑觸發與桌面小工具點擊，並透過 `PcActionDispatcher` 統一分派與執行指令。
* [`wakeonwanremotephone/`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonwanremotephone/)：**外出遙控端 Android 應用程式**。供使用者隨身攜帶，透過 TCP (IPv6/IPv4)、MQTT 或內網直連向家用助手或目標電腦發送控制指令。支援多主機 Profile 切換與桌面一鍵喚醒小工具。
* [`computer/`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/computer/)：**目標電腦端控制程式**。`pc_onoff.py` 於電腦背景監聽 UDP Port 9877，接收關機、重啟、睡眠與休眠指令並執行對應作業系統之系統呼叫。

---

## 2. 核心深模組架構：PcActionDispatcher

為了消除傳輸協議與業務邏輯之緊密耦合，家用端 (`wakeonlanhomephone`) 採用「深模組 (Deep Module)」架構設計：

```
[外部輸入端 (Transport Adapters)]
  ├── TCP 監聽器 (WolListenerService: 9876)
  ├── MQTT 訂閱器 (MqttWolService: wakeonlan/#)
  ├── 語音/捷徑 (VoiceWakeActivity: Routine / Shortcut)
  └── 桌面小工具 (WakePcWidgetProvider: App Widget)
                │
                ▼ (統一呼叫)
┌─────────────────────────────────────────────────────────────┐
│          PcActionDispatcher (深模組 / 核心業務收斂)          │
│                                                             │
│  介面: suspend fun dispatch(rawCommand, source): PcActionResult
│                                                             │
│  職責:                                                      │
│    1. 指令語法解析與正規化 (分開分隔符 :, 處理與空白修整)    │
│    2. 參數提取 (MAC 地址校驗、IP 地址解析)                  │
│    3. 業務操作執行:                                          │
│       - WAKE: 發送 WoL UDP 廣播封包 (Port 9)               │
│       - SHUTDOWN/REBOOT/SLEEP/HIBERNATE:                    │
│         發送電腦電源控制 UDP 封包至 目標IP:9877             │
│    4. 結構化結果契約封裝 (PcActionResult)                    │
└─────────────────────────────────────────────────────────────┘
                │
                ▼ (回傳結果)
        PcActionResult (Success / Failure)
                │
                ├─► TCP Adapter: 回傳 toProtocolString() ("SUCCESS: ..." / "ERROR: ...")
                ├─► MQTT / Voice Adapter: 記錄 AppLogger 與系統通知
                └─► Widget Adapter: 顯示 Toast 與更新小工具時間
```

### 2.1 介面與契約定義

* **入口介面**：
  ```kotlin
  suspend fun dispatch(rawCommand: String, source: String = "local"): PcActionResult
  ```
* **結果型別**：
  ```kotlin
  sealed class PcActionResult {
      data class Success(val message: String, val details: String? = null) : PcActionResult()
      data class Failure(val errorMessage: String, val cause: Throwable? = null) : PcActionResult()
      fun toProtocolString(): String
  }
  ```
  `toProtocolString()` 產生符合 TCP 通訊協議之字串格式，成功為 `SUCCESS: <訊息>`，失敗為 `ERROR: <原因>`。

### 2.2 支援的指令語法

`PcActionDispatcher` 支援以下所有指令格式（大小寫不敏感，支援 `:` 與 `,` 分隔符）：

| 指令格式 | 範例 | 說明 |
| :--- | :--- | :--- |
| `WAKE` | `WAKE` | 使用系統預設的 MAC 地址發送 WoL 廣播封包 |
| `WAKE:<MAC>` 或 `WAKE,<MAC>` | `WAKE:AA:BB:CC:DD:EE:FF` | 使用指定 MAC 地址發送 WoL 廣播封包 |
| `<MAC>` (純 MAC) | `AA:BB:CC:DD:EE:FF` | 自動識別為 MAC 地址並發送 WoL 封包 |
| `SHUTDOWN:<IP>` 或 `SHUTDOWN,<IP>` | `SHUTDOWN:192.168.1.100` | 發送 UDP 電源指令 `shutdown` 至 `192.168.1.100:9877` |
| `REBOOT:<IP>` 或 `REBOOT,<IP>` | `REBOOT:192.168.1.100` | 發送 UDP 電源指令 `reboot` 至 `192.168.1.100:9877` |
| `SLEEP:<IP>` 或 `SLEEP,<IP>` | `SLEEP:192.168.1.100` | 發送 UDP 電源指令 `sleep` 至 `192.168.1.100:9877` |
| `HIBERNATE:<IP>` 或 `HIBERNATE,<IP>` | `HIBERNATE:192.168.1.100` | 發送 UDP 電源指令 `hibernate` 至 `192.168.1.100:9877` |

---

## 3. 外出端多電腦 Profile 管理與桌面小工具

### 3.1 多主機設定管理 (`DeviceProfileManager`)
* **資料結構**：`DeviceProfile(id, name, mac, ip)`。
* **持久化**：使用 `SharedPreferences` 配合 Gson 儲存 JSON 陣列。
* **向下相容**：初次啟動時自動遷移舊版單一 MAC / IP 設定為「Default PC」。
* **同步機制**：切換目前選中電腦時，自動更新 App 內部狀態並廣播觸發桌面小工具 (`RemoteWakeWidgetProvider`) 更新顯示之電腦名稱。

### 3.2 雙端桌面小工具 (Desktop Widgets)
* **家用端小工具** ([`WakePcWidgetProvider.kt`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WakePcWidgetProvider.kt))：
  單鍵點擊發送內網 WoL 廣播封包，立即喚醒預設主機並顯示發送狀態與時間戳記。
* **外出端小工具** ([`RemoteWakeWidgetProvider.kt`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteWakeWidgetProvider.kt))：
  顯示當前選定的目標電腦名稱，點擊即透過連線助手發送喚醒指令，支援多主機即時連動。

---

## 4. 各模組檔案職責速查表

| 功能區塊 | 檔案路徑 | 核心職責 |
| :--- | :--- | :--- |
| **指令分派核心 (深模組)** | [`wakeonlanhomephone/.../PcActionDispatcher.kt`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt) | 指令語法切割、參數檢核、WoL 與 UDP 9877 發送、封裝 `PcActionResult`。純 JVM 實作，完全解耦 Android 依賴。 |
| **單元測試套件** | [`wakeonlanhomephone/.../PcActionDispatcherTest.kt`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonlanhomephone/app/src/test/java/com/example/wakeonlanhomephone/PcActionDispatcherTest.kt) | 涵蓋所有指令格式、邊界條件與例外處理之單元測試（10 項完整測試案例）。 |
| **TCP 服務適配器** | [`wakeonlanhomephone/.../WolListenerService.kt`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt) | 監聽 TCP 9876，將連線輸入導向 `dispatcher.dispatch()`，並回寫 `toProtocolString()`。 |
| **MQTT 服務適配器** | [`wakeonlanhomephone/.../MqttWolService.kt`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttWolService.kt) | 訂閱 MQTT Topic，將訊息 Payload 導向 `dispatcher.dispatch()`。 |
| **語音捷徑適配器** | [`wakeonlanhomephone/.../VoiceWakeActivity.kt`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/VoiceWakeActivity.kt) | 接收語音/捷徑 Intent，呼叫 `dispatcher.dispatch("WAKE")`，非同步發送後立即關閉。 |
| **家用端桌面小工具** | [`wakeonlanhomephone/.../WakePcWidgetProvider.kt`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WakePcWidgetProvider.kt) | Android 桌面微型小工具，單鍵發送內網 WoL 開機廣播封包。 |
| **WoL 底層廣播公用程式** | [`wakeonlanhomephone/.../WolUtil.kt`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolUtil.kt) | 底層 Magic Packet 二進位封包組裝與 UDP Socket 發送。 |
| **設定值管理** | [`wakeonlanhomephone/.../MqttConfigManager.kt`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttConfigManager.kt) | 管理目標電腦 MAC、MQTT 伺服器配置之 SharedPreferences 存取。 |
| **捷徑靜態註冊** | [`wakeonlanhomephone/.../res/xml/shortcuts.xml`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/res/xml/shortcuts.xml) | 宣告 Google 助理可用之靜態捷徑 `wake_pc_shortcut`。 |
| **日誌記錄器** | [`wakeonlanhomephone/.../AppLogger.kt`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/AppLogger.kt) | 提供全域 UI 即時日誌快取與顯示。 |
| **外出端多主機管理** | [`wakeonwanremotephone/.../DeviceProfileManager.kt`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/DeviceProfileManager.kt) | 外出端多電腦設定管理（支援多台電腦名稱、MAC、IP 儲存、新增、編輯與切換）。 |
| **外出端桌面小工具** | [`wakeonwanremotephone/.../RemoteWakeWidgetProvider.kt`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteWakeWidgetProvider.kt) | 外出端桌面小工具，單鍵遠端喚醒當前選定電腦。 |
| **外出端網路工具庫** | [`wakeonwanremotephone/.../RemoteNetworkUtil.kt`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt) | 外出端網路指令封裝（TCP 9876、LAN WoL 廣播、UDP 9877 直連）。 |
| **電腦端守護程式** | [`computer/pc_onoff.py`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/computer/pc_onoff.py) | 於 PC 監聽 UDP 9877，接收電源控制指令執行 Windows 系統操作。 |

---

## 5. 語音喚醒 (Gemini / Google 助理) 執行鏈路

1. **語音觸發**：使用者對家用手機說出「Hey Google，打開電腦」。
2. **助理匹配**：Gemini / Google 助理日常安排 (Routine) 匹配語音詞條，啟動應用程式捷徑 `wake_pc_shortcut`。
3. **無介面入口**：系統啟動透明 Activity [`VoiceWakeActivity`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/VoiceWakeActivity.kt)（不顯示完整 UI，不干擾畫面）。
4. **委派深模組**：呼叫 `PcActionDispatcher.dispatch("WAKE", "VoiceWakeActivity")`。
5. **廣播發送**：Dispatcher 讀取目標 MAC 並由背景執行緒發送 UDP Magic Packet 廣播至 `255.255.255.255:9`。
6. **結束生命週期**：寫入 `AppLogger`，彈出 Toast 提示，立即呼叫 `finish()` 關閉 Activity。

---

## 6. 跨平台建置、測試與驗證作業程序 (SOP)

本專案支援在 **Linux**、**macOS** 與 **Windows** 跨平台環境進行開發與驗證。

### 6.1 自動化委派與本地備援腳本 (推薦)

專案提供跨平台自動化腳本，支援**優先委派至區域網路專屬建置伺服器 (Homeserver)**；若未配置或無法連線建置主機，腳本將**自動優雅降級為本地端建置**：

* **Linux / macOS (Bash / Zsh)**：
  ```bash
  # 執行單元測試
  ./scripts/remote-build.sh -a test

  # 建置 Release APK 並下載至根目錄
  ./scripts/remote-build.sh -a build

  # 全流程 (單元測試 + 建置 APK)
  ./scripts/remote-build.sh -a all

  # 同時建置家用端與外出端
  ./scripts/remote-build.sh -a all -t both
  ```

* **Windows (PowerShell)**：
  ```powershell
  # 執行單元測試
  .\scripts\remote-build.ps1 -Action test

  # 建置 Release APK 並同步至根目錄
  .\scripts\remote-build.ps1 -Action build

  # 全流程 (單元測試 + 建置 APK)
  .\scripts\remote-build.ps1 -Action all

  # 同時建置家用端與外出端
  .\scripts\remote-build.ps1 -Action all -Target both
  ```

> [!TIP]
> **遠端建置伺服器配置**：若欲使用 SSH 遠端建置卸載本地運算，僅需於個人的 `~/.ssh/config` 中配置別名 `homeserver`：
> ```sshconfig
> Host homeserver
>     HostName <主機IP或網域名稱>
>     User <使用者名稱>
>     IdentityFile <私鑰路徑>
> ```
> 亦可透過環境變數 `BUILD_HOST=<別名或IP>` 指定遠端主機。

---

### 6.2 跨平台本地原生指令對照表

若開發者偏好直接在終端機手動執行原生指令，請依所在作業系統使用以下指令：

#### A. 執行深模組單元測試（必須 100% 通過）
* **Linux / macOS**：
  ```bash
  cd wakeonlanhomephone && ./gradlew testDebugUnitTest
  ```
* **Windows (PowerShell)**：
  ```powershell
  cd wakeonlanhomephone; .\gradlew.bat testDebugUnitTest
  ```

#### B. Kotlin 編譯語法檢查
* **Linux / macOS**：
  ```bash
  cd wakeonlanhomephone && ./gradlew compileDebugKotlin
  ```
* **Windows (PowerShell)**：
  ```powershell
  cd wakeonlanhomephone; .\gradlew.bat compileDebugKotlin
  ```

#### C. 本地打包 Release APK 並同步至根目錄
* **Linux / macOS**：
  ```bash
  cd wakeonlanhomephone && ./gradlew assembleRelease && cp app/build/outputs/apk/release/app-release.apk ../wakeonlan-home-phone.apk
  ```
* **Windows (PowerShell)**：
  ```powershell
  cd wakeonlanhomephone; .\gradlew.bat assembleRelease; Copy-Item -Path app\build\outputs\apk\release\app-release.apk -Destination ..\wakeonlan-home-phone.apk -Force
  ```

#### D. 電腦端 Python 守護腳本語法驗證
* **Linux / macOS**：
  ```bash
  python3 -m py_compile computer/pc_onoff.py
  ```
* **Windows (PowerShell)**：
  ```powershell
  python -m py_compile computer\pc_onoff.py
  ```

---

## 7. GitHub Actions CI/CD 流水線與自動化驗證守則

* **設定檔路徑**：[`.github/workflows/ci.yml`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/.github/workflows/ci.yml)
* **觸發條件**：`push` 或 `pull_request` 至 `master` / `main`，或透過 `workflow_dispatch` 手動觸發。
* **流水線工作 (Jobs)**：
  1. `build-home-phone`：以 JDK 21 執行 `./gradlew testDebugUnitTest` 與 `assembleRelease`，驗證單元測試與 APK 建置。
  2. `build-remote-phone`：以 JDK 21 建置 `wakeonwanremotephone` Release APK。
  3. `verify-computer-script`：以 Python 3.11 語法檢查 `computer/pc_onoff.py`。

### 7.1 強制性 CI/CD 監控與修復循環規範 (Post-Push Verification Loop)

> [!IMPORTANT]
> **推播後必檢規範 (Mandatory CI/CD Verification)**：
> 任何 AI Agent 或開發者在完成 `git push origin <branch>` 推播程式碼後，**絕對不得直接結束任務**。必須依循以下程序：
> 1. **監控 CI/CD 狀態**：使用 GitHub CLI 執行 `gh run list -L 1` 取得剛觸發的 Run ID，並使用 `gh run watch <RunID>`（或在背景追蹤）等待流水線執行完畢。
> 2. **檢驗執行結果**：確認所有 Jobs（`build-home-phone`、`build-remote-phone`、`verify-computer-script`）皆為綠燈通過（Success）。
> 3. **錯誤即時修復與重推 (Fix & Push Again)**：
>    - 若任何 Job 失敗（Failure / Error），必須立即調閱失敗工作之錯誤日誌（如 `gh run view <RunID> --log-failed`）。
>    - 深入分析失敗根因（例如單元測試斷言失敗、Kotlin 編譯錯誤、依賴套件衝突等）並進行程式碼修復。
>    - 重新在本機執行測試確認修復後，再次提交 (`git commit`) 並推播至 GitHub (`git push`)。
>    - 持續重複監控直到 CI/CD 100% 通過為止。

---

## 8. 常見擴充與修改指引 (How-To for Future Agents)

### 8.1 若需要新增一項電腦控制指令 (例如鎖定螢幕 LOCK)
1. **修改 [`PcActionDispatcher.kt`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt)**：
   * 在指令前綴匹配中加入 `rawUpper.startsWith("LOCK:") || rawUpper.startsWith("LOCK,")`。
   * 呼叫 `sendUdpCommand(ip, 9877, "lock")`。
2. **修改 [`computer/pc_onoff.py`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/computer/pc_onoff.py)**：
   * 在指令判斷區塊加入 `elif cmd == 'lock': ctypes.windll.user32.LockWorkStation()`。
3. **編寫單元測試**：
   * 在 [`PcActionDispatcherTest.kt`](file:///c:/Users/poo/Desktop/wake-on-lan-for-android/wakeonlanhomephone/app/src/test/java/com/example/wakeonlanhomephone/PcActionDispatcherTest.kt) 中加入 `dispatch_lockCommand_sendsUdpPacket` 測試。
4. **所有傳輸管道自動生效**：TCP、MQTT、語音與 Widget 皆無需修改任何通訊程式碼即可直接支援該新指令。

### 8.2 若需要支援多台電腦開機或定向子網廣播
* 在 `PcActionDispatcher` 初始化或呼叫時，`dispatch("WAKE:11:22:33:44:55:66")` 或 `dispatch("WAKE", ...)` 已支援動態傳入 MAC。
* 定向子網廣播可於 `PcActionDispatcher` 增加廣播位址參數（預設仍為 `255.255.255.255`）。

### 8.3 若需要新增傳輸通道 (例如 Webhook HTTP Server 或 BLE)
* 僅需建立新的傳輸適配器 Service/Receiver，將收到的文字指令直接丟入 `dispatcher.dispatch(raw, source)`，完全無須重寫指令解析與網路發送邏輯。
