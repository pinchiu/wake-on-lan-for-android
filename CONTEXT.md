# 專案領域模型與名詞字典 (CONTEXT.md)

本文件定義 `wake-on-lan-for-android` 專案之核心領域名詞與架構接縫術語，作為全專案之單一真實來源 (Single Source of Truth)。

---

## 領域名詞 (Domain Terms)

* **目標電腦 (Target PC)**：受控制之實體主機，具備網卡 MAC 地址與內網 IPv4 位址，在背景執行 `pc_onoff.py` 監聽電源指令。
* **魔術封包 (Magic Packet)**：遵循 Wake-on-LAN 協定之 102 位元組二進位資料（6x 0xFF + 16x MAC），透過 UDP Port 9 廣播發送以喚醒目標電腦。
* **電源指令 (Power Command)**：控制目標電腦作業系統狀態之指令字串（`shutdown`、`reboot`、`sleep`、`hibernate`），透過 UDP Port 9877 直接送達目標電腦。
* **家用助手 (Home Phone)**：放置在家中並長時連線家中 Wi-Fi 之 Android 裝置，作為外網進入區域網路之控制樞紐。
* **外出遙控端 (Remote Phone)**：使用者隨身攜帶之 Android 裝置，透過 TCP、MQTT 或內網直連發送控制指令。

---

## 架構接縫與深模組詞彙 (Codebase Design Terms)

* **`PcActionDispatcher`（電腦指令分派器 - 深模組）**：
  * **角色**：核心深模組。收斂所有來自外部之指令字串，封裝語法切割、格式校驗、WoL 廣播與 UDP 9877 電腦控制封包發送。
  * **介面**：暴露極簡的 `dispatch(rawCommand, sourceAddress): PcActionResult` 介面。
  * **實作**：內部隱藏所有正則校驗、MAC 位元組轉化與 DatagramSocket 網路操作。
* **`PcActionResult`（執行結果 - 契約）**：
  * 密封類別（Sealed Class），包含 `Success` 與 `Failure`。
  * 提供 `toProtocolString()` 供 TCP 傳輸端序列化回傳（例如 `SUCCESS: ...` 或 `ERROR: ...`），同時提供強型別狀態供 UI 與通知更新。
* **傳輸適配器 (Transport Adapters)**：
  * **TCP 監聽適配器 (`WolListenerService`)**：監聽 TCP Port 9876，將傳入字串交由 Dispatcher 處理，並將結果回寫客戶端。
  * **MQTT 訂閱適配器 (`MqttWolService`)**：接收 MQTT Broker Topic 之 Payload，交由 Dispatcher 處理。
  * **語音捷徑適配器 (`VoiceWakeActivity`)**：接收 Android Intent / Google 助理語音觸發，交由 Dispatcher 執行。
