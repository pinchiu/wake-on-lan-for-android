# Domain Model & Terminology Dictionary (CONTEXT.md)

This document defines the core domain terms and architectural seam terminology for the `wake-on-lan-for-android` project, serving as the single source of truth across the entire codebase.

---

## Domain Terms

* **Target PC (Target Computer)**: The physical host being controlled, identified by its MAC address and local IPv4 address, running the `pc_onoff` daemon in the background listening for power control commands on UDP port 9877.
* **Magic Packet**: Standard 102-byte binary frame following the Wake-on-LAN protocol (6x `0xFF` followed by 16 repetitions of the target MAC address), broadcast via UDP port 9 to awaken the target computer.
* **Power Command**: Plaintext instruction string controlling the host operating system state (`shutdown`, `reboot`, `sleep`, `hibernate`, `lock`), delivered directly to the computer via UDP port 9877.
* **Home Phone (Helper)**: Dedicated Android device stationed at home, continuously connected to the local Wi-Fi, acting as the gateway/bridge from WAN into the local LAN.
* **Remote Phone (Remote Controller)**: Mobile Android device carried by the user, sending control commands via TCP (over IPv6/IPv4), MQTT, or direct local LAN connection.

---

## Architecture Seams & Deep Module Terminology

* **`PcActionDispatcher` (Core Deep Module)**:
  * **Role**: Primary domain convergence module in `wakeonlanhomephone`. Ingests all external command strings, encapsulating syntax splitting, format validation, WoL Magic Packet broadcast (UDP port 9), and remote PC power datagram delivery (UDP port 9877).
  * **Interface**: Exposes a minimal interface: `suspend fun dispatch(rawCommand: String, source: String): PcActionResult`.
  * **Implementation**: Hides MAC address parsing, socket construction, broadcast address fallback, and network exception handling. Completely decoupled from Android UI and frameworks.
* **`PcActionResult` (Result Contract)**:
  * Sealed class comprising `Success(message, details)` and `Failure(errorMessage, cause)`.
  * Exposes `toProtocolString()` (`SUCCESS: ...` or `ERROR: ...`) for serialization over the TCP transport adapter, while supplying structured status for local UI logging and notifications.
* **Transport Adapters**:
  * **TCP Listener Adapter (`WolListenerService`)**: Listens on TCP port 9876, forwards received commands to `dispatcher.dispatch()`, and writes back `toProtocolString()`.
  * **MQTT Subscriber Adapter (`MqttWolService`)**: Subscribes to configured MQTT topic, passing message payloads to `dispatcher.dispatch()`.
  * **Voice / Shortcut Adapter (`VoiceWakeActivity`)**: Handles Google Assistant routines and Android App Shortcuts, dispatching `"WAKE"` to `PcActionDispatcher`.
  * **Widget Adapter (`WakePcWidgetProvider`)**: App Widget for 1-tap local Wake-on-LAN dispatch.
