<div class="wiki-breadcrumb">docs/wiki/zh / 2. 深層模組架構與接縫設計</div>

# 2. 深層模組架構與接縫設計

<details class="relevant-sources">
<summary>相關原始碼檔案</summary>

- [`computer/src/dispatch.rs`](../../../computer/src/dispatch.rs#L52-L70)
- [`computer/src/power.rs`](../../../computer/src/power.rs#L34-L56)
- [`computer/src/server.rs`](../../../computer/src/server.rs#L303-L385)
- [`wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt#L22-L175)
- [`wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt#L254-L300)
- [`wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttWolService.kt`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttWolService.kt#L232-L458)
- [`custom_components/pc_onoff/state.py`](../../../custom_components/pc_onoff/state.py#L38-L130)
- [`custom_components/pc_onoff/coordinator.py`](../../../custom_components/pc_onoff/coordinator.py#L143-L361)
- [`custom_components/pc_onoff/switch.py`](../../../custom_components/pc_onoff/switch.py#L43-L81)
- [`wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt`](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L15-L216)

</details>

## 2.1 對稱的領域核心

四個子系統都只暴露一個小介面，並把解析、socket、作業系統呼叫與框架黏著層藏在介面之後。這不是為了抽象而抽象：這是唯一能在不具備 Android 裝置、不真的關閉 PC、也不安裝 1 GB Home Assistant 的情況下，測試 wire 與狀態邏輯的方法。

| 子系統 | 深層核心 | 小介面 | 呼叫它的 adapter |
| :--- | :--- | :--- | :--- |
| Rust 常駐程式 | `Dispatcher<P: PowerController>` | `dispatch(raw) -> Result<PowerAction, DispatchError>` | `server.rs` UDP 9877、`web.rs` loopback action、`cli.rs` 生命週期 |
| 家用閘道手機 | `PcActionDispatcher` | `suspend fun dispatch(raw, source): PcActionResult` | TCP 9876、MQTT、語音捷徑、桌面小工具、Compose UI |
| Home Assistant | Layer 1 + `PcOnOffCoordinator` | `async_wake`、`async_turn_off`、`async_power(action)` | switch、button、binary sensor、config flow |
| Remote Phone | `RemoteNetworkUtil` | `sendTcpCommand` / `sendDirectCommandToPC` / `sendLocalMagicPacket` | 應用程式 UI 與桌面小工具 |

<div class="decision-context">
<div class="decision-title">為什麼這樣設計 / 設計決策背景</div>
<p><strong>每個傳輸層都可替換，領域核心不可替換。</strong> TCP 9876、MQTT、Google Home 與 Android 小工具都只是表達同樣五種電源動作。若把語法、驗證、MAC 解析或樂觀狀態塞進每個 adapter，一次變更就要改四份程式與四套測試。集中到接縫後，wire contract 只需測試一次，adapter 也能保持宣告式。</p>
<p><strong>Home Assistant Layer 1 刻意不依賴框架。</strong> protocol、probe、state machine 與 validation 規則都是純 Python，CI 不必安裝 Home Assistant 就能執行。Layer 2 負責 Home Assistant 型別與排程；Layer 3 只負責 entity 與名稱。因此樂觀狀態機可以用假單調時鐘測試，entity 測試也只需少量 `sys.modules` stub。</p>
</div>

來源：[computer/src/dispatch.rs:52-70](../../../computer/src/dispatch.rs#L52-L70)、[wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt:22-175](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt#L22-L175)、[custom_components/pc_onoff/state.py:38-130](../../../custom_components/pc_onoff/state.py#L38-L130)、[wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt:15-216](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L15-L216)

## 2.2 目標 PC 常駐程式（computer/）

常駐程式是唯一會碰觸作業系統電源 API 的子系統。它的依賴方向嚴格由內而外：`action` 與 `power` 從不匯入 transport，`dispatch` 只認識 `PowerController`，`server` 則是唯一知道 UDP 的模組。

| Rust 符號 | 檔案 / 行號 | 契約 |
| :--- | :--- | :--- |
| `PowerAction` | [`action.rs:12-93`](../../../computer/src/action.rs#L12-L93) | 五種標準動作、別名、大小寫折疊、去除 `:`/`,` 後綴 |
| `PowerController` | [`power.rs:34-56`](../../../computer/src/power.rs#L34-L56) | 唯一作業系統接縫：`execute(action) -> Result<(), PowerError>` |
| `Dispatcher<P>` | [`dispatch.rs:52-70`](../../../computer/src/dispatch.rs#L52-L70) | `dispatch(raw) -> Result<PowerAction, DispatchError>` |
| `AuthError` | [`auth.rs:41-87`](../../../computer/src/auth.rs#L41-L87) | `missing`、`bad_signature`、`expired`、`replayed`、`pre_start_replay`、`not_configured`、`malformed` |
| `serve` | [`server.rs:54-118`](../../../computer/src/server.rs#L54-L118) | 綁定 UDP、IPv4 必要、IPv6 盡力而為、把 datagram 交給 auth |
| `authenticate_datagram` | [`server.rs:303-385`](../../../computer/src/server.rs#L303-L385) | 信任來源或已驗證 envelope；回傳裸指令 |
| `status_json` | [`web.rs:560-598`](../../../computer/src/web.rs#L560-L598) | 儀表板狀態 + 最後 8 行日誌 |

常駐程式不會從 `dispatch.rs` 寫日誌；`server.rs` 把 `DispatchError::Rejected` 映射成 `warning`，把 `DispatchError::Execution` 映射成 `command_failed`。驗證失敗只記錄穩定的原因，絕不記錄 envelope 或簽章。

來源：[computer/src/action.rs:12-93](../../../computer/src/action.rs#L12-L93)、[computer/src/power.rs:34-56](../../../computer/src/power.rs#L34-L56)、[computer/src/dispatch.rs:52-70](../../../computer/src/dispatch.rs#L52-L70)、[computer/src/server.rs:303-385](../../../computer/src/server.rs#L303-L385)、[computer/src/auth.rs:41-200](../../../computer/src/auth.rs#L41-L200)、[computer/src/web.rs:560-598](../../../computer/src/web.rs#L560-L598)

## 2.3 家用閘道手機（wakeonlanhomephone/）

Kotlin dispatcher 是家用閘道手機的領域核心。它解析 `WAKE` / `ON` / `MAC` 與五種電源動作、解析每個目標的密鑰，然後送出 102-byte magic packet 或 UDP 9877 action。TCP、MQTT、語音、小工具與 Compose UI 都是 adapter。

| Kotlin 符號 | 檔案 / 行號 | 職責 |
| :--- | :--- | :--- |
| `PcActionResult` | [`PcActionDispatcher.kt:10-20`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt#L10-L20) | `Success(message)` / `Failure(error)`；`toProtocolString()` |
| `PcActionDispatcher` | [`PcActionDispatcher.kt:22-175`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt#L22-L175) | 解析、驗證、透過 UDP 9 喚醒、透過 UDP 9877 控制電源 |
| `CommandAuth` | [`CommandAuth.kt:36-280`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/CommandAuth.kt#L36-L280) | HMAC 簽章/驗證、信任 CIDR、wire 錯誤、共享 replay guard |
| `WolListenerService` | [`WolListenerService.kt:81-300`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt#L81-L300) | TCP 9876 有界單行、驗證、分派、結果行 |
| `MqttWolService` | [`MqttWolService.kt:232-458`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttWolService.kt#L232-L458) | Clean session 連線、QoS 1、忽略 retained、分派 |
| `MqttReconnectPolicy` | [`MqttSupport.kt:134-150`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttSupport.kt#L134-L150) | 指數退避 1、2、4…秒，上限 120 秒 |

來源：[wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt:10-175](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt#L10-L175)、[wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/CommandAuth.kt:36-280](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/CommandAuth.kt#L36-L280)、[wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt:81-300](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt#L81-L300)、[wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttWolService.kt:232-458](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttWolService.kt#L232-L458)

## 2.4 Home Assistant 與 Google Home 整合（custom_components/pc_onoff/）

<div class="archify-embed" data-src="diagrams/ha-optimistic-sequence.html" data-title-en="Google Home Wake + 25s Optimistic Hold & Reconciliation Sequence" data-title-zh="Google Home 喚醒與 25 秒樂觀狀態保持時序圖 (Archify 互動圖)">
  <p><a href="../diagrams/ha-optimistic-sequence.html">開啟互動式 Archify 時序圖：Google Home 喚醒與 25 秒樂觀狀態保持 ↗</a></p>
</div>

Home Assistant 嚴格分成三層。Layer 1 完全沒有 `homeassistant` 匯入；Layer 2 負責執行期與排程；Layer 3 是 entity adapter 層，從不碰觸 socket。

| 類別 / 函式 | 檔案 / 行號 | 契約 |
| :--- | :--- | :--- |
| `PcTargetConfig` | [`coordinator.py:143-207`](../../../custom_components/pc_onoff/coordinator.py#L143-L207) | 冷凍的 11 欄位目標設定；`from_mapping` 安全預設值與 MAC 正規化 |
| `PcOnOffCoordinator` | [`coordinator.py:210-361`](../../../custom_components/pc_onoff/coordinator.py#L210-L361) | `DataUpdateCoordinator`（`bool or None`）；輪詢、喚醒、電源、+26 秒對帳 |
| `OptimisticStateTracker` | [`state.py:38-130`](../../../custom_components/pc_onoff/state.py#L38-L130) | `record_intent` / `reconcile`；25 秒保留；`None` 保留狀態 |
| `async_setup_entry` | [`__init__.py:36-70`](../../../custom_components/pc_onoff/__init__.py#L36-L70) | 合併 data+options、首次重新整理、`runtime_data`、平台轉送 |
| `async_unload_entry` | [`__init__.py:72-82`](../../../custom_components/pc_onoff/__init__.py#L72-L82) | 取消排程重新整理、卸載平台 |
| `PcPowerSwitch` | [`switch.py:43-81`](../../../custom_components/pc_onoff/switch.py#L43-L81) | `_attr_name = None`；ON = 喚醒；OFF = `off_action` |
| `PcActionButton` | [`button.py:105-128`](../../../custom_components/pc_onoff/button.py#L105-L128) | 六個按鈕；`action=None` 代表喚醒 |
| `PcConnectivitySensor` | [`binary_sensor.py:41-65`](../../../custom_components/pc_onoff/binary_sensor.py#L41-L65) | 反映 `coordinator.data` |

<div class="decision-context">
<div class="decision-title">為什麼這樣設計 / 設計決策背景</div>
<p><strong>Google Home 不能把正在開機的 PC 看成 OFF。</strong> BIOS/UEFI 開機通常需要 15–35 秒才會回應 ping 或 TCP。若沒有樂觀保留，+5 秒的背景輪詢會看到 `False`，把開關翻回 OFF，讓成功的語音指令看起來像失敗。`OptimisticStateTracker` 會保留請求狀態 25 秒，探測到目標狀態就提早清除，並只安排一次 +26 秒的對帳。</p>
<p><strong>Layer 1 的存在是為了讓邏輯不必安裝 Home Assistant 也能測試。</strong> protocol 編碼、可達性、validation 與狀態轉移都是純 Python。Layer 2 負責 `DataUpdateCoordinator`、`hass`、`async_call_later` 與 unload。Layer 3 只負責 entity metadata 與方法轉送。依賴箭頭只能向下：Layer 3 → Layer 2 → Layer 1，絕不反向。</p>
</div>

來源：[custom_components/pc_onoff/coordinator.py:143-361](../../../custom_components/pc_onoff/coordinator.py#L143-L361)、[custom_components/pc_onoff/state.py:38-130](../../../custom_components/pc_onoff/state.py#L38-L130)、[custom_components/pc_onoff/switch.py:43-81](../../../custom_components/pc_onoff/switch.py#L43-L81)、[custom_components/pc_onoff/button.py:105-128](../../../custom_components/pc_onoff/button.py#L105-L128)、[custom_components/pc_onoff/__init__.py:36-82](../../../custom_components/pc_onoff/__init__.py#L36-L82)

## 2.5 Remote Phone 控制器（wakeonwanremotephone/）

Remote Phone 沒有 protocol parser，也沒有 MQTT 用戶端。它在 `MainActivity.sendCommand` 選擇傳輸方式，然後把所有 wire 細節交給 `RemoteNetworkUtil`：TCP 9876 中繼、直接 UDP 9877，或本機 UDP 9 magic packet。

| Kotlin 符號 | 檔案 / 行號 | 職責 |
| :--- | :--- | :--- |
| `MainActivity.sendCommand` | [`MainActivity.kt:212-245`](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/MainActivity.kt#L212-L245) | 選擇 `localLanMode` 或 TCP 中繼；組出 `WAKE:<MAC>` / `ACTION:<IP>` |
| `sendTcpCommand` | [`RemoteNetworkUtil.kt:52-106`](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L52-L106) | 5 秒連線、寫入單行、讀取 1024-byte 有界回應 |
| `sendLocalMagicPacket` | [`RemoteNetworkUtil.kt:128-154`](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L128-L154) | 102-byte 廣播到 `255.255.255.255:9` |
| `sendDirectCommandToPC` | [`RemoteNetworkUtil.kt:180-207`](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L180-L207) | 直接 UDP 9877，裸指令或簽章 envelope |
| `isErrorResult` | [`RemoteNetworkUtil.kt:212-216`](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L212-L216) | UI 錯誤偵測的前綴契約 |

來源：[wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/MainActivity.kt:212-245](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/MainActivity.kt#L212-L245)、[wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt:52-216](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L52-L216)
