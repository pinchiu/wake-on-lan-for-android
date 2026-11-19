"""Constants for the Wake-on-LAN & PC Power (pc_onoff) integration."""

from typing import Final

DOMAIN: Final[str] = "pc_onoff"
PLATFORMS: Final[tuple[str, ...]] = ("switch", "button", "binary_sensor")

CONF_NAME: Final[str] = "name"
CONF_MAC: Final[str] = "mac"
CONF_HOST: Final[str] = "host"
CONF_UDP_PORT: Final[str] = "udp_port"
CONF_WOL_PORT: Final[str] = "wol_port"
CONF_BROADCAST_IP: Final[str] = "broadcast_ip"
CONF_PC_SECRET: Final[str] = "pc_secret"
CONF_OFF_ACTION: Final[str] = "off_action"
CONF_PROBE_MODE: Final[str] = "probe_mode"
CONF_PROBE_PORT: Final[str] = "probe_port"
CONF_SCAN_INTERVAL: Final[str] = "scan_interval"

DEFAULT_UDP_PORT: Final[int] = 9877
DEFAULT_WOL_PORT: Final[int] = 9
DEFAULT_BROADCAST_IP: Final[str] = "255.255.255.255"
DEFAULT_OFF_ACTION: Final[str] = "shutdown"
DEFAULT_PROBE_MODE: Final[str] = "ping"
DEFAULT_PROBE_PORT: Final[int] = 445
DEFAULT_SCAN_INTERVAL: Final[int] = 30
MIN_SCAN_INTERVAL: Final[int] = 5
MAX_SCAN_INTERVAL: Final[int] = 3600
OPTIMISTIC_HOLD_SECONDS: Final[float] = 25.0

PROBE_MODE_PING: Final[str] = "ping"
PROBE_MODE_TCP: Final[str] = "tcp"
PROBE_MODE_NONE: Final[str] = "none"
PROBE_MODES: Final[tuple[str, ...]] = (PROBE_MODE_PING, PROBE_MODE_TCP, PROBE_MODE_NONE)

OFF_ACTIONS: Final[tuple[str, ...]] = ("shutdown", "sleep", "hibernate")
