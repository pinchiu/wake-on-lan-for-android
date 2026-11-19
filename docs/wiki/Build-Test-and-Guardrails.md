<div class="wiki-breadcrumb">docs/wiki / 5. Build, Test, CI/CD &amp; Developer Guardrails</div>

# 5. Build, Test, CI/CD & Developer Guardrails

<details class="relevant-sources">
<summary>Relevant source files</summary>

- [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml#L1-L181)
- [`.github/workflows/release.yml`](../../.github/workflows/release.yml#L1-L285)
- [`computer/Cargo.toml`](../../computer/Cargo.toml#L1-L20)
- [`wakeonlanhomephone/app/build.gradle.kts`](../../wakeonlanhomephone/app/build.gradle.kts#L1-L60)
- [`wakeonwanremotephone/app/build.gradle.kts`](../../wakeonwanremotephone/app/build.gradle.kts#L1-L60)
- [`tests/homeassistant/test_protocol.py`](../../tests/homeassistant/test_protocol.py#L1-L593)
- [`tests/homeassistant/test_coordinator.py`](../../tests/homeassistant/test_coordinator.py#L1-L937)
- [`tests/homeassistant/test_entities.py`](../../tests/homeassistant/test_entities.py#L1-L538)
- [`docs/frost-ui.md`](../frost-ui.md#L1-L100)

</details>

## 5.1 Subsystem Verification Commands

| Touched paths | Required command | Expected result |
| :--- | :--- | :--- |
| `computer/**` | `cargo fmt --check` + `cargo clippy -D warnings` + `cargo test` | 100% pass, zero warnings |
| `wakeonlanhomephone/**` | `./gradlew testDebugUnitTest` | 100% pass |
| `wakeonwanremotephone/**` | `./gradlew testDebugUnitTest` | 100% pass |
| `custom_components/pc_onoff/**`, `tests/homeassistant/**` | `py_compile` + `unittest discover` | 100% pass, no HA install |
| `docs/**` only | link/table/diagram checks + `git diff --check` | no broken files |

```bash
# Rust daemon — must be 100% clean
(cd computer && cargo fmt --all -- --check && cargo clippy --all-targets -- -D warnings && cargo test)

# Android Home Phone
(cd wakeonlanhomephone && ./gradlew testDebugUnitTest)

# Android Remote Phone
(cd wakeonwanremotephone && ./gradlew testDebugUnitTest)

# Home Assistant integration (stdlib unittest; run from repo root)
python3 -m py_compile custom_components/pc_onoff/*.py
python3 -m unittest discover -s tests/homeassistant -v

# Python reference daemon syntax
python3 -m py_compile computer/pc_onoff.py
```

Windows PowerShell equivalents:

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

| Per-suite HA command | Covers |
| :--- | :--- |
| `python3 -m unittest discover -s tests/homeassistant -p "test_translations.py" -v` | JSON key parity, manifest fields |
| `... -p "test_protocol.py" -v` | Fixed vector, MAC, magic packet, trust, UDP |
| `... -p "test_probe.py" -v` | ping/tcp/none, refused, timeout, mocks |
| `... -p "test_config_flow.py" -v` | User/options validation, immutability |
| `... -p "test_coordinator.py" -v` | Fake-clock tracker, config, coordinator |
| `... -p "test_entities.py" -v` | Switch, six buttons, sensor |


Sources: [.github/workflows/ci.yml:1-181](../../.github/workflows/ci.yml#L1-L181), [computer/Cargo.toml:1-20](../../computer/Cargo.toml#L1-L20), [wakeonlanhomephone/app/build.gradle.kts:1-60](../../wakeonlanhomephone/app/build.gradle.kts#L1-L60), [tests/homeassistant/test_protocol.py:1-593](../../tests/homeassistant/test_protocol.py#L1-L593)

## 5.2 GitHub Actions CI & Multi-Platform Release Matrix

`detect-changes` uses `dorny/paths-filter` with four filters. Unchanged subsystems are skipped, so a docs-only or HA-only change does not rebuild both APKs.

| CI job | Runs when | What it does |
| :--- | :--- | :--- |
| `build-home-phone` | `wakeonlanhomephone/**` | JDK 21 → `testDebugUnitTest assembleRelease` → upload APK |
| `build-remote-phone` | `wakeonwanremotephone/**` | JDK 21 → `testDebugUnitTest assembleRelease` → upload APK |
| `build-computer` | `computer/**` | `py_compile`, `cargo fmt`, `cargo clippy`, `cargo test`, Linux release build |
| `test-home-assistant` | `custom_components/pc_onoff/**`, `tests/homeassistant/**`, `hacs.json` | Python 3.12 `py_compile` + full unittest discovery |

| Release job | Output |
| :--- | :--- |
| Windows | `pc_onoff-windows-x86_64.exe`, `.zip` |
| Linux | `pc_onoff-linux-x86_64.AppImage`, standalone binary, `.tar.gz` |
| macOS | Universal binary (`aarch64` + `x86_64`), `.zip`, `.tar.gz` |
| Android | `wakeonlan-home-phone.apk`, `wakeonwan-remote-phone.apk` |
| Publish | All assets attached to the GitHub Release at the built commit |



Sources: [.github/workflows/ci.yml:1-181](../../.github/workflows/ci.yml#L1-L181), [.github/workflows/release.yml:1-285](../../.github/workflows/release.yml#L1-L285)

## 5.3 Non-Negotiable Architectural Guardrails

| # | Guardrail | Why it matters | Enforced by |
| :--- | :--- | :--- | :--- |
| 1 | LAN/Tailscale bare commands keep working | HMAC on a trusted LAN adds friction without a boundary | `net.rs`, `CommandAuth.kt`, `protocol.py` |
| 2 | Public paths fail closed | No secret/signature means no command | `prepare_power_payload`, ConfigFlow, `auth.rs` |
| 3 | Envelope format is atomic across Rust/Kotlin/Python | One changed field breaks every client | fixed vector + four test suites |
| 4 | Never log secrets, envelopes or signatures | Logs are copied into issues and screenshots | daemon auth reasons; `AppLogger` discipline |
| 5 | HA `requirements: []` and Layer 1 has no `homeassistant` imports | Keeps HACS install and stdlib tests fast | `manifest.json`, Layer 1 review |
| 6 | Keep 25 s optimistic hold semantics | Prevents Google Home switch bounce-back | `state.py`, `coordinator.py` |
| 7 | Entity identities stay stable | Google Home voice names and automations depend on them | unique IDs + immutable `name`/`mac` |
| 8 | Do not modify Android/daemon for HA work | Keeps HA integration additive | scope review |
| 9 | Never delete `app/release.keystore` | Release APKs cannot be signed | Gradle release config |
| 10 | Keep backup exclusions and widget token guards | Secrets/tokens must not leave the device | `backup_rules.xml`, `data_extraction_rules.xml` |
| 11 | Frost tokens/test tags stay stable | UI consistency and UI tests | `docs/frost-ui.md` |
| 12 | `PC_ONOFF_DATA_DIR`, 5 MB log rotation, single instance | System-service and upgrade correctness | `paths.rs`, `logging.rs`, `instance.rs` |
| 13 | One owner per file set | Parallel agents otherwise overwrite each other | slice map |
| 14 | Never skip a failing test to make CI green | Hides real regressions | review + CI |

<div class="decision-context">
<div class="decision-title">Why It Works This Way <span>(Decision Context)</span></div>
<div class="lang-en">
<p><strong>Verification is layered like the architecture.</strong> Pure Layer 1 logic runs without Home Assistant, entity tests use small stubs, Rust uses a mock `PowerController`, and the fixed HMAC vector catches cross-language drift. This is why the project can test security-sensitive behavior in seconds instead of booting a full HA instance or shutting down a real PC.</p>
<p><strong>The post-push watch is part of the definition of done.</strong> A green local run does not prove CI's JDK, Python, clippy or release packaging. The mandated `gh run watch` loop catches environment-specific failures before they become a broken release.</p>
</div>
</div>

Sources: [computer/src/auth.rs:93-200](../../computer/src/auth.rs#L93-L200), [custom_components/pc_onoff/state.py:38-130](../../custom_components/pc_onoff/state.py#L38-L130), [custom_components/pc_onoff/manifest.json:1-12](../../custom_components/pc_onoff/manifest.json#L1-L12), [wakeonlanhomephone/app/src/main/res/xml/backup_rules.xml:1-12](../../wakeonlanhomephone/app/src/main/res/xml/backup_rules.xml#L1-L12), [docs/frost-ui.md:1-80](../frost-ui.md#L1-L80)

## 5.4 End-to-End Extension Recipes

Adding a power action is a five-step, four-subsystem change. The transport does not change: a power action is still one UDP 9877 datagram; only the parser/action set and sender UI change.

| Step | Rust daemon | Home Phone | Remote Phone | Home Assistant |
| :--- | :--- | :--- | :--- | :--- |
| 1. Contract | Choose keyword, aliases, label | Accept in `KNOWN_POWER_ACTIONS` | Add UI action | Add `PowerAction`; decide off-intent |
| 2. Parser | `action.rs` enum/`ALL`/`keyword`/`parse` | `PcActionDispatcher.kt` | UI strings + `MainActivity` | `protocol.py` |
| 3. Execute | `power.rs` platform candidates | UDP send path is generic | `RemoteNetworkUtil` is generic | `coordinator.async_power` |
| 4. Surface | tests | tile/strings | tile/strings | button description + translations |
| 5. Verify | `cargo test` | `gradlew testDebugUnitTest` | `gradlew testDebugUnitTest` | `py_compile` + unittest |


```bash
# Step 2 — Rust
(cd computer && cargo fmt --all -- --check && cargo clippy --all-targets -- -D warnings && cargo test)

# Step 3 — Android
(cd wakeonlanhomephone && ./gradlew testDebugUnitTest)
(cd wakeonwanremotephone && ./gradlew testDebugUnitTest)

# Step 4 — Home Assistant
python3 -m py_compile custom_components/pc_onoff/*.py
python3 -m unittest discover -s tests/homeassistant -v
```

Do **not** add a new UDP port, a second envelope format, or a second dispatcher to implement an action.

Sources: [computer/src/action.rs:12-93](../../computer/src/action.rs#L12-L93), [computer/src/power.rs:143-268](../../computer/src/power.rs#L143-L268), [wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt:22-175](../../wakeonlanhomephone/app/src/main/java/com/example/wakeonlanhomephone/PcActionDispatcher.kt#L22-L175), [custom_components/pc_onoff/button.py:38-128](../../custom_components/pc_onoff/button.py#L38-L128), [custom_components/pc_onoff/protocol.py:103-112](../../custom_components/pc_onoff/protocol.py#L103-L112)

## 5.5 Troubleshooting & Definition of Done

| Symptom | Likely cause | Action |
| :--- | :--- | :--- |
| `failed to bind UDP 0.0.0.0:9877` | Another daemon/system service owns the port | `pc_onoff --stop`; `sudo systemctl status pc_onoff`; filter `ss -lunp` for 9877 |
| `IPv6 listener unavailable` | Platform/socket limitation | Non-fatal; IPv4 still works |
| `ERROR: authentication failed` | Secret mismatch, clock skew > 5 min, replay | Match secrets; enable NTP; fresh nonce |
| `ERROR: authentication not configured` | Helper secret blank on public TCP | Configure it or use LAN/Tailscale |
| `PC secret is required for a public IP` | Public destination without `pc_secret` | Add per-profile / HA secret |
| HA switch flips OFF during boot | Probe sees `False` before the PC answers | Verify 25 s hold and +26 s reconcile |
| HA switch never updates | `probe_mode: none`, or `ping` missing (`None`) | Use `ping`/`tcp`; ensure `ping`/`CAP_NET_RAW` |
| HA ping fails on non-Linux | `ping -W` / `--` are iputils/Linux flags | Use `probe_mode: tcp` or HAOS |
| WoL never wakes | Broadcast not on L2, Docker bridge, WoL disabled | `network_mode: host`; enable NIC/BIOS WoL |
| Release build fails signing | `app/release.keystore` missing | Restore it; never regenerate casually |
| CI skipped a docs push | `paths-ignore: '**.md'` | Run local checks or `workflow_dispatch` |

- [ ] Only owned files changed (`git diff --stat`)
- [ ] Subsystem suite passes locally
- [ ] Fixed HMAC vector matches Rust, Kotlin (×2), Python
- [ ] No secrets/envelopes/signatures in logs or commits
- [ ] HA Layer 1 still stdlib-only; `requirements` still `[]`
- [ ] Frost tokens/test tags/shortcut IDs unchanged unless versioned
- [ ] Docs updated where behavior is user-facing
- [ ] Pushed, `gh run watch` green, failures fixed and re-pushed

Sources: [computer/src/server.rs:54-118](../../computer/src/server.rs#L54-L118), [computer/src/auth.rs:93-200](../../computer/src/auth.rs#L93-L200), [custom_components/pc_onoff/probe.py:79-178](../../custom_components/pc_onoff/probe.py#L79-L178), [docs/home-assistant.md:260-290](../home-assistant.md#L260-L290)
