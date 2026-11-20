---
name: ha-google-home-pc-setup
description: >
  End-to-end automated setup of the pc_onoff Home Assistant custom integration
  with free Google Home voice control (Switch + 4 Action Scripts: Reboot, Sleep,
  Hibernate, Lock) over Tailscale Funnel or Cloudflare Tunnel, including Home
  Assistant 2026+ .storage/http reverse-proxy patching and live endpoint checks.
  Use when a user wants to install pc_onoff into their Home Assistant instance,
  connect Home Assistant to Google Home for free without Nabu Casa, add PC power
  action scenes/scripts to Google Home, or troubleshoot Google Home / Tailscale
  Funnel 400/401/OAuth issues.
---

# Home Assistant + Google Home PC Control Setup (`pc_onoff`)

Automates installing `custom_components/pc_onoff` into a local Home Assistant instance (Docker or Core) and bridging it to **Google Home** for free (zero Nabu Casa fee, zero Service Account JSON required) with full voice control:
- Primary **Switch** (`switch.<slug>`): ON = Wake-on-LAN (UDP 9) with 25s optimistic hold; OFF = configured `off_action` (`shutdown` / `sleep` / `hibernate`) over UDP 9877.
- **4 Action Scenes / Scripts** (`script.<slug>_{reboot,sleep,hibernate,lock}`): exposes Reboot, Sleep, Hibernate, and Lock Screen to Google Home App & Google Assistant Routines.

## Quick Automation Script

When working inside the `wake-on-lan-for-android` repository, run the bundled helper script first (or inspect its `--dry-run` plan):

```bash
./scripts/setup-ha-google-home.sh \
  --ha-config ~/homeassistant/config \
  --project-id <GOOGLE_HOME_PROJECT_ID> \
  --entity-slug main_pc \
  --pc-name "Main PC"
```

Use `--dry-run` to preview changes without modifying files or restarting Docker.

---

## Step-by-Step Agent Playbook (Follow in Exact Order)

### Phase 1: Discover Existing Home Assistant & Network Topology
1. Locate the Home Assistant config directory (commonly `~/homeassistant/config`, `/config`, or `~/.homeassistant`) and check:
   - `.HA_VERSION` (critical: HA `2026.x+` stores `http` config in `.storage/http`!)
   - `docker-compose.yml` (verify `network_mode: host` so UDP 9 WoL broadcasts reach the physical LAN NIC).
   - Existing `pc_onoff` or `switch.*` entities in `.storage/core.entity_registry` (do NOT print secrets from `secrets.yaml` or `core.config_entries`!).
2. Check existing `tailscale serve status` and `tailscale funnel status` BEFORE modifying Tailscale:
   - **NEVER overwrite port `443` if another service (e.g. CouchDB, Immich, Vaultwarden) is already proxied on `443`!**
   - Tailscale Funnel supports ports `443`, `8443`, and `10000`. Pick the first unoccupied port (`443` → `8443` → `10000`).

### Phase 2: Install `custom_components/pc_onoff/` & Verify Against Real HA Runtime
1. Copy `custom_components/pc_onoff/` into `<ha_config>/custom_components/pc_onoff/` (strip `__pycache__`).
2. Smoke-test imports and ConfigFlow inside the live HA container before restarting:
   ```bash
   docker exec -w /config homeassistant python3 -c "
   import sys; sys.path.insert(0, '/config')
   import custom_components.pc_onoff, custom_components.pc_onoff.config_flow, custom_components.pc_onoff.switch, custom_components.pc_onoff.button
   print('HA IMPORT OK')
   "
   ```
3. Verify reachability of the target PC from inside the HA container (`async_probe_host(host, 'ping', 445)` and `'tcp'` fallback if ICMP is firewalled).

### Phase 3: Enable Free Public HTTPS (Tailscale Funnel or Cloudflare Tunnel)
1. **Option A — Tailscale Funnel (Zero Domain Purchase, Recommended when `tailscale` is installed)**:
   - Run `sudo tailscale funnel --bg --https=<FREE_PORT> http://127.0.0.1:8123`.
   - If Funnel is not yet enabled on the tailnet, Tailscale prints a 1-click URL (`https://login.tailscale.com/f/funnel?node=...`). Present it to the user and wait until they click **Enable Funnel**, then rerun the command.
   - Record `<PUBLIC_URL>` (e.g. `https://<host>.<tailnet>.ts.net:8443`).
2. **Option B — Cloudflare Tunnel (When user owns a domain)**:
   - Point Public Hostname `https://ha.<domain>` → `http://127.0.0.1:8123` via `cloudflare/cloudflared` container with `network_mode: host`.

### Phase 4: Configure Reverse Proxy Trust (CRITICAL HA 2026+ Gotcha!)
**Trap to avoid**: In Home Assistant `2026.x+`, once `.storage/http` contains `"yaml_migration_done": true`, **any `http:` block in `configuration.yaml` is completely ignored** and Funnel requests fail with `HTTP 400 Bad Request` (`A request from a reverse proxy was received from 127.0.0.1, but your HTTP integration is not set-up for reverse proxies`).

1. Inspect `<ha_config>/.storage/http`:
   - **If `yaml_migration_done: true`**:
     1. Ensure NO `http:` block is added to `configuration.yaml` (avoids deprecation repair warning).
     2. Stop HA (`docker stop homeassistant`), patch `.storage/http` → `data.stable`:
        ```json
        "use_x_forwarded_for": true,
        "trusted_proxies": ["127.0.0.1/32", "::1/128", "172.16.0.0/12"]
        ```
     3. Start HA (`docker start homeassistant`).
   - **If `.storage/http` does not exist or `yaml_migration_done` is false (HA 2024–2025)**:
     - Add `http: { use_x_forwarded_for: true, trusted_proxies: [127.0.0.1, ::1, 172.16.0.0/12] }` to `configuration.yaml`.
2. **Mandatory Live Verification** (use `GET`, NOT `HEAD`/`-I` because HA frontend returns `405` on `HEAD`):
   ```bash
   curl -s -o /dev/null -w "GET / -> %{http_code}\n" <PUBLIC_URL>/
   curl -s -o /dev/null -w "GET /auth/authorize -> %{http_code}\n" <PUBLIC_URL>/auth/authorize
   ```
   Both MUST return **`200`**!

### Phase 5: Configure `scripts.yaml` (4 Action Scenes) & `google_assistant:` in `configuration.yaml`
1. **No Service Account JSON is required!** Setting `report_state: false` requires ONLY `project_id` (the lowercase string ID from `https://console.home.google.com/projects/<PROJECT_ID>`, NOT the numeric Google Cloud Project Number).
2. Add the 4 action scripts to `<ha_config>/scripts.yaml` targeting `button.<slug>_{reboot,sleep,hibernate,lock_screen}` (`action: button.press`).
3. Add `google_assistant:` to `<ha_config>/configuration.yaml` with:
   - `project_id: <PROJECT_ID>`
   - `report_state: false` (or `true` + `service_account: !include SERVICE_ACCOUNT.json` if user explicitly provides a real Service Account JSON containing `client_email` and `private_key`)
   - `expose_by_default: false`
   - `exposed_domains: [switch, script]`
   - `entity_config:` mapping `switch.<slug>` + the 4 `script.<slug>_{reboot,sleep,hibernate,lock}` entities with clean names and bilingual aliases.
4. Validate & restart sequentially (NEVER edit `configuration.yaml` in parallel with `docker restart`):
   ```bash
   docker exec homeassistant python3 -m homeassistant --script check_config -c /config
   docker restart homeassistant
   ```
5. Verify the fulfillment endpoint is live:
   ```bash
   curl -s -o /dev/null -w "POST /api/google_assistant -> %{http_code}\n" -X POST <PUBLIC_URL>/api/google_assistant
   ```
   Must return **`401`** (proves the route is registered and awaiting Google OAuth).

### Phase 6: Guide the Human Through Google Home Developer Console & Phone App
Provide the user with **exact, zero-whitespace copy-paste values** for `https://console.home.google.com/projects/<PROJECT_ID>` → **Cloud-to-cloud → Add / Develop**:

| Field | Exact Value |
| :--- | :--- |
| **Development / Integration name** | `My Home Assistant` (user's choice) |
| **Device types** | `Switch` + `Scene` |
| **App icon** | Any square PNG ≥ 144×144 (required to Save) |
| **Cloud fulfillment URL** | `<PUBLIC_URL>/api/google_assistant` |
| **OAuth Client ID** | `https://oauth-redirect.googleusercontent.com/r/<PROJECT_ID>` |
| **OAuth Client Secret** | Any non-empty string (e.g. `ha-wol-secret-2026`) |
| **Authorization URL** | `<PUBLIC_URL>/auth/authorize` |
| **Token URL** | `<PUBLIC_URL>/auth/token` |

**Common Pitfalls to Warn the User About**:
- **Do NOT create a Google Cloud OAuth 2.0 Client (`*.apps.googleusercontent.com` / `GOCSPX-...`)** — that is for outbound Google Assistant SDK, NOT inbound Google Home control!
- **No leading or trailing spaces** in any URL field (Google rejects leading spaces with `Please enter a valid https link`).
- **Must click `Save` → `Test` → `Start testing`** in `console.home.google.com` (never submit for public review).
- **Phone Google Home App**: sign in with the **same Google account**, force-close & reopen the app, go to **Devices → + Add → Works with Google Home**, select **`[test] My Home Assistant`**, and sign into Home Assistant.
- **Re-syncing after adding new scripts (when `report_state: false`)**: in Google Home App go to **Settings → Works with Google → `[test] My Home Assistant` → Reconnect account** (or tap **Check for new devices**).

## Verification Checklist
- [ ] `custom_components/pc_onoff/` installed and imports cleanly inside HA container.
- [ ] Existing `tailscale serve` services untouched; Funnel live on chosen HTTPS port.
- [ ] `GET <PUBLIC_URL>/` and `GET <PUBLIC_URL>/auth/authorize` both return `HTTP 200`.
- [ ] `POST <PUBLIC_URL>/api/google_assistant` returns `HTTP 401`.
- [ ] `check_config` passes with zero errors; `switch.<slug>` + 4 action scripts configured.
- [ ] Live probe (`ping` or `tcp`) from HA container to target PC verified.
