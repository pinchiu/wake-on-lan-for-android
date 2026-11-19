# Home Assistant & Google Home Integration (`pc_onoff`)

English · [繁體中文](#繁體中文)

Turn a computer into a Google Home device: say **“Hey Google, turn on Gaming PC”** and Home
Assistant sends a Wake-on-LAN magic packet; say **“Hey Google, turn off Gaming PC”** and it sends
the configured power action (`shutdown`, `sleep` or `hibernate`) to the `pc_onoff` daemon on that
PC. The same device also exposes six action buttons (wake, shutdown, reboot, sleep, hibernate,
lock screen) and a connectivity sensor for dashboards and automations.

The integration is a **custom component** (`custom_components/pc_onoff/`) with **zero external
PyPI dependencies** and **no YAML**: everything is configured through the Home Assistant UI.

```text
Google Home ──▶ Home Assistant (pc_onoff) ──(LAN UDP 9 / UDP 9877)──▶ Target PC
```

| Hop | Transport | Purpose |
| :--- | :--- | :--- |
| Google Home → Home Assistant | Google Assistant / Nabu Casa cloud | Voice intent (`turn on` / `turn off`) |
| Home Assistant → Target PC | **UDP 9** broadcast | Wake-on-LAN magic packet (102 bytes) |
| Home Assistant → Target PC | **UDP 9877** unicast | `shutdown` / `reboot` / `sleep` / `hibernate` / `lock` |
| Home Assistant → Target PC | ICMP echo or TCP connect | On/off status probe (configurable) |

The Python integration speaks exactly the same wire protocol as the Android apps and the Rust
daemon: bare commands on trusted LAN/Tailscale addresses, and the HMAC-SHA256 envelope on public
IPv4/IPv6. See [`docs/wiki/Wire-Protocols-and-Security.md`](wiki/Wire-Protocols-and-Security.md)
for the byte-level specification.

---

## 1. Requirements

| Requirement | Details |
| :--- | :--- |
| Home Assistant | **2024.1** or newer (config-entry `runtime_data`, `OptionsFlow` with a config entry) |
| Python | 3.11+ (bundled with Home Assistant; nothing to `pip install`) |
| Target PC | `pc_onoff` daemon running and listening on **UDP 9877** for power actions |
| Network | Home Assistant must reach the PC’s LAN broadcast domain for Wake-on-LAN (**UDP 9**) |
| Optional | [HACS](https://hacs.xyz/) for one-click installation and updates |

Wake-on-LAN itself is a **layer-2 broadcast**: the NIC must be armed in BIOS/UEFI and in the OS
driver, and Home Assistant must be able to emit the broadcast on the PC’s LAN (see
[§6 Networking & Docker](#6-networking--docker)).

---

## 2. Installation

### Option A — HACS custom repository (recommended)

1. Install [HACS](https://hacs.xyz/) if it is not installed yet.
2. Open **HACS → ⋮ (top-right menu) → Custom repositories**.
3. Repository: `https://github.com/pinchiu/wake-on-lan-for-android`
   Category: **Integration** → **Add**.
4. Search HACS for **Wake-on-LAN & PC Power (pc_onoff)** and choose **Download**.
5. **Restart Home Assistant** (Settings → System → Restart).

HACS reads [`hacs.json`](../hacs.json) at the repository root and installs
`custom_components/pc_onoff/` into your Home Assistant configuration directory.

### Option B — manual copy

Copy the integration folder into your Home Assistant configuration directory and restart:

```bash
# Adjust the destination to your HA config path (the folder that contains configuration.yaml)
cp -r custom_components/pc_onoff /config/custom_components/pc_onoff
```

```text
/config/
└── custom_components/
    └── pc_onoff/
        ├── __init__.py
        ├── binary_sensor.py
        ├── button.py
        ├── config_flow.py
        ├── const.py
        ├── coordinator.py
        ├── manifest.json
        ├── probe.py
        ├── protocol.py
        ├── state.py
        ├── strings.json
        ├── switch.py
        ├── validation.py
        └── translations/
            ├── en.json
            └── zh-Hant.json
```

Then restart Home Assistant. No `configuration.yaml` edit is required.

---

## 3. Configuration

1. Go to **Settings → Devices & Services**.
2. Choose **Add Integration** and search for **Wake-on-LAN & PC Power (pc_onoff)**.
3. Fill in the form (one entry per PC) and choose **Submit**.

| Field | Key | Default | Notes |
| :--- | :--- | :--- | :--- |
| **Name** | `name` | `PC` | Friendly name and Google Home voice name, e.g. `Gaming PC`. **Immutable after setup.** |
| **MAC address** | `mac` | — | Wired NIC that receives the magic packet. `AA:BB:CC:DD:EE:FF` or `AA-BB-CC-DD-EE-FF`. Also the unique ID of the entry. **Immutable after setup.** |
| **IP address** | `host` | — | LAN, Tailscale or public unicast IPv4/IPv6 address of the PC. |
| **Control port (UDP)** | `udp_port` | `9877` | Port the `pc_onoff` daemon listens on. |
| **Wake-on-LAN port (UDP)** | `wol_port` | `9` | Magic-packet port. |
| **Broadcast address** | `broadcast_ip` | `255.255.255.255` | IPv4 broadcast. Use a directed broadcast such as `192.168.1.255` when HA is on another subnet/VLAN. |
| **PC secret** | `pc_secret` | empty | **Optional on LAN/Tailscale; required for a public IP.** Copy from `pc_onoff --status` → `Command Secret: ...`. |
| **Turn-off action** | `off_action` | `shutdown` | What “turn off” means: `shutdown`, `sleep` or `hibernate`. |
| **Status probe** | `probe_mode` | `ping` | `ping` (ICMP), `tcp` (one port) or `none` (optimistic only). |
| **Probe TCP port** | `probe_port` | `445` | Checked only in `tcp` mode (445 = SMB, 22 = SSH, 3389 = RDP, …). |
| **Scan interval (seconds)** | `scan_interval` | `30` | 5–3600 s. Ignored when the probe mode is `none`. |

After setup, **Configure** on the integration card reopens the options flow. It exposes every
field except **Name** and **MAC address** — those stay fixed so entity IDs, unique IDs, the device
registry entry and therefore Google Home voice commands never break.

### 3.1 Turn-off action

| Value | Sent to the PC | Typical use |
| :--- | :--- | :--- |
| `shutdown` | `shutdown` | Default; fully powers the PC off (still wakeable via WoL if the NIC is armed). |
| `sleep` | `sleep` | Suspend-to-RAM; fast resume. |
| `hibernate` | `hibernate` | Suspend-to-disk; survives power loss. |

### 3.2 Status probe

| Mode | Behaviour | When to use |
| :--- | :--- | :--- |
| `ping` | ICMP echo to the configured IP | Most reliable when the PC answers ping. |
| `tcp` | TCP connect to `probe_port` | Ping blocked by the OS firewall. A **refused** connection still counts as “on” (the host answered). |
| `none` | No polling; the switch keeps the last requested state | PCs that never answer probes, or when you want zero network noise. The switch is then marked as an *assumed state*. |

### 3.3 Entities

Home Assistant slugifies the device name, so a PC named `Gaming PC` produces:

| Entity | Example ID | Meaning |
| :--- | :--- | :--- |
| Switch | `switch.gaming_pc` | **On** = wake (magic packet); **off** = the configured turn-off action. This is the Google Home device. |
| Button | `button.gaming_pc_wake` | Send a magic packet. |
| Button | `button.gaming_pc_shutdown` | Send `shutdown`. |
| Button | `button.gaming_pc_reboot` | Send `reboot` (does not change the switch state). |
| Button | `button.gaming_pc_sleep` | Send `sleep`. |
| Button | `button.gaming_pc_hibernate` | Send `hibernate`. |
| Button | `button.gaming_pc_lock_screen` | Send `lock` (keeps the PC on). |
| Binary sensor | `binary_sensor.gaming_pc_connectivity` | `on` when the probe succeeds, `off` when it fails, unknown while probing is disabled. |

Entity IDs follow Home Assistant’s translated-name slug rules; confirm the exact IDs in
**Developer Tools → States** (or **Settings → Devices & Services → Entities**).

### 3.4 The 25-second optimistic hold

A PC takes 15–35 s to answer after a Wake-on-LAN packet (POST, BIOS, boot loader). Without
protection, the next background poll would see “not reachable” and flip the Google Home switch
back to **off** a few seconds after you asked it to turn on.

The integration therefore holds the requested state for `OPTIMISTIC_HOLD_SECONDS = 25.0`:

1. `turn on` sends the magic packet and immediately reports **on**.
2. Polls during the hold window keep reporting the requested state.
3. The state converges early if the PC answers before the window ends.
4. One reconciliation poll runs at **+26 s**; if the PC is still unreachable, the switch reports
   the real (off) state.
5. Turning off works the same way in reverse for `shutdown`, `sleep` and `hibernate`.

`lock` and `reboot` never flip the switch, because the PC stays powered.

---

## 4. Google Home exposure

Home Assistant is the bridge: Google Home talks to Home Assistant, and Home Assistant talks to
the PC. Two connection options exist.

### Option A — Home Assistant Cloud (Nabu Casa)

1. Enable **Home Assistant Cloud** (Settings → Home Assistant Cloud).
2. Open **Settings → Voice assistants → Expose**.
3. Expose **only** `switch.gaming_pc` (the primary switch). Leave the six buttons and the
   connectivity sensor unexposed so Google Home shows one clean device.
4. In the Google Home app, link the **Home Assistant** account (if not linked yet), then say
   **“Hey Google, sync my devices”**.

### Option B — manual Google Assistant integration

Requires an HTTPS endpoint with a valid certificate (Nabu Casa, or your own domain/reverse
proxy) and a Google Cloud project with the Google Assistant API. Follow the official guide
[Google Assistant SDK → Home Assistant](https://www.home-assistant.io/integrations/google_assistant/),
then expose the switch exactly as in Option A.

### Voice commands

| You say | What happens |
| :--- | :--- |
| “Hey Google, turn on Gaming PC” | `switch.gaming_pc` → `async_wake()` → magic packet on UDP 9 |
| “Hey Google, turn off Gaming PC” | `switch.gaming_pc` → `async_turn_off()` → `shutdown` / `sleep` / `hibernate` on UDP 9877 |
| “Hey Google, is Gaming PC on?” | Answers from the last probe result |

The device name comes from the **Name** field. Keep it short, unique and easy to pronounce; the
switch deliberately has no “Power” suffix, so Google Home hears the bare device name.

### Routines for reboot / sleep / lock

Google Home only maps *on/off* to the switch. For the other actions, create a Home Assistant
script or scene that presses the button, expose the script to Google Assistant, and bind it to a
Google Home Routine:

```yaml
# scripts.yaml
pc_reboot:
  alias: "Reboot Gaming PC"
  sequence:
    - service: button.press
      target:
        entity_id: button.gaming_pc_reboot

pc_sleep:
  alias: "Sleep Gaming PC"
  sequence:
    - service: button.press
      target:
        entity_id: button.gaming_pc_sleep

pc_lock:
  alias: "Lock Gaming PC"
  sequence:
    - service: button.press
      target:
        entity_id: button.gaming_pc_lock_screen
```

Expose `script.pc_reboot`, `script.pc_sleep` and `script.pc_lock` in **Settings → Voice assistants
→ Expose**, then add the phrases in the Google Home app (**Routines → + → “When I say…” → “Adjust
scenes”** or “Try adding…”).

---

## 5. Security model

The integration follows the same LAN/Tailscale-first rules as the Android apps and the Rust
daemon:

| Target address | Payload on UDP 9877 |
| :--- | :--- |
| Loopback, RFC1918, link-local, IPv6 ULA, Tailscale/CGNAT | Bare keyword, e.g. `shutdown` |
| Public IPv4/IPv6 | `shutdown\|ts=<unix>\|nonce=<16 hex>\|sig=<64 hex>` (HMAC-SHA256) |

* A public IP **cannot be configured without a secret**: the config flow fails closed with
  `secret_required_for_public_ip`, and the protocol layer raises the same error at runtime.
* The secret is stored in the config entry (`.storage/core.config_entries`), never logged, never
  echoed in errors, and never included in diagnostics.
* Timestamps must be within ±5 minutes (keep NTP enabled); nonces are remembered by the daemon
  for 10 minutes to block replays.
* Wake-on-LAN magic packets are unsigned by design — they contain no secret and only wake a
  machine that is already off.

Prefer **Tailscale** over router port-forwarding. If you must expose UDP 9877 publicly, set the
same secret in the integration and on the PC (`pc_onoff --status`).

---

## 6. Networking & Docker

Wake-on-LAN is a **broadcast**, not a routed unicast. These details decide whether the switch
works at all:

* **Docker / Container installs**: use `network_mode: host` (or macvlan) for the Home Assistant
  container. A bridged container sends the magic packet into Docker’s NAT network, where the PC’s
  NIC never sees it.
* **Home Assistant OS / Supervised**: the host network is already used; nothing to change.
* **Different VLAN or subnet**: set **Broadcast address** to that subnet’s directed broadcast,
  e.g. `192.168.1.255`, and make sure the router forwards directed broadcasts (many do not).
* **UDP 9877**: the `pc_onoff` daemon must be running on the PC, and the firewall must allow the
  port:
  * Windows: run `pc_onoff.exe --firewall` as Administrator, or add an inbound UDP 9877 rule.
  * Linux: `sudo ufw allow 9877/udp` when ufw is active.
  * macOS: allow incoming connections for `pc_onoff` when prompted.
* **BIOS/UEFI and NIC**: enable Wake-on-LAN / PCIe power-on, and in Windows Device Manager enable
  *“Allow this device to wake the computer”* and *“Only allow a magic packet to wake the
  computer”*. Disable Windows **Fast Startup** if “shutdown” must leave the PC wakeable.

---

## 7. Troubleshooting

| Symptom | Likely cause | Fix |
| :--- | :--- | :--- |
| Switch flips back to off a few seconds after “turn on” | Poll ran before the PC answered | The 25 s hold should prevent this; if it persists, check that the PC really boots and answers the chosen probe within ~25 s, or raise `scan_interval` |
| “Turn on” does nothing | Magic packet never reached the LAN | Docker without `network_mode: host`, wrong `broadcast_ip`, or WoL disabled in BIOS/NIC |
| “Turn off” does nothing | Daemon not running or UDP 9877 blocked | Run `pc_onoff --status` on the PC; open UDP 9877 |
| Error `secret_required_for_public_ip` when adding | Public IP without a secret | Paste the secret from `pc_onoff --status`, or use the LAN/Tailscale address |
| Error `invalid_host` | Broadcast/multicast/unspecified address | Use the PC’s unicast address |
| Status always unknown | `probe_mode: none` | Expected: the switch is optimistic-only. Choose `ping` or `tcp` for real state |
| Ping probe always off | ICMP blocked | Switch to `tcp` with a port the PC really listens on |
| Google Home does not list the PC | Device not exposed or account not synced | Expose `switch.<pc>` and say “Hey Google, sync my devices” |
| Two PCs answer the same voice name | Duplicate friendly names | Give each entry a unique **Name** |

Diagnostics: check the Home Assistant log (**Settings → System → Logs**, filter `pc_onoff`) and the
entity history in **Developer Tools → States**. On the PC, `pc_onoff --log` shows the daemon side
(which commands arrived and why any were rejected).

---

## 繁體中文

將電腦變成 Google Home 裝置：說 **「Ok Google，打開 Gaming PC」**，Home Assistant 會送出
Wake-on-LAN 魔術封包；說 **「Ok Google，關閉 Gaming PC」**，Home Assistant 會依設定送出
`shutdown`、`sleep` 或 `hibernate` 指令給電腦上的 `pc_onoff` 常駐程式。同一個裝置也會建立六個
動作按鈕（喚醒、關機、重新啟動、睡眠、休眠、鎖定螢幕）與一個連線狀態感測器。

本整合是 **自訂元件**（`custom_components/pc_onoff/`），**不需額外安裝任何 PyPI 套件**，也
**不需要修改 YAML**，全部透過 Home Assistant 介面設定。

```text
Google Home ──▶ Home Assistant (pc_onoff) ──(區域網路 UDP 9 / UDP 9877)──▶ 目標電腦
```

### 需求

| 項目 | 說明 |
| :--- | :--- |
| Home Assistant | **2024.1** 以上 |
| Python | 3.11+（Home Assistant 內建，無須安裝） |
| 目標電腦 | 執行 `pc_onoff` 並監聽 **UDP 9877** |
| 網路 | Home Assistant 必須能對電腦所在網段發出 **UDP 9** 廣播 |
| 選用 | [HACS](https://hacs.xyz/) 以一鍵安裝與更新 |

### 安裝方式

**方式 A：HACS 自訂儲存庫（推薦）**

1. 安裝 [HACS](https://hacs.xyz/)。
2. 開啟 **HACS → ⋮（右上角選單）→ 自訂儲存庫 (Custom repositories)**。
3. 儲存庫網址：`https://github.com/pinchiu/wake-on-lan-for-android`，類別選 **Integration**，按 **Add**。
4. 在 HACS 搜尋 **Wake-on-LAN & PC Power (pc_onoff)**，按 **Download**。
5. 重新啟動 Home Assistant（設定 → 系統 → 重新啟動）。

**方式 B：手動複製**

```bash
# 請將 /config 換成你的 Home Assistant 設定目錄
cp -r custom_components/pc_onoff /config/custom_components/pc_onoff
```

複製後重新啟動 Home Assistant，無須修改 `configuration.yaml`。

### 設定步驟

1. 前往 **設定 → 裝置與服務**。
2. 選擇 **新增整合**，搜尋 **Wake-on-LAN & PC Power (pc_onoff)**。
3. 每台電腦建立一個項目，填寫表單後按 **提交**。

| 欄位 | 預設值 | 說明 |
| :--- | :--- | :--- |
| **名稱 (Name)** | `PC` | Google Home 語音名稱，例如 `Gaming PC`。**設定後不可修改。** |
| **MAC 位址** | — | 接收魔術封包的有線網卡，格式 `AA:BB:CC:DD:EE:FF`。**設定後不可修改。** |
| **IP 位址** | — | 電腦的區域網路、Tailscale 或公開單播 IPv4/IPv6 位址。 |
| **控制連接埠 (UDP)** | `9877` | `pc_onoff` 監聽的連接埠。 |
| **喚醒連接埠 (UDP)** | `9` | Wake-on-LAN 魔術封包連接埠。 |
| **廣播位址** | `255.255.255.255` | IPv4 廣播位址；跨網段時可填 `192.168.1.255` 之類的定向廣播。 |
| **PC 密鑰 (PC secret)** | 空白 | **內網／Tailscale 可留空；公開 IP 必填。** 可從 `pc_onoff --status` 的 `Command Secret` 取得。 |
| **關機動作** | `shutdown` | 「關閉」要執行的動作：`shutdown`、`sleep` 或 `hibernate`。 |
| **狀態偵測** | `ping` | `ping`（ICMP）、`tcp`（單一連接埠）或 `none`（僅樂觀狀態）。 |
| **偵測 TCP 連接埠** | `445` | 僅 `tcp` 模式使用（445 = SMB、22 = SSH、3389 = RDP）。 |
| **掃描間隔（秒）** | `30` | 5–3600 秒；`none` 模式不使用。 |

設定完成後，整合卡片上的 **設定 (Configure)** 可修改上述欄位（**名稱** 與 **MAC** 除外，
以確保實體 ID 與 Google Home 語音指令不會失效）。

### 實體

以名稱 `Gaming PC` 為例：

| 實體 | 範例 ID | 用途 |
| :--- | :--- | :--- |
| 開關 | `switch.gaming_pc` | **開** = 喚醒；**關** = 設定的關機動作。這就是 Google Home 看到的裝置。 |
| 按鈕 | `button.gaming_pc_wake` | 送出喚醒封包 |
| 按鈕 | `button.gaming_pc_shutdown` | 送出關機 |
| 按鈕 | `button.gaming_pc_reboot` | 送出重新啟動（不改變開關狀態） |
| 按鈕 | `button.gaming_pc_sleep` | 送出睡眠 |
| 按鈕 | `button.gaming_pc_hibernate` | 送出休眠 |
| 按鈕 | `button.gaming_pc_lock_screen` | 送出鎖定螢幕（電腦保持開機） |
| 二元感測器 | `binary_sensor.gaming_pc_connectivity` | 偵測成功為 `on`，失敗為 `off`；`none` 模式為未知 |

實體 ID 依 Home Assistant 的中文／英文名稱命名規則產生，實際 ID 請在
**開發者工具 → 狀態** 確認。

### 25 秒樂觀保持

電腦從 Wake-on-LAN 到能回應 ping 通常需要 15–35 秒。若沒有保護，喚醒後幾秒的背景輪詢會看到
「無法連線」，而把 Google Home 的開關又切回關閉。本整合會將要求狀態保持
`OPTIMISTIC_HOLD_SECONDS = 25.0` 秒：

1. 「打開」會立即送出魔術封包並回報 **開啟**。
2. 保持期間的輪詢一律回報要求狀態。
3. 若電腦提前回應，狀態立即收斂。
4. 在 **+26 秒** 執行一次對帳輪詢；若仍無法連線，才回報真實狀態。
5. `shutdown`、`sleep`、`hibernate` 的關閉流程同理反向運作。

`lock` 與 `reboot` 不會改變開關狀態，因為電腦仍保持開機。

### Google Home 語音控制

1. 啟用 **Home Assistant Cloud (Nabu Casa)**，或依
   [官方教學](https://www.home-assistant.io/integrations/google_assistant/) 設定手動 Google Assistant 整合。
2. 前往 **設定 → 語音助理 → 公開 (Expose)**。
3. **只公開** `switch.gaming_pc`（主要開關），不要公開六個按鈕與感測器，讓 Google Home 顯示單一乾淨裝置。
4. 在 Google Home App 連結 Home Assistant 帳號，並說 **「Ok Google，同步我的裝置」**。

| 你說 | 結果 |
| :--- | :--- |
| 「Ok Google，打開 Gaming PC」 | 送出 UDP 9 魔術封包喚醒電腦 |
| 「Ok Google，關閉 Gaming PC」 | 透過 UDP 9877 執行關機／睡眠／休眠 |
| 「Ok Google，Gaming PC 開著嗎？」 | 依最近一次偵測結果回答 |

若要用語音執行重新啟動、睡眠或鎖定，請建立 Home Assistant 指令碼（Script）按下對應按鈕，
再公開該指令碼並綁定 Google Home 日常安排：

```yaml
pc_reboot:
  alias: "重新啟動 Gaming PC"
  sequence:
    - service: button.press
      target:
        entity_id: button.gaming_pc_reboot
```

### 安全性

| 目標位址 | UDP 9877 內容 |
| :--- | :--- |
| 本機、RFC1918 私有網段、連結本機、IPv6 ULA、Tailscale/CGNAT | 純指令，例如 `shutdown` |
| 公開 IPv4/IPv6 | `shutdown\|ts=<unix>\|nonce=<16 hex>\|sig=<64 hex>`（HMAC-SHA256） |

* 公開 IP **必須**設定 PC 密鑰，否則設定流程會以 `secret_required_for_public_ip` 拒絕。
* 密鑰儲存在設定項目中，**不會**寫入日誌、錯誤訊息或診斷檔。
* 時間戳需在 ±5 分鐘內（請保持 NTP 同步）；Nonce 由電腦端保留 10 分鐘以避免重放。
* 喚醒封包本身不含密鑰，僅能喚醒已關機的電腦。

建議優先使用 **Tailscale**，避免將 UDP 9877 直接暴露到公網。

### 網路與 Docker 注意事項

* **Docker／容器安裝**：Home Assistant 容器必須使用 `network_mode: host`（或 macvlan）。
  橋接模式會把廣播送進 Docker 的 NAT 網路，電腦網卡永遠收不到。
* **Home Assistant OS / Supervised**：直接使用主機網路，無須調整。
* **跨 VLAN／子網段**：將 **廣播位址** 設為該網段的定向廣播（例如 `192.168.1.255`），並確認路由器允許轉發。
* **UDP 9877**：確認 `pc_onoff` 正在執行，且防火牆已放行：
  * Windows：以管理員身分執行 `pc_onoff.exe --firewall`。
  * Linux：`sudo ufw allow 9877/udp`。
  * macOS：提示時允許 `pc_onoff` 的傳入連線。
* **BIOS/UEFI 與網卡**：開啟 Wake-on-LAN／PCIe 電源喚醒；Windows 裝置管理員勾選
  「允許這個裝置喚醒電腦」與「只允許 Magic 封包喚醒電腦」；若要從關機狀態喚醒，建議關閉
  Windows 快速啟動 (Fast Startup)。

### 疑難排解

| 症狀 | 可能原因 | 解決方式 |
| :--- | :--- | :--- |
| 開啟後幾秒又變回關閉 | 電腦在保持時間內仍未回應偵測 | 確認電腦能在約 25 秒內開機並回應偵測，或加長 `scan_interval` |
| 無法喚醒 | 魔術封包未到達網段 | Docker 未使用 host 網路、廣播位址錯誤，或 BIOS／網卡未開啟 WoL |
| 無法關機 | 常駐程式未執行或 UDP 9877 被封鎖 | 在電腦上執行 `pc_onoff --status`，並放行 UDP 9877 |
| 新增時出現 `secret_required_for_public_ip` | 使用公開 IP 但未填密鑰 | 填入 `pc_onoff --status` 的密鑰，或改用內網／Tailscale 位址 |
| 狀態一直未知 | `probe_mode` 設為 `none` | 屬正常現象；改用 `ping` 或 `tcp` 取得真實狀態 |
| Google Home 找不到裝置 | 未公開實體或帳號未同步 | 公開 `switch.<pc>` 並說「Ok Google，同步我的裝置」 |

---

**Related documentation**

* [Wire protocols & security](wiki/Wire-Protocols-and-Security.md) — exact packet formats, trusted CIDRs, HMAC envelope.
* [Architecture & deep modules](wiki/Architecture-and-Deep-Modules.md) — how the HA layer fits the Rust daemon and Android apps.
* [Build, test & guardrails](wiki/Build-Test-and-Guardrails.md) — running the integration test suite locally.
