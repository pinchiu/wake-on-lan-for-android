<div class="wiki-breadcrumb">docs/wiki/zh / 3. 網路協定與安全模型</div>

# 3. 網路協定與安全模型

<details class="relevant-sources">
<summary>相關原始碼檔案</summary>

- [`computer/src/net.rs`](../../../computer/src/net.rs#L9-L37)
- [`computer/src/auth.rs`](../../../computer/src/auth.rs#L93-L359)
- [`computer/src/server.rs`](../../../computer/src/server.rs#L54-L385)
- [`computer/src/action.rs`](../../../computer/src/action.rs#L12-L93)
- [`computer/src/web.rs`](../../../computer/src/web.rs#L405-L520)
- [`wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/CommandAuth.kt`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/CommandAuth.kt#L36-L280)
- [`wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt#L81-L300)
- [`wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttWolService.kt`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttWolService.kt#L232-L458)
- [`custom_components/pc_onoff/protocol.py`](../../../custom_components/pc_onoff/protocol.py#L69-L440)

</details>

## 3.1 Wake-on-LAN 魔術封包（UDP 9）

Wake-on-LAN 刻意不驗證身分：接收者是網卡韌體，沒有 session，也沒有任何確認機制。安全邊界是 LAN 廣播域，而不是封包本身。

| 位移 | 長度 | 欄位 | 規則 |
| :--- | :--- | :--- | :--- |
| 0 | 6 | 同步串流 | `0xFF` x 6 |
| 6 | 96 | MAC 重複 | 目標 6-byte MAC x 16 |
| 102 | — | 結束 | 恰好 102 bytes；沒有 checksum，沒有 trailer |

| 發送者 | Socket 行為 | 預設目的地 |
| :--- | :--- | :--- |
| 家用閘道手機 `WolUtil` | `DatagramSocket.broadcast = true` | `255.255.255.255:9`（或設定的 `defaultBroadcastIp`） |
| Remote Phone `sendLocalMagicPacket` | 廣播 socket | `255.255.255.255:9` |
| Home Assistant `send_magic_packet_sync` | `SO_BROADCAST` | `broadcast_ip:wol_port`（預設 `255.255.255.255:9`） |
| Rust 常駐程式 | 不送 WoL | 作業系統關閉時由網卡處理魔術封包 |

來源：[wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolUtil.kt:8-42](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolUtil.kt#L8-L42)、[wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt:128-154](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L128-L154)、[custom_components/pc_onoff/protocol.py:190-207](../../../custom_components/pc_onoff/protocol.py#L190-L207)、[custom_components/pc_onoff/protocol.py:360-388](../../../custom_components/pc_onoff/protocol.py#L360-L388)

## 3.2 家用閘道中繼協定（TCP 9876 與 MQTT）

TCP 9876 是系統中唯一的同步指令通道：送入一行 UTF-8，送出一行結果。MQTT 則是另一個 inbound adapter，信任邊界不同——是 broker，而不是 HMAC 驗證器。

| TCP 屬性 | 值 | 原始碼 |
| :--- | :--- | :--- |
| 監聽埠 | `9876`（`LISTENING_PORT`） | [`WolListenerService.kt:81`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt#L81-L81) |
| 請求框架 | 單行，CR 或 LF 結束；最大 512 bytes | [`WolListenerService.kt:190-236`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt#L190-L236) |
| 回應 | `SUCCESS: <message>\n` 或 `ERROR: <reason>\n` | [`WolListenerService.kt:238-300`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt#L238-L300) |
| 用戶端讀取上限 | 1024 bytes，移除尾端 CR | [`RemoteNetworkUtil.kt:108-126`](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L108-L126) |
| Socket 逾時 | 5 秒 | [`WolListenerService.kt:83`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt#L83-L83) |
| Worker pool | 4 執行緒，佇列 16 | [`WolListenerService.kt:55-60`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt#L55-L60) |

| 來源類別 | 接受請求 | 密鑰 / 檢查 |
| :--- | :--- | :--- |
| 信任 LAN / link-local / ULA / Tailscale | 裸指令**或**簽章 envelope；只取出 command 欄位 | 無；不檢查簽章 |
| 公網 IPv4 / IPv6 | 僅接受簽章 envelope | `helperSecret`；HMAC + ±300 秒 + replay |

| 失敗情況 | 精確回應行 |
| :--- | :--- |
| 缺少、格式錯誤、過長、空指令 | `ERROR: authentication required` |
| 簽章錯誤、時間戳過期、nonce 重放 | `ERROR: authentication failed` |
| 公網連線時 helper secret 為空 | `ERROR: authentication not configured` |

| MQTT 屬性 | 值 |
| :--- | :--- |
| 預設指令 filter | `home/pc/wake`（`MqttConfigManager.DEFAULT_TOPIC`） |
| QoS / session | QoS 1；clean session `true`；連線後重新訂閱 |
| Retained 指令 | **忽略** — 避免每次重連都重播一次關機 |
| 空 payload | 忽略 |
| Envelope 處理 | 取出 command 欄位；**不**驗證 HMAC |
| 重連 | 指數退避 1、2、4……秒，上限 120 秒 |

來源：[wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt:81-300](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt#L81-L300)、[wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/CommandAuth.kt:90-180](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/CommandAuth.kt#L90-L180)、[wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttWolService.kt:232-458](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttWolService.kt#L232-L458)、[wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttSupport.kt:134-150](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttSupport.kt#L134-L150)

## 3.3 PC 常駐程式控制協定（UDP 9877 IPv4/IPv6）

常駐程式接收單一 UTF-8 datagram，驗證來源、解析動作並執行。沒有回應，也沒有重試：「已送出」只代表發送端的 socket 接受了該 datagram。

| Wire 語法 | 範例 | 備註 |
| :--- | :--- | :--- |
| `<action>` | `shutdown` | 信任來源上的裸指令 |
| `<action>:<ignored>` | `SHUTDOWN:192.168.1.10` | 舊版 Android 格式；忽略後綴 |
| `<action>,<ignored>` | `SLEEP,192.168.1.10` | 逗號變體；忽略後綴 |
| `<envelope>` | `sig=...` 後綴的標準 envelope | 公網來源必須使用 |

| 關鍵字 | 標準動作 | 別名 |
| :--- | :--- | :--- |
| `shutdown` | 關機 | `poweroff` |
| `reboot` | 重新啟動 | `restart` |
| `sleep` | 暫停至 RAM | `suspend`、`standby` |
| `hibernate` | 休眠至磁碟 | — |
| `lock` | 鎖定工作階段/螢幕 | — |

| Socket 屬性 | 行為 | 原始碼 |
| :--- | :--- | :--- |
| IPv4 綁定 | 必須 `0.0.0.0:<udp_port>`；預設 9877 | [`server.rs:54-118`](../../../computer/src/server.rs#L54-L118) |
| IPv6 綁定 | 盡力而為 `[::]:<udp_port>`；Linux 使用 `IPV6_V6ONLY=1` | [`server.rs:166-258`](../../../computer/src/server.rs#L166-L258) |
| 最大 datagram | 1024 bytes（`MAX_COMMAND_BYTES`） | [`lib.rs:26-31`](../../../computer/src/lib.rs#L26-L31) |
| 過長 datagram | 永遠不是有效 envelope；Windows `WSAEMSGSIZE` 記錄後跳過 | [`server.rs:119-165`](../../../computer/src/server.rs#L119-L165) |
| 確認機制 | 無 | [`server.rs:360-385`](../../../computer/src/server.rs#L360-L385) |

來源：[computer/src/action.rs:12-93](../../../computer/src/action.rs#L12-L93)、[computer/src/server.rs:54-385](../../../computer/src/server.rs#L54-L385)、[computer/src/auth.rs:124-200](../../../computer/src/auth.rs#L124-L200)、[computer/src/power.rs:34-56](../../../computer/src/power.rs#L34-L56)、[computer/src/lib.rs:26-31](../../../computer/src/lib.rs#L26-L31)

## 3.4 信任 CIDR 分類

Inbound listener（家用閘道 TCP 9876、常駐程式 UDP 9877）以**來源**位址評估信任；outbound sender（Android 應用程式、Home Assistant）则以**目的地位址**評估。同一張表在三種語言各實作一次；修改它就是修改協定。

| IPv4 前綴 | 信任？ | 備註 |
| :--- | :--- | :--- |
| `127.0.0.0/8` | 是 | Loopback |
| `10.0.0.0/8` | 是 | RFC1918 |
| `172.16.0.0/12` | 是 | RFC1918（`172.16.0.0`–`172.31.255.255`） |
| `192.168.0.0/16` | 是 | RFC1918 |
| `169.254.0.0/16` | 是 | Link-local |
| `100.64.0.0/10` | 是 | Tailscale / CGNAT |
| 其他所有位址 | **否** | 包含 `8.8.8.8`、`192.0.2.1`、`100.128.0.1` |

| IPv6 前綴 / 位址 | 信任？ | 備註 |
| :--- | :--- | :--- |
| `::1` | 是 | Loopback |
| `fe80::/10` | 是 | Link-local |
| `fc00::/7` | 是 | Unique local，包含 `fd7a:115c:a1e0::/48` |
| `fec0::/10` | **否** | 已棄用的 site-local；不可當成 RFC1918 |
| `::ffff:a.b.c.d` | 拆解 | 交由 IPv4 表分類 |

| 實作 | 必須避免的陷阱 | 原始碼 |
| :--- | :--- | :--- |
| Python HA | 絕不要用 `ip_address(...).is_private`；它包含 `192.0.2.0/24`，且跨版本行為會變 | [`protocol.py:217-245`](../../../custom_components/pc_onoff/protocol.py#L217-L245) |
| Rust | `is_private` + 明確 `100.64/10` + `(segments[0] & 0xfe00) == 0xfc00` | [`net.rs:9-37`](../../../computer/src/net.rs#L9-L37) |
| Kotlin | 只用 IPv4 的 `isSiteLocalAddress`；避免信任 `fec0::/10` | [`CommandAuth.kt:139-180`](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/CommandAuth.kt#L139-L180) |

<div class="decision-context">
<div class="decision-title">為什麼這樣設計 / 設計決策背景</div>
<p><strong>信任是網路事實，不是傳輸層特性。</strong> TCP 9876、UDP 9877、IPv4 與 IPv6 都共用同一張 CIDR 表。來自 `192.168.1.20` 或 `fd7a:115c:a1e0::1` 的封包已跨越私人邊界；來自 `8.8.8.8` 的封包則沒有。把規則綁在來源/目的 IP，可保持使用體驗簡單，也避免新增傳輸方式時意外繞過邊界。</p>
<p><strong>公網流量刻意採 fail-closed。</strong> 沒有「也許簽章」模式。若目的地位於公網且沒有密鑰，Android / HA 發送端會在本機拒絕；常駐程式也會拒絕沒有有效 envelope 的公網封包。這比明文回退更不方便，但明文公網電源指令本質上就是一個遠端關機原語。</p>
</div>

來源：[computer/src/net.rs:9-37](../../../computer/src/net.rs#L9-L37)、[wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/CommandAuth.kt:139-180](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/CommandAuth.kt#L139-L180)、[custom_components/pc_onoff/protocol.py:217-245](../../../custom_components/pc_onoff/protocol.py#L217-L245)

## 3.5 HMAC-SHA256 v1 Envelope、ReplayGuard 與啟動前截斷

<div class="archify-embed" data-src="diagrams/auth-trust-workflow.html" data-title-en="LAN/Tailscale vs Public IP HMAC-SHA256 & ReplayGuard Auth Workflow" data-title-zh="LAN/Tailscale 免密直通與公網 IP HMAC-SHA256 防重放驗證流程 (Archify 互動圖)">
  <p><a href="../diagrams/auth-trust-workflow.html">開啟互動式 Archify 流程圖：LAN/Tailscale 免密直通與公網 IP HMAC-SHA256 驗證 ↗</a></p>
</div>

```text
<command>|ts=<unix-seconds>|nonce=<16 lowercase hex>|sig=<64 lowercase hex>
```

| 欄位 | 規則 |
| :--- | :--- |
| `command` | 非空 UTF-8；不可有前後空白、管線字元或控制字元 |
| `ts` | Unix 秒數，十進位，非負 |
| `nonce` | 恰好 16 個小寫十六進位字元（64 隨機位元） |
| `sig` | 恰好 64 個小寫十六進位字元（HMAC-SHA256） |
| 整個 envelope | ≤ 512 UTF-8 bytes |
| 密鑰 | 去除前後空白；空值為硬錯誤；HA 拒絕所有控制字元 |

```text
sign_input = "<command>|ts=<ts>|nonce=<nonce>"
sig        = lowercase_hex(HMAC-SHA256(key=UTF8(trim(secret)), message=UTF8(sign_input)))
envelope   = sign_input + "|sig=" + sig
```

| 實作 | 檢查順序 |
| :--- | :--- |
| Rust 常駐程式 | 空密鑰 → 解析/長度/hex → 簽章 → ±300 秒 → 啟動前 → 寫入 replay |
| Kotlin 閘道 | 空密鑰（`NotConfigured`）→ 解析/長度/hex → 簽章 → ±300 秒 → 寫入 replay |
| Python HA | 僅簽章端：密鑰/控制字元 → command → `ts >= 0` → nonce 正則 → HMAC → 長度 |

| Replay 屬性 | 值 |
| :--- | :--- |
| 時間戳視窗 | ±300 秒 |
| Nonce TTL | 600 秒 |
| 容量 | 1024 筆 |
| 到期邊界 | 在 `expires_at == now` 時仍有效（含等於） |
| 淘汰 | 滿載時淘汰最舊/最接近到期者；快取永不成長 |
| 啟動前截斷 | Rust 在寫入 nonce 前拒絕 `ts < not_before` |

| 固定跨語言測試向量 | 值 |
| :--- | :--- |
| Command | `SHUTDOWN:192.168.1.10` |
| Secret | `0123456789abcdef0123456789abcdef` |
| Timestamp | `1700000000` |
| Nonce | `0011223344556677` |
| 預期簽章 | `6582b1c23ef97498af3f91ade1a74ce60f335dae38a2aa51f403254505fbfbdc` |

來源：[computer/src/auth.rs:93-200](../../../computer/src/auth.rs#L93-L200)、[computer/src/auth.rs:251-359](../../../computer/src/auth.rs#L251-L359)、[wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/CommandAuth.kt:56-118](../../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/CommandAuth.kt#L56-L118)、[custom_components/pc_onoff/protocol.py:247-318](../../../custom_components/pc_onoff/protocol.py#L247-L318)

## 3.6 本機儀表板（HTTP 9878）與密鑰階層

儀表板是僅限 loopback 的前景模式，不是另一個網路控制面。它把 datagram 送到 `127.0.0.1:<udp_port>`，藉此重用真正的 UDP/驗證路徑。

| 端點 | 方法 | 行為 |
| :--- | :--- | :--- |
| `/` | GET | 內嵌 Frost HTML（`include_str!("web/app.html")`） |
| `/api/status` | GET | JSON：version、running、pid、udp_port、local_ips、autostart、system service、paths、最後 8 行日誌 |
| `/api/log?lines=N` | GET | JSON 尾端；`N` 限制在 1..200，預設 40 |
| `/api/action` | POST | JSON `{"action":"shutdown"}`；送出 loopback UDP，若已設定則簽章 |

| 密鑰 | 用途 | 設定位置 | 回退 |
| :--- | :--- | :--- | :--- |
| PC secret | 簽署 UDP 9877 指令；與 `pc_onoff` 共享 | 兩個應用程式的每個目標設定檔；HA 的 `pc_secret`；`config.ini` 的 `secret=` | Android 全域設定 / 盾牌 |
| Helper secret | 保護公網 TCP 9876；簽署轉送指令 | 家用閘道設定 → 安全性；Remote Phone 盾牌 | 無；空值會讓公網 TCP 失敗 |
| MQTT credentials | Broker 身分驗證 | 家用閘道 MQTT 設定 | 無 |

| 密鑰格式 / 儲存規則 | 值 |
| :--- | :--- |
| 常駐程式產生的密鑰 | 32 個小寫十六進位字元（128-bit） |
| Android 產生的密鑰 | 32 個小寫十六進位字元 |
| HA 密鑰驗證 | 任何去除空白、非空且不含控制字元的值 |
| Rust 設定拒絕 | CR、LF、NUL |
| Android 備份排除 | 家用閘道：`mqtt_config.xml`、`HomePhoneDevicePrefs.xml`、`widget_security.xml`；Remote：`security_settings.xml`、`RemoteControlPrefs.xml` |

| 症狀 | 意義 | 修正 |
| :--- | :--- | :--- |
| `ERROR: authentication required` | 缺少/格式錯誤/過長的 envelope，或空行 | 送出完整 envelope |
| `ERROR: authentication failed` | 簽章錯誤、時鐘偏移超過 ±300 秒，或重放 | 對齊密鑰；啟用 NTP；使用新的 nonce |
| `ERROR: authentication not configured` | 公網連線時 helper secret 為空 | 設定密鑰，或改用 LAN/Tailscale |
| `PC secret is required for a public IP` | 公網目的地沒有 PC secret | 加入每個設定檔 / HA 的密鑰 |
| 常駐程式記錄 `not_configured` | 公網 datagram 到達，但常駐程式密鑰為空 | `pc_onoff --status`、設定、重新啟動 |
| 常駐程式記錄 `pre_start_replay` | Envelope 早於常駐程式啟動時間 | 送出新的指令 |
| 指令已送出但 PC 沒反應 | UDP 沒有 ack；作業系統呼叫可能失敗 | 讀取 `pc_onoff.log` / `--log` |

來源：[computer/src/web.rs:405-520](../../../computer/src/web.rs#L405-L520)、[computer/src/config.rs:25-120](../../../computer/src/config.rs#L25-L120)、[computer/src/config.rs:186-205](../../../computer/src/config.rs#L186-L205)、[wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/SecuritySettings.kt:18-38](../../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/SecuritySettings.kt#L18-L38)、[wakeonlanhomephone/app/src/main/res/xml/backup_rules.xml:1-12](../../../wakeonlanhomephone/app/src/main/res/xml/backup_rules.xml#L1-L12)、[wakeonwanremotephone/app/src/main/res/xml/backup_rules.xml:1-10](../../../wakeonwanremotephone/app/src/main/res/xml/backup_rules.xml#L1-L10)
