<div class="wiki-breadcrumb">docs/wiki / 2. Deep-Module Architecture &amp; Seams</div>

# 2. Deep-Module Architecture & Seams

<details class="relevant-sources">
<summary>Relevant source files</summary>

- [`computer/src/dispatch.rs`](../../computer/src/dispatch.rs#L52-L70)
- [`computer/src/power.rs`](../../computer/src/power.rs#L34-L56)
- [`computer/src/server.rs`](../../computer/src/server.rs#L303-L385)
- [`wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt`](../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt#L22-L175)
- [`wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt`](../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt#L254-L300)
- [`wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttWolService.kt`](../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttWolService.kt#L232-L458)
- [`custom_components/pc_onoff/state.py`](../../custom_components/pc_onoff/state.py#L38-L130)
- [`custom_components/pc_onoff/coordinator.py`](../../custom_components/pc_onoff/coordinator.py#L143-L361)
- [`custom_components/pc_onoff/switch.py`](../../custom_components/pc_onoff/switch.py#L43-L81)
- [`wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt`](../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L15-L216)

</details>

## 2.1 Symmetrical Domain Cores

All four subsystems expose a small interface and hide parsing, sockets, OS calls, and framework glue behind it. The point is not abstraction for its own sake: it is the only way to test the wire and state logic without an Android device, a real PC shutdown, or a 1 GB Home Assistant install.

| Subsystem | Deep core | Small interface | Adapters that call it |
| :--- | :--- | :--- | :--- |
| Rust daemon | `Dispatcher<P: PowerController>` | `dispatch(raw) -> Result<PowerAction, DispatchError>` | `server.rs` UDP 9877, `web.rs` loopback action, `cli.rs` lifecycle |
| Home Phone | `PcActionDispatcher` | `suspend fun dispatch(raw, source): PcActionResult` | TCP 9876, MQTT, voice shortcut, widget, Compose UI |
| Home Assistant | Layer 1 + `PcOnOffCoordinator` | `async_wake`, `async_turn_off`, `async_power(action)` | switch, button, binary sensor, config flow |
| Remote Phone | `RemoteNetworkUtil` | `sendTcpCommand` / `sendDirectCommandToPC` / `sendLocalMagicPacket` | app UI and widget |


<div class="decision-context">
<div class="decision-title">Why It Works This Way <span>(Decision Context)</span></div>
<div class="lang-en">
<p><strong>Every transport is replaceable; the domain is not.</strong> TCP 9876, MQTT, Google Home and Android widgets all express the same five power actions. If syntax, auth, MAC parsing or optimistic state lived inside each adapter, a change would need four rewrites and four test suites. The seam makes the wire contract testable once and keeps the adapters declarative.</p>
<p><strong>Home Assistant Layer 1 is deliberately framework-free.</strong> The protocol, probe, state machine and validation rules are plain Python, so CI can run them without installing Home Assistant. Layer 2 owns Home Assistant types and scheduling; Layer 3 owns entities and names. That is why the optimistic state machine is testable with a fake monotonic clock and why entity tests only need small `sys.modules` stubs.</p>
</div>
</div>

Sources: [computer/src/dispatch.rs:52-70](../../computer/src/dispatch.rs#L52-L70), [wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt:22-175](../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt#L22-L175), [custom_components/pc_onoff/state.py:38-130](../../custom_components/pc_onoff/state.py#L38-L130), [wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt:15-216](../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L15-L216)

## 2.2 Target PC Daemon (computer/)

The daemon is the only subsystem that touches OS power APIs. Its dependency direction is strictly inside-out: `action` and `power` never import transport, `dispatch` only knows `PowerController`, and `server` is the only UDP-aware module.

| Rust symbol | File / lines | Contract |
| :--- | :--- | :--- |
| `PowerAction` | [`action.rs:12-93`](../../computer/src/action.rs#L12-L93) | Five canonical actions, aliases, case folding, `:`/`,` suffix stripping |
| `PowerController` | [`power.rs:34-56`](../../computer/src/power.rs#L34-L56) | Single OS seam: `execute(action) -> Result<(), PowerError>` |
| `Dispatcher<P>` | [`dispatch.rs:52-70`](../../computer/src/dispatch.rs#L52-L70) | `dispatch(raw) -> Result<PowerAction, DispatchError>` |
| `AuthError` | [`auth.rs:41-87`](../../computer/src/auth.rs#L41-L87) | `missing`, `bad_signature`, `expired`, `replayed`, `pre_start_replay`, `not_configured`, `malformed` |
| `serve` | [`server.rs:54-118`](../../computer/src/server.rs#L54-L118) | UDP bind, IPv4 required, IPv6 best effort, pass datagrams to auth |
| `authenticate_datagram` | [`server.rs:303-385`](../../computer/src/server.rs#L303-L385) | Trusted source or verified envelope; returns bare command |
| `status_json` | [`web.rs:560-598`](../../computer/src/web.rs#L560-L598) | Dashboard status + last 8 log lines |


The daemon does not write logs from `dispatch.rs`; `server.rs` maps `DispatchError::Rejected` to `warning` and `DispatchError::Execution` to `command_failed`. Auth failures are logged only as stable reasons, never with the envelope or signature.

Sources: [computer/src/action.rs:12-93](../../computer/src/action.rs#L12-L93), [computer/src/power.rs:34-56](../../computer/src/power.rs#L34-L56), [computer/src/dispatch.rs:52-70](../../computer/src/dispatch.rs#L52-L70), [computer/src/server.rs:303-385](../../computer/src/server.rs#L303-L385), [computer/src/auth.rs:41-200](../../computer/src/auth.rs#L41-L200), [computer/src/web.rs:560-598](../../computer/src/web.rs#L560-L598)

## 2.3 Home Phone Gateway (wakeonlanhomephone/)

The Kotlin dispatcher is the Home Phone's domain core. It parses `WAKE` / `ON` / `MAC` and the five power actions, resolves the per-target secret, and sends either a 102-byte magic packet or a UDP 9877 action. TCP, MQTT, voice, widget and Compose UI are adapters.

| Kotlin symbol | File / lines | Responsibility |
| :--- | :--- | :--- |
| `PcActionResult` | [`PcActionDispatcher.kt:10-20`](../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt#L10-L20) | `Success(message)` / `Failure(error)`; `toProtocolString()` |
| `PcActionDispatcher` | [`PcActionDispatcher.kt:22-175`](../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt#L22-L175) | Parse, validate, wake via UDP 9, power via UDP 9877 |
| `CommandAuth` | [`CommandAuth.kt:36-280`](../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/CommandAuth.kt#L36-L280) | HMAC sign/verify, trust CIDRs, wire errors, shared replay guard |
| `WolListenerService` | [`WolListenerService.kt:81-300`](../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt#L81-L300) | TCP 9876 bounded line, auth, dispatch, result line |
| `MqttWolService` | [`MqttWolService.kt:232-458`](../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttWolService.kt#L232-L458) | Clean-session connect, QoS 1, ignore retained, dispatch |
| `MqttReconnectPolicy` | [`MqttSupport.kt:134-150`](../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttSupport.kt#L134-L150) | Exponential backoff 1, 2, 4 ... capped at 120 s |



Sources: [wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt:10-175](../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt#L10-L175), [wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/CommandAuth.kt:36-280](../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/CommandAuth.kt#L36-L280), [wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt:81-300](../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/WolListenerService.kt#L81-L300), [wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttWolService.kt:232-458](../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/MqttWolService.kt#L232-L458)

## 2.4 Home Assistant & Google Home Integration (custom_components/pc_onoff/)

<div class="archify-embed" data-src="diagrams/ha-optimistic-sequence.html" data-title-en="Google Home Wake + 25s Optimistic Hold & Reconciliation Sequence" data-title-zh="Google Home 喚醒與 25 秒樂觀狀態保持時序圖 (Archify 互動圖)">
  <p><a href="diagrams/ha-optimistic-sequence.html">Open Interactive Archify Diagram: Google Home 25s Optimistic Hold Sequence ↗</a></p>
</div>

Home Assistant is split into three strict layers. Layer 1 has zero `homeassistant` imports; Layer 2 owns the runtime and scheduling; Layer 3 is an entity adapter layer that never touches sockets.


| Class / function | File / lines | Contract |
| :--- | :--- | :--- |
| `PcTargetConfig` | [`coordinator.py:143-207`](../../custom_components/pc_onoff/coordinator.py#L143-L207) | Frozen 11-field target config; `from_mapping` safe defaults and MAC normalization |
| `PcOnOffCoordinator` | [`coordinator.py:210-361`](../../custom_components/pc_onoff/coordinator.py#L210-L361) | `DataUpdateCoordinator` of `bool or None`; polling, wake, power, +26 s reconcile |
| `OptimisticStateTracker` | [`state.py:38-130`](../../custom_components/pc_onoff/state.py#L38-L130) | `record_intent` / `reconcile`; 25 s hold; `None` preserves state |
| `async_setup_entry` | [`__init__.py:36-70`](../../custom_components/pc_onoff/__init__.py#L36-L70) | Merge data+options, first refresh, `runtime_data`, platform forwarding |
| `async_unload_entry` | [`__init__.py:72-82`](../../custom_components/pc_onoff/__init__.py#L72-L82) | Cancel scheduled refresh, unload platforms |
| `PcPowerSwitch` | [`switch.py:43-81`](../../custom_components/pc_onoff/switch.py#L43-L81) | `_attr_name = None`; ON = wake; OFF = `off_action` |
| `PcActionButton` | [`button.py:105-128`](../../custom_components/pc_onoff/button.py#L105-L128) | Six buttons; `action=None` means Wake |
| `PcConnectivitySensor` | [`binary_sensor.py:41-65`](../../custom_components/pc_onoff/binary_sensor.py#L41-L65) | Mirrors `coordinator.data` |


<div class="decision-context">
<div class="decision-title">Why It Works This Way <span>(Decision Context)</span></div>
<div class="lang-en">
<p><strong>Google Home must not see a booting PC as OFF.</strong> A BIOS/UEFI boot takes 15–35 seconds before ping or TCP answers. Without an optimistic hold, a routine poll at +5 s sees `False` and flips the switch back, making a successful voice command look broken. `OptimisticStateTracker` holds the requested state for 25 s, clears early when the probe reaches the target, and schedules exactly one +26 s reconciliation.</p>
<p><strong>Layer 1 exists so the logic is testable without Home Assistant.</strong> Protocol encoding, reachability, validation and state transitions are pure Python. Layer 2 owns `DataUpdateCoordinator`, `hass`, `async_call_later` and unload. Layer 3 owns only entity metadata and method forwarding. The dependency arrows point down only: Layer 3 → Layer 2 → Layer 1, never the reverse.</p>
</div>
</div>

Sources: [custom_components/pc_onoff/coordinator.py:143-361](../../custom_components/pc_onoff/coordinator.py#L143-L361), [custom_components/pc_onoff/state.py:38-130](../../custom_components/pc_onoff/state.py#L38-L130), [custom_components/pc_onoff/switch.py:43-81](../../custom_components/pc_onoff/switch.py#L43-L81), [custom_components/pc_onoff/button.py:105-128](../../custom_components/pc_onoff/button.py#L105-L128), [custom_components/pc_onoff/__init__.py:36-82](../../custom_components/pc_onoff/__init__.py#L36-L82)

## 2.5 Remote Phone Controller (wakeonwanremotephone/)

The Remote Phone has no protocol parser and no MQTT client. It chooses a transport in `MainActivity.sendCommand`, then delegates every wire detail to `RemoteNetworkUtil`: TCP 9876 relay, direct UDP 9877, or a local UDP 9 magic packet.

| Kotlin symbol | File / lines | Responsibility |
| :--- | :--- | :--- |
| `MainActivity.sendCommand` | [`MainActivity.kt:212-245`](../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/MainActivity.kt#L212-L245) | Choose `localLanMode` vs TCP relay; build `WAKE:<MAC>` / `ACTION:<IP>` |
| `sendTcpCommand` | [`RemoteNetworkUtil.kt:52-106`](../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L52-L106) | Connect 5 s, write one line, read 1024-byte bounded response |
| `sendLocalMagicPacket` | [`RemoteNetworkUtil.kt:128-154`](../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L128-L154) | 102-byte broadcast to `255.255.255.255:9` |
| `sendDirectCommandToPC` | [`RemoteNetworkUtil.kt:180-207`](../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L180-L207) | Direct UDP 9877, bare or signed |
| `isErrorResult` | [`RemoteNetworkUtil.kt:212-216`](../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L212-L216) | Prefix contract for UI error detection |



Sources: [wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/MainActivity.kt:212-245](../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/MainActivity.kt#L212-L245), [wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt:52-216](../../wakeonwanremotephone/app/src/main/java/com/example/wakeonwanremotephone/RemoteNetworkUtil.kt#L52-L216)
