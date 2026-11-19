<div class="wiki-breadcrumb">docs/wiki/zh / 1. 系統總覽與雙網關拓撲</div>

# 1. 系統總覽與雙網關拓撲

<details class="relevant-sources">
<summary>相關原始碼檔案</summary>

- [`computer/src/lib.rs`](../../../computer/src/lib.rs#L1-L35)
- [`computer/src/server.rs`](../../../computer/src/server.rs#L49-L164)
- [`computer/src/auth.rs`](../../../computer/src/auth.rs#L93-L200)
- [`wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt#L22-L175)
- [`wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt#L81-L300)
- [`wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt`](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L15-L216)
- [`custom_components/pc_onoff/const.py`](../../../custom_components/pc_onoff/const.py#L1-L36)
- [`custom_components/pc_onoff/protocol.py`](../../../custom_components/pc_onoff/protocol.py#L69-L440)
- [`custom_components/pc_onoff/coordinator.py`](../../../custom_components/pc_onoff/coordinator.py#L210-L361)

</details>

<div class="archify-cta">✨ <a href="../archify-explainer.html"><strong>互動式 Archify 架構圖解</strong></a> — 五個章節的單頁視覺化導覽。</div>

## 1.1 目的與核心子系統

這個儲存庫是一套完整的遠端電源控制生態系，而不是四個彼此獨立的應用程式。**目標 PC** 執行 Rust 撰寫的 `pc_onoff` 常駐程式（`computer/`），監聽 UDP 9877，並由網卡維持 UDP 9 的 Wake-on-LAN 能力。使用者可透過兩個 LAN 閘道之一存取它：

1. 一台備用的 Android **家用閘道手機**（`wakeonlanhomephone/`），監聽 TCP 9876、訂閱 MQTT、處理語音捷徑與桌面小工具，並轉送到 LAN 的 UDP 9/9877；或
2. **Home Assistant**（`custom_components/pc_onoff/`），對 Google Home 暴露一個開關、六個動作按鈕與一個連線狀態感測器。

**Remote Phone**（`wakeonwanremotephone/`）可透過 TCP 9876 使用任一閘道，也可以在區域網路上繞過閘道，直接送出 UDP 9/9877。

| 子系統 | 目錄 | 執行環境 | 網路角色 | 主要原始碼 |
| :--- | :--- | :--- | :--- | :--- |
| 目標 PC 常駐程式 | `computer/` | Windows / Linux / macOS，Rust 2024，零外部 crate | 監聽 UDP **9877**（IPv4 必要，IPv6 盡力而為）；網卡 WoL **UDP 9**；儀表板 **127.0.0.1:9878** | [`lib.rs`](../../../computer/src/lib.rs#L1-L35)、[`server.rs`](../../../computer/src/server.rs#L54-L118) |
| 家用閘道手機 | `wakeonlanhomephone/` | Android，Kotlin/Compose | 監聽 TCP **9876**；MQTT 用戶端（1883/8883）；送出 UDP **9** + **9877** | [`PcActionDispatcher.kt`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt#L22-L175) |
| Home Assistant 閘道 | `custom_components/pc_onoff/` | Python 3.11+，HA 2024.1+，零 PyPI 依賴 | 對外送出 UDP **9** + **9877**；Google Home 開關與按鈕 | [`protocol.py`](../../../custom_components/pc_onoff/protocol.py#L69-L440) |
| Remote Phone | `wakeonwanremotephone/` | Android，Kotlin/Compose | TCP **9876** 中繼，或區域網路直接 UDP **9** + **9877** | [`RemoteNetworkUtil.kt`](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L15-L216) |

<div class="decision-context">
<div class="decision-title">為什麼這樣設計 / 設計決策背景</div>
<p><strong>目標 PC 不需要 IPv6 或雲端帳號。</strong> 目標常駐程式只需要接收一個已驗證的 UDP 9877 datagram，然後執行作業系統的電源指令。WAN / Google Home 的連線由家用閘道手機或 Home Assistant 這個 LAN 閘道終結，再由閘道發出 LAN 原生的 UDP 9 magic packet 與 UDP 9877 指令。目標端保持簡單，可減少作業系統防火牆、網卡驅動與睡眠狀態造成的失敗模式。</p>
<p><strong>LAN 與 Tailscale 免密鑰；公網一律 fail closed。</strong> 信任來源已證明自己位於區域網路或 Tailscale，強制共享密鑰只會增加摩擦，卻沒有實質邊界。公網封包沒有這種證明，因此必須攜帶 HMAC-SHA256；缺少、錯誤或重放的 envelope 一律拒絕，絕不回退成明文。</p>
</div>

<div class="archify-embed" data-src="diagrams/system-topology.html" data-title-en="Dual-Gateway 4-Subsystem Ecosystem Topology" data-title-zh="雙網關與四大子系統通訊拓撲 (Archify 互動圖)">
  <p><a href="../diagrams/system-topology.html">開啟互動式 Archify 架構圖：雙網關與四大子系統通訊拓撲 ↗</a></p>
</div>

來源：[computer/src/lib.rs:1-35](../../../computer/src/lib.rs#L1-L35)、[computer/src/server.rs:54-118](../../../computer/src/server.rs#L54-L118)、[custom_components/pc_onoff/const.py:1-36](../../../custom_components/pc_onoff/const.py#L1-L36)、[docs/home-assistant.md:1-60](../../home-assistant.md#L1-L60)

## 1.2 端到端指令路由拓撲

整個系統有三種傳輸故事，但只有兩種 wire payload 家族：102-byte magic packet 與 `action` datagram/envelope。

| 路徑 | 入口 | 閘道行為 | 送往 PC 的 wire | 確認機制 |
| :--- | :--- | :--- | :--- | :--- |
| Google Home → HA → PC | `switch.turn_on/off` 或按鈕按下 | HA 讀取 `PcTargetConfig`，送出 magic packet 或 action | 從 HA 主機送出 UDP 9 或 UDP 9877 | 無；HA 更新樂觀狀態 |
| Remote Phone → 家用閘道手機 → PC | TCP 9876 單行 | 閘道驗證公網來源、分派指令、寫回結果行 | 從手機送出 UDP 9 或 UDP 9877 | `SUCCESS:` / `ERROR:` 行 |
| Remote Phone → PC（LAN） | 本機 UI / 小工具 | 無閘道；直接開啟 socket | UDP 9 或 UDP 9877 | 無 |
| MQTT 發布者 → 家用閘道手機 → PC | MQTT topic | Broker ACL/TLS 是邊界；忽略 retained 指令 | UDP 9 或 UDP 9877 | MQTT QoS 1，不是 PC 的確認 |

```text
WoL            UDP 9    102 bytes = 6 x 0xFF + 16 x MAC; no ack
Helper TCP     9876     <command-or-envelope>\n -> SUCCESS: ... | ERROR: ...; request <= 512 B
Daemon UDP     9877     shutdown|reboot|sleep|hibernate|lock; <= 1024 B; no ack
Trusted IPv4   127.0.0.0/8, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 169.254.0.0/16, 100.64.0.0/10
Trusted IPv6   ::1, fe80::/10, fc00::/7; ::ffff:a.b.c.d is unwrapped; fec0::/10 is NOT trusted
Public         command|ts=<unix>|nonce=<16 lowercase hex>|sig=<64 lowercase hex>; +/-300 s
HMAC vector    secret 0123456789abcdef0123456789abcdef, ts 1700000000, nonce 0011223344556677
               SHUTDOWN:192.168.1.10 -> sig 6582b1c23ef97498af3f91ade1a74ce60f335dae38a2aa51f403254505fbfbdc
HA hold        25.0 s optimistic, one reconcile at +26 s; probe_mode "none" never polls
Dashboard      http://127.0.0.1:9878  (loopback only, foreground --web-ui)
```

來源：[wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt:81-300](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt#L81-L300)、[wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt:15-216](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L15-L216)、[custom_components/pc_onoff/coordinator.py:210-361](../../../custom_components/pc_onoff/coordinator.py#L210-L361)、[computer/src/server.rs:303-385](../../../computer/src/server.rs#L303-L385)

## 1.3 百科導覽與代理上下文路由器

| 你的任務是… | 先讀 | 再打開 |
| :--- | :--- | :--- |
| 理解整體系統、連接埠或路由 | **本章**（Home） | [架構與深層模組](Architecture-and-Deep-Modules.md)、[網路協定與安全](Wire-Protocols-and-Security.md) |
| 修改 dispatcher、解析規則或接縫 | [架構與深層模組](Architecture-and-Deep-Modules.md) | 對應的 `.rs` / `.kt` / `.py` 檔案 |
| 修改封包、錯誤行、信任規則或 HMAC 行為 | [網路協定與安全](Wire-Protocols-and-Security.md) | `computer/src/auth.rs`、`protocol.py`、`CommandAuth.kt` |
| 找到唯一要修改的檔案，避免碰撞 | [子系統與檔案職責地圖](Subsystems-File-Map.md) | 子系統表格 |
| 執行測試/lint/build，或監看 CI | [建置、測試與護欄](Build-Test-and-Guardrails.md) | `.github/workflows/ci.yml` |
| 在四個子系統中新增電源動作 | [擴充食譜](Build-Test-and-Guardrails.md#54-end-to-end-extension-recipes) | 所有子系統表格 |
| 新增/除錯 Home Assistant 或 Google Home | [Home Assistant 接縫](Architecture-and-Deep-Modules.md#24-home-assistant--google-home-integration-custom_componentspc_onoff) | `custom_components/pc_onoff/` |
| 修改 Android UI 或 Frost 樣式 | [`docs/frost-ui.md`](../../frost-ui.md) | `ui/theme/`、`ui/components/` |
| 設定語音控制或 HACS | [`docs/home-assistant.md`](../../home-assistant.md) | `switch.py`、`button.py` |
| 除錯常駐程式 CLI、autostart 或系統服務 | [`computer/README.md`](../../../computer/README.md) | `cli.rs`、`autostart.rs`、`system_service.rs` |
| 查詢領域詞彙 | [`CONTEXT.md`](../../../CONTEXT.md) | — |
| 閱讀規範性流程文件 | [`AGENTS.md`](../../../AGENTS.md) | — |

來源：[docs/wiki/Home.md:1-172](../Home.md#L1-L172)、[docs/wiki/Architecture-and-Deep-Modules.md:1-294](../Architecture-and-Deep-Modules.md#L1-L294)、[docs/wiki/Wire-Protocols-and-Security.md:1-336](../Wire-Protocols-and-Security.md#L1-L336)、[docs/wiki/Subsystems-File-Map.md:1-228](../Subsystems-File-Map.md#L1-L228)、[docs/wiki/Build-Test-and-Guardrails.md:1-239](../Build-Test-and-Guardrails.md#L1-L239)
