//! Persistent TOFU list for public IPv6/IPv4 peers.
//!
//! Private LAN and Tailscale addresses never need an entry. A public peer is remembered
//! after its first valid HMAC command so later commands from that source need no signature.

use std::collections::HashSet;
use std::fs;
use std::io::{self, Write as _};
use std::net::IpAddr;
use std::path::{Path, PathBuf};

/// A set of public source addresses that completed the first authenticated command.
pub struct TrustedPeers {
    path: Option<PathBuf>,
    peers: HashSet<IpAddr>,
}

impl TrustedPeers {
    /// Load the peer list; a missing or malformed file simply starts empty.
    pub fn load(path: PathBuf) -> Self {
        let peers = fs::read_to_string(&path)
            .ok()
            .map(|content| {
                content
                    .lines()
                    .filter_map(|line| line.trim().parse::<IpAddr>().ok())
                    .collect()
            })
            .unwrap_or_default();
        Self {
            path: Some(path),
            peers,
        }
    }

    /// In-memory list for tests.
    #[cfg(test)]
    pub fn in_memory() -> Self {
        Self {
            path: None,
            peers: HashSet::new(),
        }
    }

    pub fn contains(&self, ip: IpAddr) -> bool {
        self.peers.contains(&ip)
    }

    /// Remember a public peer and persist the list with owner-only permissions.
    pub fn add(&mut self, ip: IpAddr) -> io::Result<()> {
        if !self.peers.insert(ip) {
            return Ok(());
        }
        self.save()
    }

    pub fn len(&self) -> usize {
        self.peers.len()
    }

    pub fn is_empty(&self) -> bool {
        self.peers.is_empty()
    }

    fn save(&self) -> io::Result<()> {
        let Some(path) = &self.path else {
            return Ok(());
        };
        if let Some(parent) = path.parent()
            && !parent.as_os_str().is_empty()
        {
            fs::create_dir_all(parent)?;
        }
        let temporary = path.with_extension(format!("tmp.{}", std::process::id()));
        let mut options = fs::OpenOptions::new();
        options.write(true).create(true).truncate(true);
        #[cfg(unix)]
        {
            use std::os::unix::fs::OpenOptionsExt;
            options.mode(0o600);
        }
        let mut file = options.open(&temporary)?;
        let mut peers: Vec<String> = self.peers.iter().map(|ip| ip.to_string()).collect();
        peers.sort();
        for peer in peers {
            writeln!(file, "{peer}")?;
        }
        file.sync_all()?;
        drop(file);
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(&temporary, fs::Permissions::from_mode(0o600))?;
        }
        fs::rename(&temporary, path)
    }
}

/// Remove every remembered public peer.
pub fn clear(path: &Path) -> io::Result<()> {
    match fs::remove_file(path) {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(error),
    }
}

/// Default peer-list path inside the daemon data directory.
pub fn path_in(directory: &Path) -> PathBuf {
    directory.join("trusted_peers")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::test_support::TempDir;

    #[test]
    fn remembers_peers_across_reloads() {
        let dir = TempDir::new("trusted-peers");
        let path = path_in(dir.path());
        let mut peers = TrustedPeers::load(path.clone());
        assert!(!peers.contains("203.0.113.5".parse().expect("ip")));

        peers
            .add("203.0.113.5".parse().expect("ip"))
            .expect("add peer");
        peers
            .add("2001:db8::1".parse().expect("ip"))
            .expect("add ipv6");

        let reloaded = TrustedPeers::load(path);
        assert!(reloaded.contains("203.0.113.5".parse().expect("ip")));
        assert!(reloaded.contains("2001:db8::1".parse().expect("ip")));
        assert_eq!(reloaded.len(), 2);
    }
}
