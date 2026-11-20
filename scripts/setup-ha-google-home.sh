#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# setup-ha-google-home.sh
#
# One-command installer for the pc_onoff Home Assistant integration plus free
# Google Home voice control through Tailscale Funnel (no Nabu Casa fee and no
# Service Account JSON when report_state: false).
#
# Tested against Home Assistant 2026.9.1 on Docker. Safe defaults:
#   * never overwrites an existing Tailscale Funnel service on port 443
#   * patches Home Assistant 2026+ .storage/http instead of the ignored YAML
#   * backs up configuration.yaml, scripts.yaml and .storage/http
#   * every write is idempotent; --dry-run changes nothing
#
# Usage: ./scripts/setup-ha-google-home.sh --help
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
COMPONENT_SRC="${REPO_ROOT}/custom_components/pc_onoff"

HA_CONFIG=""
CONTAINER="homeassistant"
PROJECT_ID=""
ENTITY_SLUG=""
PC_NAME="Main PC"
HTTPS_PORT=""
PUBLIC_URL=""
NO_FUNNEL=0
DRY_RUN=0

# Google Home Developer Console values (Client Secret is arbitrary; HA does
# not validate it for the local google_assistant integration).
OAUTH_CLIENT_SECRET="ha-wol-secret-2026"

# ---------------------------------------------------------------------------
# Logging / helpers
# ---------------------------------------------------------------------------
info()  { printf '[INFO] %s\n' "$*"; }
warn()  { printf '[WARN] %s\n' "$*" >&2; }
error() { printf '[ERROR] %s\n' "$*" >&2; }
die()   { error "$*"; exit 1; }

run() {
    if (( DRY_RUN )); then
        printf '  [dry-run] %s\n' "$*"
    else
        "$@"
    fi
}

need_cmd() { command -v "$1" >/dev/null 2>&1; }

slugify() {
    printf '%s' "$1" \
        | tr '[:upper:]' '[:lower:]' \
        | sed -E 's/[^a-z0-9]+/_/g; s/^_+//; s/_+$//'
}

yaml_escape() {
    local value="$1"
    value="${value//\\/\\\\}"
    value="${value//\"/\\\"}"
    printf '%s' "$value"
}

usage() {
    cat <<'EOF'
Install pc_onoff into Home Assistant and connect it to Google Home for free.

Usage:
  ./scripts/setup-ha-google-home.sh [options]

Options:
  --ha-config PATH      Home Assistant config directory.
                        Auto-detected from ~/homeassistant/config, /config,
                        then ~/.homeassistant.
  --container NAME      Home Assistant Docker container name (default:
                        homeassistant). Use --container "" for a Core install.
  --project-id ID       Google Home project id (REQUIRED for a real run).
                        Use the lowercase id from:
                        https://console.home.google.com/projects/<ID>
                        NOT the numeric Google Cloud project number.
  --entity-slug SLUG    Entity slug used for switch.<slug> and script.<slug>_*.
                        Default: derived from --pc-name (e.g. Main PC ->
                        main_pc).
  --pc-name NAME        Friendly PC name shown in Google Home (default:
                        "Main PC").
  --https-port PORT     Tailscale Funnel HTTPS port: 443, 8443 or 10000.
                        Default: first free port, never overwriting an
                        existing tailscale serve/funnel service.
  --public-url URL      Existing public HTTPS URL. Required with --no-funnel.
  --no-funnel           Do not modify Tailscale; use --public-url instead.
  --dry-run             Print every planned change without writing files,
                        stopping containers, restarting Home Assistant or
                        calling Tailscale.
  -h, --help            Show this help and exit.

Examples:
  # Preview the full plan (no changes)
  ./scripts/setup-ha-google-home.sh \
    --project-id my-home-1234 --pc-name "Gaming PC" --dry-run

  # Install with Tailscale Funnel on the first free port
  ./scripts/setup-ha-google-home.sh \
    --ha-config ~/homeassistant/config \
    --project-id my-home-1234 \
    --entity-slug gaming_pc \
    --pc-name "Gaming PC"

  # Port 443 is already used by another service: use 8443 explicitly
  ./scripts/setup-ha-google-home.sh \
    --project-id my-home-1234 --pc-name "Gaming PC" --https-port 8443

  # No Tailscale: reuse an existing Cloudflare Tunnel / reverse proxy
  ./scripts/setup-ha-google-home.sh \
    --project-id my-home-1234 --pc-name "Gaming PC" \
    --no-funnel --public-url https://ha.example.com

What it does:
  1. Copies custom_components/pc_onoff/ into <ha-config>/custom_components/.
  2. Configures reverse-proxy trust:
       - HA 2026+: patches .storage/http data.stable when
         yaml_migration_done == true (configuration.yaml http: is ignored).
       - Older HA: appends an http: block to configuration.yaml.
  3. Selects a free Tailscale Funnel HTTPS port (443 -> 8443 -> 10000),
     enables funnel to http://127.0.0.1:8123 and derives the public URL.
  4. Idempotently adds 4 Google Home scripts (reboot / sleep / hibernate /
     lock) to <ha-config>/scripts.yaml.
  5. Idempotently adds the free google_assistant: block with
     report_state: false (no Service Account JSON) to configuration.yaml.
  6. Runs check_config, restarts Home Assistant and verifies:
       GET  <PUBLIC_URL>/                  -> 200
       GET  <PUBLIC_URL>/auth/authorize    -> 200
       POST <PUBLIC_URL>/api/google_assistant -> 401
  7. Prints the exact Google Home Developer Console copy/paste table.

Docs: docs/home-assistant.md
Skill: docs/skills/ha-google-home-pc-setup/SKILL.md
EOF
}

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --ha-config)    HA_CONFIG="${2:?--ha-config needs a value}"; shift 2 ;;
        --container)    CONTAINER="${2-}"; shift 2 ;;
        --project-id)   PROJECT_ID="${2:?--project-id needs a value}"; shift 2 ;;
        --entity-slug)  ENTITY_SLUG="${2:?--entity-slug needs a value}"; shift 2 ;;
        --pc-name)      PC_NAME="${2:?--pc-name needs a value}"; shift 2 ;;
        --https-port)   HTTPS_PORT="${2:?--https-port needs a value}"; shift 2 ;;
        --public-url)   PUBLIC_URL="${2:?--public-url needs a value}"; shift 2 ;;
        --no-funnel)    NO_FUNNEL=1; shift ;;
        --dry-run)      DRY_RUN=1; shift ;;
        -h|--help)      usage; exit 0 ;;
        *)              error "Unknown option: $1"; echo; usage; exit 2 ;;
    esac
done

need_cmd python3 || die "python3 is required."
need_cmd curl    || die "curl is required."

# ---------------------------------------------------------------------------
# Resolve defaults and validate
# ---------------------------------------------------------------------------
if [[ -z "$ENTITY_SLUG" ]]; then
    ENTITY_SLUG="$(slugify "$PC_NAME")"
fi
[[ "$ENTITY_SLUG" =~ ^[a-z0-9_]+$ ]] \
    || die "--entity-slug must contain only lowercase letters, digits and underscores."

if [[ -z "$PROJECT_ID" ]]; then
    die "--project-id is required (lowercase id from https://console.home.google.com/projects/<ID>)."
fi
[[ "$PROJECT_ID" =~ ^[a-z0-9-]+$ ]] \
    || die "--project-id must be a lowercase project id (letters, digits and hyphens)."

if [[ -z "$HA_CONFIG" ]]; then
    for candidate in "$HOME/homeassistant/config" "/config" "$HOME/.homeassistant"; do
        if [[ -f "${candidate}/configuration.yaml" ]]; then
            HA_CONFIG="$candidate"
            break
        fi
    done
fi
if [[ -z "$HA_CONFIG" || ! -f "${HA_CONFIG}/configuration.yaml" ]]; then
    die "Home Assistant config directory not found. Pass --ha-config /path/to/config."
fi
HA_CONFIG="$(cd -- "$HA_CONFIG" && pwd)"

if [[ -n "$HTTPS_PORT" && ! "$HTTPS_PORT" =~ ^(443|8443|10000)$ ]]; then
    die "--https-port must be one of 443, 8443 or 10000 (Tailscale Funnel supported ports)."
fi

PUBLIC_URL="${PUBLIC_URL%/}"
if (( NO_FUNNEL )) && [[ -z "$PUBLIC_URL" ]]; then
    die "--no-funnel requires --public-url https://your-host.example.com"
fi

CONTAINER_OK=0
CONTAINER_RUNNING=0
if [[ -n "$CONTAINER" ]] && need_cmd docker && docker inspect "$CONTAINER" >/dev/null 2>&1; then
    CONTAINER_OK=1
    if [[ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null || true)" == "true" ]]; then
        CONTAINER_RUNNING=1
    fi
fi

info "Repository:      $REPO_ROOT"
info "HA config:       $HA_CONFIG"
info "PC name:         $PC_NAME"
info "Entity slug:     $ENTITY_SLUG"
info "Project id:      $PROJECT_ID"
if (( CONTAINER_OK )); then
    info "HA container:    $CONTAINER (running=$CONTAINER_RUNNING)"
else
    warn "Container '$CONTAINER' not found; Home Assistant files will be patched but Docker steps are skipped."
fi
if (( DRY_RUN )); then
    warn "DRY-RUN: no files will be written, no services restarted."
fi

# ---------------------------------------------------------------------------
# Backup helpers
# ---------------------------------------------------------------------------
BACKED_UP_CONFIG=0
BACKED_UP_SCRIPTS=0

backup_config_once() {
    if (( BACKED_UP_CONFIG )); then
        return 0
    fi
    BACKED_UP_CONFIG=1
    local cfg="${HA_CONFIG}/configuration.yaml"
    local backup="${cfg}.bak.$(date +%Y%m%d-%H%M%S)"
    info "Backing up configuration.yaml -> ${backup}"
    run cp -a "$cfg" "$backup"
}

backup_scripts_once() {
    if (( BACKED_UP_SCRIPTS )); then
        return 0
    fi
    BACKED_UP_SCRIPTS=1
    local file="${HA_CONFIG}/scripts.yaml"
    [[ -f "$file" ]] || return 0
    local backup="${file}.bak.$(date +%Y%m%d-%H%M%S)"
    info "Backing up scripts.yaml -> ${backup}"
    run cp -a "$file" "$backup"
}

ensure_trailing_newline() {
    local file="$1"
    [[ -f "$file" && -s "$file" ]] || return 0
    if [[ -n "$(tail -c 1 "$file")" ]]; then
        printf '\n' >> "$file"
    fi
}

append_block() {
    local file="$1" content="$2"
    if (( DRY_RUN )); then
        printf '  [dry-run] append to %s:\n%s\n' "$file" "$content"
        return 0
    fi
    ensure_trailing_newline "$file"
    if [[ -s "$file" ]]; then
        printf '\n' >> "$file"
    fi
    printf '%s\n' "$content" >> "$file"
}

# ---------------------------------------------------------------------------
# 1. Install custom_components/pc_onoff
# ---------------------------------------------------------------------------
install_component() {
    [[ -d "$COMPONENT_SRC" ]] || die "Source component not found: $COMPONENT_SRC"
    local destination="${HA_CONFIG}/custom_components/pc_onoff"

    info "Installing pc_onoff integration -> ${destination}"
    if (( DRY_RUN )); then
        printf '  [dry-run] rm -rf %s && cp -a %s/. %s/ (excluding __pycache__)\n' \
            "$destination" "$COMPONENT_SRC" "$destination"
        return 0
    fi

    mkdir -p "${HA_CONFIG}/custom_components"
    rm -rf "$destination"
    mkdir -p "$destination"
    cp -a "${COMPONENT_SRC}/." "${destination}/"
    find "$destination" -type d -name '__pycache__' -prune -exec rm -rf {} +
    find "$destination" -type f -name '*.pyc' -delete
    info "Integration files copied (no __pycache__)."
}

# ---------------------------------------------------------------------------
# 2. Reverse-proxy trust (HA 2026+ .storage/http vs configuration.yaml)
# ---------------------------------------------------------------------------
http_migration_done() {
    local storage="${HA_CONFIG}/.storage/http"
    [[ -f "$storage" ]] || return 1
    python3 - "$storage" >/dev/null 2>&1 <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    document = json.load(handle)
data = document.get("data")
if not isinstance(data, dict):
    raise SystemExit(1)
raise SystemExit(0 if data.get("yaml_migration_done") is True else 1)
PY
}

patch_http_storage() {
    local storage="${HA_CONFIG}/.storage/http"
    local backup="${storage}.bak.$(date +%Y%m%d-%H%M%S)"

    info "HA 2026+ .storage/http migration detected (yaml_migration_done=true)."
    info "Patching data.stable with reverse-proxy trust."

    if (( DRY_RUN )); then
        printf '  [dry-run] stop container %s, patch %s data.stable, start container\n' \
            "$CONTAINER" "$storage"
        return 0
    fi

    run cp -a "$storage" "$backup"

    local stopped=0
    if (( CONTAINER_RUNNING )); then
        info "Stopping container ${CONTAINER} ..."
        docker stop -t 30 "$CONTAINER" >/dev/null
        stopped=1
    else
        warn "Container is not running; ensure Home Assistant is stopped while .storage/http is patched."
    fi

    if ! python3 - "$storage" <<'PY'
import json
import os
import sys
import tempfile

path = sys.argv[1]
with open(path, encoding="utf-8") as handle:
    document = json.load(handle)

data = document.setdefault("data", {})
stable = data.setdefault("stable", {})
stable["use_x_forwarded_for"] = True
stable["trusted_proxies"] = [
    "127.0.0.1/32",
    "::1/128",
    "172.16.0.0/12",
]

directory = os.path.dirname(path) or "."
fd, temporary = tempfile.mkstemp(dir=directory, prefix=".http.", text=True)
try:
    with os.fdopen(fd, "w", encoding="utf-8") as handle:
        json.dump(document, handle, indent=4)
        handle.write("\n")
    os.replace(temporary, path)
except BaseException:
    try:
        os.unlink(temporary)
    except OSError:
        pass
    raise
PY
    then
        if (( stopped )); then
            docker start "$CONTAINER" >/dev/null || true
        fi
        die "Failed to patch ${storage}; backup is ${backup}."
    fi

    if (( stopped )); then
        info "Starting container ${CONTAINER} ..."
        docker start "$CONTAINER" >/dev/null
    fi
    info "Patched .storage/http: use_x_forwarded_for=true, trusted_proxies=[127.0.0.1/32, ::1/128, 172.16.0.0/12]"
}

ensure_http_yaml_block() {
    local cfg="${HA_CONFIG}/configuration.yaml"

    if grep -qE '^http:' "$cfg"; then
        warn "configuration.yaml already has an http: block; leaving it unchanged."
        return 0
    fi

    info "No HA 2026 .storage/http migration; appending http: trust block to configuration.yaml."
    backup_config_once
    append_block "$cfg" '# Added by scripts/setup-ha-google-home.sh — reverse proxy trust for Tailscale Funnel
http:
  use_x_forwarded_for: true
  trusted_proxies:
    - 127.0.0.1/32
    - "::1/128"
    - 172.16.0.0/12'
}

configure_http_trust() {
    if http_migration_done; then
        patch_http_storage
    else
        ensure_http_yaml_block
    fi
}

# ---------------------------------------------------------------------------
# 3. Tailscale Funnel (never overwrite an existing service)
# ---------------------------------------------------------------------------
tailscale_available() { need_cmd tailscale; }

tailscale_root() {
    if [[ "$(id -u)" -eq 0 ]]; then
        tailscale "$@"
    elif need_cmd sudo; then
        sudo tailscale "$@"
    else
        tailscale "$@"
    fi
}

tailscale_status_text() {
    {
        tailscale serve status 2>/dev/null || true
        tailscale funnel status 2>/dev/null || true
    }
}

port_in_use() {
    local port="$1" status_text="$2"
    local json rc=0

    # Prefer the structured form: it reports the default HTTPS port (443)
    # reliably, even though the human-readable status prints it without :443.
    json="$(tailscale serve status --json 2>/dev/null || true)"
    if [[ -n "$json" ]]; then
        printf '%s' "$json" | python3 -c '
import json
import sys

port = str(sys.argv[1])
try:
    document = json.load(sys.stdin)
except Exception:
    raise SystemExit(2)
if not isinstance(document, dict):
    raise SystemExit(2)
if port in (document.get("TCP") or {}):
    raise SystemExit(0)
for section in ("Web", "AllowFunnel"):
    entries = document.get(section) or {}
    if any(str(key).endswith(":" + port) for key in entries):
        raise SystemExit(0)
raise SystemExit(1)
' "$port" || rc=$?

        if [[ "$rc" -eq 0 ]]; then
            return 0
        fi
    fi

    # Text fallback (older Tailscale or unparsable JSON).
    printf '%s\n' "$status_text" | grep -Eq ":${port}([^0-9]|$)" && return 0
    if [[ "$port" == "443" ]]; then
        # A service on the default HTTPS port is printed as https://host/ (no :443).
        printf '%s\n' "$status_text" | grep -Eq 'https://[^[:space:]:]+/?$' && return 0
    fi
    return 1
}

select_funnel_port() {
    local status_text="$1"

    if [[ -n "$HTTPS_PORT" ]]; then
        if port_in_use "$HTTPS_PORT" "$status_text"; then
            die "Port ${HTTPS_PORT} is already used by tailscale serve/funnel; refusing to overwrite it. Remove that service or pick another port."
        fi
        printf '%s' "$HTTPS_PORT"
        return 0
    fi

    local candidate
    for candidate in 443 8443 10000; do
        if ! port_in_use "$candidate" "$status_text"; then
            printf '%s' "$candidate"
            return 0
        fi
    done
    return 1
}

derive_public_url() {
    local port="$1" dns=""

    dns="$(tailscale status --json 2>/dev/null | python3 -c '
import json
import sys

try:
    document = json.load(sys.stdin)
except Exception:
    raise SystemExit(1)
self_data = document.get("Self") or {}
name = (self_data.get("DNSName") or "").rstrip(".")
print(name)
' 2>/dev/null || true)"

    if [[ -z "$dns" ]]; then
        dns="$(tailscale status 2>/dev/null | awk 'NR==1 {print $2}' | sed 's/\.$//' || true)"
    fi

    [[ -n "$dns" ]] || return 1

    if [[ "$port" == "443" ]]; then
        printf 'https://%s' "$dns"
    else
        printf 'https://%s:%s' "$dns" "$port"
    fi
}

configure_funnel() {
    if (( NO_FUNNEL )); then
        info "Skipping Tailscale Funnel (--no-funnel); using ${PUBLIC_URL}"
        return 0
    fi

    if ! tailscale_available; then
        die "tailscale is not installed. Use --no-funnel --public-url <https://...> or install Tailscale."
    fi

    local status_text port
    status_text="$(tailscale_status_text)"
    port="$(select_funnel_port "$status_text")" \
        || die "All Tailscale Funnel ports (443, 8443, 10000) are already in use."

    if [[ -n "$HTTPS_PORT" ]]; then
        info "Using requested Tailscale Funnel HTTPS port: ${port}"
    else
        info "Selected first free Tailscale Funnel HTTPS port: ${port}"
    fi

    if (( DRY_RUN )); then
        printf '  [dry-run] tailscale funnel --bg --https=%s http://127.0.0.1:8123\n' "$port"
    elif ! tailscale_root funnel --bg --https="$port" http://127.0.0.1:8123; then
        error "tailscale funnel failed."
        error "If Funnel is not enabled yet, open the https://login.tailscale.com/f/funnel?... URL printed above,"
        error "click Enable Funnel, then rerun this script."
        exit 1
    fi

    if [[ -z "$PUBLIC_URL" ]]; then
        PUBLIC_URL="$(derive_public_url "$port")" \
            || die "Could not derive the public URL. Rerun with --public-url https://<host>.<tailnet>.ts.net:${port}"
        info "Derived public URL: ${PUBLIC_URL}"
    else
        info "Using supplied public URL: ${PUBLIC_URL}"
    fi
}

# ---------------------------------------------------------------------------
# 4. scripts.yaml — four Google Home action scripts
# ---------------------------------------------------------------------------
ensure_scripts_include() {
    local cfg="${HA_CONFIG}/configuration.yaml"
    if ! grep -qE '^script:[[:space:]]*!include[[:space:]]+scripts\.yaml' "$cfg"; then
        warn "configuration.yaml does not include scripts.yaml. Add: script: !include scripts.yaml"
    fi
}

append_scripts() {
    local file="${HA_CONFIG}/scripts.yaml"
    ensure_scripts_include
    backup_scripts_once
    [[ -f "$file" ]] || { (( DRY_RUN )) || : > "$file"; }

    local key script_key target alias icon block
    for key in reboot sleep hibernate lock; do
        script_key="${ENTITY_SLUG}_${key}"
        target="button.${ENTITY_SLUG}_${key}"
        [[ "$key" == "lock" ]] && target="button.${ENTITY_SLUG}_lock_screen"

        case "$key" in
            reboot)    alias="Reboot ${PC_NAME}";    icon="mdi:restart" ;;
            sleep)     alias="Sleep ${PC_NAME}";     icon="mdi:power-sleep" ;;
            hibernate) alias="Hibernate ${PC_NAME}"; icon="mdi:snowflake" ;;
            lock)      alias="Lock ${PC_NAME}";      icon="mdi:lock" ;;
        esac

        if [[ -f "$file" ]] && grep -qE "^${script_key}:" "$file"; then
            info "scripts.yaml already contains ${script_key}; skipping."
            continue
        fi

        block="${script_key}:
  alias: \"$(yaml_escape "$alias")\"
  icon: \"${icon}\"
  mode: single
  sequence:
    - service: button.press
      target:
        entity_id: ${target}"

        info "Adding script.${script_key} -> ${target}"
        append_block "$file" "$block"
    done
}

# ---------------------------------------------------------------------------
# 5. google_assistant: (free, report_state: false, no JSON)
# ---------------------------------------------------------------------------
append_google_assistant() {
    local cfg="${HA_CONFIG}/configuration.yaml"
    local pc_esc
    pc_esc="$(yaml_escape "$PC_NAME")"

    if grep -qE '^google_assistant:' "$cfg"; then
        warn "configuration.yaml already has a google_assistant: block; leaving it unchanged."
        return 0
    fi

    info "Appending free google_assistant: block (report_state: false, no Service Account JSON)."
    backup_config_once

    local block
    block="# Added by scripts/setup-ha-google-home.sh — free Google Home (no Nabu Casa, no Service Account JSON)
google_assistant:
  project_id: \"${PROJECT_ID}\"
  report_state: false
  expose_by_default: false
  exposed_domains:
    - switch
    - script
  entity_config:
    \"switch.${ENTITY_SLUG}\":
      name: \"${pc_esc}\"
      aliases:
        - \"${pc_esc}\"
        - \"${pc_esc} 電腦\"
    \"script.${ENTITY_SLUG}_reboot\":
      name: \"Reboot ${pc_esc}\"
      aliases:
        - \"Reboot ${pc_esc}\"
        - \"重新啟動 ${pc_esc}\"
    \"script.${ENTITY_SLUG}_sleep\":
      name: \"Sleep ${pc_esc}\"
      aliases:
        - \"Sleep ${pc_esc}\"
        - \"睡眠 ${pc_esc}\"
    \"script.${ENTITY_SLUG}_hibernate\":
      name: \"Hibernate ${pc_esc}\"
      aliases:
        - \"Hibernate ${pc_esc}\"
        - \"休眠 ${pc_esc}\"
    \"script.${ENTITY_SLUG}_lock\":
      name: \"Lock ${pc_esc}\"
      aliases:
        - \"Lock ${pc_esc}\"
        - \"鎖定 ${pc_esc}\""

    append_block "$cfg" "$block"
}

# ---------------------------------------------------------------------------
# 6. check_config, restart and live verification
# ---------------------------------------------------------------------------
check_config() {
    info "Validating Home Assistant configuration ..."
    if (( CONTAINER_OK )); then
        run docker exec "$CONTAINER" python3 -m homeassistant --script check_config -c /config
    elif need_cmd hass; then
        run hass --script check_config -c "$HA_CONFIG"
    else
        warn "No Docker container or hass CLI available; run check_config manually."
    fi
}

restart_home_assistant() {
    info "Restarting Home Assistant ..."
    if (( CONTAINER_OK )); then
        run docker restart "$CONTAINER" >/dev/null
    elif need_cmd ha; then
        run ha core restart
    else
        warn "Restart Home Assistant manually, then rerun the verification checks."
    fi
}

http_code() {
    curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$1" 2>/dev/null || true
}

verify_endpoints() {
    local failures=0 code attempt

    info "Waiting for Home Assistant at ${PUBLIC_URL}/ ..."
    for attempt in $(seq 1 45); do
        code="$(http_code "${PUBLIC_URL}/")"
        [[ "$code" == "200" ]] && break
        sleep 2
    done

    code="$(http_code "${PUBLIC_URL}/")"
    if [[ "$code" == "200" ]]; then
        info "GET ${PUBLIC_URL}/ -> 200 OK"
    else
        error "GET ${PUBLIC_URL}/ -> ${code:-no response} (expected 200)"
        failures=$((failures + 1))
    fi

    code="$(http_code "${PUBLIC_URL}/auth/authorize")"
    if [[ "$code" == "200" ]]; then
        info "GET ${PUBLIC_URL}/auth/authorize -> 200 OK"
    else
        error "GET ${PUBLIC_URL}/auth/authorize -> ${code:-no response} (expected 200)"
        failures=$((failures + 1))
    fi

    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 -X POST "${PUBLIC_URL}/api/google_assistant" 2>/dev/null || true)"
    if [[ "$code" == "401" ]]; then
        info "POST ${PUBLIC_URL}/api/google_assistant -> 401 Unauthorized (expected)"
    else
        error "POST ${PUBLIC_URL}/api/google_assistant -> ${code:-no response} (expected 401)"
        failures=$((failures + 1))
    fi

    return "$failures"
}

# ---------------------------------------------------------------------------
# 7. Final copy-paste table
# ---------------------------------------------------------------------------
print_console_table() {
    cat <<EOF

================================================================================
 Google Home Developer Console — copy/paste values
================================================================================
 Project console : https://console.home.google.com/projects/${PROJECT_ID}
 PC name         : ${PC_NAME}
 Entity slug     : ${ENTITY_SLUG}

 WARNING: paste values with NO leading or trailing spaces.
 WARNING: do NOT create an OAuth Client with an *.apps.googleusercontent.com
          client id. Use the oauth-redirect client id below exactly.

 | Field                         | Exact value                                             |
 | :---------------------------- | :------------------------------------------------------ |
 | Development / Integration name| ${PC_NAME}                                              |
 | Device types                  | Switch + Scene                                          |
 | App icon                      | Any square PNG >= 144x144 (required to Save)            |
 | Cloud fulfillment URL          | ${PUBLIC_URL}/api/google_assistant                      |
 | OAuth Client ID               | https://oauth-redirect.googleusercontent.com/r/${PROJECT_ID} |
 | OAuth Client Secret           | ${OAUTH_CLIENT_SECRET}                                  |
 | Authorization URL             | ${PUBLIC_URL}/auth/authorize                            |
 | Token URL                     | ${PUBLIC_URL}/auth/token                                |

 Next steps:
 1. Open the project console above, choose Cloud-to-cloud -> Add / Develop.
 2. Paste every value exactly as shown, upload a square icon, then:
        Save  ->  Test  ->  Start testing
    (Do not submit for public review.)
 3. On the phone, sign in with the SAME Google account:
        Google Home App -> Devices -> + Add -> Works with Google Home
        -> select "[test] ${PC_NAME}" -> sign into Home Assistant.
 4. To sync the 4 action scripts (Reboot / Sleep / Hibernate / Lock):
        Google Home App -> Settings -> Works with Google -> "[test] ${PC_NAME}"
        -> Reconnect account (or tap "Check for new devices").
 5. Voice control:
        "Hey Google, turn on ${PC_NAME}"   (Wake-on-LAN, 25 s optimistic hold)
        "Hey Google, turn off ${PC_NAME}"  (configured off_action over UDP 9877)
        "Hey Google, reboot ${PC_NAME}" / "sleep" / "hibernate" / "lock"

 Helper script : ./scripts/setup-ha-google-home.sh
 Agent skill   : docs/skills/ha-google-home-pc-setup/SKILL.md
 Full guide    : docs/home-assistant.md
================================================================================
EOF
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
    info "Step 1/6 — install the pc_onoff integration"
    install_component

    info "Step 2/6 — configure reverse-proxy trust"
    configure_http_trust

    info "Step 3/6 — configure Tailscale Funnel and public URL"
    configure_funnel

    info "Step 4/6 — add Google Home action scripts"
    append_scripts

    info "Step 5/6 — add google_assistant configuration"
    append_google_assistant

    info "Step 6/6 — validate, restart and verify"
    if (( DRY_RUN )); then
        if (( CONTAINER_OK )); then
            printf '  [dry-run] docker exec %s python3 -m homeassistant --script check_config -c /config\n' "$CONTAINER"
            printf '  [dry-run] docker restart %s\n' "$CONTAINER"
        else
            printf '  [dry-run] check_config + restart skipped (no Home Assistant container)\n'
        fi
        printf '  [dry-run] GET  %s/                 (expect 200)\n' "$PUBLIC_URL"
        printf '  [dry-run] GET  %s/auth/authorize   (expect 200)\n' "$PUBLIC_URL"
        printf '  [dry-run] POST %s/api/google_assistant (expect 401)\n' "$PUBLIC_URL"
    else
        check_config
        restart_home_assistant
        if ! verify_endpoints; then
            warn "One or more public endpoint checks failed. See docs/home-assistant.md troubleshooting."
            print_console_table
            exit 1
        fi
    fi

    print_console_table
}

main "$@"
