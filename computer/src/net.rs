//! Trusted-network classification for the LAN/Tailscale-first security model.
//!
//! Public IPv6/IPv4 needs the HMAC envelope; loopback, RFC1918, link-local, IPv6 ULA
//! and the Tailscale/CGNAT range are considered local and may use bare commands.

use std::net::{IpAddr, Ipv4Addr, Ipv6Addr};

/// Whether an address belongs to a trusted local or Tailscale network.
pub fn is_trusted_source(ip: IpAddr) -> bool {
    match ip {
        IpAddr::V4(ip) => is_trusted_v4(ip),
        IpAddr::V6(ip) => is_trusted_v6(ip),
    }
}

fn is_trusted_v4(ip: Ipv4Addr) -> bool {
    let octets = ip.octets();
    ip.is_loopback()
        || ip.is_private()
        || ip.is_link_local()
        // Tailscale uses 100.64.0.0/10 (shared CGNAT space).
        || (octets[0] == 100 && (64..=127).contains(&octets[1]))
}

fn is_trusted_v6(ip: Ipv6Addr) -> bool {
    if let Some(mapped) = ip.to_ipv4_mapped() {
        return is_trusted_v4(mapped);
    }
    let segments = ip.segments();
    ip.is_loopback()
        || ip.is_unicast_link_local()
        // Unique local addresses fc00::/7 (includes Tailscale fd7a:115c:a1e0::/48).
        || (segments[0] & 0xfe00) == 0xfc00
}

#[cfg(test)]
mod tests {
    use super::*;

    fn trusted(value: &str) -> bool {
        is_trusted_source(value.parse().expect("ip address"))
    }

    #[test]
    fn trusts_private_and_tailscale_addresses() {
        for address in [
            "127.0.0.1",
            "10.1.2.3",
            "172.16.4.5",
            "192.168.0.13",
            "169.254.10.20",
            "100.64.0.1",
            "100.101.102.103",
            "100.127.255.254",
            "::1",
            "fe80::1",
            "fd7a:115c:a1e0::1",
            "fc00::1",
        ] {
            assert!(trusted(address), "{address} should be trusted");
        }
    }

    #[test]
    fn rejects_public_addresses() {
        for address in [
            "8.8.8.8",
            "1.1.1.1",
            "203.0.113.5",
            "100.128.0.1",
            "2001:4860:4860::8888",
            "2606:4700:4700::1111",
        ] {
            assert!(!trusted(address), "{address} should require authentication");
        }
    }
}
