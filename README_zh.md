# Wake-on-LAN 家用手機遠端喚醒控制系統
[English README](README.md)

一套利用備用 Android 手機作為「家用助手」，透過行動網路、網際網路與區域網路，對電腦進行 Wake-on-LAN (WoL) 遠端喚醒、關機、重啟、睡眠與休眠控制的系統。

## 功能特色
- **Wake-on-LAN 遠端喚醒**：支援透過區域網路 UDP 廣播，或透過外網 TCP / MQTT 遠端喚醒電腦。
- **遠端關機 / 重啟 / 睡眠 / 休眠**：透過區域網路或遠端助手指令控制電腦電源狀態。
- **可靠的雙手機直連架構 (TCP)**：外出手機與家中助手端透過 IPv6/IPv4 以 TCP (Port 9876) 建立連線，具備即時雙向確認 (ACK) 與狀態回傳反饋。
- **內網直連模式 (Local LAN Mode)**：當外出手機連上家中 Wi-Fi 時，可切換為內網模式直接透過 UDP (Port 9) 發送 Magic Packet，無須經過助手手機中轉。
- **MQTT 雲端模式**：可連線至任何 MQTT Broker (如 Adafruit IO、HiveMQ、EMQX 等)，無須設定路由器連接埠轉發即可跨防火牆控制。
- **跨平台電腦端常駐守護程式**：使用 Rust 開發之高效獨立執行檔，支援 Windows 與 Linux，無須安裝任何執行環境。

## 下載

您可以從 [GitHub Releases 發行頁面](https://github.com/pinchiu/wake-on-lan-for-android/releases) 下載預先建置完成的檔案：

- `wakeonlan-home-phone.apk`：安裝於放置在家中且與目標電腦連接同一 Wi-Fi 網路的備用手機（助手端）。
- `wakeonwan-remote-phone.apk`：安裝於隨身攜帶的外出手機（遙控器端）。
- `pc_onoff.exe`：目標電腦端電源控制守護程式（Windows 獨立執行檔，免安裝）。

## 通訊架構與協定說明

1. **外出手機 -> 家中助手手機 (外網直連模式)**：
   - 使用 **TCP (Port 9876)** 透過公網 IPv6 / IPv4 傳輸。
   - 保證封包可靠送達並即時接收助手端回傳的執行結果。
2. **外出手機 -> 家中助手手機 (MQTT 模式)**：
   - 使用 **MQTT (TCP/TLS)** 透過外部 Broker 轉發。
   - 適合無公網 IPv6 或受限於嚴格 NAT 防火牆之環境。
3. **外出手機 -> 目標電腦 (內網直連模式)**：
   - WoL 喚醒：透過 **UDP (Port 9)** 發送 Magic Packet 廣播至 `255.255.255.255`。
   - 電腦控制：透過 **UDP (Port 9877)** 發送控制指令至電腦區域網路 IP。
4. **家中助手手機 -> 目標電腦 (內網中轉喚醒與控制)**：
   - WoL 喚醒：透過 **UDP (Port 9)** 發送 Magic Packet 廣播。
   - 電腦控制：透過 **UDP (Port 9877)** 發送控制指令至執行 `pc_onoff` 之電腦。

## 先決條件
- **Android 手機**：一支作為家中助手（與電腦處於同一區域網路），另一支作為外出遙控器。
- **目標電腦**：主機板 BIOS/UEFI 與網路卡驅動程式已啟用 Wake-on-LAN 功能。
- **網路環境**：家中助手手機與電腦需位於同一 Wi-Fi 子網路。

## 安裝與設定流程

### 1. 電腦端電源控制守護程式 (pc_onoff)
至專案 `computer/` 目錄下載 `pc_onoff.exe`（或 Linux 下編譯 `pc_onoff`）。

#### 啟動方式
- **Windows**：
  直接雙擊 `pc_onoff.exe` 即可！
  - 程式**預設直接於背景靜默常駐**監聽 UDP 9877，完全不彈出黑色命令提示字元視窗。
  - **自動啟用開機自啟**：初次執行時會自動註冊至 Windows 開機啟動項（免管理員權限），開機即自動守護。
- **Linux**：
  在終端機直接執行 `./pc_onoff`，自動註冊 `systemd --user` 與 XDG Autostart。

#### 視覺化設定介面 (Web UI)
隨時可透過以下方式開啟視覺化控制台：
- 終端機執行：`pc_onoff --ui`（自動喚起預設瀏覽器開啟 `http://127.0.0.1:9878`）。
- 或直接在瀏覽器輸入 `http://localhost:9878`。
- 可於介面中自訂 UDP 連接埠、一鍵切換開機自啟、檢視本機 IP、測試控制指令與查看近期執行日誌。

### 2. 安裝 Android 應用程式
從 [GitHub 發行頁面](https://github.com/pinchiu/wake-on-lan-for-android/releases) 下載 APK 並安裝：
- `wakeonlan-home-phone.apk`
- `wakeonwan-remote-phone.apk`

*(若系統提示未知來源應用程式，請於 Android 設定中允許安裝。)*

### 3. 使用方式

#### A. TCP 直連模式（推薦具備 IPv6 之網路）
1. 開啟 **家中助手手機 App** 並點擊啟動服務。畫面上將顯示目前本機在 TCP Port 9876 上的 IPv6 / IPv4 監聽位址。
2. 開啟 **外出手機 App**：
   - 填入家中助手手機的 IPv6 位址。
   - 填入目標電腦的 MAC 位址與區域網路 IPv4 位址。
   - 點擊 **WAKE**、**SHUTDOWN**、**REBOOT**、**SLEEP** 或 **HIBERNATE**。
   - 狀態欄將即時顯示家中助手手機回傳的具體執行結果。

#### B. MQTT 雲端模式
1. 於兩台手機 App 中設定相同的 MQTT Broker 參數（Host、Port、帳號、密碼與 Topic）。
2. 發送指令至設定的 Topic：
   - `WAKE:<MAC>`
   - `SHUTDOWN:<IP>`
   - `REBOOT:<IP>`
   - `SLEEP:<IP>`
   - `HIBERNATE:<IP>`

#### C. 內網直連模式
1. 當外出手機連上家中 Wi-Fi 時，於 App 內開啟 **Local LAN Mode**。
2. 指令將直接透過本機廣播與 UDP 發送至電腦，無需經過助手手機。

## 疑難排解
- **WoL 無法喚醒電腦**：請確認主機板 BIOS/UEFI 已開啟 Wake-on-LAN (或 PCI-E 電源喚醒)，以及 Windows 網卡進階內容中已開啟「魔術封包喚醒 (Wake on Magic Packet)」。
- **遠端關機/睡眠沒有反應**：請確認 Windows Defender 防火牆已允許 UDP 9877 通行。可至 Web UI 控制台進行測試或以管理員身分執行 `pc_onoff --firewall` 自動新增規則。
