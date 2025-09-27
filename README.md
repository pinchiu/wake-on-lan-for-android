# Wake-on-LAN Home Phone Remote Control

一個遠端控制系統，使用 Android 手機作為助手，通過行動數據和本地網路，實現對電腦的 Wake-on-LAN (WoL)、關機和重啟。

## 功能特色
- **Wake-on-LAN**：從遠端喚醒電腦（需網路支援）。
- **遠端關機/重啟**：透過行動數據和本地網路控制電腦電源。
- **跨平台電腦**：支援 Windows、Linux、macOS。
- **安全監聽**：使用 UDP 埠，無需公開 IP。

## 先決條件
- **Android 手機**：一支作為助手（在同一 LAN 網路），一支遠端控制（行動數據）。
- **電腦**：安裝 Python 3，且 BIOS/網路卡啟用 WoL（電腦需 Wake-on-LAN 支援）。
- **網路**：助手手機和電腦在同一 Wi-Fi 子網路。關機/重啟支援行動數據路由。

## 安裝與設定

### 1. 安裝電腦端 Python 監聽器
下載 `Python/remote_control_listener.py` 到電腦。
```bash
cd Python
python pc_onoff.py  # 手動測試