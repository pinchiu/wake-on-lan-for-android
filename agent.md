# Agent 開發與維護指南 (agent.md)

本文件供後續維護或功能擴充之 AI Agent 與開發者使用，詳細說明本專案之核心架構、深模組設計、指令分派管線、各檔案職責與測試驗證規範。

---

## 1. 專案整體架構與核心模組

本專案由三大核心子系統組成：

* `wakeonlanhomephone/`：**家用助手端 Android 應用程式**。放置於家中常駐連線 Wi-Fi，作為外網進入區域網路之控制樞紐。負責在區域網路發送 UDP WoL 廣播、監聽外網 TCP (Port 9876)、訂閱 MQTT Broker、接收語音捷徑觸發，並透過 `PcActionDispatcher` 統一分派與執行指令。
* `wakeonwanremotephone/`：**外出遙控端 Android 應用程式**。供使用者隨身攜帶，透過 TCP (IPv6/IPv4)、MQTT 或內網直連向家用助手或目標電腦發送控制指令。
* `computer/`：**目標電腦端控制程式**。`pc_onoff.py` 於電腦背景監聽 UDP Port 9877，接收關機、重啟、睡眠與休眠指令並執行對應 OS 系統呼叫。

---

## 2. 核心深模組架構：PcActionDispatcher

為了消除傳輸協議與業務邏輯之緊密耦合，家用端 (`wakeonlanhomephone`) 採用「深模組 (Deep Module)」架構設計：

```
[外部輸入端 (Transport Adapters)]
  ├── TCP 監聽器 (WolListenerService: 9876)
  ├── MQTT 訂閱器 (MqttWolService: wakeonlan/#)
  └── 語音/捷徑 (VoiceWakeActivity: Routine / Shortcut)
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

## 3. 各模組檔案職責速查表

| 功能區塊 | 檔案路徑 | 核心職責 |
| :--- | :--- | :--- |
| **指令分派核心 (深模組)** | `wakeonlanhomephone/.../PcActionDispatcher.kt` | 指令語法切割、參數檢核、WoL 與 UDP 9877 發送、封裝 `PcActionResult`。純 JVM 實作，完全解耦 Android 依賴。 |
| **單元測試套件** | `wakeonlanhomephone/.../PcActionDispatcherTest.kt` | 涵蓋所有指令格式、邊界條件與例外處理之單元測試（10 項完整測試案例）。 |
| **TCP 服務適配器** | `wakeonlanhomephone/.../WolListenerService.kt` | 監聽 TCP 9876，將連線輸入導向 `dispatcher.dispatch()`，並回寫 `toProtocolString()`。 |
| **MQTT 服務適配器** | `wakeonlanhomephone/.../MqttWolService.kt` | 訂閱 MQTT Topic，將訊息 Payload 導向 `dispatcher.dispatch()`。 |
| **語音捷徑適配器** | `wakeonlanhomephone/.../VoiceWakeActivity.kt` | 接收語音/捷徑 Intent，呼叫 `dispatcher.dispatch("WAKE")`，非同步發送後立即關閉。 |
| **桌面小工具 (Widget)** | `wakeonlanhomephone/.../WakePcWidgetProvider.kt` | Android 桌面微型小工具，提供單鍵點擊呼叫 `dispatcher.dispatch("WAKE")` 發送開機封包。 |
| **WoL 底層廣播公用程式** | `wakeonlanhomephone/.../WolUtil.kt` | 底層 Magic Packet 二進位封包組裝與 UDP Socket 發送。 |
| **設定值管理** | `wakeonlanhomephone/.../MqttConfigManager.kt` | 管理目標電腦 MAC、MQTT 伺服器配置之 SharedPreferences 存取。 |
| **捷徑靜態註冊** | `wakeonlanhomephone/.../res/xml/shortcuts.xml` | 宣告 Google 助理可用之靜態捷徑 `wake_pc_shortcut`。 |
| **日誌記錄器** | `wakeonlanhomephone/.../AppLogger.kt` | 提供全域 UI 即時日誌快取與顯示。 |
| **電腦端守護程式** | `computer/pc_onoff.py` | 於 PC 監聽 UDP 9877，接收電源控制指令執行 Windows 系統操作。 |

---

## 4. 語音喚醒 (Gemini / Google 助理) 執行鏈路

1. **語音觸發**：使用者對家用手機說出「Hey Google，打開電腦」。
2. **助理匹配**：Gemini / Google 助理日常安排 (Routine) 匹配語音詞條，啟動應用程式捷徑 `wake_pc_shortcut`。
3. **無介面入口**：系統啟動透明 Activity `VoiceWakeActivity`（不顯示完整 UI，不干擾畫面）。
4. **委派深模組**：`VoiceWakeActivity` 呼叫 `PcActionDispatcher.dispatch(command, "VoiceWakeActivity")`。
5. **廣播發送**：Dispatcher 讀取目標 MAC 並由背景執行緒發送 UDP Magic Packet 廣播至 `255.255.255.255:9`。
6. **結束生命週期**：寫入 `AppLogger`，彈出 Toast 提示，立即呼叫 `finish()` 關閉 Activity。

---

## 5. 常見擴充與修改指引 (How-To for Future Agents)

### 5.1 若需要新增一項電腦控制指令 (例如鎖定螢幕 LOCK)
1. **修改 `PcActionDispatcher.kt`**：
   * 在指令前綴匹配中加入 `rawUpper.startsWith("LOCK:") || rawUpper.startsWith("LOCK,")`。
   * 呼叫 `sendUdpCommand(ip, 9877, "lock")`。
2. **修改 `computer/pc_onoff.py`**：
   * 在指令判斷區塊加入 `elif cmd == 'lock': ctypes.windll.user32.LockWorkStation()`。
3. **編寫單元測試**：
   * 在 `PcActionDispatcherTest.kt` 中加入 `dispatch_lockCommand_sendsUdpPacket` 測試。
4. **所有傳輸管道自動生效**：TCP、MQTT、語音皆無需修改任何通訊程式碼即可直接支援該新指令。

### 5.2 若需要支援多台電腦開機或定向子網廣播
* 在 `PcActionDispatcher` 初始化或呼叫時，`dispatch("WAKE:11:22:33:44:55:66")` 或 `dispatch("WAKE", ...)` 已支援動態傳入 MAC。
* 定向子網廣播可於 `PcActionDispatcher` 增加廣播位址參數（預設仍為 `255.255.255.255`）。

### 5.3 若需要新增傳輸通道 (例如 Webhook HTTP Server 或 BLE)
* 僅需建立新的傳輸適配器 Service/Receiver，將收到的文字指令直接丟入 `dispatcher.dispatch(raw, source)`，完全無須重寫指令解析與網路發送邏輯。

---

## 6. 建置、測試與驗證指令

在提交或推播程式碼前，必須在 Windows PowerShell 依序執行以下驗證：

* **執行深模組單元測試（必須 100% 通過）**：
  ```powershell
  cd wakeonlanhomephone
  .\gradlew.bat testDebugUnitTest
  ```
* **Kotlin 編譯檢查**：
  ```powershell
  .\gradlew.bat compileDebugKotlin
  ```
* **建置 Release APK 並同步至根目錄**：
  ```powershell
  .\gradlew.bat assembleRelease
  Copy-Item -Path app\build\outputs\apk\release\app-release.apk -Destination ..\wakeonlan-home-phone.apk -Force
  ```
* **驗證電腦端 Python 腳本語法**：
  ```powershell
  python -m py_compile computer\pc_onoff.py
  ```

---

## 7. GitHub Actions CI/CD 流水線

* **設定檔路徑**：`.github/workflows/ci.yml`
* **觸發條件**：`push` 或 `pull_request` 至 `master` / `main`，或透過 `workflow_dispatch` 手動觸發。
* **流水線工作 (Jobs)**：
  1. `build-home-phone`：以 JDK 21 執行 `./gradlew testDebugUnitTest` 與 `assembleRelease`，驗證單元測試與 APK 建置。
  2. `build-remote-phone`：以 JDK 21 建置 `wakeonwanremotephone` Release APK。
  3. `verify-computer-script`：以 Python 3.11 語法檢查 `computer/pc_onoff.py`。
