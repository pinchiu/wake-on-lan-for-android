<div class="wiki-breadcrumb">docs/wiki/zh / 5. 建置、測試、CI/CD 與開發護欄</div>

# 5. 建置、測試、CI/CD 與開發護欄

<details class="relevant-sources">
<summary>相關原始碼檔案</summary>

- [`.github/workflows/ci.yml`](../../../.github/workflows/ci.yml#L1-L181)
- [`.github/workflows/release.yml`](../../../.github/workflows/release.yml#L1-L285)
- [`computer/Cargo.toml`](../../../computer/Cargo.toml#L1-L20)
- [`wakeonlanhomephone/app/build.gradle.kts`](../../../wakeonlanhomephone/app/build.gradle.kts#L1-L60)
- [`wakeonwanremotephone/app/build.gradle.kts`](../../../wakeonwanremotephone/app/build.gradle.kts#L1-L60)
- [`tests/homeassistant/test_protocol.py`](../../../tests/homeassistant/test_protocol.py#L1-L593)
- [`tests/homeassistant/test_coordinator.py`](../../../tests/homeassistant/test_coordinator.py#L1-L937)
- [`tests/homeassistant/test_entities.py`](../../../tests/homeassistant/test_entities.py#L1-L538)
- [`docs/frost-ui.md`](../../frost-ui.md#L1-L100)

</details>

## 5.1 子系統驗證指令

| 變更路徑 | 必要指令 | 預期結果 |
| :--- | :--- | :--- |
| `computer/**` | `cargo fmt --check` + `cargo clippy -D warnings` + `cargo test` | 100% 通過，零警告 |
| `wakeonlanhomephone/**` | `./gradlew testDebugUnitTest` | 100% 通過 |
| `wakeonwanremotephone/**` | `./gradlew testDebugUnitTest` | 100% 通過 |
| `custom_components/pc_onoff/**`、`tests/homeassistant/**` | `py_compile` + `unittest discover` | 100% 通過，不需安裝 HA |
| 僅 `docs/**` | 連結/表格/圖表檢查 + `git diff --check` | 沒有損壞檔案 |

```bash
# Rust 常駐程式 — 必須 100% 乾淨
(cd computer && cargo fmt --all -- --check && cargo clippy --all-targets -- -D warnings && cargo test)

# Android 家用閘道
(cd wakeonlanhomephone && ./gradlew testDebugUnitTest)

# Android Remote Phone
(cd wakeonwanremotephone && ./gradlew testDebugUnitTest)

# Home Assistant 整合（stdlib unittest；從儲存庫根目錄執行）
python3 -m py_compile custom_components/pc_onoff/*.py
python3 -m unittest discover -s tests/homeassistant -v

# Python 參考常駐程式語法檢查
python3 -m py_compile computer/pc_onoff.py
```

Windows PowerShell 對應指令：

```powershell
cd computer
cargo fmt --all -- --check
cargo clippy --all-targets -- -D warnings
cargo test

cd ..\wakeonlanhomephone
.\gradlew.bat testDebugUnitTest

cd ..\wakeonwanremotephone
.\gradlew.bat testDebugUnitTest

cd ..
Get-ChildItem custom_components/pc_onoff/*.py | ForEach-Object { python -m py_compile $_.FullName }
python -m unittest discover -s tests/homeassistant -v
```

| 個別 HA 測試指令 | 涵蓋範圍 |
| :--- | :--- |
| `python3 -m unittest discover -s tests/homeassistant -p "test_translations.py" -v` | JSON 鍵值一致、manifest 欄位 |
| `... -p "test_protocol.py" -v` | 固定向量、MAC、magic packet、信任、UDP |
| `... -p "test_probe.py" -v` | ping/tcp/none、refused、timeout、mock |
| `... -p "test_config_flow.py" -v` | 使用者/選項驗證、不可變性 |
| `... -p "test_coordinator.py" -v` | 假時鐘追蹤器、設定、coordinator |
| `... -p "test_entities.py" -v` | 開關、六個按鈕、感測器 |

來源：[.github/workflows/ci.yml:1-181](../../../.github/workflows/ci.yml#L1-L181)、[computer/Cargo.toml:1-20](../../../computer/Cargo.toml#L1-L20)、[wakeonlanhomephone/app/build.gradle.kts:1-60](../../../wakeonlanhomephone/app/build.gradle.kts#L1-L60)、[tests/homeassistant/test_protocol.py:1-593](../../../tests/homeassistant/test_protocol.py#L1-L593)

## 5.2 GitHub Actions CI 與多平台 Release 矩陣

`detect-changes` 使用 `dorny/paths-filter` 搭配四個 filter。未變更的子系統會跳過，因此純文件或純 HA 變更不會重建兩個 APK。

| CI job | 觸發條件 | 執行內容 |
| :--- | :--- | :--- |
| `build-home-phone` | `wakeonlanhomephone/**` | JDK 21 → `testDebugUnitTest assembleRelease` → 上傳 APK |
| `build-remote-phone` | `wakeonwanremotephone/**` | JDK 21 → `testDebugUnitTest assembleRelease` → 上傳 APK |
| `build-computer` | `computer/**` | `py_compile`、`cargo fmt`、`cargo clippy`、`cargo test`、Linux release build |
| `test-home-assistant` | `custom_components/pc_onoff/**`、`tests/homeassistant/**`、`hacs.json` | Python 3.12 `py_compile` + 完整 unittest discovery |

| Release job | 產物 |
| :--- | :--- |
| Windows | `pc_onoff-windows-x86_64.exe`、`.zip` |
| Linux | `pc_onoff-linux-x86_64.AppImage`、獨立 binary、`.tar.gz` |
| macOS | Universal binary（`aarch64` + `x86_64`）、`.zip`、`.tar.gz` |
| Android | `wakeonlan-home-phone.apk`、`wakeonwan-remote-phone.apk` |
| Publish | 所有產物附加到建置 commit 的 GitHub Release |

來源：[.github/workflows/ci.yml:1-181](../../../.github/workflows/ci.yml#L1-L181)、[.github/workflows/release.yml:1-285](../../../.github/workflows/release.yml#L1-L285)

## 5.3 不可妥協的架構護欄

| # | 護欄 | 為何重要 | 強制機制 |
| :--- | :--- | :--- | :--- |
| 1 | LAN/Tailscale 裸指令必須持續可用 | 在信任 LAN 上要求 HMAC 只會增加摩擦，沒有邊界效益 | `net.rs`、`CommandAuth.kt`、`protocol.py` |
| 2 | 公網路徑 fail closed | 沒有密鑰/簽章就沒有指令 | `prepare_power_payload`、ConfigFlow、`auth.rs` |
| 3 | Envelope 格式在 Rust/Kotlin/Python 間原子一致 | 改一個欄位就會弄壞所有用戶端 | 固定向量 + 四套測試 |
| 4 | 絕不記錄密鑰、envelope 或簽章 | 日誌會被貼進 issue 與截圖 | 常駐程式 auth 原因；`AppLogger` 紀律 |
| 5 | HA `requirements: []`，Layer 1 不匯入 `homeassistant` | 保持 HACS 安裝與 stdlib 測試快速 | `manifest.json`、Layer 1 審查 |
| 6 | 保持 25 秒樂觀保留語意 | 防止 Google Home 開關回彈 | `state.py`、`coordinator.py` |
| 7 | Entity 身分保持穩定 | Google Home 語音名稱與自動化依賴它 | 固定 unique ID + 不可變 `name`/`mac` |
| 8 | 不為了 HA 工作修改 Android/常駐程式 | 保持 HA 整合為加法 | 範圍審查 |
| 9 | 絕不刪除 `app/release.keystore` | Release APK 將無法簽章 | Gradle release 設定 |
| 10 | 保持備份排除與小工具 token 護欄 | 密鑰/token 不可離開裝置 | `backup_rules.xml`、`data_extraction_rules.xml` |
| 11 | Frost token/test tag 保持穩定 | UI 一致性與 UI 測試 | `docs/frost-ui.md` |
| 12 | `PC_ONOFF_DATA_DIR`、5 MB 日誌輪替、單一實例 | 系統服務與升級正確性 | `paths.rs`、`logging.rs`、`instance.rs` |
| 13 | 每個檔案集只有一個擁有者 | 平行代理否則會互相覆蓋 | 切片地圖 |
| 14 | 絕不跳過失敗測試來讓 CI 變綠 | 隱藏真正的回歸 | 審查 + CI |

<div class="decision-context">
<div class="decision-title">為什麼這樣設計 / 設計決策背景</div>
<p><strong>驗證方式與架構分層一致。</strong> 純 Layer 1 邏輯不需 Home Assistant 就能跑；entity 測試使用小型 stub；Rust 使用 mock `PowerController`；固定 HMAC 向量捕捉跨語言漂移。因此專案能在幾秒內測試安全敏感行為，不必啟動完整 HA 或真的關閉一台 PC。</p>
<p><strong>Push 後監看 CI 是完成定義的一部分。</strong> 本機全綠不代表 CI 的 JDK、Python、clippy 或 release 打包會成功。強制的 `gh run watch` 迴圈會在環境特定失敗變成損壞的 release 之前抓到問題。</p>
</div>

來源：[computer/src/auth.rs:93-200](../../../computer/src/auth.rs#L93-L200)、[custom_components/pc_onoff/state.py:38-130](../../../custom_components/pc_onoff/state.py#L38-L130)、[custom_components/pc_onoff/manifest.json:1-12](../../../custom_components/pc_onoff/manifest.json#L1-L12)、[wakeonlanhomephone/app/src/main/res/xml/backup_rules.xml:1-12](../../../wakeonlanhomephone/app/src/main/res/xml/backup_rules.xml#L1-L12)、[docs/frost-ui.md:1-80](../../frost-ui.md#L1-L80)

## 5.4 端到端擴充食譜

新增一個電源動作是五步、四子系統的變更。傳輸方式不變：電源動作仍然是一個 UDP 9877 datagram；只有解析器/動作集合與發送端 UI 會改變。

| 步驟 | Rust 常駐程式 | 家用閘道 | Remote Phone | Home Assistant |
| :--- | :--- | :--- | :--- | :--- |
| 1. 契約 | 選擇關鍵字、別名、label | 加入 `KNOWN_POWER_ACTIONS` | 加入 UI 動作 | 加入 `PowerAction`；決定 off-intent |
| 2. 解析 | `action.rs` enum/`ALL`/`keyword`/`parse` | `PcActionDispatcher.kt` | UI 字串 + `MainActivity` | `protocol.py` |
| 3. 執行 | `power.rs` 平台候選指令 | UDP 發送路徑是通用的 | `RemoteNetworkUtil` 是通用的 | `coordinator.async_power` |
| 4. 呈現 | 測試 | tile/字串 | tile/字串 | button description + 翻譯 |
| 5. 驗證 | `cargo test` | `gradlew testDebugUnitTest` | `gradlew testDebugUnitTest` | `py_compile` + unittest |

```bash
# 步驟 2 — Rust
(cd computer && cargo fmt --all -- --check && cargo clippy --all-targets -- -D warnings && cargo test)

# 步驟 3 — Android
(cd wakeonlanhomephone && ./gradlew testDebugUnitTest)
(cd wakeonwanremotephone && ./gradlew testDebugUnitTest)

# 步驟 4 — Home Assistant
python3 -m py_compile custom_components/pc_onoff/*.py
python3 -m unittest discover -s tests/homeassistant -v
```

**不要**為了實作一個動作而新增 UDP 埠、第二種 envelope 格式或第二個 dispatcher。

來源：[computer/src/action.rs:12-93](../../../computer/src/action.rs#L12-L93)、[computer/src/power.rs:143-268](../../../computer/src/power.rs#L143-L268)、[wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt:22-175](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt#L22-L175)、[custom_components/pc_onoff/button.py:38-128](../../../custom_components/pc_onoff/button.py#L38-L128)、[custom_components/pc_onoff/protocol.py:103-112](../../../custom_components/pc_onoff/protocol.py#L103-L112)

## 5.5 疑難排解與完成定義

| 症狀 | 可能原因 | 處理 |
| :--- | :--- | :--- |
| `failed to bind UDP 0.0.0.0:9877` | 另一個常駐程式/系統服務佔用埠 | `pc_onoff --stop`；`sudo systemctl status pc_onoff`；用 `ss -lunp` 過濾 9877 |
| `IPv6 listener unavailable` | 平台/socket 限制 | 非致命；IPv4 仍可用 |
| `ERROR: authentication failed` | 密鑰不符、時鐘偏移超過 5 分鐘、重放 | 對齊密鑰；啟用 NTP；使用新 nonce |
| `ERROR: authentication not configured` | 公網 TCP 的 helper secret 為空 | 設定密鑰或改用 LAN/Tailscale |
| `PC secret is required for a public IP` | 公網目的地沒有 `pc_secret` | 加入每個設定檔 / HA 密鑰 |
| HA 開關在開機時翻回 OFF | 探測在 PC 回應前看到 `False` | 確認 25 秒保留與 +26 秒對帳 |
| HA 開關永不更新 | `probe_mode: none`，或 `ping` 不存在（`None`） | 使用 `ping`/`tcp`；確認 `ping`/`CAP_NET_RAW` |
| 非 Linux 上 HA ping 失敗 | `ping -W` / `--` 是 iputils/Linux 旗標 | 使用 `probe_mode: tcp` 或 HAOS |
| WoL 永不喚醒 | 廣播不在 L2、Docker bridge、WoL 關閉 | `network_mode: host`；啟用 NIC/BIOS WoL |
| Release build 簽章失敗 | `app/release.keystore` 遺失 | 還原它；不要隨便重新產生 |
| CI 跳過文件 push | `paths-ignore: '**.md'` | 執行本機檢查或 `workflow_dispatch` |

- [ ] 只變更擁有的檔案（`git diff --stat`）
- [ ] 子系統測試在本機通過
- [ ] 固定 HMAC 向量在 Rust、Kotlin（×2）、Python 一致
- [ ] 日誌或 commit 中沒有密鑰/envelope/簽章
- [ ] HA Layer 1 仍只依賴 stdlib；`requirements` 仍為 `[]`
- [ ] Frost token/test tag/捷徑 ID 未變，除非刻意版本化
- [ ] 使用者可見行為已更新文件
- [ ] 已 push、`gh run watch` 全綠、失敗已修正並重新 push

來源：[computer/src/server.rs:54-118](../../../computer/src/server.rs#L54-L118)、[computer/src/auth.rs:93-200](../../../computer/src/auth.rs#L93-L200)、[custom_components/pc_onoff/probe.py:79-178](../../../custom_components/pc_onoff/probe.py#L79-L178)、[docs/home-assistant.md:260-290](../../home-assistant.md#L260-L290)
