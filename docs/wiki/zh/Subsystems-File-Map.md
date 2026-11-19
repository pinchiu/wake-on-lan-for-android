<div class="wiki-breadcrumb">docs/wiki/zh / 4. 子系統與檔案職責地圖</div>

# 4. 子系統與檔案職責地圖

<details class="relevant-sources">
<summary>相關原始碼檔案</summary>

- [`computer/src/lib.rs`](../../../computer/src/lib.rs#L1-L35)
- [`computer/src/server.rs`](../../../computer/src/server.rs#L54-L385)
- [`wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt#L22-L175)
- [`wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt`](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L15-L216)
- [`custom_components/pc_onoff/const.py`](../../../custom_components/pc_onoff/const.py#L1-L36)
- [`custom_components/pc_onoff/coordinator.py`](../../../custom_components/pc_onoff/coordinator.py#L143-L361)
- [`tests/homeassistant/test_entities.py`](../../../tests/homeassistant/test_entities.py#L1-L538)
- [`hacs.json`](../../../hacs.json#L1-L5)
- [`docs/home-assistant.md`](../../home-assistant.md#L1-L477)
- [`.github/workflows/ci.yml`](../../../.github/workflows/ci.yml#L1-L181)

</details>

## 4.1 目標 PC 常駐程式（computer/src/）

<div class="archify-embed" data-src="diagrams/repo-modules-tree.html" data-title-en="4-Subsystem & Deep-Module Repository Tree" data-title-zh="四大子系統與深層模組專案結構樹 (Archify 互動圖)">
  <p><a href="../diagrams/repo-modules-tree.html">開啟互動式 Archify 結構圖：四大子系統與深層模組專案結構樹 ↗</a></p>
</div>

| 檔案 | 行號 | 職責 | 關鍵符號 |
| :--- | :--- | :--- | :--- |
| `lib.rs` | 1-83 | 模組圖與常數 | `APP_NAME`、`DEFAULT_UDP_PORT = 9877`、`MAX_COMMAND_BYTES = 1024` |
| `main.rs` | 1-9 | 程式入口 | Windows 主控台附加；`cli::run` |
| `cli.rs` | 27-120, 285-420 | CLI、生命週期、`--status`、`--log`、`--stop`、`--web-ui`、`--service` | `CliCommand`、`run` |
| `action.rs` | 12-110 | 容錯動作解析器 | `PowerAction`、`ALL`、`keyword`、`label`、`parse` |
| `power.rs` | 34-56, 143-268 | 作業系統電源接縫 | `PowerController`、`SystemPower`、Linux/macOS 候選指令 |
| `dispatch.rs` | 15-70 | 解析 → 執行 → 結構化錯誤 | `Dispatcher<P>`、`DispatchError` |
| `auth.rs` | 93-359 | HMAC envelope、ReplayGuard、啟動前截斷 | `sign`、`verify_since`、`ReplayGuard`、`AuthError` |
| `crypto.rs` | 1-260 | SHA-256、HMAC-SHA256、hex、常數時間比較 | FIPS/RFC 4231 測試向量 |
| `net.rs` | 9-37 | 信任來源分類 | `is_trusted_source` |
| `server.rs` | 54-385 | UDP 9877 傳輸 adapter | `serve`、`receive_loop`、`authenticate_datagram` |
| `config.rs` | 25-205 | `config.ini` 解析/儲存、密鑰產生 | `AppConfig`、`ensure_secret` |
| `paths.rs` | 23-140 | 設定/日誌/鎖定檔路徑解析 | `PC_ONOFF_DATA_DIR`、平台資料目錄 |
| `logging.rs` | 18-160 | 5 MB 輪替日誌 | `Logger`、`DEFAULT_MAX_LOG_BYTES` |
| `instance.rs` | 74-140 | 單一實例 mutex/lockfile | `SingleInstance`、`request_stop` |
| `autostart.rs` | 32-70 | 每位使用者 autostart | HKCU Run、systemd user/XDG、LaunchAgent |
| `system_service.rs` | 11-120 | Linux 系統級服務 | `--install-system`、`/etc/systemd/system/pc_onoff.service` |
| `platform.rs` | 1-160 | 作業系統子程序/主控台細節 | 主控台附加、指令啟動 |
| `web.rs` | 101-180, 405-600 | Loopback 儀表板 | `serve`、`/api/status`、`/api/log`、`/api/action` |
| `web/app.html` | 1-1115 | 內嵌 Frost 儀表板 | 淺色/深色、減少動態、無外部資源 |

來源：[computer/src/lib.rs:1-35](../../../computer/src/lib.rs#L1-L35)、[computer/src/action.rs:12-110](../../../computer/src/action.rs#L12-L110)、[computer/src/server.rs:54-385](../../../computer/src/server.rs#L54-L385)、[computer/src/web.rs:405-600](../../../computer/src/web.rs#L405-L600)、[computer/src/paths.rs:23-140](../../../computer/src/paths.rs#L23-L140)

## 4.2 家用閘道手機（wakeonlanhomephone/）

| 檔案 | 行號 | 職責 |
| :--- | :--- | :--- |
| `PcActionDispatcher.kt` | 22-175 | 深層模組：解析、驗證、UDP 9、UDP 9877、`PcActionResult` |
| `WolUtil.kt` | 8-42 | 102-byte magic packet 發送器 |
| `CommandAuth.kt` | 36-280 | HMAC、信任 CIDR、`ReplayGuard`、wire 錯誤 |
| `WolListenerService.kt` | 81-300 | TCP 9876 listener、有界單行、驗證、結果行 |
| `MqttWolService.kt` | 232-458 | MQTT 連線/訂閱、retained 過濾、分派 |
| `MqttSupport.kt` | 99-150 | Endpoint 解析、topic filter、重連策略 |
| `MqttConfigManager.kt` | 46-80 | Broker 設定、helper secret、共享 PC secret |
| `DeviceProfileManager.kt` | 15-130 | 目標 PC `(name, mac, ip, pcSecret)` |
| `DeviceManager.kt` | 1-102 | MQTT 裝置註冊表 |
| `AppGlobalState.kt` | 1-53 | MQTT 連線 StateFlow |
| `AppLogger.kt` | 1-34 | 記憶體內 UI 日誌環 |
| `AppLocale.kt` | 1-64 | System / en / zh-TW locale |
| `BootReceiver.kt` | 1-36 | 開機後 MQTT 自動連線 |
| `VoiceWakeActivity.kt` | 1-40 | 隱形捷徑入口；分派 `WAKE` |
| `WakePcWidgetProvider.kt` | 1-193 | WoL 小工具；每次安裝的 token |
| `ServerScreen.kt` | 1-1124 | 目標清單、電源磚、伺服器狀態 |
| `ConnectionsScreen.kt` | 1-509 | MQTT broker/裝置 UI |
| `AddDeviceScreen.kt` | 1-395 | 新增/編輯目標表單 |
| `MainActivity.kt` | 293-330 | 直接 UI 分派與導覽 |
| `UpdateManager.kt` | 1-235 | GitHub release 更新器 |
| `GithubRelease.kt` | 1-17 | Release JSON 模型 |
| `ui/components/GlassComponents.kt` | 1-500 | Frost 面板、導覽、喚醒按鈕、sheet |
| `ui/theme/{Color,Theme,ThemeMode,Type}.kt` | 各 1-200 | Frost 調色盤、主題、外觀、字型 |
| `app/src/main/res/xml/shortcuts.xml` | 1-14 | `wake_pc_shortcut` → `VoiceWakeActivity` |
| `app/src/main/res/xml/backup_rules.xml` | 1-12 | 排除含密鑰的 SharedPreferences |
| `app/src/main/res/xml/data_extraction_rules.xml` | 1-18 | Android 12+ 轉移排除 |

| 測試 / 建置檔案 | 行號 | 涵蓋範圍 |
| :--- | :--- | :--- |
| `app/src/test/.../PcActionDispatcherTest.kt` | 1-267 | 指令格式、MAC/IP 檢查、密鑰路徑 |
| `app/src/test/.../CommandAuthTest.kt` | 1-222 | 固定向量、信任 CIDR、replay |
| `app/src/test/.../MqttSupportTest.kt` | 1-135 | Endpoint/topic/重連規則 |
| `app/src/test/.../UpdateManagerTest.kt` | 1-41 | Release 解析 |
| `app/src/androidTest/.../ConfirmPowerDialogTest.kt` | 1-37 | 確認 sheet UI |
| `app/build.gradle.kts` | 1-60 | SDK 36 / min 24、簽章 |

來源：[wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt:22-175](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt#L22-L175)、[wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt:81-300](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt#L81-L300)、[wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttWolService.kt:232-458](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttWolService.kt#L232-L458)、[wakeonlanhomephone/app/src/main/res/xml/shortcuts.xml:1-14](../../../wakeonlanhomephone/app/src/main/res/xml/shortcuts.xml#L1-L14)

## 4.3 Remote Phone 控制器（wakeonwanremotephone/）

| 檔案 | 行號 | 職責 |
| :--- | :--- | :--- |
| `MainActivity.kt` | 212-245 | 單畫面 UI、模式路由、`sendCommand` |
| `RemoteNetworkUtil.kt` | 15-216 | TCP 中繼、直接 UDP 9877、本機 UDP 9、錯誤前綴 |
| `DeviceProfileManager.kt` | 1-130 | 多 PC 設定檔 + 作用中選擇 |
| `SecuritySettings.kt` | 18-38 | Helper secret + 共享 PC secret |
| `SecuritySettingsDialog.kt` | 1-312 | 密鑰輸入盾牌 |
| `RemoteWakeWidgetProvider.kt` | 25-75 | 一鍵喚醒；每次安裝的 token |
| `CommandAuth.kt` | 36-267 | 重複的 HMAC/信任實作 |
| `AppLocale.kt` | 1-50 | Locale 偏好 |
| `UpdateManager.kt` | 1-236 | GitHub release 更新器 |
| `GithubRelease.kt` | 1-17 | Release JSON 模型 |
| `ui/components/RemoteUiComponents.kt` | 1-600 | Frost 卡片、segmented control、電源磚、sheet |
| `ui/theme/{Color,Theme,ThemeMode,Type}.kt` | 各 1-200 | Frost 調色盤/字型 |
| `app/src/main/res/xml/backup_rules.xml` | 1-10 | 排除含密鑰的 SharedPreferences |
| `app/src/main/res/xml/data_extraction_rules.xml` | 1-16 | Android 12+ 轉移排除 |
| `app/release.keystore` | — | **絕不刪除**；release 簽章 |

| 測試檔案 | 涵蓋範圍 |
| :--- | :--- |
| `app/src/test/.../RemoteNetworkUtilTest.kt` | TCP/直接框架、MAC 解析、錯誤前綴 |
| `app/src/test/.../CommandAuthTest.kt` | 固定向量與信任一致性 |
| `app/src/test/.../UpdateManagerTest.kt` | Release 解析 |
| `app/src/androidTest/.../ConfirmPowerDialogTest.kt` | 確認 sheet UI |

來源：[wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt:15-216](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L15-L216)、[wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/MainActivity.kt:212-245](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/MainActivity.kt#L212-L245)、[wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/SecuritySettings.kt:18-38](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/SecuritySettings.kt#L18-L38)

## 4.4 Home Assistant 自訂整合（custom_components/pc_onoff/）

| 檔案 | 層級 | 行號 | 職責 |
| :--- | :--- | :--- | :--- |
| `const.py` | 1 | 1-36 | `DOMAIN`、`PLATFORMS`、11 個 `CONF_*`、預設值、`OPTIMISTIC_HOLD_SECONDS` |
| `protocol.py` | 1 | 69-440 | MAC、magic packet、信任、HMAC、UDP |
| `probe.py` | 1 | 79-178 | `async_probe_host` ping/tcp/none |
| `state.py` | 1 | 38-130 | `OptimisticStateTracker` |
| `validation.py` | 1 | 143-275 | 純設定/選項驗證 |
| `config_flow.py` | 2 | 221-305 | `PcOnOffConfigFlow`、`PcOnOffOptionsFlow` |
| `coordinator.py` | 2 | 143-361 | `PcTargetConfig`、`PcOnOffCoordinator` |
| `__init__.py` | 2 | 36-87 | Entry 設定/卸載、`runtime_data` |
| `switch.py` | 3 | 43-81 | `PcPowerSwitch` |
| `button.py` | 3 | 38-128 | `PcActionButton` × 6 |
| `binary_sensor.py` | 3 | 41-65 | `PcConnectivitySensor` |
| `manifest.json` | metadata | 1-12 | Domain/version/config_flow/requirements `[]` |
| `strings.json` | metadata | 1-123 | 英文來源字串 |
| `translations/en.json` | metadata | 1-123 | 英文鍵值一致 |
| `translations/zh-Hant.json` | metadata | 1-123 | 繁體中文鍵值一致 |

| 測試檔案 | 行號 | 涵蓋範圍 |
| :--- | :--- | :--- |
| `tests/homeassistant/__init__.py` | 1 | 套件標記 |
| `test_translations.py` | 1-294 | 鍵值一致 + manifest 欄位 |
| `test_protocol.py` | 1-593 | 向量、MAC、magic packet、信任、UDP |
| `test_probe.py` | 1-269 | ping/tcp/none、refused、mock |
| `test_config_flow.py` | 1-987 | 使用者/選項驗證、不可變性 |
| `test_coordinator.py` | 1-937 | 假時鐘狀態機 + coordinator stub |
| `test_entities.py` | 1-538 | switch/button/sensor 與 `sys.modules` stub |

來源：[custom_components/pc_onoff/const.py:1-36](../../../custom_components/pc_onoff/const.py#L1-L36)、[custom_components/pc_onoff/protocol.py:69-440](../../../custom_components/pc_onoff/protocol.py#L69-L440)、[custom_components/pc_onoff/coordinator.py:143-361](../../../custom_components/pc_onoff/coordinator.py#L143-L361)、[custom_components/pc_onoff/switch.py:43-81](../../../custom_components/pc_onoff/switch.py#L43-L81)、[tests/homeassistant/test_entities.py:1-538](../../../tests/homeassistant/test_entities.py#L1-L538)

## 4.5 平行代理切片分割指南

HA 工作被切成八個互不重疊的檔案切片。同樣規則適用於任何未來變更：一個檔案只有一個擁有者；contract 檔案除非你的切片擁有它，否則視為唯讀；跨子系統的 wire 變更必須依序處理。

| 切片 | 專屬檔案 | 依賴 | 不可碰觸 |
| :--- | :--- | :--- | :--- |
| HA-1 | `const.py`、`manifest.json`、`strings.json`、`translations/*`、`test_translations.py` | — | 任何 protocol/entity 檔案 |
| HA-2 | `protocol.py`、`test_protocol.py` | `const.py` 契約 | `auth.rs`、Kotlin `CommandAuth.kt`（除非同步修改 wire） |
| HA-3 | `probe.py`、`test_probe.py` | `const.py` | `coordinator.py` |
| HA-4 | `validation.py`、`config_flow.py`、`test_config_flow.py` | `const.py`、`protocol.py` | `coordinator.py`、entities |
| HA-5 | `state.py`、`coordinator.py`、`__init__.py`、`test_coordinator.py` | Layer 1 契約 | entity 檔案 |
| HA-6 | `switch.py`、`button.py`、`binary_sensor.py`、`test_entities.py` | coordinator API | protocol/state |
| HA-7 | `hacs.json`、`docs/home-assistant.md`、`README*`、`AGENTS.md`、CI | 所有層 | `docs/wiki/` |
| HA-8 | `docs/wiki/*` | 所有頁面 | 任何原始碼檔案 |

<div class="decision-context">
<div class="decision-title">為什麼這樣設計 / 設計決策背景</div>
<p><strong>檔案所有權是架構邊界，不是官僚流程。</strong> HA 各層共用精確符號（`PowerAction`、`PcTargetConfig`、`OptimisticStateTracker`），但由不同 worker 實作。若兩個代理同時改同一個檔案，契約會無聲漂移，entity 層就會開始偷渡 protocol 邏輯。切片地圖讓每個行為只有一個檔案負責，跨子系統變更也必須刻意規劃。</p>
<p><strong>唯讀 contract 檔案可避免意外 API 變動。</strong> `const.py`、`protocol.py`、`state.py`、`validation.py`、`coordinator.py` 被多個切片消費。除非你的切片擁有它，否則視為唯讀，才能平行工作而不產生合併衝突或符號改名。</p>
</div>

來源：[custom_components/pc_onoff/const.py:1-36](../../../custom_components/pc_onoff/const.py#L1-L36)、[custom_components/pc_onoff/protocol.py:69-440](../../../custom_components/pc_onoff/protocol.py#L69-L440)、[custom_components/pc_onoff/state.py:38-130](../../../custom_components/pc_onoff/state.py#L38-L130)、[custom_components/pc_onoff/coordinator.py:143-361](../../../custom_components/pc_onoff/coordinator.py#L143-L361)、[.github/workflows/ci.yml:1-181](../../../.github/workflows/ci.yml#L1-L181)
