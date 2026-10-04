# Agent 開發與維護指南 (agent.md)

本文件供 AI Agent 快速理解本專案之架構、通訊邏輯及各模組檔案職責，特別針對「語音喚醒（Gemini / Google 助理）與 Wake-on-LAN 廣播」功能提供精確之程式碼定位與修改指引。

---

## 1. 專案整體模組結構

本專案由三大核心子系統組成：

* `wakeonlanhomephone/`：**家用助手端 Android 應用程式**。放置於家中常駐連線 Wi-Fi，負責在區域網路發送 UDP WoL 廣播、監聽外網 TCP (Port 9876)、訂閱 MQTT Broker 以及接收語音捷徑指令。
* `wakeonwanremotephone/`：**外出遙控端 Android 應用程式**。供使用者隨身攜帶，透過 TCP (IPv6/IPv4)、MQTT 或內網直連發送控制指令。
* `computer/`：**目標電腦端控制程式**。`pc_onoff.py` 於電腦背景監聽 UDP Port 9877，接收關機、重啟、睡眠與休眠指令。

---

## 2. 語音喚醒 (Gemini / Google Assistant) 與捷徑架構

### 2.1 執行鏈路 (Execution Flow)
1. **語音輸入**：使用者對 homephone 說出「Hey Google，打開電腦」。
2. **助理觸發**：Gemini / Google 助理日常安排 (Routine) 匹配語音指令，啟動應用程式捷徑 `wake_pc_shortcut`。
3. **無介面入口**：系統啟動透明 Activity `VoiceWakeActivity`（不顯示完整 UI，不干擾畫面）。
4. **讀取設定**：`VoiceWakeActivity` 讀取 `MqttConfigManager` 儲存的目標電腦 MAC 地址（或 Intent Extra `EXTRA_MAC`）。
5. **發送封包**：透過協程在背景執行緒呼叫 `WolUtil.sendMagicPacket()`，發送 UDP Magic Packet 廣播至 `255.255.255.255:9`。
6. **記錄與結束**：寫入 `AppLogger`，彈出 Toast 提示，立即呼叫 `finish()` 關閉 Activity。

### 2.2 核心檔案職責對照表

| 功能區塊 | 檔案路徑 | 核心職責 |
| :--- | :--- | :--- |
| **語音捷徑 Activity** | `wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/VoiceWakeActivity.kt` | 接收語音/捷徑 Intent，讀取 MAC，背景非同步發送 WoL 封包並快速銷毀。 |
| **WoL 發送核心** | `wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolUtil.kt` | 組裝 102 位元組 Magic Packet（6x 0xFF + 16x MAC），透過 UDP DatagramSocket 廣播。 |
| **捷徑宣告** | `wakeonlanhomephone/app/src/main/res/xml/shortcuts.xml` | 靜態註冊 `wake_pc_shortcut`，標籤為「打開電腦」，目標指向 `VoiceWakeActivity`。 |
| **捷徑字串** | `wakeonlanhomephone/app/src/main/res/values/strings.xml` | 定義 `shortcut_wake_pc_short` 與 `shortcut_wake_pc_long`。 |
| **清單檔權限與元件** | `wakeonlanhomephone/app/src/main/AndroidManifest.xml` | 在 `MainActivity` 關聯 `shortcuts.xml`；宣告 `VoiceWakeActivity` 為透明主題且 `excludeFromRecents="true"`。 |
| **電腦參數管理** | `wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttConfigManager.kt` | 管理目標電腦之 `targetMac`（儲存於 `mqtt_config` SharedPreferences）。 |
| **日誌模組** | `wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/AppLogger.kt` | 提供全域執行日誌記錄，可在主畫面直接查看發送狀態。 |

---

## 3. 常見擴充與修改指引 (How-To for Future Agents)

### 3.1 若需要修改廣播 IP 或 Port（例如改為子網定向廣播）
* **定位檔案**：`wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolUtil.kt`
* **修改點**：`WolUtil.sendMagicPacket(macAddress, broadcastAddr, port)` 已具備預設參數 `broadcastAddr = "255.255.255.255"`, `port = 9`。若需改為特定子網廣播（如 `192.168.1.255`），在 `VoiceWakeActivity.kt` 中傳入第二個參數即可。

### 3.2 若需要支援語音控制多台電腦開機
1. **修改 `shortcuts.xml`**：在 `<shortcuts>` 內加入第二組 `<shortcut>`，使用不同 `android:shortcutId`（如 `wake_pc_2`）及不同標籤。
2. **Intent 傳遞參數**：在 `<shortcut>` 的 `<intent>` 節點中加入：
   ```xml
   <extra android:name="EXTRA_MAC" android:value="目標電腦MAC" />
   ```
3. **`VoiceWakeActivity` 已原生支援**：`VoiceWakeActivity.kt` 會優先檢查 `intent.getStringExtra("EXTRA_MAC")`，若存在則直接發送該 MAC，無須更動 Activity 代碼。

### 3.3 若需要新增語音關機 / 睡眠指令
1. 目標電腦需在背景運行 `computer/pc_onoff.py`（監聽 UDP 9877）。
2. 在 `shortcuts.xml` 中宣告新捷徑（如「關閉電腦」），目標指向新 Activity 或於 Intent Extra 帶入 `ACTION=SHUTDOWN` 與目標電腦 IP。
3. 發送 UDP 封包至 `電腦IP:9877`，Payload 為指令字串（`shutdown`、`sleep`、`reboot`、`hibernate`），邏輯可參考 `WolListenerService.kt` 中的 `sendCommandToPC()`。

---

## 4. 建置與編譯檢查指令

修改 `wakeonlanhomephone` 後，必須執行下列指令確認編譯無誤：

* **Kotlin 語法與編譯檢查**：
  ```powershell
  cd wakeonlanhomephone
  .\gradlew.bat compileDebugKotlin
  ```
* **建置 Debug APK**：
  ```powershell
  .\gradlew.bat assembleDebug
  # 輸出路徑：app/build/outputs/apk/debug/app-debug.apk
  ```
* **建置 Release APK 並同步至根目錄**：
  ```powershell
  .\gradlew.bat assembleRelease
  Copy-Item -Path app\build\outputs\apk\release\app-release.apk -Destination ..\wakeonlan-home-phone.apk -Force
  ```

---

## 5. homephone 實機環境必備設定

Agent 若需協助使用者排查語音無反應之問題，請依序檢查以下三項手機端設定：

1. **Google 助理日常安排 (Routine)**：
   * 觸發指令：「打開電腦」或「開機」。
   * 動作：選取「wake on lan home phone」之「打開電腦」捷徑。
2. **應用程式電池最佳化**：
   * 進入 Android 設定 -> 應用程式 -> wake on lan home phone -> 電池 -> 設定為「無限制 (Unrestricted)」，避免系統休眠截斷網路。
3. **目標 MAC 地址儲存**：
   * 確認 App 內之「Target MAC」已正確儲存電腦網卡實體位址。

---

## 6. GitHub Actions CI/CD 流水線

* **設定檔路徑**：`.github/workflows/ci.yml`
* **觸發條件**：`push` 或 `pull_request` 至 `master` / `main`，或透過 `workflow_dispatch` 手動觸發。
* **驗證與建置項目**：
  1. `build-home-phone`：以 JDK 21 建置 `wakeonlanhomephone` Release APK，產出構件上傳。
  2. `build-remote-phone`：以 JDK 21 建置 `wakeonwanremotephone` Release APK，產出構件上傳。
  3. `verify-computer-script`：以 Python 3.11 語法編譯檢查 `computer/pc_onoff.py`。

