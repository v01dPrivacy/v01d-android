# Tor VPN DNS/UDP Routing Plan

## Problem
TorVpnService drops ALL UDP packets, including DNS queries (port 53).
Apps can't resolve hostnames → browsing fails even though TCP forwarding works.

## Solution: DNS-over-TCP through Tor SOCKS

Instead of implementing a full DNS server or SOCKS5 UDP ASSOCIATE,
convert DNS queries to DNS-over-TCP and forward through Tor's SOCKS proxy
(which already handles TCP natively).

### Flow
```
App sends DNS query (UDP :53)
  → TUN intercepts UDP packet
  → Parse DNS query from UDP payload
  → Open TCP connection to DNS resolver (1.1.1.1:53) via Tor SOCKS
  → Send DNS-over-TCP: [2-byte length][DNS wire format]
  → Read response: [2-byte length][DNS wire format]
  → Build UDP response packet (swap src/dst, correct checksums)
  → Write back to TUN
  → App receives DNS answer
```

### Why DNS-over-TCP
- Tor SOCKS handles TCP natively (proven in our bidirectional forwarding)
- DNS-over-TCP is standard (RFC 1035 §4.2.2)
- DNS resolution happens through Tor → no DNS leaks
- No need for SOCKS5 UDP ASSOCIATE (Tor may not support it)
- No need for a local DNS server daemon

### Components
1. `handleUdp()` in TorVpnService — intercept UDP packets
2. `resolveDnsOverTcp()` — DNS-over-TCP through SOCKS
3. UDP checksum calculation (IP pseudo-header + UDP header + payload)
4. Build UDP response packet

### Non-DNS UDP
Other UDP traffic (QUIC, games, VoIP) is still dropped.
This is acceptable — most privacy-sensitive traffic is TCP (HTTPS).

## Files
- Update `TorVpnService.kt` — add UDP/DNS handling
